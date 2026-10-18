package com.abdev.partituraspdf.library

import com.abdev.partituraspdf.core.document.*
import com.abdev.partituraspdf.core.foundation.LibraryEvent
import com.abdev.partituraspdf.core.result.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryConcurrencyTest {
    @Test fun noncooperativeOldRefreshCannotOverwriteNewRows() = runTest {
        LibraryFixture(this).use { f ->
            val release = CompletableDeferred<Unit>(); var attempt = 0
            f.list = {
                val first = ++attempt == 1
                if (first) withContext(NonCancellable) { release.await() }
                Outcome.Success(DocumentListing(listOf(entry(id = if (first) "old" else "new")), !first))
            }
            f.controller.start(); runCurrent(); assertTrue(f.state.content is LibraryContent.Loading)
            f.controller.dispatch(LibraryAction.Refresh); runCurrent()
            assertEquals("new", f.ready.rows.single().entry.storage.value); assertTrue(f.ready.complete)
            release.complete(Unit); runCurrent()
            assertEquals("new", f.ready.rows.single().entry.storage.value); assertTrue(f.ready.complete)
        }
    }
    @Test fun selectionDuringLoadAndLateOldRootResultCannotReplaceAcceptedRoot() = runTest {
        LibraryFixture(this).use { f ->
            val release = CompletableDeferred<Unit>()
            f.list = { folder ->
                if (folder == root) withContext(NonCancellable) { release.await() }
                Outcome.Success(DocumentListing(listOf(entry(folder = folder)), true))
            }
            f.controller.start(); runCurrent()
            f.controller.dispatch(LibraryAction.ChooseRoot); runCurrent()
            val other = FolderRef("test", "other"); f.navigation = trail(other, version = 8)
            f.controller.onRootSelectionFinished(Outcome.Success(other)); runCurrent()
            assertEquals(other, f.available.snapshot.root)
            release.complete(Unit); runCurrent()
            assertEquals(other, f.available.snapshot.root); assertEquals(other, f.ready.rows.single().entry.folder)
        }
    }
    @Test fun closeCancelsOwnedWorkRejectsLateCallbacksAndKeepsParentAlive() = runTest {
        LibraryFixture(this).use { f ->
            val release = CompletableDeferred<Unit>()
            f.list = { withContext(NonCancellable) { release.await() }; Outcome.Success(DocumentListing(emptyList(), true)) }
            f.controller.dispatch(LibraryAction.Refresh) // start is explicit.
            f.controller.onRootSelectionFinished(Outcome.Success(root))
            f.controller.start(); runCurrent(); val before = f.state
            f.controller.close(); f.controller.close(); f.controller.start()
            f.controller.dispatch(LibraryAction.Refresh); f.controller.onRootSelectionFinished(Outcome.Success(root))
            release.complete(Unit); runCurrent()
            assertSame(before, f.state); assertTrue(currentCoroutineContext().isActive)
        }
        LibraryFixture(this).use { f -> f.controller.close(); f.controller.start(); runCurrent(); assertEquals(0, f.restores) }
    }
    @Test fun rapidEnterIsSerializedAndRepeatedTapUsesStaleRowNotAnotherMove() = runTest {
        LibraryFixture(this).use { f ->
            f.controller.start(); runCurrent(); val key = f.ready.rows.single().key
            val release = CompletableDeferred<Unit>(); val entered = CompletableDeferred<Unit>()
            f.event = { command ->
                assertTrue(command is LibraryEvent.Enter); entered.complete(Unit); release.await()
                f.navigation = trail(root, FolderRef("test", "child"), version = 2); Outcome.Success(Unit)
            }
            f.controller.dispatch(LibraryAction.Enter(key)); runCurrent(); entered.await()
            assertEquals(LibraryOperation.Pending, f.state.navigation)
            f.controller.dispatch(LibraryAction.Enter(key)); f.controller.dispatch(LibraryAction.Back(1))
            release.complete(Unit); runCurrent()
            assertEquals(1, f.events.size); assertEquals(2, f.available.snapshot.breadcrumbs.size)
            assertEquals(AppError.Conflict.STALE_SESSION, (f.state.navigation as LibraryOperation.Failed).failure.error)
        }
    }
    @Test fun externalNavigationChangeWhileLoadingIsReconciledNotOverwritten() = runTest {
        LibraryFixture(this).use { f ->
            val release = CompletableDeferred<Unit>(); var first = true
            f.list = { folder -> if (first) { first = false; release.await() }; Outcome.Success(DocumentListing(listOf(entry(folder = folder)), true)) }
            f.controller.start(); runCurrent()
            val other = FolderRef("test", "changed"); f.navigation = trail(other, version = 20)
            release.complete(Unit); runCurrent()
            assertEquals(other, f.available.snapshot.root); assertEquals(other, f.ready.rows.single().entry.folder)
            assertEquals(AppError.Conflict.STALE_SESSION, (f.state.navigation as LibraryOperation.Failed).failure.error)
        }
    }
    @Test fun externallyLostNavigationCannotKeepOldRowsOrSendOldEnter() = runTest {
        LibraryFixture(this).use { f ->
            f.controller.start(); runCurrent(); f.navigation = null; f.enter(); runCurrent()
            assertEquals(LibraryLocation.NoRoot, f.state.location); assertEquals(LibraryContent.NotLoaded, f.state.content); assertTrue(f.events.isEmpty())
        }
    }
    @Test fun retainedNavigationVersionChangeIsCheckedBeforeHostCommand() = runTest {
        LibraryFixture(this).use { f ->
            f.controller.start(); runCurrent(); val oldKey = f.ready.rows.single().key
            f.navigation = trail(root, version = 2)
            f.controller.dispatch(LibraryAction.Enter(oldKey)); runCurrent()
            assertTrue(f.events.isEmpty()); assertEquals(2, f.available.snapshot.version)
            assertEquals(AppError.Conflict.STALE_SESSION, (f.state.navigation as LibraryOperation.Failed).failure.error)
        }
    }
    @Test fun cancellationIsNotReportedAsProviderFailure() = runTest {
        LibraryFixture(this).use { f ->
            var canceled = false
            f.list = { try { awaitCancellation() } finally { canceled = true } }
            f.controller.start(); runCurrent(); f.controller.dispatch(LibraryAction.Refresh); runCurrent()
            assertTrue(canceled); assertTrue(f.state.content is LibraryContent.Loading)
            assertEquals(LibraryOperation.Idle, f.state.navigation)
        }
    }
    @Test fun parentCancellationStopsWorkWithoutInventingFailure() = runTest {
        val parent = SupervisorJob()
        LibraryFixture(CoroutineScope(coroutineContext + parent)).use { f ->
            f.controller.start(); runCurrent(); val before = f.state
            parent.cancel(); f.controller.dispatch(LibraryAction.Refresh); runCurrent()
            assertSame(before, f.state)
        }
    }
    @Test fun cancellationDuringPickerOrNavigationCannotPublishAfterClose() = runTest {
        for (picker in listOf(false, true)) LibraryFixture(this).use { f ->
            f.controller.start(); runCurrent()
            val release = CompletableDeferred<Unit>()
            f.event = { withContext(NonCancellable) { release.await() }; Outcome.Success(Unit) }
            if (picker) f.controller.dispatch(LibraryAction.ChooseRoot) else f.enter()
            runCurrent(); f.controller.close(); val before = f.state
            release.complete(Unit); runCurrent(); assertSame(before, f.state)
        }
    }
    @Test fun programmingDefectsReachOwnerHandlerAndStopControllerWork() = runTest {
        val parent = SupervisorJob()
        val failures = mutableListOf<Throwable>()
        val handler = CoroutineExceptionHandler { _, failure -> failures += failure }
        LibraryFixture(CoroutineScope(coroutineContext + parent + handler)).use { f ->
            val defect = IllegalStateException("broken catalog invariant")
            f.list = { throw defect }
            f.controller.start(); runCurrent()
            assertEquals(listOf(defect), failures)
            assertTrue(f.state.content is LibraryContent.Loading) // Never a fabricated operational failure.
            val calls = f.calls.size; f.controller.dispatch(LibraryAction.Refresh); runCurrent()
            assertEquals(calls, f.calls.size); assertTrue(parent.isActive)
        }
        parent.cancel()
    }
    @Test fun synchronousHostCompletionIsQueuedUntilLaunchAcknowledgement() = runTest {
        LibraryFixture(this).use { f ->
            f.controller.start(); runCurrent()
            f.event = {
                val other = FolderRef("test", "accepted")
                f.navigation = trail(other, version = 2)
                f.controller.onRootSelectionFinished(Outcome.Success(other))
                Outcome.Success(Unit)
            }
            f.controller.dispatch(LibraryAction.ChooseRoot); runCurrent()
            assertEquals(FolderRef("test", "accepted"), f.available.snapshot.root)
            assertEquals(LibraryOperation.Idle, f.state.rootSelection)
        }
    }
}
