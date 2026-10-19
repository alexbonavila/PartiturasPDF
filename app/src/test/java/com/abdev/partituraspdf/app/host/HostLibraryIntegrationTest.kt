package com.abdev.partituraspdf.app.host

import com.abdev.partituraspdf.app.coordination.FoundationHost
import com.abdev.partituraspdf.core.document.*
import com.abdev.partituraspdf.core.foundation.*
import com.abdev.partituraspdf.core.result.*
import com.abdev.partituraspdf.library.*
import com.abdev.partituraspdf.settings.SettingsFixture
import com.abdev.partituraspdf.storage.api.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

/** Real F controller + FoundationHost, contract repositories. No Android grants are simulated as real. */
@OptIn(ExperimentalCoroutinesApi::class)
class HostLibraryIntegrationTest {
    private val root = FolderRef("test", "root")
    private class Session : AppHostSession {
        val data = LibraryDocumentFixture()
        var stored: FolderRef? = null
        var persistenceCalls = 0
        var selectionCalls = 0
        var navigation: FolderNavigationState? = null
        var selectionFailure: Outcome.Failure? = null
        val folders = object : FolderNavigation {
            override suspend fun state() = navigation
            override suspend fun select(root: FolderRef): Outcome<FolderNavigationState> {
                selectionCalls++
                selectionFailure?.let { return it }
                return Outcome.Success(FolderNavigationState(root, listOf(root), (navigation?.version ?: 0) + 1).also { navigation = it })
            }
            override suspend fun enter(entry: DocumentEntry, expectedVersion: Long): Outcome<FolderNavigationState> = error("Covered by SAF navigation tests")
            override suspend fun back(expectedVersion: Long): Outcome<FolderNavigationState> = error("Covered by SAF navigation tests")
        }
        lateinit var features: AppHostFeatures
        override fun load(scope: CoroutineScope): AppHostFeatures {
            val roots = object : SelectedRootRepository {
                override suspend fun select(root: FolderRef): Outcome<Unit> { persistenceCalls++; stored = root; return Outcome.Success(Unit) }
                override suspend fun restore() = Outcome.Success(stored)
                override suspend fun clear(): Outcome<Unit> = error("Integration must never clear roots")
            }
            lateinit var library: LibraryController
            val picker = RootPickerCoordinator(scope, folders,
                accept = { _, _ -> val root = FolderRef("test", "root"); roots.select(root); Outcome.Success(root) },
                restore = { roots.restore() }, completed = { library.onRootSelectionFinished(it) },
                refreshed = { library.dispatch(LibraryAction.Refresh) },
                interrupted = { scope.coroutineContext[CoroutineExceptionHandler]!!.handleException(scope.coroutineContext, it) })
            val host = FoundationHost(roots, folders, data.commands, picker::choose,
                open = { Outcome.Failure(AppError.Unsupported.OPERATION) })
            val catalog = object : DocumentCatalog {
                override suspend fun list(folder: FolderRef) = Outcome.Success(DocumentListing(
                    listOf(DocumentEntry(StorageDocumentRef("test", "pdf"), DocumentInfo("score.pdf", "application/pdf"), folder)), true))
                override suspend fun inspect(document: StorageDocumentRef): Outcome<DocumentInfo> = error("No Reader")
            }
            library = LibraryController(scope, catalog, folders, host, data.registry, data.metadata, data.commands)
            return AppHostFeatures(folders, library, SettingsFixture(scope).controller, picker).also { features = it }
        }
        override fun stop() { features.picker!!.close(); features.library.close(); features.settings.close() }
        override fun close() {}
    }
    private fun TestScope.host(session: Session) = AppHostViewModel({ session },
        StandardTestDispatcher(testScheduler), StandardTestDispatcher(testScheduler), { false }, { throw it })
    private fun Session.pdf() = (features.library.state.value.content as LibraryContent.Ready).rows.single() as LibraryRow.Pdf
    private suspend fun TestScope.select(host: AppHostViewModel, session: Session) {
        host.library(LibraryAction.ChooseRoot, 0); runCurrent()
        assertEquals(LibraryOperation.Pending, session.features.library.state.value.rootSelection)
        val picker = session.features.picker!!; val id = picker.request.value!!
        assertTrue(picker.claim(id)); picker.launched(id, Outcome.Success(Unit)); runCurrent()
        assertEquals(LibraryOperation.Pending, session.features.library.state.value.rootSelection)
        picker.result(id, true, "opaque", 1); runCurrent()
    }

