package com.abdev.partituraspdf.app.host

import com.abdev.partituraspdf.app.navigation.AppDestination
import com.abdev.partituraspdf.core.preferences.ThemeSelection
import com.abdev.partituraspdf.core.result.Outcome
import com.abdev.partituraspdf.library.LibraryAction
import com.abdev.partituraspdf.library.LibraryFixture
import com.abdev.partituraspdf.settings.SettingsAction
import com.abdev.partituraspdf.settings.SettingsFixture
import java.io.IOException
import java.util.concurrent.CountDownLatch
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewModelScope

@OptIn(ExperimentalCoroutinesApi::class)
class AppHostViewModelTest {
    private class Session : AppHostSession {
        lateinit var f: LibraryFixture
        lateinit var h: SettingsFixture
        val events = mutableListOf<String>()
        var loads = 0
        var stops = 0
        var closes = 0
        var loadFailure: Throwable? = null
        var stopFailure: Throwable? = null
        var closeFailure: Throwable? = null
        var duringLoad: () -> Unit = {}
        var duringStop: () -> Unit = {}
        var duringClose: () -> Unit = {}
        override fun load(scope: CoroutineScope): AppHostFeatures {
            loads++; events += "load"
            f = LibraryFixture(scope).also { it.restore = { Outcome.Success(null) } }
            h = SettingsFixture(scope)
            duringLoad()
            loadFailure?.let { throw it }
            return AppHostFeatures(f.navigator, f.controller, h.controller)
        }
        override fun stop() {
            stops++; events += "stop"
            f.close(); h.controller.close()
            duringStop(); stopFailure?.let { throw it }
        }
        override fun close() {
            closes++; events += "close"
            duringClose(); closeFailure?.let { throw it }
        }
    }
    private fun TestScope.host(session: Session, errors: MutableList<Throwable> = mutableListOf(),
        io: CoroutineDispatcher = StandardTestDispatcher(testScheduler),
        create: () -> AppHostSession = { session }): AppHostViewModel = AppHostViewModel(
        create, StandardTestDispatcher(testScheduler), io, { it is IOException }, errors::add,
    )

    @Test fun loadingReadySingleGraphAndControllerStartupSurviveRepeatedInitialization() = runTest {
        val session = Session(); var factories = 0
        val host = host(session) { factories++; session }
        assertEquals(AppHostState.Loading, host.state.value)
        repeat(5) { host.start(); host.retry() }
        runCurrent()
        val ready = host.state.value as AppHostState.Ready
        assertSame(session.f.navigator, ready.features.folders)
        assertSame(session.f.controller, ready.features.library)
        assertSame(session.h.controller, ready.features.settings)
        assertEquals(1, session.loads); assertEquals(1, factories)
        assertEquals(1, session.f.restores); assertEquals(1, session.h.collections)
        repeat(5) { host.start(); host.retry() }; runCurrent()
        assertSame(ready, host.state.value)
        assertEquals(1, session.loads)
        assertNull(host.dispose().await())
        assertEquals(listOf("load","stop","close"), session.events)
    }

    @Test fun operationalStartupFailureClosesPartialAttemptAndExplicitRetryUsesFreshResources() = runTest {
        val first = Session().also { it.loadFailure = IOException("storage") }
        val next = Session(); var count = 0
        val errors = mutableListOf<Throwable>()
        val host = host(first, errors) { if (count++ == 0) first else next }
        runCurrent()
        val failed = host.state.value as AppHostState.Failed
        assertSame(first.loadFailure, failed.cause); assertTrue(failed.retryable)
        assertEquals(1, first.closes); assertEquals(0, first.h.collectors)
        host.start(); runCurrent(); assertEquals(1, count) // Never implicitly retry.
        host.retry(); host.retry(); runCurrent()
        assertTrue(host.state.value is AppHostState.Ready)
        assertEquals(2, count); assertEquals(1, next.loads)
        assertEquals(listOf(first.loadFailure), errors)
        host.dispose().await()
    }

    @Test fun programmingFailureRemainsOriginalAndCannotBeRetriedAsStorageFailure() = runTest {
        val session = Session().also { it.loadFailure = IllegalStateException("defect") }
        val errors = mutableListOf<Throwable>(); val host = host(session, errors)
        runCurrent()
        val failure = host.state.value as AppHostState.Failed
        assertSame(session.loadFailure, failure.cause); assertFalse(failure.retryable)
        host.retry(); runCurrent(); assertEquals(1, session.loads)
        assertSame(session.loadFailure, errors.single())
        host.dispose().await()
    }

    @Test fun providerConstructionFailureDoesNotLeaveAnAttemptOrAllowParallelBoots() = runTest {
        val failure = IOException("cannot construct owner"); var calls = 0
        val session = Session(); val host = host(session) { calls++; throw failure }
        runCurrent()
        assertSame(failure, (host.state.value as AppHostState.Failed).cause)
        assertEquals(0, session.loads)
        host.retry(); host.retry(); runCurrent(); assertEquals(2, calls)
        host.dispose().await()
    }

