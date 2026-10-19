package com.abdev.partituraspdf.pdf.android.isolated

import android.content.*
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.os.*
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.abdev.partituraspdf.core.document.PageIndex
import com.abdev.partituraspdf.core.document.StorageDocumentRef
import com.abdev.partituraspdf.core.result.AppError
import com.abdev.partituraspdf.core.result.Outcome
import com.abdev.partituraspdf.pdf.api.*
import com.abdev.partituraspdf.storage.api.*
import java.io.*
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.*
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Real native production acceptance, separate from the unchanged direct Pdfium sentinels. */
@RunWith(AndroidJUnit4::class)
class IsolatedPdfReadEngineTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val limits = RenderLimits(512, 512L * 512 * 2)
    private val directory by lazy { File(context.cacheDir, "isolated-pdf-${UUID.randomUUID()}").apply { check(mkdirs()) } }
    private val engine by lazy { IsolatedPdfReadEngine(context, limits) }
    private val resources = mutableListOf<Source>()
    private val sessions = mutableListOf<PdfReadSession>()
    private val leases = mutableListOf<RenderedPage>()

    private class Source(val file: File, override val descriptor: ParcelFileDescriptor) : ReadResource {
        val closes = AtomicInteger()
        lateinit var handle: ReadHandle
        override fun close() {
            closes.incrementAndGet()
            try { descriptor.close() } finally { check(file.delete()) }
        }
    }
    private fun source(id: String): Pair<PdfReadSource, Source> {
        val manifest = instrumentation.context.assets.open("manifest.json").bufferedReader().use { JSONObject(it.readText()).getJSONArray("files") }
        val entry = (0 until manifest.length()).map(manifest::getJSONObject).single { it.getString("id") == id }
        val copy = File.createTempFile("fixture-$id-", ".pdf", directory)
        instrumentation.context.assets.open(entry.getString("path")).use { input -> copy.outputStream().use { input.copyTo(it) } }
        assertEquals(entry.getString("sha256"), MessageDigest.getInstance("SHA-256").digest(copy.readBytes()).joinToString("") { "%02x".format(it.toInt() and 255) })
        val resource = Source(copy, ParcelFileDescriptor.open(copy, ParcelFileDescriptor.MODE_READ_ONLY))
        resource.handle = ReadHandle(resource, ReadProvenance.TemporaryReadCopy(StorageDocumentRef("canonical", id)), true)
        resources += resource
        return PdfReadSource(resource.handle) to resource
    }
    private suspend fun opened(id: String = "01", adapter: IsolatedPdfReadEngine = engine): IsolatedPdfReadSession {
        val (source, resource) = source(id)
        val result = adapter.open(source)
        assertTrue("Native isolated open $id: $result", result is Outcome.Success)
        val session = (result as Outcome.Success).value as IsolatedPdfReadSession
        sessions += session
        assertEquals(0, resource.closes.get()); assertTrue(resource.file.exists())
        assertNotEquals(android.os.Process.myPid(), session.identity.pid)
        return session
    }
    private fun page(session: PdfReadSession, index: Int = 0) = ReadPageRef(session.info.id, session.info.sourceGeneration, PageIndex(index), session.info.revision)
    private suspend fun rendered(session: PdfReadSession, index: Int = 0): RenderedPage {
        val request = RenderRequest(RenderRequestId(UUID.randomUUID().toString()), page(session, index), 256, 320, 0)
        val result = session.render(request)
        assertTrue("Native isolated render: $result", result is Outcome.Success)
        return (result as Outcome.Success).value.also {
            leases += it
            assertEquals(request, it.request); assertEquals(request.page, it.displayInfo.page)
            assertEquals(256, it.bitmap.width); assertEquals(320, it.bitmap.height)
            assertEquals(Bitmap.Config.ARGB_8888, it.bitmap.config)
            val pixels = IntArray(256 * 320); it.bitmap.getPixels(pixels, 0, 256, 0, 0, 256, 320)
            assertTrue("A real rendered page must contain ink", pixels.any { pixel -> pixel != -1 })
            assertTrue("Native output uses an opaque sheet", pixels.all { pixel -> pixel ushr 24 == 255 })
        }
    }
    private suspend fun close(session: PdfReadSession) = withContext(Dispatchers.IO) { session.close() }
    private fun evidence(sequence: String, worker: PdfWorkerIdentity, elapsed: Long) {
        val data = JSONObject().put("sequence", sequence).put("appPid", android.os.Process.myPid())
            .put("workerPid", worker.pid).put("workerStarted", worker.started).put("workerEpoch", worker.epoch)
            .put("elapsedMs", elapsed).put("api", Build.VERSION.SDK_INT)
        Log.i("IsolatedPdfAcceptance", data.toString())
        instrumentation.sendStatus(2, Bundle().apply { putString("isolatedPdfEvidence", data.toString()) })
    }
    private suspend fun sequence(vararg invalids: String) {
        val appPid = android.os.Process.myPid()
        var previous: PdfWorkerIdentity? = null
        suspend fun valid(label: String) {
            val start = SystemClock.elapsedRealtime()
            val session = opened()
            previous?.let { assertTrue("Fresh process, not just a new renderer", it.pid != session.identity.pid || it.started != session.identity.started) }
            rendered(session).close()
            evidence(label, session.identity, SystemClock.elapsedRealtime() - start)
            previous = session.identity
            close(session)
        }
        valid("initial valid")
        for (id in invalids) {
            val (input, resource) = source(id)
            val invalidStarted = SystemClock.elapsedRealtime()
            val result = engine.open(input)
            assertTrue("Invalid $id must not open: $result", result is Outcome.Failure)
            val error = (result as Outcome.Failure).error
            assertTrue("Preserve native format/parser failure: $error", error in setOf(
                AppError.Validation.INVALID_DOCUMENT, AppError.Unsupported.DOCUMENT_FORMAT, AppError.Access.PROVIDER_IO_FAILURE))
            val worker = checkNotNull(engine.lastWorker)
            previous?.let { assertTrue(it.pid != worker.pid || it.started != worker.started) }
            previous = worker
            assertEquals(1, resource.closes.get()); assertFalse(resource.file.exists())
            evidence("invalid $id retired", worker, SystemClock.elapsedRealtime() - invalidStarted)
        }
        valid("recovered valid")
        assertEquals("Main application must not restart", appPid, android.os.Process.myPid())
    }
    @After fun cleanup() {
        runBlocking(Dispatchers.IO) {
            try { leases.forEach { it.close() } }
            finally { sessions.forEach { it.close() } }
            resources.forEach {
                if (it.handle.ownership == ReadOwnership.OWNED) it.handle.close()
                assertEquals("Exactly one source close", 1, it.closes.get())
                assertFalse(it.descriptor.fileDescriptor.valid()); assertFalse(it.file.exists())
            }
            if (resources.isNotEmpty()) assertTrue(directory.deleteRecursively())
        }
    }

    @Test fun validCorruptedValidRecoversWithoutApplicationRestart() { runBlocking { sequence("15") } }
    @Test fun validProtectedValidRecoversWithoutApplicationRestart() { runBlocking { sequence("14") } }
    @Test fun validCorruptedProtectedValidReplacesEveryFailedWorker() { runBlocking { sequence("15", "14") } }
    @Test fun validProtectedCorruptedValidReplacesEveryFailedWorker() { runBlocking { sequence("14", "15") } }

    @Test fun healthyWorkerIsReusedForInformationAndMultiplePages() { runBlocking {
        val session = opened("05"); val identity = session.identity
        for (index in listOf(0, session.info.pageCount - 1)) {
            assertTrue(session.displayInfo(page(session, index)) is Outcome.Success)
            rendered(session, index).close()
            assertEquals(identity, session.identity); assertEquals(identity, engine.lastWorker)
        }
        close(session); close(session)
        assertEquals(Outcome.Failure(AppError.Conflict.SESSION_CLOSED), session.displayInfo(page(session)))
    } }
    @Test fun exclusiveSessionAndRequestValidationPreserveHealthyWorker() { runBlocking {
        val session = opened()
        val (extra, resource) = source("01")
        assertEquals(Outcome.Failure(AppError.Conflict.STALE_SESSION), engine.open(extra))
        assertEquals(1, resource.closes.get())
        assertEquals(Outcome.Failure(AppError.Validation.INVALID_PAGE), session.displayInfo(page(session, session.info.pageCount)))
        assertEquals(Outcome.Failure(AppError.Conflict.STALE_SESSION), session.displayInfo(page(session).copy(sourceGeneration = 1)))
        assertEquals(Outcome.Failure(AppError.Conflict.STALE_SESSION), session.displayInfo(page(session).copy(session = ReadSessionId("stale"))))
        val oversized = RenderRequest(RenderRequestId("oversized"), page(session), Int.MAX_VALUE, Int.MAX_VALUE, 0)
        assertEquals(Outcome.Failure(AppError.Resource.RENDER_LIMIT), session.render(oversized))
        rendered(session).close()
    } }
    @Test fun clientBitmapSurvivesSessionAndWorkerRetirementAndClosesExactlyOnce() { runBlocking {
        val session = opened(); val lease = rendered(session); val bitmap = lease.bitmap
        val sample = bitmap.getPixel(40, 40)
        close(session)
        assertFalse(bitmap.isRecycled); assertEquals(sample, lease.bitmap.getPixel(40, 40))
        lease.close(); lease.close(); assertTrue(bitmap.isRecycled)
        assertThrows(IllegalStateException::class.java) { lease.bitmap }
    } }
    @Test fun cancellationBeforeBindingTransfersAndClosesInputOnce() { runBlocking {
        val (input, resource) = source("01")
        val operation = launch(start = CoroutineStart.UNDISPATCHED) {
            currentCoroutineContext().cancel()
            engine.open(input)
            fail("Cancelled open cannot succeed")
        }
        operation.join()
        assertTrue(operation.isCancelled); assertEquals(1, resource.closes.get()); assertNull(engine.lastWorker)
    } }
    @Test fun failedBindingReturnsTypedFailureAndReleasesBackingCopy() { runBlocking {
        val binds = AtomicInteger()
        val denied = object : ContextWrapper(context) {
            override fun getApplicationContext(): Context = this
            override fun bindService(intent: Intent, connection: ServiceConnection, flags: Int): Boolean { binds.incrementAndGet(); return false }
        }
        val (input, resource) = source("01")
        val adapter = IsolatedPdfReadEngine(denied, limits)
        assertEquals("Construction must not start a worker", 0, binds.get())
        assertEquals(Outcome.Failure(AppError.Access.PROVIDER_UNAVAILABLE), adapter.open(input))
        assertEquals(1, binds.get()); assertEquals(1, resource.closes.get())
    } }
    @Test fun cancelledBindingUnbindsAndNeverStartsNativeWork() { runBlocking {
        val bound = CompletableDeferred<Unit>(); val unbound = AtomicInteger()
        val stalled = object : ContextWrapper(context) {
            override fun getApplicationContext(): Context = this
            override fun bindService(intent: Intent, connection: ServiceConnection, flags: Int): Boolean { bound.complete(Unit); return true }
            override fun unbindService(connection: ServiceConnection) { unbound.incrementAndGet() }
        }
        val (input, resource) = source("01")
        val operation = launch { IsolatedPdfReadEngine(stalled, limits).open(input); fail("Cancelled binding cannot succeed") }
        bound.await(); operation.cancelAndJoin()
        assertEquals(1, unbound.get()); assertEquals(1, resource.closes.get())
    } }
    @Test fun serviceIsPrivateIsolatedAndNotAnApplicationGraph() {
        @Suppress("DEPRECATION")
        val service = context.packageManager.getServiceInfo(ComponentName(context, PdfWorkerService::class.java), 0)
        assertFalse(service.exported); assertTrue(service.flags and ServiceInfo.FLAG_ISOLATED_PROCESS != 0)
        assertEquals("${context.packageName}:pdf", service.processName)
        assertNull(service.permission)
    }

    @Test fun bindingDeadlineFailsWithoutAdoptingAnUnidentifiedService() { runBlocking {
        val unbound = AtomicInteger()
        val unavailable = object : ContextWrapper(context) {
            override fun getApplicationContext(): Context = this
            override fun bindService(intent: Intent, connection: ServiceConnection, flags: Int) = true
            override fun unbindService(connection: ServiceConnection) { unbound.incrementAndGet() }
        }
        val (input, resource) = source("01")
        assertEquals(Outcome.Failure(AppError.Access.PROVIDER_UNAVAILABLE), IsolatedPdfReadEngine(unavailable, limits).open(input))
        assertEquals(1, unbound.get()); assertEquals(1, resource.closes.get())
    } }
    @Test fun multiMegabyteNativePageUsesPipeRatherThanBinderBitmapPayload() { runBlocking {
        val session = opened(adapter = IsolatedPdfReadEngine(context, RenderLimits(1024, 2_097_152)))
        val request = RenderRequest(RenderRequestId("large-native-page"), page(session), 768, 1024, 0)
        val result = session.render(request)
        assertTrue("A 3 MiB pixel payload must not exceed Binder transaction limits: $result", result is Outcome.Success)
        val lease = (result as Outcome.Success).value; leases += lease
        assertEquals(768, lease.bitmap.width); assertEquals(1024, lease.bitmap.height)
        assertEquals(3_145_728, lease.bitmap.byteCount)
    } }
    @Test fun bitmapRowsRoundTripExactArgbChannelsWithoutStrideOrPaddingAssumptions() {
        val original = Bitmap.createBitmap(7, 3, Bitmap.Config.ARGB_8888)
        val reconstructed = Bitmap.createBitmap(7, 3, Bitmap.Config.ARGB_8888)
        try {
            val colors = IntArray(21) { index -> intArrayOf(0xff123456.toInt(), 0x8050a0f0.toInt(), 0x00000000, 0xffffffff.toInt(), 0xff000000.toInt())[index % 5] }
            original.setPixels(colors, 0, 7, 0, 0, 7, 3)
            val expected = IntArray(21); original.getPixels(expected, 0, 7, 0, 0, 7, 3)
            val bytes = ByteArrayOutputStream()
            PdfPixels.write(DataOutputStream(bytes), "epoch", "session", "request", 7, 3) { y, row -> original.getPixels(row, 0, 7, 0, y, 7, 1) }
            PdfPixels.read(DataInputStream(ByteArrayInputStream(bytes.toByteArray())), "epoch", "session", "request", 7, 3) { y, row -> reconstructed.setPixels(row, 0, 7, 0, y, 7, 1) }
            val actual = IntArray(21); reconstructed.getPixels(actual, 0, 7, 0, 0, 7, 3)
            assertArrayEquals(expected, actual); assertTrue(original.sameAs(reconstructed))
        } finally { original.recycle(); reconstructed.recycle() }
    }

    /** A second test-owned binding terminates the actual worker through its existing retirement
     * protocol. The engine did not request this death: no production crash/kill test hook exists.
     */
    private suspend fun terminateWorker(expected: PdfWorkerIdentity) {
        val remote = CompletableDeferred<IPdfWorker>(); val died = CompletableDeferred<Unit>()
        val callback = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, binder: IBinder) {
                binder.linkToDeath({ died.complete(Unit) }, 0)
                remote.complete(IPdfWorker.Stub.asInterface(binder))
            }
            override fun onServiceDisconnected(name: ComponentName) { }
        }
        assertTrue(context.bindService(Intent(context, PdfWorkerService::class.java), callback, Context.BIND_AUTO_CREATE))
        try {
            val worker = withTimeout(10_000) { remote.await() }
            withContext(Dispatchers.IO) {
                val actual = worker.identity()
                assertEquals(expected.pid, actual.getInt("pid")); assertEquals(expected.started, actual.getLong("started"))
                worker.retire()
            }
            withTimeout(10_000) { died.await() }
        } finally { context.unbindService(callback) }
    }
    @Test fun unexpectedWorkerDeathInvalidatesSessionAndPreservesPublishedPixels() { runBlocking {
        val appPid = android.os.Process.myPid(); val session = opened(); val lease = rendered(session)
        terminateWorker(session.identity)
        assertEquals(Outcome.Failure(AppError.Conflict.STALE_SESSION), session.displayInfo(page(session)))
        assertFalse(lease.bitmap.isRecycled)
        val recovered = opened()
        assertTrue(session.identity.pid != recovered.identity.pid || session.identity.started != recovered.identity.started)
        rendered(recovered).close()
        assertEquals(appPid, android.os.Process.myPid())
    } }
    /** Test-only interception of the real remote Binder. Native rendering/permissions are not
     * replaced; latches expose otherwise nondeterministic pending/late-reply cancellation windows.
     */
    private class RenderBarrierContext(base: Context, private val afterReply: Boolean) : ContextWrapper(base) {
        val reached = CompletableDeferred<Unit>()
        val release = java.util.concurrent.CountDownLatch(1)
        private val callbacks = java.util.concurrent.ConcurrentHashMap<ServiceConnection, ServiceConnection>()
        private val once = java.util.concurrent.atomic.AtomicBoolean()
        override fun getApplicationContext(): Context = this
        override fun bindService(intent: Intent, connection: ServiceConnection, flags: Int): Boolean {
            val callback = object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName, service: IBinder) {
                    val wrapped = object : IBinder by service {
                        override fun queryLocalInterface(descriptor: String): IInterface? = null
                        override fun transact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
                            val intercept = code == IPdfWorker.Stub.TRANSACTION_render && once.compareAndSet(false, true)
                            fun barrier() {
                                reached.complete(Unit)
                                check(release.await(15, java.util.concurrent.TimeUnit.SECONDS)) { "Test must release its Binder barrier" }
                            }
                            if (intercept && !afterReply) barrier()
                            val result = service.transact(code, data, reply, flags)
                            if (intercept && afterReply) barrier()
                            return result
                        }
                    }
                    connection.onServiceConnected(name, wrapped)
                }
                override fun onServiceDisconnected(name: ComponentName) = connection.onServiceDisconnected(name)
                override fun onBindingDied(name: ComponentName) = connection.onBindingDied(name)
                override fun onNullBinding(name: ComponentName) = connection.onNullBinding(name)
            }
            callbacks[connection] = callback
            return super.bindService(intent, callback, flags)
        }
        override fun unbindService(connection: ServiceConnection) {
            super.unbindService(checkNotNull(callbacks.remove(connection)))
        }
    }
    @Test fun cancellationDuringPendingRenderDrainsRpcAndAllowsFreshExplicitOpen() { runBlocking {
        val barrier = RenderBarrierContext(context, false)
        val adapter = IsolatedPdfReadEngine(barrier, limits)
        val session = opened(adapter = adapter)
        val operation = async { session.render(RenderRequest(RenderRequestId("cancelled"), page(session), 256, 320, 0)) }
        try {
            withTimeout(10_000) { barrier.reached.await() }
            operation.cancel()
        } finally { barrier.release.countDown() }
        operation.join(); assertTrue(operation.isCancelled)
        assertEquals(1, resources.first().closes.get())
        val recovered = opened(adapter = adapter)
        assertTrue(session.identity.pid != recovered.identity.pid || session.identity.started != recovered.identity.started)
        rendered(recovered).close()
    } }
    @Test fun lateSuccessfulNativeReplyAfterCancellationCannotPublishOrLeakClientBitmap() { runBlocking {
        val barrier = RenderBarrierContext(context, true)
        val adapter = IsolatedPdfReadEngine(barrier, limits)
        val session = opened(adapter = adapter)
        val operation = async { session.render(RenderRequest(RenderRequestId("late-reply"), page(session), 256, 320, 0)) }
        try {
            withTimeout(10_000) { barrier.reached.await() }
            operation.cancel()
        } finally { barrier.release.countDown() }
        operation.join(); assertTrue(operation.isCancelled)
        assertEquals(1, resources.first().closes.get())
        assertEquals(Outcome.Failure(AppError.Conflict.SESSION_CLOSED), session.render(RenderRequest(RenderRequestId("closed"), page(session), 256, 320, 1)))
    } }
    @Test fun workerDeathWithPendingRenderReturnsFailureAndNextExplicitOpenRecovers() { runBlocking {
        val barrier = RenderBarrierContext(context, false)
        val adapter = IsolatedPdfReadEngine(barrier, limits)
        val session = opened(adapter = adapter)
        val operation = async { session.render(RenderRequest(RenderRequestId("worker-death"), page(session), 256, 320, 0)) }
        try {
            withTimeout(10_000) { barrier.reached.await() }
            terminateWorker(session.identity)
        } finally { barrier.release.countDown() }
        assertEquals(Outcome.Failure(AppError.Conflict.STALE_SESSION), operation.await())
        assertEquals(1, resources.first().closes.get())
        val recovered = opened(adapter = adapter)
        assertTrue(session.identity.pid != recovered.identity.pid || session.identity.started != recovered.identity.started)
        rendered(recovered).close()
    } }

    @Test fun canonicalVectorRasterAndScoreFixturesRenderThroughSameBoundedTransport() { runBlocking {
        for (id in listOf("01", "05", "08", "09", "10", "11")) {
            val started = SystemClock.elapsedRealtime()
            val session = opened(id)
            rendered(session).close(); rendered(session).close()
            evidence("benchmark fixture $id two renders", session.identity, SystemClock.elapsedRealtime() - started)
            close(session)
        }
    } }
}
