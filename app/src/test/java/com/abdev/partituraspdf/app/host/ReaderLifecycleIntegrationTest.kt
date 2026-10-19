package com.abdev.partituraspdf.app.host

import android.content.ComponentCallbacks2
import com.abdev.partituraspdf.core.document.*
import com.abdev.partituraspdf.core.foundation.*
import com.abdev.partituraspdf.core.preferences.PageFit
import com.abdev.partituraspdf.core.result.*
import com.abdev.partituraspdf.library.LibraryRowKey
import com.abdev.partituraspdf.pdf.api.*
import com.abdev.partituraspdf.reader.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

/** Real G controller/frame protocol; native/graphics are explicit JVM doubles. */
@OptIn(ExperimentalCoroutinesApi::class)
class ReaderLifecycleIntegrationTest {
    private class Fixture(val test: TestScope) {
        val events = mutableListOf<String>()
        val reports = mutableListOf<ReaderExitReport>()
        val errors = mutableListOf<Throwable>()
        val sessions = mutableListOf<TestSession>()
        var current: ReaderReady? = null
        var opens = 0
        var closes = 0
        var flushes = 0
        var checks = 0
        var displayed = 0
        var opening: suspend (DocumentOpenRequest) -> Outcome<ReaderReady> = { request ->
            Outcome.Success(ReaderReady(request, TestSession("session-${++opens}").also { sessions += it }, PageIndex(0)))
        }
        var flush: suspend () -> Outcome<Unit> = { Outcome.Success(Unit) }
        var closing: suspend () -> Outcome<Unit> = { current = null; Outcome.Success(Unit) }
        var inspect: suspend () -> ReaderReady? = { current }
        val route = object : ReaderRoute {
            override suspend fun current(): ReaderReady? { events += "current"; checks++; return inspect() }
            override suspend fun open(request: DocumentOpenRequest): Outcome<ReaderReady> {
                events += "open"
                return opening(request).also { if (it is Outcome.Success) current = it.value }
            }
            override suspend fun displayed(session: ReadSessionId, page: PageIndex): Outcome<Unit> {
                displayed++; current = current!!.copy(page = page); return Outcome.Success(Unit)
            }
            override suspend fun background(): Outcome<Unit> { events += "flush"; flushes++; return flush() }
            override suspend fun close(): Outcome<Unit> { events += "close"; closes++; return closing() }
        }
        val lifetime = ReaderHostLifetime(CoroutineScope(test.backgroundScope.coroutineContext + CoroutineExceptionHandler { _, e -> errors += e }),
            { route }, ceiling, { ampleHeap }, { Outcome.Success(PageFit.PAGE) })
        init { lifetime.connect({ _, _ -> true }, {}, {}, reports::add) }
        val entry get() = lifetime.state.value.entry!!
        fun open(request: DocumentOpenRequest = transientRequest) {
            lifetime.arm(LibraryRowKey(StorageDocumentRef("test", "row"), 1, 1), 0)
            assertEquals(Outcome.Success(Unit), lifetime.accept(request)); test.runCurrent()
        }
        fun trim(trim: ReaderTrim) { lifetime.trim(entry.intent.sequence, trim); test.runCurrent() }
        fun resume() { lifetime.foreground(true); test.runCurrent() }
        suspend fun finish() { val done = lifetime.leave(); test.runCurrent(); done.await() }
        fun render() { lifetime.dispatch(entry.intent.sequence, ReaderAction.Viewport(ReaderViewport(600, 900))); test.runCurrent() }
    }
    @Test fun backgroundQuiescesFlushesClosesAndResumeReopensOnlyThroughGWithoutNewIntent() = runTest {
        val f = Fixture(this); f.open(); val entry = f.entry; val intent = entry.intent
        f.trim(ReaderTrim.BACKGROUND)
        assertEquals(listOf("open", "flush", "close"), f.events)
        assertEquals(ReaderPhase.QUIESCED, entry.controller.state.value.phase)
        assertTrue(entry.owner.isActive); assertTrue(f.lifetime.state.value.suspended)
        f.resume(); assertEquals(2, f.opens); assertSame(entry, f.entry); assertEquals(intent, f.entry.intent)
        assertEquals(listOf("open", "flush", "close", "current", "open"), f.events)
        f.finish()
    }
    @Test fun lowMemoryReleasesPageButNeitherFlushesNorClosesOrAutomaticallyReallocates() = runTest {
        val f = Fixture(this); f.open(); f.render(); val page = f.sessions.single().pages.single()
        f.trim(ReaderTrim.LOW_MEMORY)
        assertEquals(1, page.closes); assertEquals(0, f.flushes); assertEquals(0, f.closes)
        assertTrue(f.lifetime.state.value.suspended); assertNull(f.entry.controller.state.value.frame)
        f.resume(); assertEquals(1, f.opens); assertEquals(1, f.checks); f.finish()
    }
    @Test fun criticalMemoryClosesSessionAndRetainsControllerAndLogicalIntent() = runTest {
        val f = Fixture(this); f.open(); val controller = f.entry.controller
        f.trim(ReaderTrim.CRITICAL)
        assertEquals(1, f.closes); assertEquals(1, f.flushes); assertSame(controller, f.entry.controller)
        f.resume(); assertEquals(2, f.opens); assertSame(controller, f.entry.controller); f.finish()
    }
    @Test fun resumeDuringDelayedFlushWaitsForCloseAndHappensOnce() = runTest {
        val f = Fixture(this); f.open(); val gate = CompletableDeferred<Unit>()
        f.flush = { gate.await(); Outcome.Success(Unit) }
        f.trim(ReaderTrim.BACKGROUND); repeat(3) { f.resume() }
        assertEquals(0, f.closes); assertEquals(1, f.opens)
        gate.complete(Unit); runCurrent(); assertEquals(1, f.closes); assertEquals(2, f.opens)
        repeat(3) { f.resume() }; assertEquals(2, f.opens); f.finish()
    }
    @Test fun repeatedBackgroundAndCriticalRequestsDoNotRepeatCheckpointOrClose() = runTest {
        val f = Fixture(this); f.open()
        repeat(4) { f.trim(ReaderTrim.BACKGROUND) }
        assertEquals(1, f.flushes); assertEquals(1, f.closes)
        repeat(3) { f.trim(ReaderTrim.CRITICAL) }
        assertEquals(1, f.flushes); assertEquals(1, f.closes); f.finish(); assertEquals(1, f.closes)
    }
    @Test fun delayedDrawAttachmentBlocksCleanupWithoutRecyclingAndCanDetachWithoutAnyCollector() = runTest {
        val f = Fixture(this); f.open(); f.render()
        val attachment = f.entry.controller.state.value.frame!!.attach()!!
        f.trim(ReaderTrim.BACKGROUND)
        assertTrue(f.lifetime.state.value.suspended); assertEquals(0, f.closes)
        assertEquals(0, f.sessions.single().pages.single().closes)
        // No state collector exists in this test. An explicit graphics completion still unblocks G.
        attachment.detached(); runCurrent()
        assertEquals(1, f.closes); assertEquals(1, f.sessions.single().pages.single().closes); f.finish()
    }
    @Test fun lowThenCriticalWhileRenderIsNonCooperativeCannotDowngradeCleanup() = runTest {
        val f = Fixture(this); f.open(); val gate = CompletableDeferred<Unit>(); var late: TestPage? = null
        f.sessions.single().render = { request -> withContext(NonCancellable) { gate.await(); Outcome.Success(TestPage(request).also { late = it }) } }
        f.render(); f.trim(ReaderTrim.LOW_MEMORY); f.trim(ReaderTrim.CRITICAL); f.trim(ReaderTrim.LOW_MEMORY)
        assertEquals(0, f.closes)
        gate.complete(Unit); runCurrent()
        assertEquals(1, f.closes); assertEquals(1, late!!.closes); assertNull(f.entry.controller.state.value.frame)
        assertTrue(f.lifetime.state.value.suspended); f.finish()
    }
    @Test fun delayedNativeOpenCompletesBeforeBackgroundCanCloseTheRoute() = runTest {
        val f = Fixture(this); val gate = CompletableDeferred<Unit>()
        f.opening = { request -> withContext(NonCancellable) {
            gate.await(); Outcome.Success(ReaderReady(request, TestSession().also { f.sessions += it }, PageIndex(0)))
        } }
        f.open(); f.trim(ReaderTrim.BACKGROUND); assertEquals(0, f.closes)
        gate.complete(Unit); runCurrent(); assertEquals(1, f.closes); f.finish()
    }
    @Test fun backDuringBackgroundWinsOverQueuedResumeAndDoesNotDoubleClose() = runTest {
        val f = Fixture(this); f.open(); val gate = CompletableDeferred<Unit>()
        f.flush = { gate.await(); Outcome.Success(Unit) }
        f.trim(ReaderTrim.BACKGROUND); f.resume()
        val exit = f.lifetime.leave(); runCurrent(); assertFalse(exit.isCompleted)
        gate.complete(Unit); runCurrent(); exit.await()
        assertEquals(1, f.closes); assertEquals(1, f.opens); assertNull(f.lifetime.state.value.entry)
        f.lifetime.foreground(true); runCurrent(); assertEquals(1, f.opens)
    }
    @Test fun terminalDisposalJoinsMandatoryCleanupAndRejectsStaleLifecycleEvents() = runTest {
        val f = Fixture(this); f.open(); val sequence = f.entry.intent.sequence; val gate = CompletableDeferred<Unit>()
        f.flush = { gate.await(); Outcome.Success(Unit) }; f.trim(ReaderTrim.BACKGROUND)
        val stopped = backgroundScope.async { f.lifetime.terminate() }; runCurrent()
        f.lifetime.trim(sequence, ReaderTrim.CRITICAL); f.resume()
        assertFalse(stopped.isCompleted); gate.complete(Unit); runCurrent(); stopped.await()
        assertEquals(1, f.closes); assertEquals(1, f.opens)
    }
    @Test fun checkpointFailureStaysVisibleButDoesNotInventDurabilityOrReuseAClosedSession() = runTest {
        val f = Fixture(this); f.open(); f.flush = { Outcome.Failure(AppError.Access.PRIVATE_STORAGE_UNAVAILABLE) }
        f.trim(ReaderTrim.BACKGROUND)
        assertEquals(AppError.Access.PRIVATE_STORAGE_UNAVAILABLE, f.lifetime.state.value.report.failures.single().error)
        assertEquals(1, f.reports.size); f.resume(); assertEquals(2, f.opens); f.finish()
    }
    @Test fun closeFailureBlocksAutomaticReuseAndBackRetainsExactFailureEvidence() = runTest {
        val f = Fixture(this); f.open(); f.closing = { Outcome.Failure(AppError.Access.PROVIDER_IO_FAILURE) }
        f.trim(ReaderTrim.BACKGROUND); f.resume()
        assertTrue(f.lifetime.state.value.recoveryBlocked); assertEquals(1, f.opens); assertEquals(0, f.checks)
        val exit = f.lifetime.leave(); runCurrent()
        assertEquals(ReaderCleanupStage.CLOSE, exit.await().failures.single().stage)
    }
    @Test fun cancellationAndProgrammingDefectsRemainOriginalAndRequireRecovery() = runTest {
        for (failure in listOf(CancellationException("flush"), IllegalStateException("flush defect"))) {
            val f = Fixture(this); f.open(); f.flush = { throw failure }; f.trim(ReaderTrim.CRITICAL)
            assertSame(failure, f.lifetime.state.value.report.cause); assertTrue(f.lifetime.state.value.recoveryBlocked)
            assertSame(failure, f.reports.single().cause); f.finish()
        }
    }
    @Test fun transientReopenUsesOnlyTheLastActuallyPresentedInProcessPage() = runTest {
        val f = Fixture(this); f.open(); f.render()
        f.lifetime.dispatch(1, ReaderAction.Page(3)); runCurrent()
        val presentation = f.entry.controller.state.value.frame!!.attach()!!
        f.lifetime.dispatch(1, ReaderAction.Presented(presentation)); runCurrent(); presentation.detached()
        f.trim(ReaderTrim.BACKGROUND); f.resume()
        assertEquals(PageIndex(3), f.entry.controller.state.value.requestedPage)
        assertEquals(1, f.displayed); assertEquals(2, f.opens); f.finish()
    }
    @Test fun merelyRequestedOrRenderedTransientPageNeverBecomesAResumeHint() = runTest {
        val f = Fixture(this); f.open(); f.render()
        f.lifetime.dispatch(1, ReaderAction.Page(4)); runCurrent()
        assertNull(f.entry.controller.state.value.presentedPage)
        f.trim(ReaderTrim.BACKGROUND); f.resume()
        assertEquals(PageIndex(0), f.entry.controller.state.value.requestedPage)
        assertEquals(0, f.displayed); f.finish()
    }

