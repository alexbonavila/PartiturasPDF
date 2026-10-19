package com.abdev.partituraspdf.pdf.android.isolated

import android.app.Service
import android.content.Intent
import android.os.*
import com.abdev.partituraspdf.core.document.PageIndex
import com.abdev.partituraspdf.core.document.StorageDocumentRef
import com.abdev.partituraspdf.core.result.*
import com.abdev.partituraspdf.pdf.android.AndroidPdfReadEngine
import com.abdev.partituraspdf.pdf.api.*
import com.abdev.partituraspdf.storage.api.*
import java.io.BufferedOutputStream
import java.io.DataOutputStream
import java.io.IOException
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking

/** No graph, storage resolver, credentials or UI. The isolated UID receives capabilities only. */
class PdfWorkerService : Service() {
    private val epoch = UUID.randomUUID().toString()
    private val quarantined = AtomicBoolean()
    private val dispatcher = Executors.newSingleThreadExecutor { runnable -> Thread(runnable, "pdf-native") }.asCoroutineDispatcher()
    private var session: PdfReadSession? = null
    private var token = ""

    override fun onCreate() {
        super.onCreate()
        check(Process.myUid() != applicationInfo.uid) { "PDF service must have an isolated UID" }
    }
    private fun retireSelf() {
        quarantined.set(true)
        // Only this isolated service can execute this path; never kill a supplied PID/UID.
        check(Process.myUid() != applicationInfo.uid)
        Handler(Looper.getMainLooper()).post { Process.killProcess(Process.myPid()) }
    }
    override fun onDestroy() { retireSelf(); super.onDestroy() }
    override fun onBind(intent: Intent): IBinder = binder

    private fun work(id: String, action: suspend () -> Bundle): Bundle = runBlocking(dispatcher) {
        if (quarantined.get()) return@runBlocking PdfWorkerProtocol.failure(epoch, id, AppError.Conflict.STALE_SESSION, true)
        try { action() }
        catch (failure: Exception) {
            quarantined.set(true)
            // Defects are NOT ordinary invalid-PDF outcomes. Do not include document paths.
            PdfWorkerProtocol.reply(epoch, id).apply { putString("defect", failure.javaClass.simpleName); putBoolean("poisoned", true) }
        }
    }
    private fun current(e: String, id: String): PdfReadSession? = session.takeIf { e == epoch && id == token }
    private fun geometry(reply: Bundle, info: PageDisplayInfo): Bundle = reply.apply {
        putInt("pageWidth", info.width); putInt("pageHeight", info.height); putString("annotations", info.annotations.name)
    }
    private val binder = object : IPdfWorker.Stub() {
        override fun identity(): Bundle = PdfWorkerProtocol.reply(epoch).apply {
            putInt("pid", Process.myPid()); putLong("started", Process.getStartElapsedRealtime())
            putBoolean("isolated", Process.myUid() != applicationInfo.uid)
        }
        override fun open(id: String, input: ParcelFileDescriptor, dimension: Int, pixels: Long): Bundle {
            var adopted = false
            try { return work(id) {
                require(id.length in 1..128 && dimension in 1..16384 && pixels in 1..4_194_304L)
                check(session == null) { "Only one native session" }
                val handle = ReadHandle(object : ReadResource {
                    override val descriptor = input
                    override fun close() = input.close()
                }, ReadProvenance.Provider(StorageDocumentRef("isolated", id)), true)
                adopted = true
                val result = try { AndroidPdfReadEngine(RenderLimits(dimension, pixels), dispatcher).open(PdfReadSource(handle)) }
                    finally { if (handle.ownership == ReadOwnership.OWNED) handle.close() }
                when (result) {
                    is Outcome.Failure -> {
                        quarantined.set(true) // All native-open failures, on ALL APIs, are quarantined.
                        PdfWorkerProtocol.failure(epoch, id, result.error, true)
                    }
                    is Outcome.Success -> {
                        session = result.value; token = id
                        PdfWorkerProtocol.reply(epoch, id).apply { putInt("pages", result.value.info.pageCount) }
                    }
                }
            } } finally { if (!adopted) input.close() }
        }
        override fun info(e: String, id: String, page: Int): Bundle = work(id) {
            val active = current(e, id) ?: return@work PdfWorkerProtocol.failure(epoch, id, AppError.Conflict.SESSION_CLOSED)
            if (page !in 0 until active.info.pageCount) return@work PdfWorkerProtocol.failure(epoch, id, AppError.Validation.INVALID_PAGE)
            when (val result = active.displayInfo(ReadPageRef(active.info.id, active.info.sourceGeneration, PageIndex(page)))) {
                is Outcome.Failure -> { quarantined.set(true); PdfWorkerProtocol.failure(epoch, id, result.error, true) }
                is Outcome.Success -> geometry(PdfWorkerProtocol.reply(epoch, id), result.value)
            }
        }
        override fun render(e: String, id: String, request: String, page: Int, width: Int, height: Int, generation: Long, output: ParcelFileDescriptor): Bundle = output.use {
            work(id) {
                val active = current(e, id) ?: return@work PdfWorkerProtocol.failure(epoch, id, AppError.Conflict.SESSION_CLOSED)
                if (page !in 0 until active.info.pageCount) return@work PdfWorkerProtocol.failure(epoch, id, AppError.Validation.INVALID_PAGE)
                require(request.length in 1..128 && width > 0 && height > 0 && generation >= 0)
                val render = RenderRequest(RenderRequestId(request), ReadPageRef(active.info.id, active.info.sourceGeneration, PageIndex(page)), width, height, generation)
                val checked = render.validate(active.renderLimits)
                if (checked is Outcome.Failure) return@work PdfWorkerProtocol.failure(epoch, id, checked.error)
                when (val result = active.render(render)) {
                    is Outcome.Failure -> { quarantined.set(true); PdfWorkerProtocol.failure(epoch, id, result.error, true) }
                    is Outcome.Success -> result.value.use { lease ->
                        try {
                            DataOutputStream(BufferedOutputStream(ParcelFileDescriptor.AutoCloseOutputStream(output), 32768)).use { stream ->
                                PdfPixels.write(stream, epoch, id, request, width, height, generation) { y, row -> lease.bitmap.getPixels(row, 0, width, 0, y, width, 1) }
                            }
                            geometry(PdfWorkerProtocol.reply(epoch, id), lease.displayInfo).apply { putString("request", request) }
                        } catch (_: IOException) {
                            quarantined.set(true)
                            PdfWorkerProtocol.failure(epoch, id, AppError.Access.PROVIDER_IO_FAILURE, true)
                        }
                    }
                }
            }
        }
        override fun closeSession(e: String, id: String): Bundle = work(id) {
            val active = current(e, id) ?: return@work PdfWorkerProtocol.failure(epoch, id, AppError.Conflict.SESSION_CLOSED)
            try { active.close() } finally { session = null; token = "" }
            PdfWorkerProtocol.reply(epoch, id)
        }
        override fun retire() { retireSelf() }
    }
}
