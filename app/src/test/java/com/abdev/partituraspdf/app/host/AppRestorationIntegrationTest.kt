package com.abdev.partituraspdf.app.host

import android.os.ParcelFileDescriptor
import com.abdev.partituraspdf.app.coordination.ReaderRouteOwner
import com.abdev.partituraspdf.app.navigation.*
import com.abdev.partituraspdf.core.document.*
import com.abdev.partituraspdf.core.foundation.*
import com.abdev.partituraspdf.core.preferences.*
import com.abdev.partituraspdf.core.result.*
import com.abdev.partituraspdf.library.*
import com.abdev.partituraspdf.pdf.api.*
import com.abdev.partituraspdf.reader.*
import com.abdev.partituraspdf.settings.SettingsFixture
import com.abdev.partituraspdf.storage.api.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test

/** Fresh owners with real G + ReaderRouteOwner; storage/native are explicit contract doubles. */
@OptIn(ExperimentalCoroutinesApi::class)
class AppRestorationIntegrationTest {
    private class Disk {
        val document = DocumentRef(DocumentId("Registered:Case-Sensitive"))
        var committed: PageIndex? = null
        var writeError: AppError? = null
        var restores = 0; var saves = 0; var writers = 0
        val positions = object : ReaderPositionRepository {
            override suspend fun writer(document: DocumentRef) = PositionWriter(document, (++writers).toLong())
            override suspend fun restore(document: DocumentRef, pageCount: Int): Outcome<PageIndex?> { restores++; return Outcome.Success(committed) }
            override suspend fun saveVisible(writer: PositionWriter, sequence: Long, page: PageIndex): Outcome<Unit> {
                if (writer.epoch != writers.toLong()) return Outcome.Failure(AppError.Conflict.STALE_SESSION)
                saves++
                return writeError?.let { Outcome.Failure(it) } ?: Outcome.Success(Unit).also { committed = page }
            }
        }
    }
    private class Session(val disk: Disk = Disk()) : AppHostSession {
        lateinit var features: AppHostFeatures
        lateinit var library: LibraryFixture
        lateinit var settings: SettingsFixture
        val events = mutableListOf<String>()
        val native = mutableListOf<TestSession>()
        var preference: AppPreferences? = AppPreferences()
        var rootRestore: suspend (LibraryFixture) -> Outcome<FolderRef?> = { f -> f.navigation = trail(root); Outcome.Success(root) }
        var resolve: suspend (DocumentRef) -> Outcome<RegisteredDocument> = { document ->
            Outcome.Success(RegisteredDocument(document, pdfEntry.storage, DocumentRevision.Unknown(document), DocumentAvailability.UNKNOWN))
        }
        var accessError: AppError? = null
        var positionError: AppError? = null
        var routeCount = 0
        var opens = 0
        val registry = object : DocumentRegistry {
            override suspend fun resolve(document: DocumentRef): Outcome<RegisteredDocument> { events += "resolve"; return resolve.invoke(document) }
            override suspend fun register(document: DocumentRef, storage: StorageDocumentRef): Outcome<RegisteredDocument> = error("Never register on restore")
            override suspend fun findRegistration(storage: StorageDocumentRef): Outcome<RegisteredDocument> = error("Separate Library lookup double")
            override suspend fun rebind(document: DocumentRef, expected: StorageDocumentRef, replacement: StorageDocumentRef): Outcome<RegisteredDocument> = error("Never relocate")
            override suspend fun recordObservation(document: DocumentRef, expected: StorageDocumentRef, revision: DocumentRevision, availability: DocumentAvailability): Outcome<Unit> = error("Never erase/change availability")
        }
        override fun load(scope: CoroutineScope): AppHostFeatures {
            library = LibraryFixture(scope).also { f ->
                f.restore = { events += "root"; rootRestore(f) }
                f.list = { Outcome.Success(DocumentListing(listOf(pdfEntry), true)) }
            }
            settings = SettingsFixture(scope, preference)
            val access = object : DocumentReadAccess {
                override suspend fun openRead(document: StorageDocumentRef): Outcome<ReadHandle> {
                    events += "read"
                    accessError?.let { return Outcome.Failure(it) }
                    return Outcome.Success(ReadHandle(object : ReadResource {
                        override val descriptor: ParcelFileDescriptor get() = error("JVM no native descriptor")
                        override fun close() { events += "input-close" }
                    }, ReadProvenance.Provider(document), true))
                }
                override suspend fun openSeekable(document: StorageDocumentRef, limits: ReadAccessLimits) = openRead(document)
            }
            val engine = object : PdfReadEngine {
                override suspend fun open(source: PdfReadSource): Outcome<PdfReadSession> {
                    opens++; source.takeHandle().close()
                    return Outcome.Success(TestSession("new-$opens", count = 6).also { native += it })
                }
            }
            val positions = object : ReaderPositionRepository by disk.positions {
                override suspend fun restore(document: DocumentRef, pageCount: Int): Outcome<PageIndex?> =
                    positionError?.let { Outcome.Failure(it) } ?: disk.positions.restore(document, pageCount)
            }
            val reading = ReaderHostLifetime(scope, {
                routeCount++
                ReaderRouteOwner(it, registry, access, engine, positions, ReadAccessLimits(1024))
            }, ceiling, { ampleHeap }, { confirmedReaderFit(settings.state.preferences) })
            val navigationEvent = library.event
            library.event = { if (it is LibraryEvent.Open) reading.accept(it.request) else navigationEvent(it) }
            return AppHostFeatures(library.navigator, library.controller, settings.controller, reader = reading).also { features = it }
        }
        override suspend fun retireReader() = features.reader!!.terminate()
        override fun stop() { library.close(); settings.controller.close(); events += "stop" }
        override fun close() { events += "graph-close" }
        val entry get() = features.reader!!.state.value.entry!!
    }
    private fun TestScope.host(s: Session, saved: String? = AppRestoration.Reader(s.disk.document).encode(), errors: MutableList<Throwable> = mutableListOf()) =
        AppHostViewModel({ s }, StandardTestDispatcher(testScheduler), StandardTestDispatcher(testScheduler), { false }, errors::add, saved)
    private fun TestScope.present(s: Session, page: Int) {
        s.entry.controller.dispatch(ReaderAction.Viewport(ReaderViewport(600, 900))); runCurrent()
        s.entry.controller.dispatch(ReaderAction.Page(page)); runCurrent()
        val consumer = s.entry.controller.state.value.frame!!.attach()!!
        s.entry.controller.dispatch(ReaderAction.Presented(consumer)); runCurrent(); consumer.detached()
    }
    @Test fun noSavedStateFreshLaunchEntersLibraryAndNeverOpensReader() = runTest {
        val s = Session(); val h = host(s, null); runCurrent()
        assertEquals(AppDestination.LIBRARY, h.navigation.value.destination); assertEquals(0, s.opens)
        assertEquals(1, s.library.restores); h.dispose().await()
    }
    @Test fun savedSettingsUsesNewControllersAndActualPreferences() = runTest {
        val disk = Disk(); val first = Session(disk); val a = host(first, "1:settings"); runCurrent()
        val saved = a.saveNavigation(); a.dispose().await()
        val second = Session(disk); val b = host(second, saved); runCurrent()
        assertNotEquals(a.lifetimeId, b.lifetimeId); assertNotSame(first.settings.controller, second.settings.controller)
        assertNotSame(first.library.controller, second.library.controller)
        assertEquals(AppDestination.SETTINGS, b.navigation.value.destination); assertEquals(0, second.opens); b.dispose().await()
    }
    @Test fun validRegisteredStateCreatesOneFreshIntentAndResolvesBeforeRead() = runTest {
        val s = Session(); val h = host(s); runCurrent()
        assertEquals(AppDestination.READER, h.navigation.value.destination)
        assertEquals(DocumentOpenRequest.Registered(s.disk.document), s.entry.intent.request)
        assertEquals(1L, s.entry.intent.sequence); assertEquals(1, s.routeCount); assertEquals(1, s.opens)
        assertTrue(s.events.indexOf("resolve") < s.events.indexOf("read")); assertNull(s.entry.controller.state.value.frame)
        repeat(3) { h.start(); h.activityCreated("activity"); h.activityStarted("activity") }; runCurrent()
        assertEquals(1, s.opens); h.dispose().await()
    }
    @Test fun configurationRecreationKeepsOwnerControllerIntentAndNestedNavigation() = runTest {
        val s = Session(); val h = host(s, null); h.activityCreated("old"); h.activityStarted("old"); runCurrent()
        s.library.list = { folder -> Outcome.Success(DocumentListing(if (folder == root) listOf(entry(), pdfEntry) else listOf(pdfEntry.copy(folder = folder)), true)) }
        h.library(LibraryAction.Refresh, 0); runCurrent(); s.library.enter(); runCurrent(); val nested = s.library.navigation
        assertEquals(2, nested!!.breadcrumbs.size)
        h.activityStopped("old", true); h.activityCreated("new"); h.activityStarted("new"); h.start(); runCurrent()
        assertSame(nested, s.library.navigation); assertEquals(1, s.library.restores)
        h.library(LibraryAction.Open(s.library.pdf.key), 0); runCurrent()
        val old = s.entry
        h.activityStopped("new", true); h.activityCreated("replacement"); h.activityStarted("replacement"); runCurrent()
        assertSame(old, s.entry); assertEquals(1, s.opens); h.dispose().await()
    }
    @Test fun processReconstructionNeverSharesControllerOrNativeSession() = runTest {
        val disk = Disk(); val first = Session(disk); val a = host(first); runCurrent(); val old = first.entry
        val saved = a.saveNavigation(); a.dispose().await()
        val second = Session(disk); val b = host(second, saved); runCurrent()
        assertNotSame(old.controller, second.entry.controller); assertNotSame(first.native.single(), second.native.single())
        assertEquals(1, second.opens); assertEquals(1L, second.entry.intent.sequence); assertNull(second.entry.controller.state.value.frame)
        b.dispose().await()
    }
    @Test fun transientSnapshotDowngradesToLibraryWithoutWritingPositions() = runTest {
        val s = Session(); val h = host(s, null); runCurrent(); h.library(LibraryAction.Open(s.library.pdf.key), 0); runCurrent()
        assertTrue(s.entry.intent.request is DocumentOpenRequest.Transient)
        assertEquals("1:library", h.saveNavigation()); present(s, 2); h.dispose().await()
        assertEquals(0, s.disk.restores); assertEquals(0, s.disk.saves)
        val next = Session(s.disk); val b = host(next, "1:library"); runCurrent(); assertEquals(0, next.opens); b.dispose().await()
    }
    @Test fun backDuringRootRestorePreventsLateSavedRoutePublication() = runTest {
        val gate = CompletableDeferred<Unit>(); val s = Session().also { it.rootRestore = { f -> gate.await(); f.navigation = trail(root); Outcome.Success(root) } }
        val h = host(s); runCurrent(); h.back(0); gate.complete(Unit); runCurrent()
        assertEquals(AppDestination.LIBRARY, h.navigation.value.destination); assertEquals(0, s.opens); assertEquals("1:library", h.saveNavigation()); h.dispose().await()
    }
    @Test fun settingsNavigationSupersedesPendingRegisteredRestoration() = runTest {
        val gate = CompletableDeferred<Unit>(); val s = Session().also { it.rootRestore = { f -> gate.await(); f.navigation = trail(root); Outcome.Success(root) } }
        val h = host(s); runCurrent(); h.showSettings(0); gate.complete(Unit); runCurrent()
        assertEquals(AppDestination.SETTINGS, h.navigation.value.destination); assertEquals(0, s.opens); h.dispose().await()
    }
    @Test fun libraryCommandSupersedesRestoreEvenBeforePreferencesArrive() = runTest {
        val s = Session().also { it.preference = null }; val h = host(s); runCurrent()
        h.library(LibraryAction.Open(s.library.pdf.key), 0); runCurrent() // Fit Loading rejects this user attempt; saved intent must still be canceled.
        s.settings.values.tryEmit(Observation.Ready(AppPreferences())); runCurrent()
        assertEquals(0, s.opens); assertEquals("1:library", h.saveNavigation()); h.dispose().await()
    }
    @Test fun backgroundBeforeBarrierDefersUntilStartAndDoesNotDuplicate() = runTest {
        val s = Session(); val h = host(s); h.activityCreated("activity"); h.activityStopped("activity", false); runCurrent()
        assertEquals(0, s.opens); assertEquals(AppDestination.LIBRARY, h.navigation.value.destination)
        repeat(3) { h.activityStarted("activity"); h.start() }; runCurrent()
        assertEquals(1, s.opens); assertEquals(1, s.routeCount); h.dispose().await()
    }
    @Test fun startWhileRootIsPendingCannotBypassInitializationBarrier() = runTest {
        val gate = CompletableDeferred<Unit>(); val s = Session().also { it.rootRestore = { f -> gate.await(); f.navigation = trail(root); Outcome.Success(root) } }
        val h = host(s); h.activityCreated("activity"); runCurrent(); h.activityStarted("activity"); runCurrent()
        assertEquals(0, s.opens); gate.complete(Unit); runCurrent(); assertEquals(1, s.opens); h.dispose().await()
    }
    @Test fun pendingMemoryPressureCancelsAutomaticReaderAdmission() = runTest {
        for (level in listOf(android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW, android.content.ComponentCallbacks2.TRIM_MEMORY_COMPLETE)) {
            val s = Session().also { it.preference = null }; val h = host(s); h.activityCreated("activity"); runCurrent()
            h.activityMemory("activity", level); s.settings.values.tryEmit(Observation.Ready(AppPreferences())); runCurrent()
            assertEquals(0, s.opens); assertEquals("1:library", h.saveNavigation()); h.dispose().await()
        }
    }
    @Test fun rootFailurePreservesRecoveryAndNeverStartsRegisteredRead() = runTest {
        for (error in listOf(AppError.Access.PERMISSION_REVOKED, AppError.Access.PROVIDER_UNAVAILABLE)) {
            val s = Session().also { it.rootRestore = { Outcome.Failure(error) } }; val h = host(s); runCurrent()
            assertEquals(error, (s.library.state.location as LibraryLocation.Failed).failure.error)
            assertEquals(0, s.opens); assertEquals(AppDestination.LIBRARY, h.navigation.value.destination); h.dispose().await()
        }
    }
    @Test fun missingRootSafelyDiscardsReaderButDoesNotLaunchPicker() = runTest {
        val s = Session().also { it.rootRestore = { Outcome.Success(null) } }; val h = host(s); runCurrent()
        assertEquals(LibraryLocation.NoRoot, s.library.state.location); assertEquals(0, s.opens); assertTrue(s.library.events.isEmpty()); h.dispose().await()
    }
    @Test fun failedPreferenceReadDoesNotSubstituteDefaultFit() = runTest {
        val s = Session().also { it.preference = null }; val h = host(s); runCurrent()
        s.settings.values.tryEmit(Observation.Failed(AppError.Access.PRIVATE_STORAGE_UNAVAILABLE)); runCurrent()
        assertEquals(0, s.opens); assertEquals("1:library", h.saveNavigation()); h.dispose().await()
    }
    @Test fun missingRegistrationNeverReachesProviderOrFabricatesTransient() = runTest {
        val s = Session().also { it.resolve = { Outcome.Failure(AppError.Access.NOT_REGISTERED) } }; val h = host(s); runCurrent()
        assertEquals(AppError.Access.NOT_REGISTERED, s.entry.controller.state.value.failure!!.error)
        assertFalse("read" in s.events); assertEquals("1:library", h.saveNavigation())
        h.back(1); runCurrent(); assertEquals(AppDestination.LIBRARY, h.navigation.value.destination); h.dispose().await()
    }
    @Test fun revokedAndUnavailableAccessRemainTypedWithoutRegistryMutations() = runTest {
        for (error in listOf(AppError.Access.PERMISSION_REVOKED, AppError.Access.PROVIDER_UNAVAILABLE)) {
            val s = Session().also { it.accessError = error }; val h = host(s); runCurrent()
            assertEquals(error, s.entry.controller.state.value.failure!!.error); assertEquals(0, s.opens)
            h.back(1); runCurrent(); assertEquals(AppDestination.LIBRARY, h.navigation.value.destination); h.dispose().await()
        }
    }
    @Test fun previousCommittedPositionsIncludingClampUseRealRouteAndNeverSaveOnOpen() = runTest {
        for ((saved, expected) in listOf(null to 0, 0 to 0, 3 to 3, 99 to 5)) {
            val s = Session(Disk().also { it.committed = saved?.let(::PageIndex) }); val h = host(s); runCurrent()
            assertEquals(PageIndex(expected), s.entry.controller.state.value.requestedPage)
            assertEquals(1, s.disk.restores); assertEquals(0, s.disk.saves); h.dispose().await(); assertEquals(0, s.disk.saves)
        }
    }
    @Test fun requestedAndRenderedPagesNeverBecomeDurableDuringReconstruction() = runTest {
        val disk = Disk().also { it.committed = PageIndex(1) }; val s = Session(disk); val h = host(s); runCurrent()
        s.entry.controller.dispatch(ReaderAction.Viewport(ReaderViewport(600, 900))); s.entry.controller.dispatch(ReaderAction.Page(4)); runCurrent()
        assertNotNull(s.entry.controller.state.value.frame); assertNull(s.entry.controller.state.value.presentedPage)
        val saved = h.saveNavigation(); h.dispose().await(); assertEquals(PageIndex(1), disk.committed)
        val next = Session(disk); val b = host(next, saved); runCurrent(); assertEquals(PageIndex(1), next.entry.controller.state.value.requestedPage); b.dispose().await()
    }
    @Test fun pendingPresentationIsUnconfirmedAndReconstructionReadsEarlierCommittedPage() = runTest {
        val disk = Disk().also { it.committed = PageIndex(1) }; val s = Session(disk); val h = host(s); runCurrent()
        present(s, 4); assertEquals(PageIndex(4), s.entry.controller.state.value.presentedPage)
        assertEquals(PageIndex(1), disk.committed); assertEquals(0, disk.saves)
        // A separate new owner reads actual storage before the old scheduled write commits.
        // This is deterministic interrupted-write simulation, not system process death.
        val next = Session(disk); val b = host(next, h.saveNavigation()); runCurrent()
        assertEquals(PageIndex(1), next.entry.controller.state.value.requestedPage)
        h.dispose().await(); b.dispose().await()
    }
    @Test fun failedFlushNeverInventsNewCommittedPageAndNewOwnerReadsActualStorage() = runTest {
        val disk = Disk().also { it.committed = PageIndex(1); it.writeError = AppError.Access.PRIVATE_STORAGE_UNAVAILABLE }
        val s = Session(disk); val errors = mutableListOf<Throwable>(); val h = host(s, errors = errors); runCurrent(); present(s, 4)
        h.activityCreated("a"); h.activityStopped("a", false); runCurrent()
        s.features.reader!!.state.first { it.report.failures.isNotEmpty() }
        assertEquals(PageIndex(1), disk.committed); assertTrue(errors.single() is ReaderExitException)
        val saved = h.saveNavigation(); h.dispose().await(); disk.writeError = null
        val next = Session(disk); val b = host(next, saved); runCurrent(); assertEquals(PageIndex(1), next.entry.controller.state.value.requestedPage); b.dispose().await()
    }
    @Test fun successfulBackgroundFlushIsRestoredByNewOwnerWithoutAnotherPositionStore() = runTest {
        val disk = Disk(); val s = Session(disk); val h = host(s); runCurrent(); present(s, 3)
        h.activityCreated("a"); h.activityStopped("a", false); runCurrent(); assertEquals(PageIndex(3), disk.committed)
        val saved = h.saveNavigation(); h.dispose().await()
        val next = Session(disk); val b = host(next, saved); runCurrent(); assertEquals(PageIndex(3), next.entry.controller.state.value.requestedPage); b.dispose().await()
    }
    @Test fun lateRegistryCompletionCannotPublishAfterBackOrCloseGraphEarly() = runTest {
        val gate = CompletableDeferred<Unit>(); val s = Session(); val normal = s.resolve
        s.resolve = { withContext(NonCancellable) { gate.await(); normal(it) } }
        val h = host(s); runCurrent(); val old = s.entry; h.back(1); val done = h.dispose(); runCurrent()
        assertFalse(done.isCompleted); assertFalse("graph-close" in s.events)
        gate.complete(Unit); runCurrent(); done.await()
        // Noncooperative registry may complete a native open, but the real route closes its late session.
        assertEquals(1, s.native.single().closes)
        assertEquals(ReaderPhase.RETIRED, old.controller.state.value.phase)
        assertEquals(AppHostState.Closed(null), h.state.value)
    }
    @Test fun terminalWhileBarrierPendingCannotPublishEvenWithLateRootResult() = runTest {
        val gate = CompletableDeferred<Unit>(); val s = Session().also { it.rootRestore = { f -> withContext(NonCancellable) { gate.await(); f.navigation = trail(root); Outcome.Success(root) } } }
        val h = host(s); runCurrent(); val done = h.dispose(); runCurrent(); assertFalse(done.isCompleted)
        assertEquals("1:library", h.saveNavigation()); gate.complete(Unit); runCurrent(); done.await(); assertEquals(0, s.opens)
    }
    @Test fun backSnapshotCannotReopenObsoleteReaderWhileCleanupIsPending() = runTest {
        val s = Session(); val h = host(s); runCurrent(); h.back(1)
        assertEquals("1:library", h.saveNavigation()); runCurrent(); assertEquals("1:library", h.saveNavigation()); h.dispose().await()
    }
    @Test fun positionReadFailureClosesUnretainedNativeSessionAndPreservesTypedError() = runTest {
        val s = Session().also { it.positionError = AppError.Access.PRIVATE_STORAGE_UNAVAILABLE }; val h = host(s); runCurrent()
        val failed = s.entry.controller.state.first { it.failure != null }
        assertEquals(AppError.Access.PRIVATE_STORAGE_UNAVAILABLE, failed.failure!!.error)
        assertEquals(1, s.native.single().closes); h.dispose().await()
    }
    @Test fun directRestorationPermitCannotBypassOneShotHostAuthorizationOrTerminalAdmission() = runTest {
        val s = Session(); val h = host(s, null); runCurrent()
        assertEquals(Outcome.Failure(AppError.Conflict.STALE_SESSION), s.features.reader!!.restore(s.disk.document, 0))
        assertEquals(0, s.opens); h.dispose().await()
        assertEquals(Outcome.Failure(AppError.Conflict.STALE_SESSION), s.features.reader!!.restore(s.disk.document, 0))
    }
    @Test fun alreadyActiveReaderRejectsAnotherRestorationIntent() = runTest {
        val s = Session(); val h = host(s); runCurrent()
        assertEquals(Outcome.Failure(AppError.Conflict.STALE_SESSION), s.features.reader!!.restore(s.disk.document, 1))
        assertEquals(1, s.opens); h.dispose().await()
    }
    @Test fun newerWriterRejectsLateOldCheckpointAfterReconstruction() = runTest {
        val disk = Disk().also { it.committed = PageIndex(1) }
        val old = Session(disk); val a = host(old); runCurrent(); present(old, 4)
        val next = Session(disk); val b = host(next, a.saveNavigation()); runCurrent()
        assertEquals(PageIndex(1), next.entry.controller.state.value.requestedPage)
        assertTrue(a.dispose().await() is ReaderExitException)
        assertEquals(PageIndex(1), disk.committed)
        present(next, 2); b.dispose().await(); assertEquals(PageIndex(2), disk.committed)
    }
    @Test fun newerUserOpeningReplacesCanceledSavedReaderIdentityAndSnapshot() = runTest {
        val s = Session().also { it.preference = null }; val h = host(s); runCurrent()
        h.showSettings(0); h.back(1)
        s.settings.values.tryEmit(Observation.Ready(AppPreferences())); runCurrent()
        h.library(LibraryAction.Register(s.library.pdf.key), 2); runCurrent()
        h.library(LibraryAction.Open(s.library.pdf.key), 2); runCurrent()
        val actual = (s.entry.intent.request as DocumentOpenRequest.Registered).document
        assertNotEquals(s.disk.document, actual)
        assertEquals(AppRestoration.Reader(actual).encode(), h.saveNavigation()); assertEquals(1, s.opens)
        h.dispose().await()
    }

    @Test fun programmingDefectInRegistryRemainsOriginalAndTerminatesOwnedHost() = runTest {
        val defect = IllegalStateException("registry defect"); val s = Session().also { it.resolve = { throw defect } }
        val errors = mutableListOf<Throwable>(); val h = host(s, errors = errors); runCurrent()
        val failure = h.dispose().await()!!
        assertSame(defect, generateSequence(failure) { it.cause }.last())
        assertTrue(errors.any { generateSequence(it) { cause -> cause.cause }.any { cause -> cause === defect } })
    }
}