    @Test fun registeredReopenUsesAuthoritativeRoutePositionRatherThanTransientHint() = runTest {
        val f = Fixture(this); f.open(registeredRequest); f.trim(ReaderTrim.BACKGROUND)
        f.opening = { request -> Outcome.Success(ReaderReady(request, TestSession().also { f.sessions += it }, PageIndex(5))) }
        f.resume(); assertEquals(PageIndex(5), f.entry.controller.state.value.requestedPage); f.finish()
    }
    @Test fun staleGenerationAndEventsAfterExitCannotAffectAReplacementReader() = runTest {
        val f = Fixture(this); f.open(); val old = f.entry.intent.sequence; f.finish(); f.open()
        f.lifetime.trim(old, ReaderTrim.CRITICAL); runCurrent()
        assertFalse(f.lifetime.state.value.suspended); assertEquals(1, f.closes); f.finish()
    }
    @Test fun viewportCanUpdateWhileSuspendedButPageAndFitActionsCannotAllocate() = runTest {
        val f = Fixture(this); f.open(); f.trim(ReaderTrim.LOW_MEMORY)
        f.lifetime.dispatch(1, ReaderAction.Viewport(ReaderViewport(800, 600)))
        f.lifetime.dispatch(1, ReaderAction.Fit(PageFit.WIDTH)); f.lifetime.dispatch(1, ReaderAction.Page(4)); runCurrent()
        assertEquals(ReaderViewport(800, 600), f.entry.controller.state.value.viewport)
        assertEquals(PageFit.PAGE, f.entry.controller.state.value.fit); assertTrue(f.sessions.single().requests.isEmpty())
        f.resume(); assertTrue(f.sessions.single().requests.isNotEmpty()); f.finish()
    }
    @Test fun foreignRetainedSessionIsRejectedByGInsteadOfReusedOnResume() = runTest {
        val f = Fixture(this); f.open(); f.trim(ReaderTrim.LOW_MEMORY)
        f.inspect = { ReaderReady(transientRequest, TestSession("foreign"), PageIndex(0)) }
        f.resume()
        assertEquals(AppError.Conflict.STALE_SESSION, f.entry.controller.state.value.failure!!.error)
        assertEquals(1, f.opens); f.finish()
    }
    @Test fun releaseDefectAndTypedCheckpointFailureRemainTogetherInTerminalEvidence() = runTest {
        val f = Fixture(this); f.open(); val defect = IllegalStateException("release defect")
        f.sessions.single().render = { request -> Outcome.Success(object : RenderedPage {
            override val request = request
            override val displayInfo = PageDisplayInfo(request.page, 600, 900)
            override val bitmap: android.graphics.Bitmap get() = error("No JVM bitmap")
            override fun close() { throw defect }
        }) }
        f.flush = { Outcome.Failure(AppError.Access.PRIVATE_STORAGE_UNAVAILABLE) }
        f.render(); f.trim(ReaderTrim.CRITICAL)
        val report = f.lifetime.state.value.report
        assertNotNull(report.cause)
        assertTrue(report.cause!!.suppressed.any { it is ReaderExitException })
        assertEquals(ReaderCleanupStage.CHECKPOINT, report.failures.single().stage)
        f.finish()
    }

    @Test fun namedAndroidLevelsHaveExplicitMappingsAndUnknownLevelsAreIgnored() {
        assertEquals(ReaderTrim.LOW_MEMORY, readerTrimForAndroid(ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW))
        for (level in listOf(ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL, ComponentCallbacks2.TRIM_MEMORY_MODERATE, ComponentCallbacks2.TRIM_MEMORY_COMPLETE))
            assertEquals(ReaderTrim.CRITICAL, readerTrimForAndroid(level))
        for (level in listOf(ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN, ComponentCallbacks2.TRIM_MEMORY_BACKGROUND))
            assertEquals(ReaderTrim.BACKGROUND, readerTrimForAndroid(level))
        assertNull(readerTrimForAndroid(ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN, true))
        assertEquals(ReaderTrim.CRITICAL, readerTrimForAndroid(ComponentCallbacks2.TRIM_MEMORY_COMPLETE, true))
        assertNull(readerTrimForAndroid(ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE)); assertNull(readerTrimForAndroid(-1))
    }
}
