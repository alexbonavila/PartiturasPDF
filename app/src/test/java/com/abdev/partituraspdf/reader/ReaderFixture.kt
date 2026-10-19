package com.abdev.partituraspdf.reader

import android.graphics.Bitmap
import com.abdev.partituraspdf.core.document.*
import com.abdev.partituraspdf.core.foundation.*
import com.abdev.partituraspdf.core.preferences.PageFit
import com.abdev.partituraspdf.core.result.*
import com.abdev.partituraspdf.pdf.api.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*

internal val registeredRequest = DocumentOpenRequest.Registered(DocumentRef(DocumentId("reader-test")))
internal val transientRequest = DocumentOpenRequest.Transient(StorageDocumentRef("test", "opaque"))
internal val ceiling = RenderLimits(4096, 4_194_304)
internal val ampleHeap = HeapAvailability(256L * 1024 * 1024, 0)

internal class TestPage(override val request: RenderRequest) : RenderedPage {
    override val displayInfo = PageDisplayInfo(request.page, 600, 900)
    override val bitmap: Bitmap get() = error("JVM test must not fabricate an Android bitmap")
    var closes = 0
    override fun close() { closes++ }
}

internal class TestSession(id: String = "native-test", count: Int = 8) : PdfReadSession {
    override val info = ReadSessionInfo(ReadSessionId(id), 7, count)
    override var renderLimits = ceiling
    val requests = mutableListOf<RenderRequest>()
    val pages = mutableListOf<TestPage>()
    val inspections = mutableListOf<ReadPageRef>()
    var closes = 0
    var render: suspend (RenderRequest) -> Outcome<RenderedPage> = { request ->
        TestPage(request).also { pages += it }.let { Outcome.Success(it) }
    }
    var inspect: suspend (ReadPageRef) -> Outcome<PageDisplayInfo> = { Outcome.Success(PageDisplayInfo(it, 600, 900)) }
    override suspend fun displayInfo(page: ReadPageRef): Outcome<PageDisplayInfo> {
        inspections += page; return inspect(page)
    }
    override suspend fun render(request: RenderRequest): Outcome<RenderedPage> {
        requests += request; return render.invoke(request)
    }
    override fun close() { closes++ }
}

@OptIn(ExperimentalCoroutinesApi::class)
internal class ReaderFixture(val test: TestScope, scope: CoroutineScope = test.backgroundScope) {
    val session = TestSession()
    val opens = mutableListOf<DocumentOpenRequest>()
    val displayed = mutableListOf<Pair<ReadSessionId, PageIndex>>()
    val consumers = mutableListOf<ReaderPresentation>()
    val gates = mutableListOf<CompletableDeferred<Unit>>()
    var heap = ampleHeap
    var active: ReaderReady? = null
    var currentCalls = 0
    var routeCloses = 0
    var backgrounds = 0
    var opening: suspend (DocumentOpenRequest) -> Outcome<ReaderReady> = { Outcome.Success(ReaderReady(it, session, PageIndex(2))) }
    var beforeDisplayed: suspend () -> Unit = {}
    var acknowledge: suspend () -> Outcome<Unit> = { Outcome.Success(Unit) }
    var current: suspend () -> ReaderReady? = { active }
    val route = object : ReaderRoute {
        override suspend fun current(): ReaderReady? { currentCalls++; return this@ReaderFixture.current() }
        override suspend fun open(request: DocumentOpenRequest): Outcome<ReaderReady> {
            opens += request
            return opening(request).also { if (it is Outcome.Success) active = it.value }
        }
        override suspend fun displayed(session: ReadSessionId, page: PageIndex): Outcome<Unit> {
            beforeDisplayed()
            displayed += session to page; return acknowledge()
        }
        override suspend fun background(): Outcome<Unit> { backgrounds++; return Outcome.Success(Unit) }
        override suspend fun close(): Outcome<Unit> { routeCloses++; active = null; return Outcome.Success(Unit) }
    }
    val controller = ReaderController(scope, route, ceiling, { heap }, PageFit.PAGE)
    val state get() = controller.state.value
    fun gate() = CompletableDeferred<Unit>().also { gates += it }
    fun attach(): ReaderPresentation = checkNotNull(state.frame?.attach()).also { consumers += it }
    fun open(request: DocumentOpenRequest = registeredRequest, sequence: Long = 1, viewport: Boolean = true) {
        if (viewport) controller.dispatch(ReaderAction.Viewport(ReaderViewport(600, 900)))
        controller.open(ReaderOpenIntent(sequence, request))
        test.runCurrent()
    }
    suspend fun finish() {
        consumers.forEach { it.detached() }
        gates.forEach { it.complete(Unit) }
        val stop = test.async { controller.retire() }
        test.runCurrent(); stop.await()
    }
}

internal suspend fun TestScope.withReader(block: suspend ReaderFixture.() -> Unit) {
    val f = ReaderFixture(this)
    try { f.block() } finally { f.finish() }
}
