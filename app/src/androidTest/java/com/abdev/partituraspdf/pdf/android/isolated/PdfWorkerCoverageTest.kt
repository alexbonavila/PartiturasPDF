package com.abdev.partituraspdf.pdf.android.isolated

import android.content.*
import android.os.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.abdev.partituraspdf.coverage.PdfCoverageWorkerService
import com.abdev.partituraspdf.core.result.AppError
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestName
import org.junit.runner.RunWith

/** Real Binder/native guard tests, not replacement or duplicates of client acceptance tests. */
@RunWith(AndroidJUnit4::class)
class PdfWorkerCoverageTest {
    @get:Rule val name = TestName()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext

    private inner class Worker : AutoCloseable {
        private val connected = CompletableFuture<IBinder>()
        private val died = CompletableFuture<Unit>()
        private val connection = object : ServiceConnection {
            override fun onServiceConnected(component: ComponentName, service: IBinder) { connected.complete(service) }
            // A framework callback is not independent proof that native descriptors died.
            override fun onServiceDisconnected(component: ComponentName) { }
            override fun onNullBinding(component: ComponentName) { connected.completeExceptionally(IllegalStateException("Null worker")) }
        }
        private val bound = context.bindService(Intent(context, PdfCoverageWorkerService::class.java), connection, Context.BIND_AUTO_CREATE)
        val binder: IBinder
        val remote: IPdfWorker
        val identity: Bundle
        val epoch: String
        val token = UUID.randomUUID().toString()
        init {
            check(bound)
            try {
                binder = connected.get(10, TimeUnit.SECONDS)
                binder.linkToDeath({ died.complete(Unit) }, 0)
                remote = IPdfWorker.Stub.asInterface(binder)
                identity = remote.identity()
                epoch = requireNotNull(identity.getString("epoch"))
                assertTrue(identity.getBoolean("isolated"))
                assertNotEquals(android.os.Process.myPid(), identity.getInt("pid"))
                assertTrue(identity.getLong("started") > 0)
            } catch (failure: Throwable) {
                try { connected.getNow(null)?.let { IPdfWorker.Stub.asInterface(it).retire() } }
                catch (cleanup: Throwable) { failure.addSuppressed(cleanup) }
                try { context.unbindService(connection) } catch (cleanup: Throwable) { failure.addSuppressed(cleanup) }
                throw failure
            }
        }
        fun open(id: String = "01", session: String = token, dimension: Int = 512, pixels: Long = 262144): Bundle {
            val manifest = instrumentation.context.assets.open("manifest.json").bufferedReader().use { JSONObject(it.readText()).getJSONArray("files") }
            val entry = (0 until manifest.length()).map(manifest::getJSONObject).single { it.getString("id") == id }
            val file = File.createTempFile("worker-guard-", ".pdf", context.cacheDir)
            try {
                instrumentation.context.assets.open(entry.getString("path")).use { input -> file.outputStream().use { input.copyTo(it) } }
                assertEquals(entry.getString("sha256"), hash(file.readBytes()))
                return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { remote.open(session, it, dimension, pixels) }
            } finally { assertTrue(file.delete()) }
        }
        fun render(e: String = epoch, session: String = token, page: Int = 0, request: String = "render", width: Int = 32, height: Int = 32, generation: Long = 0): Bundle {
            // A file is a bounded explicit output capability, never an app/provider pathname in the worker.
            val file = File.createTempFile("worker-pixels-", ".bin", context.cacheDir)
            try {
                return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_WRITE_ONLY).use {
                    remote.render(e, session, request, page, width, height, generation, it)
                }
            } finally { assertTrue(file.delete()) }
        }
        private fun snapshot() {
            val nonce = InstrumentationRegistry.getArguments().getString("workerCoverageRun") ?: return
            require(nonce.matches(Regex("[a-f0-9]{32}")))
            val directory = File(context.filesDir, "local-coverage-$nonce/workers")
            check(directory.parentFile!!.isDirectory) { "Collector-owned UUID directory is required" }
            check(directory.exists() || directory.mkdir())
            val stem = UUID.randomUUID().toString()
            val output = File(directory, "$stem.ec")
            check(output.createNewFile())
            val data = Parcel.obtain(); val reply = Parcel.obtain()
            try {
                data.writeInterfaceToken(PdfCoverageWorkerService.DESCRIPTOR)
                data.writeString(nonce)
                ParcelFileDescriptor.open(output, ParcelFileDescriptor.MODE_WRITE_ONLY).use { fd ->
                    fd.writeToParcel(data, 0)
                    check(binder.transact(PdfCoverageWorkerService.SNAPSHOT, data, reply, 0))
                    reply.readException()
                    val metadata = requireNotNull(reply.readBundle(javaClass.classLoader))
                    assertEquals(identity.getInt("pid"), metadata.getInt("pid"))
                    assertEquals(epoch, metadata.getString("epoch"))
                    assertEquals(identity.getLong("started"), metadata.getLong("started"))
                    assertEquals(context.applicationInfo.uid, metadata.getInt("appUid"))
                    assertEquals(android.os.Process.myPid(), metadata.getInt("appPid"))
                    assertEquals(nonce, metadata.getString("nonce"))
                    assertEquals(output.length(), metadata.getInt("bytes").toLong())
                    assertEquals(hash(output.readBytes()), metadata.getString("sha256"))
                    val record = JSONObject()
                    for (key in metadata.keySet()) record.put(key, metadata.get(key))
                    record.put("test", PdfWorkerCoverageTest::class.java.name + "." + name.methodName)
                    record.put("file", "$stem.ec")
                    val temporary = File(directory, "$stem.tmp")
                    temporary.writeText(record.toString())
                    check(temporary.renameTo(File(directory, "$stem.json")))
                }
            } finally { data.recycle(); reply.recycle() }
        }
        override fun close() {
            try { snapshot() } finally {
                try { remote.retire(); died.get(10, TimeUnit.SECONDS) } finally { context.unbindService(connection) }
            }
        }
    }
    private fun hash(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    private fun error(reply: Bundle, expected: AppError, poisoned: Boolean = false) {
        assertEquals(PdfRemoteErrors.encode(expected), reply.getString("error"))
        assertEquals(poisoned, reply.getBoolean("poisoned")); assertNull(reply.getString("defect"))
    }
    private fun success(reply: Bundle) { assertNull(reply.getString("error")); assertNull(reply.getString("defect")); assertFalse(reply.getBoolean("poisoned")) }

    @Test fun wrongTokensAndPageBoundsDoNotPoisonHealthyWorker() {
        Worker().use { w ->
            val opened = w.open(); success(opened); assertEquals(1, opened.getInt("pages"))
            for ((epoch, session) in listOf("wrong" to w.token, w.epoch to "wrong")) {
                error(w.remote.info(epoch, session, 0), AppError.Conflict.SESSION_CLOSED)
                error(w.render(e = epoch, session = session), AppError.Conflict.SESSION_CLOSED)
                error(w.remote.closeSession(epoch, session), AppError.Conflict.SESSION_CLOSED)
            }
            for (page in listOf(-1, opened.getInt("pages"))) {
                error(w.remote.info(w.epoch, w.token, page), AppError.Validation.INVALID_PAGE)
                error(w.render(page = page), AppError.Validation.INVALID_PAGE)
            }
            assertTrue(w.remote.info(w.epoch, w.token, 0).getInt("pageWidth") > 0)
            success(w.render()); success(w.remote.closeSession(w.epoch, w.token))
            error(w.remote.info(w.epoch, w.token, 0), AppError.Conflict.SESSION_CLOSED)
            error(w.render(), AppError.Conflict.SESSION_CLOSED)
            error(w.remote.closeSession(w.epoch, w.token), AppError.Conflict.SESSION_CLOSED)
            success(w.open(session = "replacement")); success(w.remote.closeSession(w.epoch, "replacement"))
        }
    }
    @Test fun duplicateSessionIsProgrammingDefectAndQuarantinesWorker() {
        Worker().use { w ->
            success(w.open())
            val defect = w.open(session = "duplicate")
            assertEquals("IllegalStateException", defect.getString("defect")); assertTrue(defect.getBoolean("poisoned")); assertNull(defect.getString("error"))
            error(w.remote.info(w.epoch, w.token, 0), AppError.Conflict.STALE_SESSION, true)
            error(w.render(), AppError.Conflict.STALE_SESSION, true)
            error(w.remote.closeSession(w.epoch, w.token), AppError.Conflict.STALE_SESSION, true)
            error(w.open(), AppError.Conflict.STALE_SESSION, true)
        }
    }
    @Test fun invalidOpenBoundsAreDefectsRatherThanInvalidDocuments() {
        for ((session, dimension, pixels) in listOf(Triple("", 512, 262144L), Triple("x".repeat(129), 512, 262144L),
            Triple("ok", 0, 262144L), Triple("ok", 16385, 262144L), Triple("ok", 512, 0L), Triple("ok", 512, 4194305L))) {
            Worker().use { w ->
                val result = w.open(session = session, dimension = dimension, pixels = pixels)
                assertEquals("IllegalArgumentException", result.getString("defect")); assertTrue(result.getBoolean("poisoned")); assertNull(result.getString("error"))
                error(w.remote.info(w.epoch, session, 0), AppError.Conflict.STALE_SESSION, true)
            }
        }
    }
    @Test fun invalidRenderParametersAreDefectsAndOversizeIsTypedRecoverableFailure() {
        Worker().use { w ->
            success(w.open())
            error(w.render(width = 513), AppError.Resource.RENDER_LIMIT)
            error(w.render(width = 512, height = 513), AppError.Resource.RENDER_LIMIT)
            success(w.render()); success(w.remote.closeSession(w.epoch, w.token))
        }
        for ((request, width, height, generation) in listOf(RenderCase("", 32, 32, 0), RenderCase("x".repeat(129), 32, 32, 0),
            RenderCase("ok", 0, 32, 0), RenderCase("ok", 32, 0, 0), RenderCase("ok", 32, 32, -1))) {
            Worker().use { w ->
                success(w.open())
                val result = w.render(request = request, width = width, height = height, generation = generation)
                assertEquals("IllegalArgumentException", result.getString("defect")); assertTrue(result.getBoolean("poisoned"))
                error(w.remote.info(w.epoch, w.token, 0), AppError.Conflict.STALE_SESSION, true)
            }
        }
    }
    private data class RenderCase(val request: String, val width: Int, val height: Int, val generation: Long)
    @Test fun invalidNativeOpenQuarantinesBeforeAnyLaterNativeCall() {
        Worker().use { w ->
            val failure = w.open(id = "15")
            assertTrue(failure.getBoolean("poisoned")); assertNull(failure.getString("defect"))
            assertTrue(failure.getString("error") in setOf(PdfRemoteErrors.encode(AppError.Validation.INVALID_DOCUMENT),
                PdfRemoteErrors.encode(AppError.Unsupported.DOCUMENT_FORMAT), PdfRemoteErrors.encode(AppError.Access.PROVIDER_IO_FAILURE)))
            error(w.open(), AppError.Conflict.STALE_SESSION, true)
            error(w.remote.info(w.epoch, w.token, 0), AppError.Conflict.STALE_SESSION, true)
        }
    }
    @Test fun brokenPipeIsTypedIoFailureAndNewWorkerRecovers() {
        var previous = ""
        Worker().use { w ->
            previous = w.epoch; success(w.open())
            val pipe = ParcelFileDescriptor.createReliablePipe()
            pipe[0].close()
            val result = pipe[1].use { w.remote.render(w.epoch, w.token, "broken", 0, 32, 32, 0, it) }
            error(result, AppError.Access.PROVIDER_IO_FAILURE, true)
            error(w.remote.info(w.epoch, w.token, 0), AppError.Conflict.STALE_SESSION, true)
        }
        Worker().use { w ->
            assertNotEquals(previous, w.epoch); success(w.open()); success(w.render()); success(w.remote.closeSession(w.epoch, w.token))
        }
    }
}
