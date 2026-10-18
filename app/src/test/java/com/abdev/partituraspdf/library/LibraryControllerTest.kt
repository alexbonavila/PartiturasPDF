package com.abdev.partituraspdf.library

import com.abdev.partituraspdf.core.document.*
import com.abdev.partituraspdf.core.foundation.LibraryEvent
import com.abdev.partituraspdf.core.result.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryControllerTest {
    @Test fun noRootDoesNotListAndSelectionLaunchIsNotAcceptance() = runTest {
        LibraryFixture(this).use { f ->
            f.restore = { Outcome.Success(null) }
            f.controller.start(); f.controller.start(); runCurrent()
            assertEquals(LibraryLocation.NoRoot, f.state.location); assertTrue(f.calls.isEmpty()); assertEquals(1, f.restores)
            f.controller.dispatch(LibraryAction.ChooseRoot); runCurrent()
            assertEquals(LibraryOperation.Pending, f.state.rootSelection)
            assertEquals(listOf(LibraryEvent.ChooseRoot), f.events); assertTrue(f.calls.isEmpty())
            for (action in listOf(LibraryAction.ChooseRoot, LibraryAction.Refresh, LibraryAction.Retry, LibraryAction.Back(1))) f.controller.dispatch(action)
            runCurrent(); assertEquals(1, f.events.size)
            f.controller.onRootSelectionFinished(Outcome.Success(null)); runCurrent()
            assertEquals(LibraryOperation.Canceled, f.state.rootSelection); assertEquals(LibraryLocation.NoRoot, f.state.location)
            f.controller.onRootSelectionFinished(Outcome.Success(root)); runCurrent(); assertTrue(f.calls.isEmpty())
        }
    }
    @Test fun retainedGrandchildAndRefreshNeverRestoreOrResetBreadcrumbs() = runTest {
        val retained = trail(root, FolderRef("test", "child"), FolderRef("test", "grand"), version = 7)
        LibraryFixture(this, retained).use { f ->
            f.controller.start(); runCurrent()
            assertSame(retained, f.available.snapshot); assertTrue(f.available.canGoBack); assertEquals(0, f.restores)
            f.controller.dispatch(LibraryAction.Refresh); runCurrent()
            assertSame(retained, f.available.snapshot); assertEquals(listOf(retained.current, retained.current), f.calls); assertEquals(0, f.restores)
        }
    }
    @Test fun restoredRootLoadsOnceAndIdleRetryDoesNothing() = runTest {
        LibraryFixture(this).use { f ->
            f.controller.start(); runCurrent(); f.controller.start(); f.controller.dispatch(LibraryAction.Retry); runCurrent()
            assertEquals(1, f.restores); assertEquals(listOf(root), f.calls); assertFalse(f.available.canGoBack)
            assertEquals(LibraryOperation.Idle, f.state.rootSelection)
        }
    }
    @Test fun restoreFailuresKeepExactErrorsAndRetryRecoversWithoutDeletion() = runTest {
        for (error in errors) LibraryFixture(this).use { f ->
            f.restore = { Outcome.Failure(error) }
            f.controller.start(); runCurrent()
            val failed = f.state.location as LibraryLocation.Failed
            assertEquals(error, failed.failure.error); assertEquals(LibraryFailureOperation.RESTORE, failed.failure.operation); assertTrue(f.calls.isEmpty())
            f.restore = { f.navigation = trail(root); Outcome.Success(root) }
            f.controller.dispatch(LibraryAction.Retry); runCurrent(); assertEquals(root, f.available.snapshot.root); assertEquals(2, f.restores)
        }
    }
    @Test fun inconsistentRestoreCannotInventNavigation() = runTest {
        for (mode in 0..2) LibraryFixture(this).use { f ->
            f.restore = {
                if (mode != 0) f.navigation = trail(root)
                Outcome.Success(if (mode == 1) null else FolderRef("test", "other"))
            }
            f.controller.start(); runCurrent()
            assertEquals(AppError.Conflict.STALE_SESSION, (f.state.location as LibraryLocation.Failed).failure.error)
            assertTrue(f.calls.isEmpty())
        }
    }
    @Test fun acceptedRootMustAlreadyMatchSharedNavigator() = runTest {
        LibraryFixture(this).use { f ->
            f.controller.start(); runCurrent(); f.controller.dispatch(LibraryAction.ChooseRoot); runCurrent()
            val replacement = FolderRef("test", "replacement")
            f.navigation = trail(replacement, version = 10)
            f.controller.onRootSelectionFinished(Outcome.Success(replacement)); runCurrent()
            assertSame(f.navigation, f.available.snapshot); assertEquals(replacement, f.calls.last()); assertEquals(1, f.restores)
            assertEquals(LibraryOperation.Idle, f.state.rootSelection)
            f.controller.onRootSelectionFinished(Outcome.Failure(AppError.Access.PERMISSION_DENIED)); runCurrent()
            assertEquals(LibraryOperation.Idle, f.state.rootSelection)
        }
    }
    @Test fun mismatchedOrAbsentAcceptedNavigationRemainsSelectionFailure() = runTest {
        for (absent in listOf(false, true)) LibraryFixture(this).use { f ->
            f.controller.start(); runCurrent(); val count = f.calls.size
            f.controller.dispatch(LibraryAction.ChooseRoot); runCurrent()
            if (absent) f.navigation = null
            f.controller.onRootSelectionFinished(Outcome.Success(FolderRef("test", "other"))); runCurrent()
            assertEquals(AppError.Conflict.STALE_SESSION, (f.state.rootSelection as LibraryOperation.Failed).failure.error)
            assertEquals(count, f.calls.size)
            if (absent) assertEquals(LibraryLocation.NoRoot, f.state.location) else assertSame(f.navigation, f.available.snapshot)
        }
    }
    @Test fun launchAndCompletionErrorsAreNotPickerCancellationAndCanBeRetried() = runTest {
        for (launch in listOf(false, true)) for (error in errors) LibraryFixture(this).use { f ->
            f.controller.start(); runCurrent()
            if (launch) f.event = { Outcome.Failure(error) }
            f.controller.dispatch(LibraryAction.ChooseRoot); runCurrent()
            if (!launch) f.controller.onRootSelectionFinished(Outcome.Failure(error))
            runCurrent(); assertEquals(error, (f.state.rootSelection as LibraryOperation.Failed).failure.error)
            f.event = { Outcome.Success(Unit) }; f.controller.dispatch(LibraryAction.Retry); runCurrent()
            assertEquals(LibraryOperation.Pending, f.state.rootSelection)
            f.controller.onRootSelectionFinished(Outcome.Success(null)); runCurrent()
            assertEquals(LibraryOperation.Canceled, f.state.rootSelection); assertEquals(root, f.available.snapshot.root)
        }
    }
    @Test fun completeAndIncompleteEmptyAndNonemptyListingsRemainDistinct() = runTest {
        for (complete in listOf(false, true)) for (empty in listOf(false, true)) LibraryFixture(this).use { f ->
            f.list = { Outcome.Success(DocumentListing(if (empty) emptyList() else listOf(entry(mime = "APPLICATION/PDF", name = null)), complete)) }
            f.controller.start(); runCurrent()
            assertEquals(complete, f.ready.complete); assertEquals(if (empty) 0 else 1, f.ready.rows.size)
            if (!empty) { assertTrue(f.ready.rows.single() is LibraryRow.Pdf); assertNull(f.ready.rows.single().entry.info.name); assertNull(f.ready.rows.single().entry.info.byteSize) }
        }
    }
    @Test fun listingFailureHasNarrowRetryAndKeepsRoot() = runTest {
        for (error in errors) LibraryFixture(this).use { f ->
            f.list = { Outcome.Failure(error) }; f.controller.start(); runCurrent()
            assertEquals(error, (f.state.content as LibraryContent.Failed).failure.error)
            assertEquals(LibraryFailureOperation.LISTING, (f.state.content as LibraryContent.Failed).failure.operation)
            f.list = { Outcome.Success(DocumentListing(emptyList(), true)) }
            f.controller.dispatch(LibraryAction.Retry); runCurrent()
            assertTrue(f.ready.complete); assertEquals(root, f.available.snapshot.root); assertEquals(1, f.restores)
        }
    }
    @Test fun enterUsesOriginalEntryAndBackUsesHostAndAuthoritativeBreadcrumbs() = runTest {
        LibraryFixture(this).use { f ->
            val original = entry(); f.list = { folder -> Outcome.Success(DocumentListing(if (folder == root) listOf(original) else emptyList(), true)) }
            f.controller.start(); runCurrent(); f.enter(); runCurrent()
            val command = f.events.single() as LibraryEvent.Enter
            assertSame(original, command.entry); assertEquals(1, command.version)
            assertSame(f.navigation, f.available.snapshot); assertEquals(2, f.available.snapshot.breadcrumbs.size)
            f.controller.dispatch(LibraryAction.Back(2)); runCurrent()
            assertEquals(LibraryEvent.Back(2), f.events.last()); assertEquals(listOf(root), f.available.snapshot.breadcrumbs)
            f.controller.dispatch(LibraryAction.Back(3)); runCurrent()
            assertEquals(listOf(root), f.available.snapshot.breadcrumbs); assertFalse(f.available.canGoBack)
        }
    }
    @Test fun navigationFailureAndStaleActionsRefreshWithoutReplayingMoves() = runTest {
        LibraryFixture(this).use { f ->
            f.controller.start(); runCurrent(); val oldKey = f.ready.rows.single().key
            f.event = { Outcome.Failure(AppError.Access.PERMISSION_REVOKED) }
            f.enter(); runCurrent()
            assertEquals(AppError.Access.PERMISSION_REVOKED, (f.state.navigation as LibraryOperation.Failed).failure.error)
            assertEquals(listOf(root), f.available.snapshot.breadcrumbs)
            f.controller.dispatch(LibraryAction.Retry); runCurrent(); assertEquals(1, f.events.size)
            f.controller.dispatch(LibraryAction.Enter(oldKey)); runCurrent()
            assertEquals(AppError.Conflict.STALE_SESSION, (f.state.navigation as LibraryOperation.Failed).failure.error); assertEquals(1, f.events.size)
            f.controller.dispatch(LibraryAction.Back(999)); runCurrent(); assertEquals(1, f.events.size)
        }
    }
    @Test fun pdfCannotBeEnteredAndBackWithoutRootCannotInventFolder() = runTest {
        LibraryFixture(this).use { f ->
            f.list = { Outcome.Success(DocumentListing(listOf(entry(mime = "application/pdf")), true)) }
            f.controller.start(); runCurrent(); f.enter(); runCurrent()
            assertEquals(AppError.Validation.INVALID_DOCUMENT, (f.state.navigation as LibraryOperation.Failed).failure.error); assertTrue(f.events.isEmpty())
        }
        LibraryFixture(this).use { f ->
            f.restore = { Outcome.Success(null) }; f.controller.start(); runCurrent()
            f.controller.dispatch(LibraryAction.Back(0)); runCurrent()
            assertEquals(LibraryLocation.NoRoot, f.state.location); assertTrue(f.events.isEmpty())
            f.controller.dispatch(LibraryAction.Refresh); runCurrent(); assertEquals(2, f.restores)
        }
    }
    @Test fun hostSuccessWithoutExpectedNavigationTransitionIsNotSuccess() = runTest {
        for (mode in 0..4) LibraryFixture(this).use { f ->
            f.controller.start(); runCurrent()
            f.event = {
                f.navigation = when (mode) {
                    0 -> null
                    1 -> trail(root)
                    2 -> trail(FolderRef("test", "foreign"), FolderRef("test", "child"), version = 2)
                    3 -> trail(root, FolderRef("test", "a"), FolderRef("test", "b"), version = 2)
                    else -> trail(root, root, version = 2)
                }; Outcome.Success(Unit)
            }
            f.enter(); runCurrent()
            assertEquals(AppError.Conflict.STALE_SESSION, (f.state.navigation as LibraryOperation.Failed).failure.error)
        }
    }
    @Test fun invalidProviderMembershipIsAListingFailureNotAnEmptySuccess() = runTest {
        LibraryFixture(this).use { f ->
            f.list = { Outcome.Success(DocumentListing(listOf(entry(folder = FolderRef("test", "wrong"))), true)) }
            f.controller.start(); runCurrent()
            assertEquals(AppError.Validation.INVALID_DOCUMENT, (f.state.content as LibraryContent.Failed).failure.error)
            assertEquals(root, f.available.snapshot.root)
        }
    }
    @Test fun backMustObserveActualParentAndCannotTrustHostAcknowledgementAlone() = runTest {
        LibraryFixture(this, trail(root, FolderRef("test", "child"), version = 4)).use { f ->
            f.controller.start(); runCurrent()
            f.event = { Outcome.Success(Unit) }
            f.controller.dispatch(LibraryAction.Back(4)); runCurrent()
            assertEquals(AppError.Conflict.STALE_SESSION, (f.state.navigation as LibraryOperation.Failed).failure.error)
            assertEquals(2, f.available.snapshot.breadcrumbs.size)
        }
    }
    companion object {
        val errors: List<AppError> = listOf(AppError.Access.PERMISSION_DENIED, AppError.Access.PERMISSION_REVOKED, AppError.Access.PROVIDER_UNAVAILABLE,
            AppError.Access.PROVIDER_IO_FAILURE, AppError.Access.PRIVATE_STORAGE_UNAVAILABLE, AppError.Validation.INVALID_DOCUMENT)
    }
}
