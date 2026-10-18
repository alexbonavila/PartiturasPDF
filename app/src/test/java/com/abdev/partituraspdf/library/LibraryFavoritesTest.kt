package com.abdev.partituraspdf.library

import com.abdev.partituraspdf.core.document.*
import com.abdev.partituraspdf.core.result.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryFavoritesTest {
    @Test fun metadataLoadingReadyAndFailureRemainDistinctWithoutDefaultFavorites() = runTest {
        LibraryFixture(this).use { f ->
            f.pdfFolder(); val record = f.documents.confirm(pdfEntry.storage)
            val values = f.documents.values.getValue(record.document)
            values.value = Observation.Loading
            f.controller.start(); runCurrent()
            assertEquals(Observation.Loading, f.registered.metadata); assertFalse(f.pdf.canFavorite)
            f.controller.dispatch(LibraryAction.SetFavorite(f.pdf.key, true)); runCurrent(); assertTrue(f.documents.writes.isEmpty())
            values.value = Observation.Ready(DocumentMetadata(false)); runCurrent(); assertTrue(f.pdf.canFavorite)
            values.value = Observation.Ready(DocumentMetadata(true)); runCurrent()
            assertTrue((f.registered.metadata as Observation.Ready).value.favorite)
            values.value = Observation.Failed(AppError.Access.NOT_REGISTERED); runCurrent()
            assertEquals(record, f.registered.value); assertEquals(Observation.Failed(AppError.Access.NOT_REGISTERED), f.registered.metadata)
            assertFalse(f.pdf.canFavorite)
            values.value = Observation.Ready(DocumentMetadata(true))
            f.controller.dispatch(LibraryAction.RetryMetadata(f.pdf.key)); f.controller.dispatch(LibraryAction.RetryMetadata(f.pdf.key)); runCurrent()
            assertTrue((f.registered.metadata as Observation.Ready).value.favorite); assertEquals(1, f.documents.activeCollectors)
        }
    }
    @Test fun failedTerminalObservationRequiresExplicitFreshCollection() = runTest {
        LibraryFixture(this).use { f ->
            f.pdfFolder(); f.documents.confirm(pdfEntry.storage)
            f.documents.observeFlow = { flowOf(Observation.Loading, Observation.Failed(AppError.Access.PRIVATE_STORAGE_UNAVAILABLE)) }
            f.controller.start(); runCurrent()
            assertEquals(0, f.documents.activeCollectors); assertTrue(f.registered.metadata is Observation.Failed)
            f.documents.observeFlow = { flowOf(Observation.Loading, Observation.Ready(DocumentMetadata(true))) }
            f.controller.dispatch(LibraryAction.RetryMetadata(f.pdf.key)); runCurrent()
            assertTrue((f.registered.metadata as Observation.Ready).value.favorite)
            assertEquals(2, f.documents.observations.size)
        }
    }
    @Test fun favoriteWaitsForDurableAcknowledgementAndReconcilesFreshObservation() = runTest {
        LibraryFixture(this).use { f ->
            f.pdfFolder(); val record = f.documents.confirm(pdfEntry.storage)
            val position = ReadingPosition(PageIndex(4), DocumentRevision.Unknown(record.document))
            val values = f.documents.values.getValue(record.document)
            values.value = Observation.Ready(DocumentMetadata(false, position))
            val release = CompletableDeferred<Unit>()
            f.documents.write = { _, _ -> release.await(); values.value = Observation.Ready(DocumentMetadata(true, position)); Outcome.Success(Unit) }
            f.controller.start(); runCurrent()
            f.controller.dispatch(LibraryAction.SetFavorite(f.pdf.key, true)); runCurrent()
            assertEquals(LibraryFavorite.Pending(true), f.registered.favorite); assertFalse(f.pdf.canFavorite)
            repeat(3) { f.controller.dispatch(LibraryAction.SetFavorite(f.pdf.key, false)) }; runCurrent(); assertEquals(1, f.documents.writes.size)
            values.value = Observation.Ready(DocumentMetadata(true, position)); runCurrent()
            assertEquals(LibraryFavorite.Pending(true), f.registered.favorite) // Observation is not the write acknowledgement.
            release.complete(Unit); runCurrent()
            assertEquals(LibraryFavorite.Idle, f.registered.favorite)
            assertEquals(DocumentMetadata(true, position), (f.registered.metadata as Observation.Ready).value)
            assertEquals(2, f.documents.observations.size); assertEquals(1, f.documents.activeCollectors)
            f.controller.dispatch(LibraryAction.SetFavorite(f.pdf.key, true)); runCurrent(); assertEquals(1, f.documents.writes.size)
        }
    }
    @Test fun failedWriteRetainsFailureAndUsesActualObservedValueRatherThanRollback() = runTest {
        for (committed in listOf(false, true)) LibraryFixture(this).use { f ->
            f.pdfFolder(); val record = f.documents.confirm(pdfEntry.storage)
            val values = f.documents.values.getValue(record.document)
            f.documents.write = { _, _ ->
                if (committed) values.value = Observation.Ready(DocumentMetadata(true))
                Outcome.Failure(AppError.Access.PRIVATE_STORAGE_UNAVAILABLE)
            }
            f.controller.start(); runCurrent()
            f.controller.dispatch(LibraryAction.SetFavorite(f.pdf.key, true)); runCurrent()
            assertEquals(LibraryFavorite.Failed(true, AppError.Access.PRIVATE_STORAGE_UNAVAILABLE), f.registered.favorite)
            assertEquals(committed, (f.registered.metadata as Observation.Ready).value.favorite)
            f.documents.write = { _, desired -> values.value = Observation.Ready(DocumentMetadata(desired)); Outcome.Success(Unit) }
            f.controller.dispatch(LibraryAction.RetryFavorite(f.pdf.key)); f.controller.dispatch(LibraryAction.RetryFavorite(f.pdf.key)); runCurrent()
            assertEquals(LibraryFavorite.Idle, f.registered.favorite)
            assertEquals(if (committed) 1 else 2, f.documents.writes.size)
            assertTrue((f.registered.metadata as Observation.Ready).value.favorite)
        }
    }
    @Test fun canceledWriteMayHaveCommittedAndMustBeReobserved() = runTest {
        LibraryFixture(this).use { f ->
            f.pdfFolder(); val record = f.documents.confirm(pdfEntry.storage)
            f.documents.write = { _, _ ->
                f.documents.values.getValue(record.document).value = Observation.Ready(DocumentMetadata(true))
                throw CancellationException("committed before cancellation")
            }
            f.controller.start(); runCurrent()
            f.controller.dispatch(LibraryAction.SetFavorite(f.pdf.key, true)); runCurrent()
            assertEquals(LibraryFavorite.Idle, f.registered.favorite)
            assertTrue((f.registered.metadata as Observation.Ready).value.favorite)
            assertTrue(currentCoroutineContext().isActive); assertEquals(2, f.documents.observations.size)
        }
    }
    @Test fun failedReconciliationDoesNotPermitBlindRetryAndCanRecover() = runTest {
        LibraryFixture(this).use { f ->
            f.pdfFolder(); f.documents.confirm(pdfEntry.storage)
            f.controller.start(); runCurrent()
            f.documents.write = { _, _ -> Outcome.Failure(AppError.Access.PRIVATE_STORAGE_UNAVAILABLE) }
            f.documents.observeFlow = { flowOf(Observation.Loading, Observation.Failed(AppError.Access.PROVIDER_IO_FAILURE)) }
            f.controller.dispatch(LibraryAction.SetFavorite(f.pdf.key, true)); runCurrent()
            assertTrue(f.registered.metadata is Observation.Failed); assertTrue(f.registered.favorite is LibraryFavorite.Failed)
            f.controller.dispatch(LibraryAction.RetryFavorite(f.pdf.key)); runCurrent(); assertEquals(1, f.documents.writes.size)
            f.documents.observeFlow = { flowOf(Observation.Loading, Observation.Ready(DocumentMetadata(true))) }
            f.controller.dispatch(LibraryAction.RetryFavorite(f.pdf.key)); runCurrent()
            assertEquals(LibraryFavorite.Idle, f.registered.favorite); assertEquals(1, f.documents.writes.size)
        }
    }
    @Test fun setFavoriteAfterFailureReconcilesRequestedTargetBeforeAnotherWrite() = runTest {
        LibraryFixture(this).use { f ->
            f.pdfFolder(); f.documents.confirm(pdfEntry.storage); f.controller.start(); runCurrent()
            f.documents.write = { _, _ -> Outcome.Failure(AppError.Access.PRIVATE_STORAGE_UNAVAILABLE) }
            f.controller.dispatch(LibraryAction.SetFavorite(f.pdf.key, true)); runCurrent()
            f.controller.dispatch(LibraryAction.SetFavorite(f.pdf.key, false)); runCurrent()
            assertEquals(LibraryFavorite.Idle, f.registered.favorite); assertEquals(1, f.documents.writes.size)
        }
    }
    @Test fun twoDocumentsMutateIndependentlyAndPendingWriteDoesNotBlockBrowsing() = runTest {
        LibraryFixture(this).use { f ->
            val second = pdfEntry.copy(storage = StorageDocumentRef("test", "second"))
            val one = f.documents.confirm(pdfEntry.storage); val two = f.documents.confirm(second.storage)
            f.list = { Outcome.Success(DocumentListing(listOf(pdfEntry, second), true)) }
            val release = CompletableDeferred<Unit>()
            f.documents.write = { document, desired ->
                if (document == one.document) release.await()
                f.documents.values.getValue(document).value = Observation.Ready(DocumentMetadata(desired)); Outcome.Success(Unit)
            }
            f.controller.start(); runCurrent()
            val rows = f.ready.rows.filterIsInstance<LibraryRow.Pdf>()
            rows.forEach { f.controller.dispatch(LibraryAction.SetFavorite(it.key, true)) }; runCurrent()
            val done = f.ready.rows.filterIsInstance<LibraryRow.Pdf>().single { (it.association as LibraryAssociation.Registered).value.document == two.document }
            assertEquals(LibraryFavorite.Idle, (done.association as LibraryAssociation.Registered).favorite)
            assertEquals(2, f.documents.writes.size)
            release.complete(Unit); runCurrent()
        }
    }
    @Test fun canceledOldWriteCannotOverwriteNewGenerationOppositeIntent() = runTest {
        LibraryFixture(this).use { f ->
            f.pdfFolder(); val record = f.documents.confirm(pdfEntry.storage)
            val release = CompletableDeferred<Unit>(); var attempt = 0
            f.documents.write = { _, desired ->
                if (++attempt == 1) withContext(NonCancellable) { release.await() }
                f.documents.values.getValue(record.document).value = Observation.Ready(DocumentMetadata(desired)); Outcome.Success(Unit)
            }
            f.controller.start(); runCurrent()
            f.controller.dispatch(LibraryAction.SetFavorite(f.pdf.key, true)); runCurrent()
            f.controller.dispatch(LibraryAction.Refresh); runCurrent()
            f.controller.dispatch(LibraryAction.SetFavorite(f.pdf.key, false)); runCurrent()
            assertEquals(1, f.documents.writes.size) // New mutation waits for the old uncertain commit.
            release.complete(Unit); runCurrent()
            assertEquals(listOf(true, false), f.documents.writes.map { it.second })
            assertFalse((f.registered.metadata as Observation.Ready).value.favorite)
            assertEquals(LibraryFavorite.Idle, f.registered.favorite)
        }
    }
    @Test fun oldDocumentObserverCannotUpdateSameVisualRowAfterRefreshAndCloseReleasesCollectors() = runTest {
        val f = LibraryFixture(this)
        try {
            f.pdfFolder(); val old = f.documents.confirm(pdfEntry.storage)
            f.controller.start(); runCurrent(); assertEquals(1, f.documents.activeCollectors)
            val new = f.documents.confirm(pdfEntry.storage)
            f.controller.dispatch(LibraryAction.Refresh); runCurrent()
            f.documents.values.getValue(old.document).value = Observation.Ready(DocumentMetadata(true)); runCurrent()
            assertEquals(new.document, f.registered.value.document)
            assertFalse((f.registered.metadata as Observation.Ready).value.favorite); assertEquals(1, f.documents.activeCollectors)
        } finally { f.close() }
        runCurrent(); assertEquals(0, f.documents.activeCollectors)
    }
    @Test fun queuedOldObserverEmissionCannotOverrideExplicitFreshRetryInSameGeneration() = runTest {
        LibraryFixture(this).use { f ->
            f.pdfFolder(); f.documents.confirm(pdfEntry.storage)
            val emissions = Channel<Observation<DocumentMetadata>>(Channel.UNLIMITED)
            f.documents.observeFlow = { emissions.receiveAsFlow() }
            f.controller.start(); runCurrent()
            emissions.send(Observation.Failed(AppError.Access.PRIVATE_STORAGE_UNAVAILABLE)); runCurrent()
            val readEntered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
            f.readNavigation = { readEntered.complete(Unit); release.await(); f.navigation }
            f.controller.dispatch(LibraryAction.RetryMetadata(f.pdf.key)); runCurrent(); readEntered.await()
            emissions.send(Observation.Ready(DocumentMetadata(true))); runCurrent() // Queued behind retry, while old collector is still valid.
            f.documents.observeFlow = { flowOf(Observation.Loading, Observation.Ready(DocumentMetadata(false))) }
            f.readNavigation = { f.navigation }; release.complete(Unit); runCurrent()
            assertFalse((f.registered.metadata as Observation.Ready).value.favorite)
            assertEquals(2, f.documents.observations.size); emissions.cancel()
        }
    }
    @Test fun metadataRetryDoesNotInterruptAnUnacknowledgedFavoriteMutation() = runTest {
        LibraryFixture(this).use { f ->
            f.pdfFolder(); val record = f.documents.confirm(pdfEntry.storage)
            val release = CompletableDeferred<Unit>()
            f.documents.write = { _, _ -> release.await(); f.documents.values.getValue(record.document).value = Observation.Ready(DocumentMetadata(true)); Outcome.Success(Unit) }
            f.controller.start(); runCurrent()
            f.controller.dispatch(LibraryAction.SetFavorite(f.pdf.key, true)); runCurrent()
            f.documents.values.getValue(record.document).value = Observation.Failed(AppError.Access.PRIVATE_STORAGE_UNAVAILABLE); runCurrent()
            f.controller.dispatch(LibraryAction.RetryMetadata(f.pdf.key)); runCurrent()
            assertEquals(1, f.documents.observations.size); assertEquals(LibraryFavorite.Pending(true), f.registered.favorite)
            release.complete(Unit); runCurrent()
            assertEquals(LibraryFavorite.Idle, f.registered.favorite)
        }
    }
}
