package com.abdev.partituraspdf.pdf.android.isolated

import android.graphics.Bitmap
import androidx.core.graphics.createBitmap
import android.os.Bundle
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.os.RemoteException
import com.abdev.partituraspdf.core.result.AppError
import com.abdev.partituraspdf.core.result.Outcome
import com.abdev.partituraspdf.pdf.api.*
import com.abdev.partituraspdf.storage.api.ReadHandle
import java.io.Closeable
import java.io.BufferedInputStream
import java.io.DataInputStream
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class IsolatedPdfReadSession(
    private val worker: PdfWorkerConnection,
    internal val identity: PdfWorkerIdentity,
    private val owner: ReadHandle,
    override val info: ReadSessionInfo,
    override val renderLimits: RenderLimits,
) : PdfReadSession {
    private val closed = AtomicBoolean()
    private val operations = Mutex()
    val isClosed: Boolean get() = closed.get()
    private val token get() = info.id.value
    private fun valid(page: ReadPageRef): Outcome<Unit> = when {
        closed.get() -> Outcome.Failure(AppError.Conflict.SESSION_CLOSED)
        !worker.alive() || !worker.lifecycle.permits(identity) -> Outcome.Failure(AppError.Conflict.STALE_SESSION)
        else -> info.validate(page)
    }
    private fun geometry(reply: Bundle, page: ReadPageRef) = PageDisplayInfo(
        page, reply.getInt("pageWidth"), reply.getInt("pageHeight"),
        annotations = AnnotationRendering.valueOf(checkNotNull(reply.getString("annotations"))),
    )
    private suspend fun retire() {
        closed.set(true)
        withContext(NonCancellable + Dispatchers.IO) {
            worker.releaseOwner(owner)
        }
    }
    private suspend fun failure(reply: Bundle): Outcome.Failure? {
        PdfWorkerProtocol.check(reply, identity.epoch, token)
        val error = reply.getString("error") ?: return null
        if (reply.getBoolean("poisoned")) retire()
        return Outcome.Failure(PdfRemoteErrors.decode(error))
    }
    private suspend fun <T> operation(page: ReadPageRef, action: suspend () -> Outcome<T>): Outcome<T> =
        withContext(Dispatchers.IO) {
            operations.withLock {
                currentCoroutineContext().ensureActive()
                val checked = valid(page)
                if (checked is Outcome.Failure) {
                    if (!closed.get() && !worker.alive()) retire()
                    return@withLock checked
                }
                try {
                    val result = action()
                    currentCoroutineContext().ensureActive()
                    if (result is Outcome.Success && (closed.get() || !worker.alive())) {
                        // An unpublished lease is still ours when close races with RPC completion.
                        (result as? Outcome.Success<*>)?.value.let { if (it is RenderedPage) it.close() }
                        Outcome.Failure(AppError.Conflict.STALE_SESSION)
                    } else result
                } catch (_: RemoteException) {
                    retire()
                    Outcome.Failure(AppError.Conflict.STALE_SESSION)
                } catch (failure: Throwable) {
                    try { retire() } catch (cleanup: Throwable) { failure.addSuppressed(cleanup) }
                    throw failure // Includes cancellation and protocol/programming defects.
                }
            }
        }

    override suspend fun displayInfo(page: ReadPageRef): Outcome<PageDisplayInfo> = operation(page) {
        val reply = PdfWorkerCall.run(worker::close) { worker.remote!!.info(identity.epoch, token, page.page.value) }
        failure(reply) ?: Outcome.Success(geometry(reply, page))
    }

    override suspend fun render(request: RenderRequest): Outcome<RenderedPage> {
        var unpublished: RenderedPage? = null
        var delivered = false
        try {
            val result = operation(request.page) {
                val bounded = request.validate(renderLimits)
                if (bounded is Outcome.Failure) return@operation bounded
                // Wire request tokens have bounded length, independent of a caller's cache-key string.
                val wireRequest = java.util.UUID.randomUUID().toString()
                val bitmap = try { createBitmap(request.width, request.height, Bitmap.Config.ARGB_8888) }
                    catch (_: OutOfMemoryError) { return@operation Outcome.Failure(AppError.Resource.RENDER_LIMIT) }
                var claimed = false
                try {
                    val pipe = try { ParcelFileDescriptor.createReliablePipe() }
                        catch (_: IOException) { return@operation Outcome.Failure(AppError.Access.PROVIDER_IO_FAILURE) }
                    val transport = Closeable { pipe[0].use { pipe[1].close() } }
                    transport.use {
                        coroutineScope {
                            // Catch stream failure locally so the producer can return its original typed error.
                            val received = async(Dispatchers.IO) {
                                try {
                                    DataInputStream(BufferedInputStream(ParcelFileDescriptor.AutoCloseInputStream(pipe[0]), 32768)).use { input ->
                                        PdfPixels.read(input, identity.epoch, token, wireRequest, request.width, request.height, request.viewportGeneration) { y, row ->
                                            bitmap.setPixels(row, 0, request.width, 0, y, request.width, 1)
                                        }
                                    }
                                    null
                                } catch (failure: IOException) { failure }
                            }
                            val reply = PdfWorkerCall.run({ try { transport.close() } finally { worker.close() } }) {
                                // The local write end MUST close after the transaction: EOF is part of completion.
                                pipe[1].use { output ->
                                    worker.remote!!.render(identity.epoch, token, wireRequest, request.page.page.value,
                                        request.width, request.height, request.viewportGeneration, output)
                                }
                            }
                            val remoteFailure = failure(reply)
                            if (remoteFailure != null) {
                                transport.close()
                                received.await()
                                remoteFailure
                            } else {
                                val transferFailure = received.await()
                                if (transferFailure != null) {
                                    retire()
                                    Outcome.Failure(AppError.Access.PROVIDER_IO_FAILURE)
                                } else {
                                    check(reply.getString("request") == wireRequest) { "Stale render reply" }
                                    currentCoroutineContext().ensureActive()
                                    val lease = IsolatedRenderedPage(request, geometry(reply, request.page), bitmap)
                                    unpublished = lease
                                    claimed = true
                                    Outcome.Success(lease)
                                }
                            }
                        }
                    }
                } finally { if (!claimed) bitmap.recycle() }
            }
            delivered = true
            return result
        } finally {
            // Includes cancellation on withContext's dispatcher handoff, after successful reconstruction.
            if (!delivered) unpublished?.close()
        }
    }

    override fun close() {
        check(Looper.myLooper() != Looper.getMainLooper()) { "Close PDF sessions off the main thread" }
        if (!closed.compareAndSet(false, true)) return
        // An active blocking native operation is interrupted only by retiring its isolated process.
        val locked = operations.tryLock()
        if (!locked) worker.close()
        runBlocking(Dispatchers.IO) {
            if (!locked) operations.lock()
            try {
                var primary: Throwable? = null
                try {
                    if (worker.alive()) {
                        val reply = PdfWorkerCall.run(worker::close) { worker.remote!!.closeSession(identity.epoch, token) }
                        PdfWorkerProtocol.check(reply, identity.epoch, token)
                    }
                } catch (_: RemoteException) {
                    // Death already invalidates this closing session; no successful result is synthesized.
                } catch (failure: Throwable) {
                    primary = failure
                    throw failure
                } finally {
                    try { worker.releaseOwner(owner) }
                    catch (cleanup: Throwable) { if (primary != null) primary.addSuppressed(cleanup) else throw cleanup }
                }
            } finally { operations.unlock() }
        }
    }
}

/** Published pixels are entirely client-owned; service/session death cannot recycle them. */
internal class IsolatedRenderedPage(
    override val request: RenderRequest,
    override val displayInfo: PageDisplayInfo,
    private val pixels: Bitmap,
) : RenderedPage {
    private var closed = false
    override val bitmap: Bitmap @Synchronized get() { check(!closed); return pixels }
    @Synchronized override fun close() { if (!closed) { closed = true; pixels.recycle() } }
}
