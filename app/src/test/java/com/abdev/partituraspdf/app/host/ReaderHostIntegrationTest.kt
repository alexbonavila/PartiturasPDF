package com.abdev.partituraspdf.app.host

import com.abdev.partituraspdf.app.navigation.AppDestination
import com.abdev.partituraspdf.core.document.*
import com.abdev.partituraspdf.core.foundation.*
import com.abdev.partituraspdf.core.result.*
import com.abdev.partituraspdf.library.*
import com.abdev.partituraspdf.reader.*
import com.abdev.partituraspdf.settings.SettingsFixture
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReaderHostIntegrationTest {
    private class Session : AppHostSession {
        lateinit var features: AppHostFeatures
        lateinit var f: LibraryFixture
        lateinit var h: SettingsFixture
        val events = mutableListOf<String>()
        var flush: suspend () -> Outcome<Unit> = { Outcome.Success(Unit) }
        var retiringFailure: Throwable? = null
        val routes = mutableListOf<com.abdev.partituraspdf.pdf.api.ReaderRoute>()
        val requests = mutableListOf<DocumentOpenRequest>()
        override fun load(scope: CoroutineScope): AppHostFeatures {
            f = LibraryFixture(scope).also { it.list = { Outcome.Success(DocumentListing(listOf(pdfEntry), true)) } }
            h = SettingsFixture(scope)
            val reading = ReaderHostLifetime(scope, routeFactory = {
                object : com.abdev.partituraspdf.pdf.api.ReaderRoute {
                    val session = TestSession()
                    override suspend fun current(): com.abdev.partituraspdf.pdf.api.ReaderReady? = null
                    override suspend fun open(request: DocumentOpenRequest): Outcome<com.abdev.partituraspdf.pdf.api.ReaderReady> {
                        events += "open"; requests += request
                        return Outcome.Success(com.abdev.partituraspdf.pdf.api.ReaderReady(request, session, PageIndex(0)))
                    }
                    override suspend fun displayed(session: com.abdev.partituraspdf.pdf.api.ReadSessionId, page: PageIndex): Outcome<Unit> = error("No JVM graphics")
                    override suspend fun background(): Outcome<Unit> { events += "flush"; return flush() }
                    override suspend fun close(): Outcome<Unit> { events += "route-close"; return Outcome.Success(Unit) }
                }.also { routes += it }
            }, renderCeiling = ceiling, heap = { ampleHeap }, fit = { confirmedReaderFit(h.state.preferences) })
            f.event = { event -> if (event is LibraryEvent.Open) reading.accept(event.request) else Outcome.Success(Unit) }
            features = AppHostFeatures(f.navigator, f.controller, h.controller, reader = reading)
            return features
        }
        override suspend fun retireReader(): ReaderExitReport? { retiringFailure?.let { throw it }; return features.reader!!.terminate() }
        override fun stop() { events += "stop-features"; f.close(); h.controller.close() }
        override fun close() { events += "graph-close" }
    }
    private fun TestScope.host(session: Session, failures: MutableList<Throwable> = mutableListOf()) = AppHostViewModel({ session },
        StandardTestDispatcher(testScheduler), StandardTestDispatcher(testScheduler), { false }, failures::add)
    private fun TestScope.open(host: AppHostViewModel, session: Session) {
        host.library(LibraryAction.Open(session.f.pdf.key), host.navigation.value.revision); runCurrent()
    }
    @Test fun realFRegisteredRequestEntersRetainedReaderAndBackRefreshPermitsFreshReopen() = runTest {
        val s = Session(); val h = host(s); runCurrent()
        h.library(LibraryAction.Register(s.f.pdf.key), 0); runCurrent()
        val document = s.f.registered.value.document
        open(h, s)
        val reader = s.features.reader!!; val entry = reader.state.value.entry!!
        assertEquals(listOf(DocumentOpenRequest.Registered(document)), s.requests)
        assertEquals(LibraryOpening.Accepted, s.f.pdf.opening)
        assertEquals(AppDestination.READER, h.navigation.value.destination)
        repeat(3) { h.start(); h.library(LibraryAction.Open(s.f.pdf.key), 0); h.showSettings(1) }; runCurrent()
        assertSame(entry, reader.state.value.entry); assertEquals(1, s.routes.size)
        h.back(1); h.back(1); runCurrent()
        assertEquals(AppDestination.LIBRARY, h.navigation.value.destination)
        assertEquals(LibraryOpening.Idle, s.f.pdf.opening)
        open(h, s); assertEquals(2L, reader.state.value.entry!!.intent.sequence)
        assertNotSame(entry.controller, reader.state.value.entry!!.controller)
        h.dispose().await(); assertEquals(2, s.events.count { it == "route-close" })
    }
    @Test fun transientRequestIsNotRegisteredAndStaleRenderedCallbacksCannotMutateCurrentReader() = runTest {
        val s = Session(); val h = host(s); runCurrent(); open(h, s)
        val entry = s.features.reader!!.state.value.entry!!
        assertEquals(DocumentOpenRequest.Transient(s.f.pdf.entry.storage), s.requests.single())
        assertTrue(s.f.documents.registrations.isEmpty())
        h.reader(ReaderAction.Fit(com.abdev.partituraspdf.core.preferences.PageFit.WIDTH), entry.intent.sequence, 0)
        assertEquals(com.abdev.partituraspdf.core.preferences.PageFit.PAGE, entry.controller.state.value.fit)
        h.reader(ReaderAction.Fit(com.abdev.partituraspdf.core.preferences.PageFit.WIDTH), entry.intent.sequence, 1)
        assertEquals(com.abdev.partituraspdf.core.preferences.PageFit.WIDTH, entry.controller.state.value.fit)
        h.dispose().await()
        h.reader(ReaderAction.Retry, entry.intent.sequence, 1); h.back(1)
        assertEquals(1, s.requests.size)
    }
    @Test fun staleArmedCallbackCannotNavigateAfterSettingsRoundTripOrListingRefresh() = runTest {
        val s = Session(); val h = host(s); runCurrent(); val reader = s.features.reader!!; val key = s.f.pdf.key
        reader.arm(key, 0); h.showSettings(0); h.back(1)
        assertTrue(reader.accept(DocumentOpenRequest.Transient(key.storage)) is Outcome.Failure)
        assertTrue(s.routes.isEmpty())
        reader.arm(key, 2); h.library(LibraryAction.Refresh, 2); runCurrent()
        assertTrue(reader.accept(DocumentOpenRequest.Transient(key.storage)) is Outcome.Failure)
        assertEquals(AppDestination.LIBRARY, h.navigation.value.destination)
        h.dispose().await()
    }
    @Test fun wrongRegisteredIdentityCannotConsumeAPermitForAnUnregisteredRow() = runTest {
        val s = Session(); val h = host(s); runCurrent()
        s.features.reader!!.arm(s.f.pdf.key, 0)
        assertTrue(s.features.reader!!.accept(registeredRequest) is Outcome.Failure)
        assertTrue(s.routes.isEmpty()); h.dispose().await()
    }
    @Test fun failedLookupCannotBeBypassedByDirectHostCallbackOrFakeRegisteredIdentity() = runTest {
        val s = Session(); val h = host(s); runCurrent()
        s.f.documents.find = { Outcome.Failure(AppError.Access.PRIVATE_STORAGE_UNAVAILABLE) }
        h.library(LibraryAction.Refresh, 0); runCurrent()
        s.features.reader!!.arm(s.f.pdf.key, 0)
        assertTrue(s.features.reader!!.accept(DocumentOpenRequest.Transient(s.f.pdf.key.storage)) is Outcome.Failure)
        assertTrue(s.routes.isEmpty()); h.dispose().await()
    }
    @Test fun terminalGraphCloseWaitsForOngoingReaderBackAndFlushAndRejectsNewCommands() = runTest {
        val s = Session(); val h = host(s); runCurrent(); open(h, s)
        val gate = CompletableDeferred<Unit>(); s.flush = { gate.await(); Outcome.Success(Unit) }
        h.back(1); runCurrent(); val disposed = h.dispose(); runCurrent()
        assertFalse(disposed.isCompleted); assertFalse("graph-close" in s.events)
        h.library(LibraryAction.Refresh, 0); h.showSettings(0); h.back(1)
        gate.complete(Unit); runCurrent(); assertNull(disposed.await())
        assertEquals(listOf("open", "flush", "route-close", "stop-features", "graph-close"), s.events)
        assertEquals(1, s.routes.size)
    }
    @Test fun typedCheckpointFailureRemainsVisibleOnLibraryAndInTerminalShutdownEvidence() = runTest {
        val s = Session(); val failures = mutableListOf<Throwable>(); val h = host(s, failures); runCurrent(); open(h, s)
        s.flush = { Outcome.Failure(AppError.Access.PRIVATE_STORAGE_UNAVAILABLE) }
        h.back(1); runCurrent()
        assertEquals(AppDestination.LIBRARY, h.navigation.value.destination)
        assertTrue(failures.single() is ReaderExitException)
        assertEquals(AppError.Access.PRIVATE_STORAGE_UNAVAILABLE, s.features.reader!!.state.value.report.failures.single().error)
        assertTrue(h.dispose().await() is ReaderExitException)
    }
    @Test fun unexpectedCleanupFailureRetainsOriginalAndTerminatesHostAfterNativeCleanup() = runTest {
        val s = Session(); val failures = mutableListOf<Throwable>(); val h = host(s, failures); runCurrent(); open(h, s)
        val original = IllegalStateException("flush defect"); s.flush = { throw original }
        h.back(1); runCurrent(); assertSame(original, h.dispose().await())
        assertTrue(failures.any { it === original }); assertTrue(s.events.indexOf("route-close") < s.events.indexOf("graph-close"))
    }
    @Test fun registeredRowCannotBeOpenedThroughATransientOrWrongRegisteredIdentityCallback() = runTest {
        val s = Session(); val h = host(s); runCurrent()
        h.library(LibraryAction.Register(s.f.pdf.key), 0); runCurrent()
        val reading = s.features.reader!!
        reading.arm(s.f.pdf.key, 0)
        assertTrue(reading.accept(DocumentOpenRequest.Transient(s.f.pdf.entry.storage)) is Outcome.Failure)
        reading.arm(s.f.pdf.key, 0)
        assertTrue(reading.accept(registeredRequest) is Outcome.Failure)
        assertTrue(s.routes.isEmpty()); h.dispose().await()
    }
    @Test fun staleCallbackDuringUnfinishedListingCannotCreateAReaderWithoutAnAuthoritativeRow() = runTest {
        val s = Session(); val h = host(s); runCurrent(); val key = s.f.pdf.key
        val listing = CompletableDeferred<DocumentListing>()
        s.f.list = { Outcome.Success(listing.await()) }
        h.library(LibraryAction.Refresh, 0); runCurrent()
        s.features.reader!!.arm(key, 0)
        assertTrue(s.features.reader!!.accept(DocumentOpenRequest.Transient(key.storage)) is Outcome.Failure)
        assertTrue(s.routes.isEmpty()); h.dispose().await()
    }

    @Test fun configurationStopAndObsoleteActivityCallbacksDoNotCloseOrReopenReader() = runTest {
        val s = Session(); val h = host(s); h.activityCreated("old"); h.activityStarted("old"); runCurrent(); open(h, s)
        val entry = s.features.reader!!.state.value.entry
        h.activityStopped("old", true); h.activityCreated("new"); h.activityStopped("old", false)
        h.activityMemory("old", android.content.ComponentCallbacks2.TRIM_MEMORY_COMPLETE); h.activityStarted("new"); runCurrent()
        assertSame(entry, s.features.reader!!.state.value.entry); assertEquals(1, s.requests.size)
        assertFalse("route-close" in s.events); h.dispose().await()
    }
    @Test fun realBoundaryBackgroundAndForegroundSerializeWithoutChangingUserIntent() = runTest {
        val s = Session(); val h = host(s); h.activityCreated("activity"); h.activityStarted("activity"); runCurrent(); open(h, s)
        val entry = s.features.reader!!.state.value.entry!!
        val gate = CompletableDeferred<Unit>(); s.flush = { gate.await(); Outcome.Success(Unit) }
        h.activityStopped("activity", false); runCurrent(); h.activityStarted("activity"); runCurrent()
        assertEquals(1, s.requests.size); assertFalse("route-close" in s.events)
        gate.complete(Unit); runCurrent()
        assertEquals(2, s.requests.size); assertSame(entry, s.features.reader!!.state.value.entry)
        assertEquals(1L, entry.intent.sequence); h.dispose().await()
    }
    @Test fun lowAndCriticalCallbacksUseDistinctPoliciesAndConfigurationHintsAreIgnored() = runTest {
        val s = Session(); val h = host(s); h.activityCreated("activity"); runCurrent(); open(h, s)
        h.activityMemory("activity", -1)
        h.activityMemory("activity", android.content.ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN, true)
        assertFalse(s.features.reader!!.state.value.suspended)
        h.activityMemory("activity", android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW); runCurrent()
        assertTrue(s.features.reader!!.state.value.suspended); assertFalse("route-close" in s.events)
        h.activityMemory("activity", android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL); runCurrent()
        assertEquals(1, s.events.count { it == "route-close" })
        h.resumeReader(99); assertTrue(s.features.reader!!.state.value.suspended)
        h.resumeReader(h.navigation.value.revision); runCurrent(); assertEquals(2, s.requests.size)
        h.dispose().await()
    }
    @Test fun backgroundBeforeStartupRejectsOpeningWithoutAllocatingNativeOwnership() = runTest {
        val s = Session(); val h = host(s); h.activityCreated("activity"); h.activityStopped("activity", false); runCurrent()
        open(h, s); assertTrue(s.routes.isEmpty()); assertEquals(AppDestination.LIBRARY, h.navigation.value.destination)
        h.activityStarted("activity"); h.library(LibraryAction.RetryOpen(s.f.pdf.key), 0); runCurrent()
        assertEquals(1, s.requests.size); h.dispose().await()
    }
    @Test fun lifecycleDefectIsTerminalOriginalEvidenceAndGraphClosesOnlyAfterReader() = runTest {
        val s = Session(); val errors = mutableListOf<Throwable>(); val h = host(s, errors)
        h.activityCreated("activity"); runCurrent(); open(h, s)
        val defect = IllegalStateException("background flush"); s.flush = { throw defect }
        h.activityStopped("activity", false); runCurrent()
        assertSame(defect, h.dispose().await())
        assertTrue(s.events.indexOf("route-close") < s.events.indexOf("graph-close"))
        h.activityStarted("activity"); h.activityMemory("activity", 15); h.resumeReader(1); runCurrent()
        assertEquals(1, s.requests.size)
    }
    @Test fun typedLifecycleFailureIsLoggedAndRetainsSafeBackAndLibraryState() = runTest {
        val s = Session(); val errors = mutableListOf<Throwable>(); val h = host(s, errors)
        h.activityCreated("activity"); runCurrent(); open(h, s)
        s.flush = { Outcome.Failure(AppError.Access.PRIVATE_STORAGE_UNAVAILABLE) }
        h.activityStopped("activity", false); runCurrent()
        assertTrue(errors.single() is ReaderExitException)
        h.back(1); runCurrent(); assertEquals(AppDestination.LIBRARY, h.navigation.value.destination)
        assertEquals(LibraryOpening.Idle, s.f.pdf.opening); h.dispose().await()
    }

    @Test fun duplicateStartedCallbackCannotUndoAForegroundMemorySuspension() = runTest {
        val s = Session(); val h = host(s); h.activityCreated("activity"); h.activityStarted("activity"); runCurrent(); open(h, s)
        h.activityMemory("activity", android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW); runCurrent()
        h.activityStarted("activity"); runCurrent()
        assertTrue(s.features.reader!!.state.value.suspended)
        h.resumeReader(1); runCurrent(); assertFalse(s.features.reader!!.state.value.suspended)
        h.dispose().await()
    }

    @Test fun uiHiddenNotificationDoesNotSuppressTheNextForegroundAcknowledgment() = runTest {
        val s = Session(); val h = host(s); h.activityCreated("activity"); h.activityStarted("activity"); runCurrent(); open(h, s)
        h.activityMemory("activity", android.content.ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN); runCurrent()
        assertTrue(s.features.reader!!.state.value.suspended)
        h.activityStarted("activity"); runCurrent()
        assertFalse(s.features.reader!!.state.value.suspended); assertEquals(2, s.requests.size)
        h.dispose().await()
    }

    @Test fun retirementEntryPointFailureStillStopsDependentsAndPreservesOriginalShutdownError() = runTest {
        val s = Session(); val h = host(s); runCurrent(); val failure = IllegalArgumentException("cleanup hook")
        s.retiringFailure = failure
        assertSame(failure, h.dispose().await()); assertEquals(listOf("stop-features", "graph-close"), s.events)
    }
}
