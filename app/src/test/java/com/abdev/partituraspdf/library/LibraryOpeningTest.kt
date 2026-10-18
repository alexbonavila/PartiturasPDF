package com.abdev.partituraspdf.library

import com.abdev.partituraspdf.core.document.*
import com.abdev.partituraspdf.core.foundation.*
import com.abdev.partituraspdf.core.result.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryOpeningTest {
    @Test fun registeredAndTransientRequestsUseOnlyConfirmedIdentityWithoutRegistering() = runTest {
        for (registered in listOf(false, true)) LibraryFixture(this).use { f ->
            f.pdfFolder(); val record = if (registered) f.documents.confirm(pdfEntry.storage) else null
            f.controller.start(); runCurrent()
            assertFalse(f.pdf.canFavorite && !registered)
            f.controller.dispatch(LibraryAction.Open(f.pdf.key)); runCurrent()
            val expected = if (record != null) DocumentOpenRequest.Registered(record.document) else DocumentOpenRequest.Transient(pdfEntry.storage)
            assertEquals(listOf(LibraryEvent.Open(expected)), f.events)
            assertEquals(LibraryOpening.Accepted, f.pdf.opening); assertTrue(f.documents.registrations.isEmpty())
            f.controller.dispatch(LibraryAction.Open(f.pdf.key)); runCurrent(); assertEquals(1, f.events.size)
        }
    }
    @Test fun openFailureAndCancellationRemainRetryableWithoutClaimingReaderSuccess() = runTest {
        for (cancel in listOf(false, true)) LibraryFixture(this).use { f ->
            f.pdfFolder(); f.controller.start(); runCurrent()
            f.event = { if (cancel) throw CancellationException("host canceled") else Outcome.Failure(AppError.Access.PERMISSION_REVOKED) }
            f.controller.dispatch(LibraryAction.Open(f.pdf.key)); runCurrent()
            assertEquals(if (cancel) LibraryOpening.Rejected else LibraryOpening.Failed(AppError.Access.PERMISSION_REVOKED), f.pdf.opening)
            f.event = { Outcome.Success(Unit) }; f.controller.dispatch(LibraryAction.RetryOpen(f.pdf.key)); runCurrent()
            assertEquals(LibraryOpening.Accepted, f.pdf.opening); assertEquals(2, f.events.size)
            assertTrue(f.documents.registrations.isEmpty()); assertTrue(f.documents.writes.isEmpty())
        }
    }
    @Test fun repeatedOpeningTapsAreDeduplicatedWhileHostIsPending() = runTest {
        LibraryFixture(this).use { f ->
            f.pdfFolder(); f.controller.start(); runCurrent()
            val release = CompletableDeferred<Unit>()
            f.event = { release.await(); Outcome.Success(Unit) }
            repeat(4) { f.controller.dispatch(LibraryAction.Open(f.pdf.key)) }; runCurrent()
            assertEquals(1, f.events.size); assertEquals(LibraryOpening.Pending, f.pdf.opening)
            release.complete(Unit); runCurrent(); assertEquals(LibraryOpening.Accepted, f.pdf.opening)
        }
    }
    @Test fun staleAndDirectoryActionsAreRejectedWithoutHostOrPersistenceCalls() = runTest {
        LibraryFixture(this).use { f ->
            f.list = { Outcome.Success(DocumentListing(listOf(entry(), pdfEntry), true)) }; f.controller.start(); runCurrent()
            val directory = f.ready.rows.first().key
            f.controller.dispatch(LibraryAction.Open(directory)); runCurrent()
            assertEquals(AppError.Validation.INVALID_DOCUMENT, f.state.documentError)
            val old = f.pdf.key; f.controller.dispatch(LibraryAction.Refresh); runCurrent()
            val actions = listOf(LibraryAction.Open(old), LibraryAction.Register(old), LibraryAction.SetFavorite(old, true), LibraryAction.RetryLookup(old),
                LibraryAction.RetryRegistration(old), LibraryAction.RetryMetadata(old), LibraryAction.RetryFavorite(old), LibraryAction.RetryOpen(old))
            actions.forEach { f.controller.dispatch(it) }; runCurrent()
            assertEquals(AppError.Conflict.STALE_SESSION, f.state.documentError)
            assertTrue(f.events.isEmpty()); assertTrue(f.documents.registrations.isEmpty()); assertTrue(f.documents.writes.isEmpty())
        }
    }
    @Test fun delayedOpeningCannotModifyNewGenerationOrClosedController() = runTest {
        for (close in listOf(false, true)) LibraryFixture(this).use { f ->
            f.pdfFolder(); f.controller.start(); runCurrent()
            val release = CompletableDeferred<Unit>()
            f.event = { withContext(NonCancellable) { release.await() }; Outcome.Success(Unit) }
            f.controller.dispatch(LibraryAction.Open(f.pdf.key)); runCurrent()
            if (close) f.controller.close() else f.controller.dispatch(LibraryAction.Refresh)
            runCurrent(); val before = f.state
            release.complete(Unit); runCurrent()
            assertSame(before, f.state)
            if (!close) assertEquals(LibraryOpening.Idle, f.pdf.opening)
        }
    }
    @Test fun rowRetriesAndFavoriteControlsDoNotCreateWorkForUnregisteredRows() = runTest {
        LibraryFixture(this).use { f ->
            f.pdfFolder(); f.controller.start(); runCurrent(); val key = f.pdf.key
            listOf(LibraryAction.RetryLookup(key), LibraryAction.RetryRegistration(key), LibraryAction.RetryMetadata(key), LibraryAction.RetryFavorite(key),
                LibraryAction.RetryOpen(key), LibraryAction.SetFavorite(key, true)).forEach { f.controller.dispatch(it) }
            runCurrent()
            assertFalse(f.pdf.canFavorite); assertTrue(f.documents.writes.isEmpty()); assertTrue(f.documents.observations.isEmpty())
            assertTrue(f.events.isEmpty()); assertEquals(1, f.documents.lookups.size)
        }
    }
}