    @Test fun acceptedPickerSynchronizesSharedNavigatorOnceBeforeLibraryLoadsAndDoesNotAutoRegister() = runTest {
        val session = Session(); val host = host(session); runCurrent(); select(host, session)
        assertSame(session.folders, session.features.folders)
        assertEquals(root, (session.features.library.state.value.location as LibraryLocation.Available).snapshot.root)
        assertEquals(1, session.persistenceCalls); assertEquals(1, session.selectionCalls)
        assertEquals(LibraryAssociation.Unregistered, session.pdf().association)
        assertTrue(session.data.registrations.isEmpty())
        host.dispose().await()
    }

    @Test fun registrationFavoriteRefreshSettingsAndStaleCommandsUseExistingFeatureContracts() = runTest {
        val session = Session(); val host = host(session); runCurrent(); select(host, session)
        val oldKey = session.pdf().key
        host.library(LibraryAction.Register(oldKey), 0); runCurrent()
        val record = (session.pdf().association as LibraryAssociation.Registered).value
        assertEquals(listOf(oldKey.storage), session.data.registrations)
        host.library(LibraryAction.SetFavorite(oldKey, true), 0); runCurrent()
        assertEquals(listOf(record.document to true), session.data.writes)
        val trail = session.navigation
        host.showSettings(0); host.library(LibraryAction.SetFavorite(oldKey, false), 0); runCurrent()
        assertSame(trail, session.navigation); assertEquals(1, session.data.writes.size)
        host.back(1); host.library(LibraryAction.Refresh, 2); runCurrent()
        assertTrue(((session.pdf().association as LibraryAssociation.Registered).metadata as Observation.Ready).value.favorite)
        host.library(LibraryAction.SetFavorite(oldKey, false), 2); runCurrent()
        assertEquals(AppError.Conflict.STALE_SESSION, session.features.library.state.value.documentError)
        assertEquals(1, session.data.writes.size)
        host.dispose().await()
    }

    @Test fun registrationFailureIsNotAnUnregisteredSuccessAndCannotEraseExistingMetadata() = runTest {
        val session = Session(); val host = host(session); runCurrent(); select(host, session)
        session.data.register = { Outcome.Failure(AppError.Access.PRIVATE_STORAGE_UNAVAILABLE) }
        host.library(LibraryAction.Register(session.pdf().key), 0); runCurrent()
        val failure = session.pdf().association as LibraryAssociation.Failed
        assertEquals(LibraryAssociationOperation.REGISTRATION, failure.operation)
        assertEquals(AppError.Access.PRIVATE_STORAGE_UNAVAILABLE, failure.error)
        assertTrue(session.data.records.isEmpty())
        host.dispose().await()
    }

    @Test fun transientAndRegisteredReaderRequestsRemainExplicitFailuresWithoutNativeOpening() = runTest {
        val session = Session(); val host = host(session); runCurrent(); select(host, session)
        host.library(LibraryAction.Open(session.pdf().key), 0); runCurrent()
        assertEquals(LibraryOpening.Failed(AppError.Unsupported.OPERATION), session.pdf().opening)
        host.library(LibraryAction.Register(session.pdf().key), 0); runCurrent()
        host.library(LibraryAction.RetryOpen(session.pdf().key), 0); runCurrent()
        assertEquals(LibraryOpening.Failed(AppError.Unsupported.OPERATION), session.pdf().opening)
        host.dispose().await()
    }

