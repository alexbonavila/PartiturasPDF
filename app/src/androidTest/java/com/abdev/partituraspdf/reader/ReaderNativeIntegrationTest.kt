package com.abdev.partituraspdf.reader

import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.system.Os
import android.system.OsConstants
import com.abdev.partituraspdf.storage.saf.SafDocumentReadAccess
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.util.Log
import android.view.PixelCopy
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.abdev.partituraspdf.MainActivity
import com.abdev.partituraspdf.core.document.*
import com.abdev.partituraspdf.core.foundation.*
import com.abdev.partituraspdf.core.preferences.PageFit
import com.abdev.partituraspdf.core.result.*
import com.abdev.partituraspdf.pdf.android.AndroidPdfReadEngine
import com.abdev.partituraspdf.pdf.android.isolated.IsolatedPdfReadEngine
import com.abdev.partituraspdf.pdf.api.*
import com.abdev.partituraspdf.storage.api.*
import com.abdev.partituraspdf.ui.theme.PartiturasPDFTheme
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.*
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters

/** Real production PdfRenderer -> G controller -> software SurfaceView. The route is a test
 * owner, not production registration/SAF/checkpoint persistence. Existing test-APK assets only.
 * Negative Pdfium inputs run last, following the existing canonical adapter test convention.
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@RunWith(AndroidJUnit4::class)
class ReaderNativeIntegrationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val composition = ReaderTestComposition(compose)
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val limits = RenderLimits(512, 512L * 512)
    private val isolatedEngine by lazy { IsolatedPdfReadEngine(instrumentation.targetContext, limits) }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var directory: File
    private lateinit var controller: ReaderController
    private lateinit var selected: JSONObject
    private var hint = PageIndex(0)
    private var sequence = 0L
    private var renderCompletionGate: CompletableDeferred<Unit>? = null
    private var ready: ReaderReady? = null
    private var owner: NativeOwner? = null
    private val sessions = CopyOnWriteArrayList<NativeOwner>()
    private val sources = CopyOnWriteArrayList<Pair<File, String>>()
    private val resources = CopyOnWriteArrayList<InputResource>()
    private val leases = CopyOnWriteArrayList<Lease>()
    private val displayed = CopyOnWriteArrayList<Pair<ReadSessionId, PageIndex>>()
    private val opens = AtomicInteger()
    private val activeRenders = AtomicInteger()
    private val maximumRenders = AtomicInteger()
    private val retained = AtomicInteger()
    private val maximumRetained = AtomicInteger()
    private val presentedActions = AtomicInteger()
    @Volatile private var nativeOperation = "idle"
    @Volatile private var plannedPixels = 0L

    private class InputResource(override val descriptor: ParcelFileDescriptor) : ReadResource {
        val closes = AtomicInteger()
        override fun close() { closes.incrementAndGet(); descriptor.close() }
    }
    private class Lease(val native: RenderedPage, val bitmap: Bitmap, val budgetPixels: Long) {
        val closes = AtomicInteger()
    }
    private class NativeOwner(val native: PdfReadSession, val borrowed: PdfReadSession) {
        private var closed = false
        val isClosed: Boolean get() = closed
        fun close() { if (!closed) { closed = true; native.close() } }
    }
    private fun heap(): HeapAvailability = Runtime.getRuntime().let {
        HeapAvailability(it.maxMemory(), it.totalMemory() - it.freeMemory())
    }
    private fun hash(file: File): String = file.inputStream().use { input ->
        val digest = MessageDigest.getInstance("SHA-256"); val bytes = ByteArray(8192)
        while (true) { val n = input.read(bytes); if (n < 0) break; digest.update(bytes, 0, n) }
        digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
    }
    private fun fixtures(): List<JSONObject> {
        val rows = instrumentation.context.assets.open("manifest.json").bufferedReader().use {
            JSONObject(it.readText()).getJSONArray("files")
        }
        return (0 until rows.length()).map(rows::getJSONObject)
    }
    /** Inspect the exact descriptor without changing the offset shared with PdfRenderer. */
    private fun verifyDescriptor(descriptor: ParcelFileDescriptor, row: JSONObject) {
        val fd=descriptor.fileDescriptor
        assertTrue("Descriptor invalid",fd.valid())
        assertEquals(row.getLong("bytes"),Os.fstat(fd).st_size)
        assertEquals(0L,Os.lseek(fd,0,OsConstants.SEEK_CUR))
        val digest=MessageDigest.getInstance("SHA-256")
        val bytes=ByteArray(8192);var position=0L
        while(position<row.getLong("bytes")) {
            val n=Os.pread(fd,bytes,0,minOf(bytes.size.toLong(),row.getLong("bytes")-position).toInt(),position)
            assertTrue("Truncated descriptor",n>0);digest.update(bytes,0,n);position+=n
        }
        assertEquals(0,Os.pread(fd,bytes,0,1,position))
        assertEquals(row.getString("sha256"),digest.digest().joinToString(""){"%02x".format(it.toInt() and 255)})
        if(row.getString("expected_status")=="valid") {
            val header=ByteArray(5);assertEquals(5,Os.pread(fd,header,0,5,0))
            assertEquals("%PDF-",String(header,Charsets.US_ASCII))
        }
        assertEquals("Diagnostics must preserve offset",0L,Os.lseek(fd,0,OsConstants.SEEK_CUR))
    }
    private fun borrowed(native: PdfReadSession): PdfReadSession = object : PdfReadSession by native {
        override fun close(): Unit = error("G must never close the borrowed native session")
        override suspend fun render(request: RenderRequest): Outcome<RenderedPage> {
            val count = activeRenders.incrementAndGet(); maximumRenders.updateAndGet { maxOf(it, count) }
            val before = heap(); val started = SystemClock.elapsedRealtimeNanos()
            try {
                nativeOperation = "render started: page=${request.page.page.value}"
                val result = native.render(request)
                nativeOperation = "render completed: ${if (result is Outcome.Success) "bitmap" else result}"
                if (result !is Outcome.Success) return result
                val lease = Lease(result.value, result.value.bitmap, plannedPixels); leases += lease
                val held = retained.incrementAndGet(); maximumRetained.updateAndGet { maxOf(it, held) }
                Log.i("ReaderNativeAcceptance", JSONObject().apply {
                    put("api", android.os.Build.VERSION.SDK_INT); put("page", request.page.page.value)
                    put("fixture", selected.getString("id")); put("session", request.page.session.value)
                    put("pageCount", native.info.pageCount)
                    put("heapMaxBytes", before.maximumBytes); put("heapBeforeBytes", before.usedBytes)
                    put("heapAfterBytes", heap().usedBytes); put("width", request.width); put("height", request.height)
                    put("bitmapAllocationBytes", lease.bitmap.allocationByteCount)
                    put("estimatedArgbBytes", request.width.toLong() * request.height * 4)
                    put("renderNanos", SystemClock.elapsedRealtimeNanos() - started)
                }.toString())
                val published = object : RenderedPage by result.value {
                    override fun close() {
                        // Count attempts too: an idempotent native close must not hide a G double-close.
                        if (lease.closes.incrementAndGet() == 1) retained.decrementAndGet()
                        result.value.close()
                    }
                }
                var returned = false
                try {
                    // Test-only controlled handoff after a REAL native render, not a renderer substitute.
                    renderCompletionGate?.let { withContext(NonCancellable) { it.await() } }
                    return Outcome.Success(published).also { returned = true }
                } finally { if (!returned) published.close() }
            } finally { activeRenders.decrementAndGet() }
        }
    }
    private val route = object : ReaderRoute {
        override suspend fun current() = ready
        override suspend fun open(request: DocumentOpenRequest): Outcome<ReaderReady> {
            opens.incrementAndGet()
            var acquired: NativeOwner? = null
            var delivered = false
            try {
                val result = withContext(Dispatchers.IO) {
                    nativeOperation = "preparing fixture and closing previous owner"
                    owner?.close(); owner = null; ready = null
                    val copy = File.createTempFile("canonical-", ".pdf", directory)
                    val expected = selected.getString("sha256"); sources += copy to expected
                    instrumentation.context.assets.open(selected.getString("path")).use { input ->
                        copy.outputStream().use { output -> input.copyTo(output) }
                    }
                    assertEquals(expected, hash(copy))
                    val resource = InputResource(ParcelFileDescriptor.open(copy, ParcelFileDescriptor.MODE_READ_ONLY))
                    resources += resource
                    val handle = ReadHandle(resource, ReadProvenance.Provider(StorageDocumentRef("canonical", selected.getString("path"))), true)
                    nativeOperation = "native open started"
                    val opened = try {
                        verifyDescriptor(resource.descriptor,selected)
                        isolatedEngine.open(PdfReadSource(handle, (request as? DocumentOpenRequest.Registered)?.document))
                    } finally { if (handle.ownership == ReadOwnership.OWNED) handle.close() }
                    nativeOperation = "native open completed: ${if (opened is Outcome.Success) "session" else opened}"
                    when (opened) {
                        is Outcome.Failure -> opened
                        is Outcome.Success -> {
                            val record = NativeOwner(opened.value, borrowed(opened.value)); acquired = record; sessions += record
                            Outcome.Success(ReaderReady(request, record.borrowed, restoredPage(hint, opened.value.info.pageCount)))
                        }
                    }
                }
                delivered = result is Outcome.Success
                if (result is Outcome.Success) { owner = acquired; ready = result.value }
                return result
            } finally {
                if (!delivered) withContext(NonCancellable + Dispatchers.IO) { acquired?.close() }
            }
        }
        override suspend fun displayed(session: ReadSessionId, page: PageIndex): Outcome<Unit> {
            val current = checkNotNull(ready)
            assertEquals(current.session.info.id, session)
            assertTrue(page.value < current.session.info.pageCount)
            displayed += session to page
            ready = current.copy(page = page)
            return Outcome.Success(Unit) // Scheduling seam only; no durable writer in this test route.
        }
        override suspend fun background(): Outcome<Unit> = Outcome.Success(Unit)
        override suspend fun close(): Outcome<Unit> {
            withContext(Dispatchers.IO) { owner?.close(); owner = null; ready = null }
            return Outcome.Success(Unit)
        }
    }

    @Before fun setup() {
        directory = File(instrumentation.targetContext.cacheDir, "reader-native-${UUID.randomUUID()}")
        check(directory.mkdir())
        controller = ReaderController(scope, route, limits, { heap().also { plannedPixels = ReaderMemoryPolicy.budget(it).maxArgbPixels } }, PageFit.PAGE)
        composition.mount {
            val state by controller.state.collectAsState()
            PartiturasPDFTheme { ReaderScreen(state, { action ->
                if (action is ReaderAction.Presented) presentedActions.incrementAndGet()
                controller.dispatch(action)
            }, {}) }
        }
    }
    @After fun cleanup() {
        try {
            composition.unmount()
            renderCompletionGate?.complete(Unit)
            // This timeout fails a hung test; it never authorizes recycling or forced route closure.
            runBlocking { withTimeout(30_000) { controller.retire() }; route.close() }
            assertEquals(0, activeRenders.get()); assertEquals(0, retained.get())
            leases.forEach { assertEquals(1, it.closes.get()); assertTrue(it.bitmap.isRecycled) }
            resources.forEach { assertEquals(1, it.closes.get()) }
            sessions.forEach { assertTrue("Native route owner was not closed", it.isClosed) }
            sources.forEach { (file, expected) -> assertEquals("Source PDF changed", expected, hash(file)) }
            assertTrue(directory.deleteRecursively()) // Only this test's unique synthetic-copy directory.
        } catch (failure: Throwable) {
            if (::controller.isInitialized) reportFailure(failure, "cleanup", -1)
            throw failure
        } finally { scope.cancel() }
    }
    private fun open(row: JSONObject, registered: Boolean = false, restored: Int = 0) {
        selected = row; hint = PageIndex(restored)
        val request = if (registered) DocumentOpenRequest.Registered(DocumentRef(DocumentId("canonical-${row.getString("id")}")))
            else DocumentOpenRequest.Transient(StorageDocumentRef("canonical", row.getString("path")))
        compose.runOnUiThread { controller.open(ReaderOpenIntent(++sequence, request)) }
    }
    private fun visible(page: Int) {
        try {
            compose.waitUntil(20_000) { controller.state.value.let { it.phase == ReaderPhase.ERROR || (it.phase == ReaderPhase.VISIBLE && it.presentedPage?.value == page) } }
            val state=controller.state.value
            if(state.phase==ReaderPhase.ERROR)throw AssertionError("Native terminal error: fixture=${selected.getString("id")},error=${state.failure?.error},operation=$nativeOperation,session=${state.session}")
        } catch (failure: Throwable) {
            // Preserve the primary failure and snapshot before @After changes the state.
            reportFailure(failure, "waiting for VISIBLE", page)
            throw failure
        }
    }
    private fun awaitCheckpoint(frame:ReaderFrame) {
        val expected=frame.request.page.session to frame.request.page.page
        try {
            assertSame(frame,controller.state.value.frame)
            assertEquals(expected.first,controller.state.value.session!!.id)
            assertEquals(expected.second,controller.state.value.presentedPage)
            compose.waitUntil(20_000) {
                displayed.lastOrNull()==expected &&
                    controller.state.value.frame===frame &&
                    controller.state.value.checkpoint is ReaderCheckpoint.Unconfirmed
            }
            // This is completion of the test route's scheduling call, never a disk-write claim.
            assertEquals(ReaderCheckpoint.Unconfirmed(Outcome.Success(Unit)),controller.state.value.checkpoint)
            assertEquals(expected,displayed.last())
            compose.waitForIdle()
            assertSame(frame,controller.state.value.frame)
            assertEquals(expected,displayed.last())
        } catch(failure:Throwable) {reportFailure(failure,"waiting for checkpoint $expected",expected.second.value);throw failure}
    }
    private fun reportFailure(failure: Throwable, stage: String, page: Int) {
        try {
            val details = "$stage: ${diagnostics(page)}"
            Log.e("ReaderNativeAcceptance", details)
            failure.addSuppressed(AssertionError(details))
        } catch (diagnosticFailure: Throwable) { failure.addSuppressed(diagnosticFailure) }
    }
    private fun diagnostics(expected: Int): String {
        val state = controller.state.value
        var views = "unavailable"
        compose.runOnUiThread {
            views = composition.surfaces().joinToString { view ->
                "attached=${view.isAttachedToWindow},shown=${view.isShown},visibility=${view.visibility}," +
                    "window=${view.windowVisibility},size=${view.width}x${view.height},surfaceValid=${view.holder.surface.isValid}"
            }
        }
        fun jobs(job: Job): String = "active=${job.isActive}/completed=${job.isCompleted}/cancelled=${job.isCancelled}" +
            job.children.joinToString(prefix = "[", postfix = "]") { jobs(it) }
        return "Reader timeout: api=${android.os.Build.VERSION.SDK_INT},fixture=${if (::selected.isInitialized) selected.getString("id") else "none"}," +
            "expected=$expected,phase=${state.phase},intentSequence=${state.intent?.sequence}," +
            "requested=${state.requestedPage},presented=${state.presentedPage},session=${state.session}," +
            "sessionValid=${state.frame?.let { ready?.session?.info?.validate(it.request.page) }}," +
            "viewport=${state.viewport},viewportGeneration=${state.viewportGeneration},request=${state.frame?.request}," +
            "frame=${state.frame?.let(System::identityHashCode)},attachable=${state.frame?.availability?.value}," +
            "failure=${state.failure},cancellationRetry=${state.cancellationRetry},checkpoint=${state.checkpoint},jobs=${jobs(scope.coroutineContext[Job]!!)}," +
            "opens=${opens.get()},activeRenders=${activeRenders.get()},produced=${leases.size},retained=${retained.get()}," +
            "nativeOperation=$nativeOperation,PresentedActions=${presentedActions.get()},displayed=${displayed.size},views=[$views]; draw/post unobserved unless a current Presented reached the route"
    }
    private fun surface(): ReaderPageSurface {
        fun find(view: View): ReaderPageSurface? {
            if (view is ReaderPageSurface) return view
            if (view is ViewGroup) for (i in 0 until view.childCount) find(view.getChildAt(i))?.let { return it }
            return null
        }
        return checkNotNull(find(compose.activity.findViewById(android.R.id.content)))
    }
    private fun copy(view: ReaderPageSurface): Bitmap {
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        val done = CountDownLatch(1); var status = -1
        try {
            PixelCopy.request(view.holder.surface, bitmap, { status = it; done.countDown() }, Handler(Looper.getMainLooper()))
            assertTrue(done.await(20, TimeUnit.SECONDS)); assertEquals(PixelCopy.SUCCESS, status)
            return bitmap
        } catch (failure: Throwable) { bitmap.recycle(); throw failure }
    }

    @Test fun a0_sameCanonicalBytesOpenDirectThroughEngineAndSaf() {
        runBlocking(Dispatchers.IO) {
            val row=fixtures().single{it.getString("id")=="01"}
            val copy=File(directory,"differential-a4.pdf")
            instrumentation.context.assets.open(row.getString("path")).use { input -> copy.outputStream().use { input.copyTo(it) } }
            assertEquals(row.getString("sha256"),hash(copy))
            val results=mutableListOf<String>()
            val failures=mutableListOf<Throwable>()
            fun experiment(name:String, action:()->Unit) {
                try { action();results+="$name: PASS" }
                catch(failure:Exception) {
                    if(failure is CancellationException)throw failure
                    failures+=failure;results+="$name: ${failure.javaClass.simpleName}: ${failure.message}"
                } catch(failure:AssertionError) {failures+=failure;results+="$name: ${failure.message}"}
                Log.i("ReaderNativeDifferential",results.last())
            }
            experiment("A direct PdfRenderer") {
                ParcelFileDescriptor.open(copy,ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
                    verifyDescriptor(fd,row)
                    PdfRenderer(fd).use { assertEquals(row.getInt("expected_pages"),it.pageCount) }
                }
            }
            // Each experiment owns an independent descriptor, so A cannot advance or close B/C.
            suspend fun engine(handle:ReadHandle) {
                try {
                    verifyDescriptor(handle.descriptor,row)
                    val result=AndroidPdfReadEngine(limits).open(PdfReadSource(handle))
                    try {
                        assertEquals(ReadOwnership.TRANSFERRED,handle.ownership)
                        assertTrue("Expected native session, got $result",result is Outcome.Success)
                        assertEquals(row.getInt("expected_pages"),(result as Outcome.Success).value.info.pageCount)
                    } finally {if(result is Outcome.Success)result.value.close()}
                } finally {if(handle.ownership==ReadOwnership.OWNED)handle.close()}
            }
            // experiment is non-suspending; runBlocking stays on this test-owned IO thread.
            experiment("B file descriptor through production engine") {
                val resource=InputResource(ParcelFileDescriptor.open(copy,ParcelFileDescriptor.MODE_READ_ONLY));resources+=resource
                runBlocking { engine(ReadHandle(resource,ReadProvenance.Provider(StorageDocumentRef("canonical","01")),true)) }
                assertEquals(1,resource.closes.get())
            }
            val token=UUID.randomUUID().toString()
            val uri=Uri.parse("content://com.abdev.partituraspdf.test.integration.pdf/document/file-01").buildUpon().appendQueryParameter("request",token).build()
            val resolver=instrumentation.targetContext.contentResolver
            try {
                experiment("C SAF provider through production access and engine") {
                    runBlocking {
                        val ref=StorageDocumentRef("saf",uri.toString())
                        val isolated=object:ContextWrapper(instrumentation.targetContext) {
                            override fun getApplicationContext():Context=this
                            override fun getCacheDir():File=directory
                        }
                        val result=SafDocumentReadAccess(resolver,isolated).openSeekable(ref,ReadAccessLimits(row.getLong("bytes")))
                        assertTrue("SAF open failed: $result",result is Outcome.Success)
                        val handle=(result as Outcome.Success).value
                        try { assertEquals(ReadProvenance.Provider(ref),handle.provenance);engine(handle) }
                        finally {if(handle.ownership==ReadOwnership.OWNED)handle.close()}
                    }
                }
            } finally {
                val report=checkNotNull(resolver.call(uri,"await",token,null))
                assertNull(report.getString("failure"));assertEquals(row.getLong("bytes"),report.getLong("bytes"));assertTrue(report.getBoolean("unlinked"))
            }
            Log.i("ReaderNativeDifferential",results.joinToString("; "))
            if(failures.isNotEmpty())throw AssertionError(results.joinToString("; ")).also { failure -> failures.forEach(failure::addSuppressed) }
        }
    }

    @Test fun a_readableCanonicalCorpusUsesNativeRenderingAndGPublication() {
        val readable = fixtures().filter { it.getString("expected_status") == "valid" }
        assertEquals(13, readable.size)
        for (row in readable) {
            open(row); visible(0)
            assertEquals(row.getInt("expected_pages"), controller.state.value.session!!.pageCount)
            val indices = if (row.getString("id") in listOf("08", "09")) (0 until row.getInt("expected_pages")).toList()
                else listOf(0, row.getInt("expected_pages") - 1).distinct()
            val sizes = mutableSetOf<Pair<Int, Int>>()
            for (index in indices) {
                compose.runOnUiThread { controller.dispatch(ReaderAction.Page(index)) }; visible(index)
                val frame = controller.state.value.frame!!; val lease = leases.last()
                assertEquals(frame.request, lease.native.request)
                assertEquals(Outcome.Success(Unit), frame.request.validate(limits))
                assertEquals(Outcome.Success(Unit), ready!!.session.info.validate(frame.request.page))
                assertTrue(frame.request.width.toLong() * frame.request.height <= lease.budgetPixels)
                assertEquals(frame.request.width, lease.bitmap.width); assertEquals(frame.request.height, lease.bitmap.height)
                assertFalse(lease.bitmap.isRecycled); lease.bitmap.getPixel(0, 0)
                if (row.getString("id") in listOf("10", "11")) {
                    // Actual scanned/vector content must survive native rendering, not just allocate white pixels.
                    val pixels = IntArray(lease.bitmap.width * lease.bitmap.height)
                    lease.bitmap.getPixels(pixels, 0, lease.bitmap.width, 0, 0, lease.bitmap.width, lease.bitmap.height)
                    assertTrue("Native score rendered without visible ink", pixels.any { Color.red(it) + Color.green(it) + Color.blue(it) < 300 })
                }
                sizes += frame.displayInfo.width to frame.displayInfo.height
                assertNull(frame.displayInfo.rotation); assertEquals(AnnotationRendering.UNKNOWN, frame.displayInfo.annotations)
                awaitCheckpoint(frame)
                assertEquals(frame.request.page.session to PageIndex(index), displayed.last())
            }
            if (row.getString("id") == "08") assertTrue(sizes.size > 1)
        }
        assertEquals(13, opens.get()); assertEquals(1, maximumRenders.get()); assertEquals(1, maximumRetained.get())
    }
    @Test fun b_hundredPagesRestoreClampAndRapidNavigationKeepOneNativeLease() {
        open(fixtures().single { it.getString("id") == "05" }, registered = true, restored = 9); visible(9)
        awaitCheckpoint(controller.state.value.frame!!)
        compose.runOnUiThread { repeat(200) { controller.dispatch(ReaderAction.Step(1)) } }; visible(99)
        awaitCheckpoint(controller.state.value.frame!!)
        assertEquals(listOf(9, 99), displayed.map { it.second.value })
        compose.runOnUiThread { controller.dispatch(ReaderAction.Step(1)) }; compose.waitForIdle()
        assertEquals(PageIndex(99), controller.state.value.requestedPage)
        assertEquals(1, opens.get()); assertEquals(1, maximumRenders.get()); assertEquals(1, maximumRetained.get())
        assertTrue(leases.size < 100) // Navigation never renders/caches all intervening pages.
        open(fixtures().single { it.getString("id") == "05" }, registered = true, restored = 1000); visible(99)
        awaitCheckpoint(controller.state.value.frame!!)
        assertEquals(PageIndex(99), controller.state.value.requestedPage); assertEquals(2, opens.get())
        assertEquals(1, maximumRetained.get())
    }
    @Test fun c_nativePostedPixelsSurviveOriginalLeaseRecycling() {
        open(fixtures().single { it.getString("id") == "01" }); visible(0)
        lateinit var view: ReaderPageSurface
        compose.runOnUiThread { view = surface() }
        val before = copy(view)
        try {
            val geometry = readerImageLayout(controller.state.value.frame!!.displayInfo.width,
                controller.state.value.frame!!.displayInfo.height, ReaderViewport(view.width, view.height), PageFit.PAGE, ReaderTransform())!!
            var ink = false
            for (y in (geometry.top.toInt() + 2) until (geometry.top + geometry.height).toInt() - 2 step 2)
                for (x in (geometry.left.toInt() + 2) until (geometry.left + geometry.width).toInt() - 2 step 2) {
                    val pixel = before.getPixel(x, y)
                    if (Color.red(pixel) + Color.green(pixel) + Color.blue(pixel) < 300) ink = true
                }
            assertTrue("Posted PDF must contain real ink, not just an empty surface", ink)
            compose.runOnUiThread { controller.state.value.frame!!.requestRetirement(); view.release() }
            assertTrue(leases.single().bitmap.isRecycled)
            val after = copy(view)
            try { assertTrue("Posted pixels changed after source lease release", before.sameAs(after)) }
            finally { after.recycle() }
        } finally { before.recycle() }
    }
    @Test fun d_invalidRequestsAndClosedNativeSessionCannotBeReusedOnResume() {
        open(fixtures().single { it.getString("id") == "02" }); visible(0)
        val old = owner!!.native; val good = controller.state.value.frame!!.request.page
        runBlocking {
            assertEquals(Outcome.Failure(AppError.Validation.INVALID_PAGE), old.displayInfo(good.copy(page = PageIndex(2))))
            assertEquals(Outcome.Failure(AppError.Conflict.STALE_SESSION), old.displayInfo(good.copy(sourceGeneration = good.sourceGeneration + 1)))
            assertEquals(Outcome.Failure(AppError.Resource.RENDER_LIMIT), old.render(RenderRequest(RenderRequestId("over-limit"), good, 513, 32, 0)))
        }
        lateinit var barrier: Deferred<Outcome<Unit>>
        compose.runOnUiThread { barrier = scope.async { controller.quiesce(ReaderTrim.BACKGROUND) } }
        compose.waitUntil(20_000) { barrier.isCompleted }
        runBlocking {
            assertEquals(Outcome.Success(Unit), barrier.await()); assertEquals(Outcome.Success(Unit), route.background()); route.close()
            assertEquals(Outcome.Failure(AppError.Conflict.SESSION_CLOSED), old.displayInfo(good))
        }
        compose.runOnUiThread { controller.resume() }; visible(0)
        assertNotEquals(old.info.id, controller.state.value.session!!.id)
        assertEquals(2, opens.get()); assertEquals(1, maximumRetained.get())
    }
    @Test fun e_quiescenceDisposesLateRealNativeOutputWithoutPresentationOrAnotherOpen() {
        renderCompletionGate = CompletableDeferred()
        open(fixtures().single { it.getString("id") == "10" })
        compose.waitUntil(20_000) { leases.size == 1 }
        assertNull(controller.state.value.frame); assertTrue(displayed.isEmpty())
        lateinit var barrier: Deferred<Outcome<Unit>>
        compose.runOnUiThread { barrier = scope.async { controller.quiesce(ReaderTrim.LOW_MEMORY) } }
        assertFalse(barrier.isCompleted); assertFalse(leases.single().bitmap.isRecycled)
        renderCompletionGate!!.complete(Unit)
        compose.waitUntil(20_000) { barrier.isCompleted }
        assertEquals(Outcome.Success(Unit), runBlocking { barrier.await() })
        assertTrue(leases.single().bitmap.isRecycled); assertEquals(1, leases.single().closes.get())
        assertNull(controller.state.value.frame); assertTrue(displayed.isEmpty())
        renderCompletionGate = null
        compose.runOnUiThread { controller.resume() }; visible(0)
        assertEquals(1, opens.get()); assertEquals(1, maximumRenders.get()); assertEquals(1, maximumRetained.get())
    }
    @Test fun z_protectedAndCorruptInputsSurfaceActualAdapterFailuresWithoutPublishingABitmap() {
        for (id in listOf("15", "14")) {
            open(fixtures().single { it.getString("id") == id })
            compose.waitUntil(20_000) { controller.state.value.phase == ReaderPhase.ERROR }
            val error = controller.state.value.failure!!.error
            if (id == "15") assertEquals(AppError.Validation.INVALID_DOCUMENT, error)
            else assertTrue(error == AppError.Unsupported.DOCUMENT_FORMAT || error == AppError.Validation.INVALID_DOCUMENT)
            assertEquals(ReaderRetry.NONE, readerRetry(controller.state.value))
            val failure=assertThrows(AssertionError::class.java){visible(0)}
            assertTrue(failure.message!!.startsWith("Native terminal error:"))
            assertTrue(failure.message!!.contains("error=$error"))
            assertNull(controller.state.value.frame); assertTrue(displayed.isEmpty()); assertTrue(leases.isEmpty())
        }
        assertEquals(2, opens.get())
    }
}
