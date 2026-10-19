package com.abdev.partituraspdf.app.host

import com.abdev.partituraspdf.core.document.*
import com.abdev.partituraspdf.core.foundation.*
import com.abdev.partituraspdf.core.preferences.*
import com.abdev.partituraspdf.core.result.*
import com.abdev.partituraspdf.library.LibraryRowKey
import com.abdev.partituraspdf.pdf.api.*
import com.abdev.partituraspdf.reader.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReaderHostLifetimeTest {
    private class Fixture(val test: TestScope) {
        val errors = mutableListOf<Throwable>()
        val events = mutableListOf<String>()
        val routes = mutableListOf<Route>()
        val reports = mutableListOf<ReaderExitReport>()
        val accepted = mutableListOf<ReaderOpenPermit>()
        val key = LibraryRowKey(StorageDocumentRef("test", "opaque"), 1, 1)
        var valid = true
        var preference: Observation<AppPreferences> = Observation.Ready(AppPreferences())
        var factoryFailure: Throwable? = null
        var heapReads = 0
        var createRoute: (CoroutineScope) -> Route = { Route().also { routes += it } }
        inner class Route : ReaderRoute {
            val session = TestSession()
            val opens = mutableListOf<DocumentOpenRequest>()
            var opening: suspend (DocumentOpenRequest) -> Outcome<ReaderReady> = { Outcome.Success(ReaderReady(it, session, PageIndex(0))) }
            var flush: suspend () -> Outcome<Unit> = { Outcome.Success(Unit) }
            var closing: suspend () -> Outcome<Unit> = { Outcome.Success(Unit) }
            var entry: ReaderHostEntry? = null
            var flushPhase = ReaderPhase.QUIESCED
            override suspend fun current(): ReaderReady? = null
            override suspend fun open(request: DocumentOpenRequest): Outcome<ReaderReady> { events += "open"; opens += request; return opening(request) }
            override suspend fun displayed(session: ReadSessionId, page: PageIndex): Outcome<Unit> = error("Only a real/simulated G consumer may acknowledge presentation")
            override suspend fun background(): Outcome<Unit> {
                events += "flush"
                assertEquals(flushPhase, entry!!.controller.state.value.phase)
                return flush()
            }
            override suspend fun close(): Outcome<Unit> {
                events += "close"
                assertEquals(ReaderPhase.RETIRED, entry!!.controller.state.value.phase)
                return closing()
            }
        }
        val lifetime = ReaderHostLifetime(CoroutineScope(test.backgroundScope.coroutineContext + CoroutineExceptionHandler { _, failure -> errors += failure }),
            routeFactory = { factoryFailure?.let { throw it }; createRoute(it) },
            renderCeiling = ceiling, heap = { heapReads++; ampleHeap }, fit = { confirmedReaderFit(preference) })
        init { lifetime.connect({ _, _ -> valid }, accepted::add, reports::add) }
        fun arm(revision: Long = 0) = lifetime.arm(key, revision)
        fun open(request: DocumentOpenRequest = registeredRequest): ReaderHostEntry {
            arm(); assertEquals(Outcome.Success(Unit), lifetime.accept(request))
            val entry = lifetime.state.value.entry!!; routes.last().entry = entry; test.runCurrent(); return entry
        }
        suspend fun leave(): ReaderExitReport { val result = lifetime.leave(); test.runCurrent(); return result.await() }
    }

    @Test fun registeredAndTransientIdentityEachReachGExactlyOnceWithIncreasingSequenceAndFreshLifetime() = runTest {
        val f = Fixture(this); val first = f.open()
        assertEquals(listOf(registeredRequest), f.routes[0].opens)
        assertEquals(1L, first.intent.sequence)
        assertEquals(Outcome.Failure(AppError.Conflict.STALE_SESSION), f.lifetime.accept(registeredRequest))
        f.arm(); assertEquals(Outcome.Failure(AppError.Conflict.STALE_SESSION), f.lifetime.accept(transientRequest))
        f.leave(); val next = f.open(transientRequest)
        assertEquals(2L, next.intent.sequence); assertNotSame(first.controller, next.controller)
        assertNotSame(first.route, next.route); assertFalse(first.owner.isActive)
        assertEquals(listOf(transientRequest), f.routes[1].opens)
        f.leave()
    }
    @Test fun missingOrStalePermitCannotAllocateResourcesOrPublishNavigation() = runTest {
        val f = Fixture(this)
        assertTrue(f.lifetime.accept(registeredRequest) is Outcome.Failure)
        f.valid = false; f.arm(7); assertTrue(f.lifetime.accept(registeredRequest) is Outcome.Failure)
        assertTrue(f.routes.isEmpty()); assertTrue(f.accepted.isEmpty())
    }
    @Test fun noConnectionCannotInventHostAcceptance() = runTest {
        val lifetime = ReaderHostLifetime(backgroundScope, { error("Must not allocate") }, ceiling, { ampleHeap }, { Outcome.Success(PageFit.PAGE) })
        lifetime.arm(LibraryRowKey(StorageDocumentRef("test", "x"), 1, 1), 0)
        assertTrue(lifetime.accept(transientRequest) is Outcome.Failure)
        assertEquals(ReaderExitReport(), lifetime.terminate())
    }
    @Test fun confirmedFitUsesActualPageOrWidthAndLoadingFailureNeverAllocatesAReader() = runTest {
        val f = Fixture(this)
        f.preference = Observation.Loading; f.arm()
        assertEquals(Outcome.Failure(AppError.Conflict.STALE_SESSION), f.lifetime.accept(transientRequest))
        f.preference = Observation.Failed(AppError.Access.PRIVATE_STORAGE_UNAVAILABLE); f.arm()
        assertEquals(Outcome.Failure(AppError.Access.PRIVATE_STORAGE_UNAVAILABLE), f.lifetime.accept(transientRequest))
        assertTrue(f.routes.isEmpty())
        for (fit in PageFit.entries) {
            f.preference = Observation.Ready(AppPreferences(reader = ReaderPreferences(fit)))
            val entry = f.open(); assertEquals(fit, entry.controller.state.value.fit); f.leave()
        }
    }
    @Test fun freshHeapIsSuppliedForEveryRenderAndLocalFitNeverWritesPreferences() = runTest {
        val f = Fixture(this); val entry = f.open()
        f.lifetime.dispatch(entry.intent.sequence, ReaderAction.Viewport(ReaderViewport(500, 800))); runCurrent()
        assertEquals(1, f.heapReads)
        f.lifetime.dispatch(entry.intent.sequence, ReaderAction.Fit(PageFit.WIDTH)); runCurrent()
        assertEquals(2, f.heapReads); assertEquals(PageFit.PAGE, (f.preference as Observation.Ready).value.reader.pageFit)
        f.leave()
    }
    @Test fun staleActionsAndActionsAfterTerminalDisposalCannotMutateRetainedController() = runTest {
        val f = Fixture(this); val entry = f.open()
        f.lifetime.dispatch(999, ReaderAction.Fit(PageFit.WIDTH))
        assertEquals(PageFit.PAGE, entry.controller.state.value.fit)
        f.lifetime.stopAccepting(); f.lifetime.arm(f.key, 0)
        f.lifetime.dispatch(entry.intent.sequence, ReaderAction.Fit(PageFit.WIDTH))
        assertEquals(PageFit.PAGE, entry.controller.state.value.fit)
        assertTrue(f.lifetime.accept(transientRequest) is Outcome.Failure)
        val stopped = backgroundScope.async { f.lifetime.terminate() }; runCurrent(); stopped.await()
        assertTrue(f.reports.isEmpty()); assertFalse(entry.owner.isActive)
    }
    @Test fun renderedIsNotPresentedAndBackWaitsForExplicitGraphicalDetachWithoutRecyclingEarly() = runTest {
        val f = Fixture(this); val entry = f.open()
        f.lifetime.dispatch(1, ReaderAction.Viewport(ReaderViewport(600, 900))); runCurrent()
        val frame = entry.controller.state.value.frame!!; val attachment = frame.attach()!!
        assertEquals(ReaderPhase.RENDERED, entry.controller.state.value.phase)
        assertNull(entry.controller.state.value.presentedPage)
        val stopped = f.lifetime.leave(); assertTrue(f.lifetime.state.value.leaving); runCurrent()
        assertFalse(stopped.isCompleted); assertNull(entry.controller.state.value.frame)
        assertEquals(0, f.routes.single().session.pages.single().closes)
        assertFalse("flush" in f.events); assertSame(stopped, f.lifetime.leave())
        attachment.detached(); runCurrent(); stopped.await()
        assertEquals(1, f.routes.single().session.pages.single().closes)
        assertEquals(listOf("open", "flush", "close"), f.events)
        assertEquals(1, f.reports.size)
    }
    @Test fun delayedNonCooperativeNativeRenderMustFinishBeforeFlushRetireAndClose() = runTest {
        val f = Fixture(this); val entry = f.open(); val gate = CompletableDeferred<Unit>()
        f.routes.single().session.render = { request -> withContext(NonCancellable) { gate.await(); Outcome.Success(TestPage(request)) } }
        f.lifetime.dispatch(1, ReaderAction.Viewport(ReaderViewport(600, 900))); runCurrent()
        val stopped = f.lifetime.leave(); runCurrent()
        assertFalse(stopped.isCompleted); assertFalse("close" in f.events)
        f.lifetime.dispatch(1, ReaderAction.Fit(PageFit.WIDTH))
        assertEquals(PageFit.PAGE, entry.controller.state.value.fit)
        gate.complete(Unit); runCurrent(); stopped.await()
        assertEquals(listOf("open", "flush", "close"), f.events)
    }
    @Test fun pendingNonCooperativeOpenFinishesBeforeReaderRouteCanClose() = runTest {
        val f = Fixture(this); val gate = CompletableDeferred<Unit>()
        f.createRoute = { f.Route().also { route ->
            f.routes += route
            route.opening = { request -> withContext(NonCancellable) {
                gate.await(); Outcome.Success(ReaderReady(request, route.session, PageIndex(0)))
            } }
        } }
        val entry = f.open(); val stopped = f.lifetime.leave(); runCurrent()
        assertFalse(stopped.isCompleted); assertFalse("close" in f.events); assertTrue(entry.owner.isActive)
        gate.complete(Unit); runCurrent(); stopped.await()
        assertEquals(listOf("open", "flush", "close"), f.events)
        assertFalse(entry.owner.isActive)
    }

    @Test fun nativeOpenFailureIsVisibleInExistingGStateAfterHostAcknowledgment() = runTest {
        val f = Fixture(this); f.createRoute = { f.Route().also { r -> f.routes += r; r.opening = { Outcome.Failure(AppError.Validation.INVALID_DOCUMENT) } } }
        val entry = f.open()
        assertEquals(ReaderFailure(ReaderStage.OPEN, AppError.Validation.INVALID_DOCUMENT, ReaderRetry.NONE), entry.controller.state.value.failure)
        assertEquals(1, f.accepted.size); f.leave()
    }
    @Test fun cancellationDuringOpenRemainsCanceledAndCanBeSafelyRetired() = runTest {
        val f = Fixture(this); f.createRoute = { f.Route().also { r -> f.routes += r; r.opening = { throw CancellationException("provider interrupted") } } }
        val entry = f.open(); assertEquals(ReaderPhase.CANCELED, entry.controller.state.value.phase)
        assertNull(entry.controller.state.value.failure); f.leave()
    }
    @Test fun checkpointAndCloseTypedFailuresAreVisibleAndNeverPreventRemainingCleanup() = runTest {
        val f = Fixture(this); val entry = f.open()
        f.routes.single().flush = { Outcome.Failure(AppError.Access.PRIVATE_STORAGE_UNAVAILABLE) }
        f.routes.single().closing = { Outcome.Failure(AppError.Conflict.SESSION_CLOSED) }
        val report = f.leave()
        assertEquals(listOf(ReaderCleanupFailure(ReaderCleanupStage.CHECKPOINT, AppError.Access.PRIVATE_STORAGE_UNAVAILABLE),
            ReaderCleanupFailure(ReaderCleanupStage.CLOSE, AppError.Conflict.SESSION_CLOSED)), report.failures)
        assertTrue(report.failure() is ReaderExitException); assertFalse(entry.owner.isActive)
        assertEquals(report, f.reports.single()); assertEquals(report, f.leave())
    }
    @Test fun originalCleanupDefectAndSecondaryFailuresArePreservedWithoutSkippingClose() = runTest {
        val f = Fixture(this); f.open(); val original = IllegalStateException("flush defect"); val secondary = IllegalArgumentException("close defect")
        f.routes.single().flush = { throw original }; f.routes.single().closing = { throw secondary }
        val report = f.leave(); assertSame(original, report.failure()); assertEquals(listOf(secondary), original.suppressed.toList())
        assertEquals(listOf("open", "flush", "close"), f.events)
    }
    @Test fun canceledFlushIsPreservedAsCancellationWhileNativeCloseStillFinishes() = runTest {
        val f = Fixture(this); f.open(); val canceled = CancellationException("flush interrupted")
        f.routes.single().flush = { throw canceled }
        val report = f.leave(); assertSame(canceled, report.cause); assertTrue("close" in f.events)
    }
    @Test fun typedCleanupFailureAndOriginalExceptionBothRemainInspectable() = runTest {
        val f = Fixture(this); f.open(); val original = IllegalStateException("close")
        f.routes.single().flush = { Outcome.Failure(AppError.Access.PRIVATE_STORAGE_UNAVAILABLE) }
        f.routes.single().closing = { throw original }
        val report = f.leave(); assertSame(original, report.failure())
        assertEquals(ReaderCleanupStage.CHECKPOINT, (original.suppressed.single() as ReaderExitException).report.failures.single().stage)
    }
    @Test fun disposalDuringBackSharesSingleCleanupAndSuppressesNavigationReturn() = runTest {
        val f = Fixture(this); val entry = f.open(); val gate = CompletableDeferred<Unit>()
        f.routes.single().flush = { gate.await(); Outcome.Success(Unit) }
        val leaving = f.lifetime.leave(); runCurrent()
        val terminating = backgroundScope.async { f.lifetime.terminate() }; runCurrent()
        assertFalse(terminating.isCompleted); assertFalse(entry.owner.isCancelled)
        gate.complete(Unit); runCurrent(); assertEquals(leaving.await(), terminating.await())
        assertTrue(f.reports.isEmpty()); assertEquals(1, f.events.count { it == "close" })
    }
    @Test fun routeConstructionDefectDoesNotAdvanceIntentOrCreateAPublishedLifetime() = runTest {
        val f = Fixture(this); val defect = IllegalStateException("factory"); f.factoryFailure = defect; f.arm()
        assertSame(defect, assertThrows(IllegalStateException::class.java) { f.lifetime.accept(transientRequest) })
        assertNull(f.lifetime.state.value.entry); assertTrue(f.accepted.isEmpty())
        f.factoryFailure = null; assertEquals(1L, f.open().intent.sequence); f.leave()
    }
    @Test fun graphicalLeaseReleaseDefectStillAttemptsRetirementFlushAndRouteClose() = runTest {
        val f = Fixture(this); val entry = f.open(); val defect = IllegalStateException("lease release failed")
        f.routes.single().flushPhase = ReaderPhase.RETIRED
        f.routes.single().session.render = { request -> Outcome.Success(object : RenderedPage {
            override val request = request
            override val displayInfo = PageDisplayInfo(request.page, 600, 900)
            override val bitmap: android.graphics.Bitmap get() = error("No JVM bitmap")
            override fun close() { throw defect }
        }) }
        f.lifetime.dispatch(1, ReaderAction.Viewport(ReaderViewport(600, 900))); runCurrent()
        val report = f.leave()
        assertNotNull(report.cause)
        assertTrue(generateSequence(report.cause) { it.cause }.any { it === defect })
        assertFalse(entry.owner.isActive); assertTrue("flush" in f.events); assertTrue("close" in f.events)
        assertTrue(f.errors.isNotEmpty())
    }

    @Test fun leavingWithoutReaderIsSafeAndDoesNotAllocateOrInvokeNavigation() = runTest {
        val f = Fixture(this); assertEquals(ReaderExitReport(), f.leave()); assertTrue(f.reports.isEmpty())
        assertEquals(ReaderExitReport(), f.lifetime.terminate()); assertTrue(f.routes.isEmpty())
        f.lifetime.arm(f.key, 0); assertTrue(f.lifetime.accept(registeredRequest) is Outcome.Failure)
    }
}
