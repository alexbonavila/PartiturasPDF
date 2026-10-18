package com.abdev.partituraspdf.library

import com.abdev.partituraspdf.core.document.*
import com.abdev.partituraspdf.core.foundation.*
import com.abdev.partituraspdf.core.result.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

internal fun LibraryFixture.pdfFolder() {
    list = { folder -> Outcome.Success(DocumentListing(listOf(pdfEntry.copy(folder = folder)), false)) }
}

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryRegistrationTest {
    @Test fun registryConfirmationOverridesCatalogHintAndOnlyPdfsAreLookedUp() = runTest {
        LibraryFixture(this).use { f ->
            val record = f.documents.confirm(pdfEntry.storage)
            val hinted = pdfEntry.copy(document = DocumentRef(DocumentId("untrusted-hint")))
            f.list = { Outcome.Success(DocumentListing(listOf(entry(), hinted), false)) }
            f.controller.start(); runCurrent()
            assertEquals(listOf(pdfEntry.storage), f.documents.lookups)
            assertEquals(record, f.registered.value); assertSame(hinted, f.pdf.entry)
            assertEquals(listOf(record.document), f.documents.observations); assertTrue(f.pdf.canFavorite)
            assertTrue(f.documents.registrations.isEmpty()); assertTrue(f.documents.writes.isEmpty()); assertFalse(f.ready.complete)
        }
    }
    @Test fun notRegisteredIsDistinctFromEveryOperationalLookupFailureAndRetryIsTargeted() = runTest {
        val errors: List<AppError> = LibraryControllerTest.errors + listOf(AppError.Access.DOCUMENT_DELETED, AppError.Conflict.REGISTRATION_CHANGED)
        for (error in errors) LibraryFixture(this).use { f ->
            f.pdfFolder(); f.documents.find = { Outcome.Failure(error) }
            f.controller.start(); runCurrent()
            assertEquals(LibraryAssociation.Failed(LibraryAssociationOperation.LOOKUP, error), f.pdf.association)
            f.controller.dispatch(LibraryAction.Open(f.pdf.key)); runCurrent()
            assertEquals(LibraryOpening.Rejected, f.pdf.opening); assertTrue(f.events.isEmpty()); assertFalse(f.pdf.canFavorite)
            f.documents.find = { Outcome.Failure(AppError.Access.NOT_REGISTERED) }
            f.controller.dispatch(LibraryAction.RetryLookup(f.pdf.key)); runCurrent()
            assertEquals(LibraryAssociation.Unregistered, f.pdf.association); assertTrue(f.documents.observations.isEmpty())
        }
    }
    @Test fun wrongLookupStorageIsNotAConfirmedIdentity() = runTest {
        LibraryFixture(this).use { f ->
            f.pdfFolder(); val wrong = f.documents.confirm(StorageDocumentRef("test", "wrong"))
            f.documents.find = { Outcome.Success(wrong) }; f.controller.start(); runCurrent()
            assertEquals(LibraryAssociation.Failed(LibraryAssociationOperation.LOOKUP, AppError.Conflict.REGISTRATION_CHANGED), f.pdf.association)
            assertTrue(f.documents.observations.isEmpty())
        }
    }
    @Test fun delayedLookupDoesNotGuessTransientAndRegisteringInvalidatesOlderResponse() = runTest {
        LibraryFixture(this).use { f ->
            f.pdfFolder(); val release = CompletableDeferred<Unit>()
            f.documents.find = { withContext(NonCancellable) { release.await() }; Outcome.Failure(AppError.Access.NOT_REGISTERED) }
            f.controller.start(); runCurrent()
            assertEquals(LibraryAssociation.LookingUp, f.pdf.association)
            f.controller.dispatch(LibraryAction.Open(f.pdf.key)); runCurrent(); assertTrue(f.events.isEmpty())
            f.controller.dispatch(LibraryAction.Register(f.pdf.key)); runCurrent()
            val confirmed = f.registered.value
            release.complete(Unit); runCurrent()
            assertEquals(confirmed, f.registered.value); assertEquals(1, f.documents.registrations.size)
            f.controller.dispatch(LibraryAction.RetryOpen(f.pdf.key)); runCurrent()
            assertEquals(LibraryEvent.Open(DocumentOpenRequest.Registered(confirmed.document)), f.events.single())
        }
    }
    @Test fun lookupPoolHasAtMostFourQueriesEvenForLargeListingAndRefresh() = runTest {
        LibraryFixture(this).use { f ->
            f.list = { Outcome.Success(DocumentListing((1..30).map { pdfEntry.copy(storage = StorageDocumentRef("test", "$it")) }, true)) }
            val release = CompletableDeferred<Unit>(); var active = 0; var maximum = 0
            f.documents.find = {
                active++; maximum = maxOf(maximum, active)
                try { release.await(); Outcome.Failure(AppError.Access.NOT_REGISTERED) } finally { active-- }
            }
            f.controller.start(); runCurrent(); assertEquals(4, active); assertEquals(4, f.documents.lookups.size)
            f.controller.dispatch(LibraryAction.Refresh); runCurrent(); assertEquals(4, active)
            release.complete(Unit); runCurrent(); assertEquals(0, active); assertEquals(4, maximum)
            assertTrue(f.ready.rows.filterIsInstance<LibraryRow.Pdf>().all { it.association == LibraryAssociation.Unregistered })
        }
    }
    @Test fun explicitRegistrationIsDeduplicatedAndPreservesEntryKeyAndCompleteness() = runTest {
        LibraryFixture(this).use { f ->
            f.pdfFolder(); f.controller.start(); runCurrent(); val original = f.pdf
            val release = CompletableDeferred<Unit>()
            f.documents.register = { release.await(); Outcome.Success(f.documents.confirm(it)) }
            repeat(3) { f.controller.dispatch(LibraryAction.Register(original.key)) }; runCurrent()
            assertEquals(LibraryAssociation.Registering, f.pdf.association); assertEquals(1, f.documents.registrations.size)
            release.complete(Unit); runCurrent()
            assertEquals(original.key, f.pdf.key); assertSame(original.entry, f.pdf.entry); assertFalse(f.ready.complete)
            assertEquals(f.documents.records.getValue(pdfEntry.storage), f.registered.value)
            assertTrue(f.events.none { it is LibraryEvent.Register })
            f.controller.dispatch(LibraryAction.Register(f.pdf.key)); runCurrent(); assertEquals(1, f.documents.registrations.size)
            val doc = f.registered.value.document
            f.controller.dispatch(LibraryAction.Refresh); runCurrent(); assertEquals(doc, f.registered.value.document)
        }
    }
    @Test fun conflictReconcilesOnlyActualAssociationAndUnresolvedConflictRemainsFailure() = runTest {
        for (persisted in listOf(false, true)) LibraryFixture(this).use { f ->
            f.pdfFolder(); f.controller.start(); runCurrent()
            f.documents.register = { if (persisted) f.documents.confirm(it); Outcome.Failure(AppError.Conflict.REGISTRATION_CHANGED) }
            f.controller.dispatch(LibraryAction.Register(f.pdf.key)); runCurrent()
            if (persisted) assertEquals(f.documents.records[pdfEntry.storage], f.registered.value)
            else assertEquals(LibraryAssociation.Failed(LibraryAssociationOperation.REGISTRATION, AppError.Conflict.REGISTRATION_CHANGED), f.pdf.association)
            assertEquals(1, f.documents.registrations.size)
        }
    }
    @Test fun registrationFailureDoesNotHidePdfOrRetryWritesWithoutLookup() = runTest {
        LibraryFixture(this).use { f ->
            f.pdfFolder(); f.controller.start(); runCurrent()
            f.documents.register = { Outcome.Failure(AppError.Access.PRIVATE_STORAGE_UNAVAILABLE) }
            f.controller.dispatch(LibraryAction.Register(f.pdf.key)); runCurrent()
            assertEquals(LibraryAssociation.Failed(LibraryAssociationOperation.REGISTRATION, AppError.Access.PRIVATE_STORAGE_UNAVAILABLE), f.pdf.association)
            assertEquals(1, f.ready.rows.size)
            val record = f.documents.confirm(pdfEntry.storage) // A prior uncertain write was actually committed.
            f.controller.dispatch(LibraryAction.RetryRegistration(f.pdf.key)); runCurrent()
            assertEquals(record, f.registered.value); assertEquals(1, f.documents.registrations.size)
        }
    }
    @Test fun retryRegistrationWritesOnceOnlyAfterConfirmedMissingAssociation() = runTest {
        LibraryFixture(this).use { f ->
            f.pdfFolder(); f.controller.start(); runCurrent()
            f.documents.register = { Outcome.Failure(AppError.Access.PRIVATE_STORAGE_UNAVAILABLE) }
            f.controller.dispatch(LibraryAction.Register(f.pdf.key)); runCurrent()
            f.documents.register = { Outcome.Success(f.documents.confirm(it)) }
            f.controller.dispatch(LibraryAction.Register(f.pdf.key)) // Failed Register follows the same safe retry path.
            f.controller.dispatch(LibraryAction.RetryRegistration(f.pdf.key)); runCurrent()
            assertTrue(f.pdf.association is LibraryAssociation.Registered); assertEquals(2, f.documents.registrations.size)
        }
    }
    @Test fun canceledRegistrationReconcilesPossibleCommitWithoutInventingRollback() = runTest {
        LibraryFixture(this).use { f ->
            f.pdfFolder(); f.controller.start(); runCurrent()
            f.documents.register = { f.documents.confirm(it); throw CancellationException("after commit") }
            f.controller.dispatch(LibraryAction.Register(f.pdf.key)); runCurrent()
            assertEquals(f.documents.records[pdfEntry.storage], f.registered.value); assertEquals(1, f.documents.registrations.size)
            assertTrue(currentCoroutineContext().isActive)
        }
    }
    @Test fun wrongRegistrationResultAndReconciliationFailureAreNotSuccess() = runTest {
        for (conflict in listOf(false, true)) LibraryFixture(this).use { f ->
            f.pdfFolder(); f.controller.start(); runCurrent()
            f.documents.register = {
                if (conflict) {
                    f.documents.find = { Outcome.Failure(AppError.Access.PRIVATE_STORAGE_UNAVAILABLE) }
                    Outcome.Failure(AppError.Conflict.REGISTRATION_CHANGED)
                } else Outcome.Success(f.documents.confirm(StorageDocumentRef("test", "other")))
            }
            f.controller.dispatch(LibraryAction.Register(f.pdf.key)); runCurrent()
            assertEquals(if (conflict) AppError.Access.PRIVATE_STORAGE_UNAVAILABLE else AppError.Conflict.REGISTRATION_CHANGED, (f.pdf.association as LibraryAssociation.Failed).error)
            assertTrue(f.documents.observations.isEmpty())
        }
    }
    @Test fun refreshOrRootReplacementWaitsForAnOldUncertainRegistrationBeforeLookup() = runTest {
        for (replaceRoot in listOf(false, true)) LibraryFixture(this).use { f ->
            f.pdfFolder(); f.controller.start(); runCurrent(); val oldKey = f.pdf.key
            val release = CompletableDeferred<Unit>()
            f.documents.register = { withContext(NonCancellable) { release.await() }; Outcome.Success(f.documents.confirm(it)) }
            f.controller.dispatch(LibraryAction.Register(oldKey)); runCurrent()
            if (replaceRoot) {
                f.controller.dispatch(LibraryAction.ChooseRoot); runCurrent()
                val other = FolderRef("test", "new-root"); f.navigation = trail(other, version = 2)
                f.controller.onRootSelectionFinished(Outcome.Success(other))
            } else f.controller.dispatch(LibraryAction.Refresh)
            runCurrent(); assertNotEquals(oldKey, f.pdf.key); assertEquals(LibraryAssociation.LookingUp, f.pdf.association)
            release.complete(Unit); runCurrent()
            assertEquals(f.documents.records[pdfEntry.storage], f.registered.value)
            assertEquals(if (replaceRoot) FolderRef("test", "new-root") else root, f.available.snapshot.root)
        }
    }
    @Test fun explicitRegistrationAlsoInvalidatesAnOlderTargetedLookupRetry() = runTest {
        LibraryFixture(this).use { f ->
            f.pdfFolder(); f.documents.find = { Outcome.Failure(AppError.Access.PRIVATE_STORAGE_UNAVAILABLE) }
            f.controller.start(); runCurrent()
            val release = CompletableDeferred<Unit>()
            f.documents.find = { withContext(NonCancellable) { release.await() }; Outcome.Failure(AppError.Access.NOT_REGISTERED) }
            f.controller.dispatch(LibraryAction.RetryLookup(f.pdf.key)); runCurrent()
            f.controller.dispatch(LibraryAction.Register(f.pdf.key)); runCurrent()
            val actual = f.registered.value
            release.complete(Unit); runCurrent()
            assertEquals(actual, f.registered.value); assertEquals(1, f.documents.registrations.size)
        }
    }
    @Test fun failedRetryLookupNeverReissuesARegistrationWrite() = runTest {
        LibraryFixture(this).use { f ->
            f.pdfFolder(); f.controller.start(); runCurrent()
            f.documents.register = { Outcome.Failure(AppError.Access.PRIVATE_STORAGE_UNAVAILABLE) }
            f.controller.dispatch(LibraryAction.Register(f.pdf.key)); runCurrent()
            f.documents.find = { Outcome.Failure(AppError.Access.PROVIDER_UNAVAILABLE) }
            f.controller.dispatch(LibraryAction.RetryRegistration(f.pdf.key)); runCurrent()
            assertEquals(LibraryAssociation.Failed(LibraryAssociationOperation.LOOKUP, AppError.Access.PROVIDER_UNAVAILABLE), f.pdf.association)
            assertEquals(1, f.documents.registrations.size)
        }
    }
}