    @Test fun disposalBeforeStartupRejectsAllCommandsAndDoesNotAllocateResources() = runTest {
        val session = Session(); val host = host(session)
        val completion = host.dispose()
        assertEquals(AppHostState.Closing, host.state.value)
        host.start(); host.retry(); host.showSettings(0); host.back(0)
        host.library(LibraryAction.Refresh, 0)
        host.settings(SettingsAction.SelectTheme(ThemeSelection.DARK), 0)
        assertNull(completion.await())
        assertEquals(0, session.loads); assertEquals(0, session.closes)
        assertEquals(AppDestination.LIBRARY, host.navigation.value.destination)
        assertEquals(AppHostState.Closed(null), host.state.value)
        assertSame(completion, host.dispose())
    }

    @Test fun settingsNavigationRetainsLibraryAndRejectsStaleFeatureEvents() = runTest {
        val session = Session(); val host = host(session)
        // Not-ready navigation cannot escape startup.
        host.showSettings(0); host.back(0); runCurrent()
        host.showSettings(0); host.showSettings(0)
        assertEquals(AppDestination.SETTINGS, host.navigation.value.destination)
        host.library(LibraryAction.Refresh, 0) // Stale source route.
        host.settings(SettingsAction.SelectTheme(ThemeSelection.DARK), 0)
        host.settings(SettingsAction.SelectTheme(ThemeSelection.DARK), 1)
        runCurrent(); assertEquals(1, session.h.calls.size)
        host.back(0); assertEquals(AppDestination.SETTINGS, host.navigation.value.destination)
        host.back(1); host.back(1)
        assertEquals(AppDestination.LIBRARY, host.navigation.value.destination)
        host.settings(SettingsAction.SelectFit(com.abdev.partituraspdf.core.preferences.PageFit.WIDTH), 2)
        host.library(LibraryAction.Refresh, 1) // Old revision even though destination matches.
        host.library(LibraryAction.Refresh, 2); runCurrent()
        assertEquals(1, session.h.calls.size)
        assertEquals(1, session.h.collections); assertEquals(1, session.loads)
        host.dispose().await()
    }

    @Test fun terminalDisposalStopsDependentsBeforeCloseAndReportsFailure() = runTest {
        val session = Session(); val errors = mutableListOf<Throwable>(); val host = host(session, errors)
        runCurrent()
        session.duringClose = { assertEquals(0, session.h.collectors); assertEquals(0, session.h.activeWrites) }
        val failure = IOException("close failed"); session.closeFailure = failure
        val completion = host.dispose()
        host.showSettings(0); host.library(LibraryAction.Refresh, 0); host.retry(); host.start()
        assertSame(failure, completion.await())
        assertSame(failure, (host.state.value as AppHostState.Closed).failure)
        assertSame(failure, errors.single())
        assertSame(completion, host.dispose(failure))
        assertEquals(1, session.closes)
    }

    @Test fun stopAndCloseFailuresRetainPrimaryAndSuppressSecondary() = runTest {
        val primary = IOException("stop"); val secondary = IllegalStateException("close")
        val session = Session().also { it.stopFailure = primary; it.closeFailure = secondary }
        val host = host(session); runCurrent()
        assertSame(primary, host.dispose().await())
        assertArrayEquals(arrayOf(secondary), primary.suppressed)
        assertEquals(1, session.closes)
    }

    @Test fun originalStartupFailureSurvivesCleanupFailureAndDisablesUnsafeRetry() = runTest {
        val primary = IOException("load"); val secondary = IOException("cleanup")
        val session = Session().also { it.loadFailure = primary; it.closeFailure = secondary }
        val host = host(session); runCurrent()
        val failed = host.state.value as AppHostState.Failed
        assertSame(primary, failed.cause); assertFalse(failed.retryable)
        assertArrayEquals(arrayOf(secondary), primary.suppressed)
        host.retry(); runCurrent(); assertEquals(1, session.loads)
        host.dispose().await()
    }

    @Test fun identicalFailureIsNotSelfSuppressed() = runTest {
        val failure = IOException("same")
        val session = Session().also { it.loadFailure = failure; it.closeFailure = failure }
        val host = host(session); runCurrent()
        assertSame(failure, (host.state.value as AppHostState.Failed).cause)
        assertTrue(failure.suppressed.isEmpty())
        assertFalse((host.state.value as AppHostState.Failed).retryable)
        host.dispose().await()
    }

    @Test fun cancellationDuringBlockingStartupWaitsForPartialResourcesBeforeClosing() = runTest {
        val entered = CompletableDeferred<Unit>(); val release = CountDownLatch(1)
        val session = Session().also { it.duringLoad = { entered.complete(Unit); release.await() } }
        val host = host(session, io = Dispatchers.IO)
        runCurrent(); entered.await()
        val completion = host.dispose(); runCurrent()
        assertFalse(completion.isCompleted)
        assertEquals(0, session.closes)
        release.countDown()
        assertNull(completion.await())
        assertEquals(listOf("load","stop","close"), session.events)
        assertEquals(AppHostState.Closed(null), host.state.value)
    }