    @Test fun partialSelectionBlocksEvenPreviouslyRenderedCommandsUntilExplicitReconciliation() = runTest {
        val session = Session(); val host = host(session); runCurrent()
        session.selectionFailure = Outcome.Failure(AppError.Access.PERMISSION_REVOKED)
        select(host, session)
        assertEquals(AppError.Access.PERMISSION_REVOKED, session.features.picker!!.recovery.value)
        host.library(LibraryAction.Refresh, 0); runCurrent(); assertNull(session.features.picker!!.request.value)
        session.selectionFailure = null; session.features.picker!!.reconcile(); runCurrent()
        assertNull(session.features.picker!!.recovery.value); assertEquals(LibraryAssociation.Unregistered, session.pdf().association)
        assertEquals(1, session.persistenceCalls)
        host.dispose().await()
        host.library(LibraryAction.ChooseRoot, 0); runCurrent(); assertNull(session.features.picker!!.request.value)
    }

    @Test fun recoveryAllowsOnlyExplicitRealSelectionAndRejectsNormalLibraryActions() = runTest {
        val session = Session(); val host = host(session); runCurrent()
        session.selectionFailure = Outcome.Failure(AppError.Access.PERMISSION_REVOKED)
        select(host, session)
        session.selectionFailure = null
        select(host, session) // New user-authorized picker can replace the unusable persisted root.
        assertNull(session.features.picker!!.recovery.value)
        assertEquals(LibraryAssociation.Unregistered, session.pdf().association)
        assertEquals(2, session.persistenceCalls)
        host.dispose().await()
    }

    @Test fun uncertainFavoriteWriteIsReconciledByFAndDoesNotBecomeAnOptimisticHostSuccess() = runTest {
        val session = Session(); val host = host(session); runCurrent(); select(host, session)
        host.library(LibraryAction.Register(session.pdf().key), 0); runCurrent()
        session.data.write = { _, _ -> Outcome.Failure(AppError.Access.PRIVATE_STORAGE_UNAVAILABLE) }
        host.library(LibraryAction.SetFavorite(session.pdf().key, true), 0); runCurrent()
        val associated = session.pdf().association as LibraryAssociation.Registered
        assertFalse((associated.metadata as Observation.Ready).value.favorite)
        assertEquals(LibraryFavorite.Failed(true, AppError.Access.PRIVATE_STORAGE_UNAVAILABLE), associated.favorite)
        assertEquals(1, session.data.writes.size)
        host.dispose().await()
    }

    @Test fun existingSelectedRootRestoresThroughFoundationHostWithoutPersistingOrLaunchingPicker() = runTest {
        val session = Session().also { it.stored = root }; val host = host(session); runCurrent()
        assertEquals(root, (session.features.library.state.value.location as LibraryLocation.Available).snapshot.root)
        assertEquals(0, session.persistenceCalls); assertEquals(1, session.selectionCalls)
        assertNull(session.features.picker!!.request.value)
        host.dispose().await()
    }

    @Test fun canceledReplacementPreservesLibraryAndOnlyOnePendingPickerSurvivesRepeatedRequests() = runTest {
        val session = Session(); val host = host(session); runCurrent(); select(host, session)
        val before = session.navigation
        host.library(LibraryAction.ChooseRoot, 0); host.library(LibraryAction.ChooseRoot, 0); runCurrent()
        val picker = session.features.picker!!; val id = picker.request.value!!
        assertTrue(picker.claim(id)); picker.launched(id, Outcome.Success(Unit)); runCurrent()
        picker.result(id, false, null, 0); runCurrent()
        assertSame(before, session.navigation); assertEquals(1, session.persistenceCalls)
        assertEquals(LibraryOperation.Canceled, session.features.library.state.value.rootSelection)
        assertNotNull(session.pdf())
        host.dispose().await()
    }
}
