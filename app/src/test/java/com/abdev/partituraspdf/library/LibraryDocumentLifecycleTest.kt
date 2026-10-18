package com.abdev.partituraspdf.library

import com.abdev.partituraspdf.core.document.*
import com.abdev.partituraspdf.core.foundation.*
import com.abdev.partituraspdf.core.result.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryDocumentLifecycleTest {
    @Test fun registrationCanFinishAfterFolderNavigationWithoutChangingNewRows() = runTest {
        LibraryFixture(this).use { f ->
            f.list = { folder -> Outcome.Success(DocumentListing(if (folder == root) listOf(entry(), pdfEntry) else emptyList(), true)) }
            f.controller.start(); runCurrent(); val release = CompletableDeferred<Unit>()
            f.documents.register = { withContext(NonCancellable) { release.await() }; Outcome.Success(f.documents.confirm(it)) }
            f.controller.dispatch(LibraryAction.Register(f.pdf.key)); runCurrent()
            f.controller.dispatch(LibraryAction.Enter(f.ready.rows.first().key)); runCurrent()
            val before = f.state; release.complete(Unit); runCurrent()
            assertSame(before, f.state); assertTrue(f.ready.rows.isEmpty()); assertEquals(2, f.available.snapshot.breadcrumbs.size)
        }
    }
    @Test fun closeDuringRegistrationOrFavoriteCannotPublishLateSuccessOrLeakCollectors() = runTest {
        for (favorite in listOf(false, true)) {
            val f = LibraryFixture(this)
            try {
                f.pdfFolder(); val record = if (favorite) f.documents.confirm(pdfEntry.storage) else null
                val release = CompletableDeferred<Unit>()
                f.documents.register = { withContext(NonCancellable) { release.await() }; Outcome.Success(f.documents.confirm(it)) }
                f.documents.write = { _, _ -> withContext(NonCancellable) { release.await() }; Outcome.Success(Unit) }
                f.controller.start(); runCurrent()
                f.controller.dispatch(if (record == null) LibraryAction.Register(f.pdf.key) else LibraryAction.SetFavorite(f.pdf.key, true)); runCurrent()
                f.close(); val before = f.state; release.complete(Unit); runCurrent()
                assertSame(before, f.state); assertEquals(0, f.documents.activeCollectors); assertTrue(currentCoroutineContext().isActive)
            } finally { f.close() }
        }
    }
    @Test fun externalNavigationDuringLookupCannotPublishOldAssociation() = runTest {
        LibraryFixture(this).use { f ->
            f.pdfFolder(); val release = CompletableDeferred<Unit>(); var first = true
            f.documents.find = {
                if (first) { first = false; release.await() }
                Outcome.Failure(AppError.Access.NOT_REGISTERED)
            }
            f.controller.start(); runCurrent()
            val other = FolderRef("test", "externally-selected"); f.navigation = trail(other, version = 9)
            release.complete(Unit); runCurrent()
            assertEquals(other, f.available.snapshot.root); assertEquals(other, f.pdf.entry.folder)
            assertEquals(LibraryAssociation.Unregistered, f.pdf.association)
            assertEquals(AppError.Conflict.STALE_SESSION, (f.state.navigation as LibraryOperation.Failed).failure.error)
        }
    }
    @Test fun externallyChangedNavigatorRejectsCurrentLookingRowBeforeOpening() = runTest {
        LibraryFixture(this).use { f ->
            f.pdfFolder(); f.controller.start(); runCurrent()
            f.navigation = trail(root, version = 7)
            f.controller.dispatch(LibraryAction.Open(f.pdf.key)); runCurrent()
            assertEquals(AppError.Conflict.STALE_SESSION, f.state.documentError); assertTrue(f.events.isEmpty())
        }
    }
    @Test fun onlyOneHostOpenIsPendingAcrossDifferentRowsAndRootChangeInvalidatesIt() = runTest {
        LibraryFixture(this).use { f ->
            val second = pdfEntry.copy(storage = StorageDocumentRef("test", "second"))
            f.list = { folder -> Outcome.Success(DocumentListing(listOf(pdfEntry.copy(folder = folder), second.copy(folder = folder)), true)) }
            f.controller.start(); runCurrent()
            val rows = f.ready.rows.filterIsInstance<LibraryRow.Pdf>(); val release = CompletableDeferred<Unit>()
            f.event = { if (it is LibraryEvent.Open) withContext(NonCancellable) { release.await() }; Outcome.Success(Unit) }
            rows.forEach { f.controller.dispatch(LibraryAction.Open(it.key)) }; runCurrent(); assertEquals(1, f.events.size)
            f.controller.dispatch(LibraryAction.ChooseRoot); runCurrent()
            val other = FolderRef("test", "other"); f.navigation = trail(other, version = 3)
            f.controller.onRootSelectionFinished(Outcome.Success(other)); runCurrent()
            release.complete(Unit); runCurrent()
            assertTrue(f.ready.rows.filterIsInstance<LibraryRow.Pdf>().all { it.opening == LibraryOpening.Idle })
            assertEquals(other, f.available.snapshot.root)
        }
    }
    @Test fun canceledFavoriteWithFailedReadExposesUncertaintyNotGuessedFavorite() = runTest {
        LibraryFixture(this).use { f ->
            f.pdfFolder(); f.documents.confirm(pdfEntry.storage); f.controller.start(); runCurrent()
            f.documents.write = { _, _ ->
                f.documents.observeFlow = { flowOf(Observation.Loading, Observation.Failed(AppError.Access.PRIVATE_STORAGE_UNAVAILABLE)) }
                throw CancellationException("uncertain commit")
            }
            f.controller.dispatch(LibraryAction.SetFavorite(f.pdf.key, true)); runCurrent()
            assertEquals(LibraryFavorite.Failed(true, AppError.Access.PRIVATE_STORAGE_UNAVAILABLE), f.registered.favorite)
            assertTrue(f.registered.metadata is Observation.Failed); assertFalse(f.pdf.canFavorite)
        }
    }
    @Test fun errorsFromLookupObserverAndWritesPropagateAsDefectsAndStopOwnedJobs() = runTest {
        for (operation in 0..3) {
            val parent = SupervisorJob(); val errors = mutableListOf<Throwable>()
            val f = LibraryFixture(CoroutineScope(coroutineContext + parent + CoroutineExceptionHandler { _, error -> errors += error }))
            try {
                f.pdfFolder(); val defect = IllegalStateException("defective contract implementation")
                when (operation) {
                    0 -> f.documents.find = { throw defect }
                    1 -> { f.documents.confirm(pdfEntry.storage); f.documents.observeFlow = { flow { throw defect } } }
                    2 -> f.documents.register = { throw defect }
                    3 -> { f.documents.confirm(pdfEntry.storage); f.documents.write = { _, _ -> throw defect } }
                }
                f.controller.start(); runCurrent()
                if (operation == 2) f.controller.dispatch(LibraryAction.Register(f.pdf.key))
                if (operation == 3) f.controller.dispatch(LibraryAction.SetFavorite(f.pdf.key, true))
                runCurrent()
                assertEquals(listOf(defect), errors); assertTrue(parent.isActive)
                val calls = f.documents.lookups.size; f.controller.dispatch(LibraryAction.Refresh); runCurrent()
                assertEquals(calls, f.documents.lookups.size); assertEquals(0, f.documents.activeCollectors)
            } finally { f.close(); parent.cancel() }
        }
    }
    @Test fun lookupFailureDoesNotEnableWrongRetryOrFavoriteAndNoRootRejectsRowCommands() = runTest {
        LibraryFixture(this).use { f ->
            f.pdfFolder(); f.documents.find = { Outcome.Failure(AppError.Access.PRIVATE_STORAGE_UNAVAILABLE) }
            f.controller.start(); runCurrent()
            f.controller.dispatch(LibraryAction.RetryRegistration(f.pdf.key)); f.controller.dispatch(LibraryAction.SetFavorite(f.pdf.key, true)); runCurrent()
            assertTrue(f.documents.registrations.isEmpty()); assertTrue(f.documents.writes.isEmpty())
        }
        LibraryFixture(this).use { f ->
            f.restore = { Outcome.Success(null) }; f.controller.start(); runCurrent()
            f.controller.dispatch(LibraryAction.Open(LibraryRowKey(pdfEntry.storage, 0, 1))); runCurrent()
            assertEquals(AppError.Conflict.STALE_SESSION, f.state.documentError); assertTrue(f.events.isEmpty())
        }
    }
    @Test fun cooperativeRegistrationAndFavoriteAreCanceledOnCloseWithoutReconciliationJobs() = runTest {
        for (favorite in listOf(false, true)) {
            val f = LibraryFixture(this); var canceled = false
            try {
                f.pdfFolder(); if (favorite) f.documents.confirm(pdfEntry.storage)
                f.documents.register = { try { awaitCancellation() } finally { canceled = true } }
                f.documents.write = { _, _ -> try { awaitCancellation() } finally { canceled = true } }
                f.controller.start(); runCurrent()
                f.controller.dispatch(if (favorite) LibraryAction.SetFavorite(f.pdf.key, true) else LibraryAction.Register(f.pdf.key)); runCurrent()
                val lookups = f.documents.lookups.size; f.close(); val before = f.state; runCurrent()
                assertTrue(canceled); assertSame(before, f.state)
                assertEquals(lookups, f.documents.lookups.size); assertEquals(0, f.documents.activeCollectors)
            } finally { f.close() }
        }
    }
}