    @Test fun clearingViewModelStoreDoesNotCancelAsyncCloseAndNeverClosesTwice() = runTest {
        val session = Session(); val host = host(session); runCurrent()
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val automatic = host.viewModelScope.launch { awaitCancellation() }
            runCurrent()
            val store = ViewModelStore(); store.put("host", host)
            store.clear()
            assertTrue(automatic.isCancelled)
            assertEquals(AppHostState.Closing, host.state.value)
            assertNull(host.dispose().await())
            assertEquals(1, session.stops); assertEquals(1, session.closes)
            assertEquals(0, session.h.collectors)
        } finally { Dispatchers.resetMain() }
    }

    @Test fun dependentWorkMustFinishBeforeGraphCloseEvenWhenItIgnoresCancellation() = runTest {
        val gate = CompletableDeferred<Unit>(); var finished = false
        val session = Session()
        session.duringLoad = {
            session.f.list = { withContext(NonCancellable) { gate.await() }; finished = true
                Outcome.Success(com.abdev.partituraspdf.core.document.DocumentListing(emptyList(), true)) }
            session.f.restore = { session.f.navigation = com.abdev.partituraspdf.library.trail(com.abdev.partituraspdf.library.root)
                Outcome.Success(com.abdev.partituraspdf.library.root) }
        }
        val host = host(session); runCurrent()
        session.duringClose = { assertTrue(finished) }
        val completion = host.dispose(); runCurrent()
        assertFalse(completion.isCompleted); assertEquals(0, session.closes)
        gate.complete(Unit)
        assertNull(completion.await()); assertEquals(1, session.closes)
    }

    @Test fun terminalCauseSurvivesStopAndCloseErrors() = runTest {
        val primary = IllegalStateException("owned worker defect")
        val stop = IOException("stop"); val close = IOException("close")
        val session = Session().also { it.stopFailure = stop; it.closeFailure = close }
        val host = host(session); runCurrent()
        assertSame(primary, host.dispose(primary).await())
        assertArrayEquals(arrayOf(stop,close), primary.suppressed)
    }
    @Test fun spontaneousStartupCancellationIsVisibleAndCanBeExplicitlyRetried() = runTest {
        val cancellation = CancellationException("startup canceled")
        val first = Session().also { it.loadFailure = cancellation }
        val next = Session(); var attempts = 0
        val errors = mutableListOf<Throwable>()
        val host = host(first, errors) { if (attempts++ == 0) first else next }
        runCurrent()
        val failed = host.state.value as AppHostState.Failed
        assertSame(cancellation, failed.cause); assertTrue(failed.retryable)
        assertTrue(errors.isEmpty()); assertEquals(1, first.closes)
        host.retry(); runCurrent()
        assertTrue(host.state.value is AppHostState.Ready)
        host.dispose().await()
    }

    @Test fun cancellationWithCleanupFailureRetainsBothAndCannotRetry() = runTest {
        val cancellation = CancellationException("canceled")
        val cleanup = IOException("close")
        val session = Session().also { it.loadFailure = cancellation; it.closeFailure = cleanup }
        val host = host(session); runCurrent()
        val failure = host.state.value as AppHostState.Failed
        assertSame(cancellation, failure.cause); assertFalse(failure.retryable)
        assertArrayEquals(arrayOf(cleanup), cancellation.suppressed)
        host.dispose().await()
    }

    @Test fun dependentProgrammingFailureInitiatesObservableTerminalCleanup() = runTest {
        val failure = IllegalStateException("observer defect")
        val session = Session().also { it.duringLoad = { it.f.restore = { throw failure } } }
        val errors = mutableListOf<Throwable>(); val host = host(session, errors)
        runCurrent()
        assertSame(failure, host.dispose().await())
        assertSame(failure, (host.state.value as AppHostState.Closed).failure)
        assertEquals(1, session.closes)
        assertTrue(errors.contains(failure))
    }

    @Test fun dependentFailureArrivingDuringShutdownIsNotLost() = runTest {
        val gate = CompletableDeferred<Unit>()
        val failure = IllegalStateException("late dependent defect")
        val session = Session().also { candidate -> candidate.duringLoad = {
            candidate.f.list = {
                val original = withContext(NonCancellable) { gate.await(); failure }
                throw original
            }
            candidate.f.restore = { candidate.f.navigation = com.abdev.partituraspdf.library.trail(com.abdev.partituraspdf.library.root)
                Outcome.Success(com.abdev.partituraspdf.library.root) }
        } }
        val errors = mutableListOf<Throwable>(); val host = host(session, errors); runCurrent()
        val done = host.dispose(); runCurrent()
        assertFalse(done.isCompleted)
        gate.complete(Unit)
        assertSame(failure, done.await())
        assertSame(failure, (host.state.value as AppHostState.Closed).failure)
        assertTrue(errors.contains(failure)); assertEquals(1, session.closes)
    }

}
