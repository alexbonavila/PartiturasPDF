package com.abdev.partituraspdf.data.metadata

import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.abdev.partituraspdf.core.document.*
import com.abdev.partituraspdf.core.result.AppError
import com.abdev.partituraspdf.core.result.Observation
import com.abdev.partituraspdf.core.result.Outcome
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

/** Real Room/SQLite tests. Every JUnit method explicitly returns Unit/void. */
@RunWith(AndroidJUnit4::class)
class RoomDocumentMetadataRepositoryTest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var db: MetadataDatabase
    private val repository get() = RoomDocumentMetadataRepository(db)
    private val registry get() = RoomDocumentRegistry(db)
    private val dao get() = db.documents()
    private val document = DocumentRef(DocumentId("logical-c03"))
    private val other = DocumentRef(DocumentId("other-c03"))
    private val location = StorageDocumentRef("provider", "entry")
    private val moved = StorageDocumentRef("provider", "moved")
    private val unknown = DocumentRevision.Unknown(document)
    private val snapshot = DocumentRevision.VerifiedSnapshot(document, SnapshotEvidence("a".repeat(64), 42))
    private val nextSnapshot = DocumentRevision.VerifiedSnapshot(document, SnapshotEvidence("b".repeat(64), 43))
    private val position = ReadingPosition(PageIndex(7), snapshot)
    private val success = Outcome.Success(Unit)
    private val missing = Outcome.Failure(AppError.Access.NOT_REGISTERED)
    private val stale = Outcome.Failure(AppError.Conflict.REVISION_CHANGED)

    @Before fun open() { db = Room.inMemoryDatabaseBuilder(context, MetadataDatabase::class.java).build() }
    @After fun close() { db.close() }

    @Test fun coldFlowStartsLoadingAndEachCollectorReadsItsOwnCurrentState() {
        runBlocking {
            val flow = repository.observe(document)
            // The Flow was constructed before registration. It must query only on collection.
            registry.register(document, location)
            assertEquals(listOf(Observation.Loading, Observation.Ready(DocumentMetadata())), flow.take(2).toList())
            assertEquals(success, repository.setFavorite(document, true))
            assertEquals(listOf(Observation.Loading, Observation.Ready(DocumentMetadata(true))), flow.take(2).toList())
        }
    }

    @Test fun missingObservationRemainsLiveAndBecomesReadyAfterRegistration() {
        runBlocking {
            val states = Channel<Observation<DocumentMetadata>>(Channel.UNLIMITED)
            val collector = launch { repository.observe(document).distinctUntilChanged().collect { states.send(it) } }
            try {
                assertEquals(Observation.Loading, next(states))
                assertEquals(Observation.Failed(AppError.Access.NOT_REGISTERED), next(states))
                registry.register(document, location)
                assertEquals(Observation.Ready(DocumentMetadata()), next(states))
            } finally { collector.cancelAndJoin(); states.close() }
        }
    }

    @Test fun missingMutationsDoNotCreateMetadataOrMisreportRevisionConflicts() {
        runBlocking {
            assertEquals(missing, repository.setFavorite(document, true))
            assertEquals(missing, repository.setReadingPosition(document, ReadingPosition(PageIndex(0), unknown)))
            assertEquals(missing, repository.setReadingPosition(document, position))
            assertEquals(missing, repository.setReadingPosition(document, null))
            assertNull(dao.registry(document.id.value))
            assertNull(dao.metadata(document.id.value))
        }
    }

    @Test fun favoriteOnlyUpdatesFavoriteAndPreservesPositionAndRegistration() {
        runBlocking {
            seed()
            val before = registry.resolve(document)
            assertEquals(success, repository.setFavorite(document, false))
            assertEquals(DocumentMetadata(false, position), ready())
            assertEquals(before, registry.resolve(document))
            assertEquals(success, repository.setFavorite(document, false))
            assertEquals(DocumentMetadata(false, position), ready())
        }
    }

    @Test fun unknownPositionOnUnknownRegistryIsOnlyAResumeHint() {
        runBlocking {
            registry.register(document, location)
            repository.setFavorite(document, true)
            val hint = ReadingPosition(PageIndex(0), unknown)
            assertEquals(success, repository.setReadingPosition(document, hint))
            assertEquals(DocumentMetadata(true, hint), ready())
            assertEquals("unknown", dao.metadata(document.id.value)?.readingRevisionType)
            assertNull(dao.metadata(document.id.value)?.readingSha256)
            assertNull(dao.metadata(document.id.value)?.readingByteCount)
        }
    }

    @Test fun unknownHintOnVerifiedRegistryIsNotPromotedToVerifiedEvidence() {
        runBlocking {
            seed()
            val before = registry.resolve(document)
            val hint = ReadingPosition(PageIndex(3), unknown)
            assertEquals(success, repository.setReadingPosition(document, hint))
            assertEquals(DocumentMetadata(true, hint), ready())
            assertEquals(before, registry.resolve(document))
            val row = requireNotNull(dao.metadata(document.id.value))
            assertEquals("unknown", row.readingRevisionType)
            assertNull(row.readingSha256)
            assertNull(row.readingByteCount)
        }
    }

    @Test fun verifiedPositionRequiresExactRegistryTypeDigestAndLength() {
        runBlocking {
            registry.register(document, location)
            assertEquals(stale, repository.setReadingPosition(document, position))
            assertEquals(DocumentMetadata(), ready())
            registry.recordObservation(document, location, snapshot, DocumentAvailability.AVAILABLE)
            assertEquals(success, repository.setReadingPosition(document, position))
            val before = ready()
            for (revision in listOf(
                DocumentRevision.VerifiedSnapshot(document, SnapshotEvidence("b".repeat(64), 42)),
                DocumentRevision.VerifiedSnapshot(document, SnapshotEvidence("a".repeat(64), 43)),
                unknown,
            )) {
                registry.recordObservation(document, location, revision, DocumentAvailability.AVAILABLE)
                assertEquals(stale, repository.setReadingPosition(document, position.copy(page = PageIndex(9))))
                assertEquals(before, ready())
            }
        }
    }

    @Test fun foreignRevisionOwnershipPropagatesBeforePersistence() {
        runBlocking {
            seed()
            val before = ready()
            for (revision in listOf(DocumentRevision.Unknown(other),
                DocumentRevision.VerifiedSnapshot(other, SnapshotEvidence("a".repeat(64), 42)))) {
                assertThrows(IllegalArgumentException::class.java) {
                    runBlocking { repository.setReadingPosition(document, ReadingPosition(PageIndex(0), revision)) }
                }
                assertEquals(before, ready())
            }
        }
    }

    @Test fun clearingUsesCurrentRegistryRevisionEvenWhenHistoricalPositionIsStale() {
        runBlocking {
            seed()
            registry.recordObservation(document, location, nextSnapshot, DocumentAvailability.PERMISSION_REVOKED)
            val before = registry.resolve(document)
            assertEquals(success, repository.setReadingPosition(document, null))
            assertEquals(DocumentMetadata(true), ready())
            val row = requireNotNull(dao.metadata(document.id.value))
            assertNull(row.readingPage)
            assertNull(row.readingRevisionType)
            assertNull(row.readingSha256)
            assertNull(row.readingByteCount)
            assertEquals(before, registry.resolve(document))
            assertEquals(success, repository.setReadingPosition(document, null))
        }
    }

    @Test fun rebindPreservesHistoricalMetadataUntilExplicitClear() {
        runBlocking {
            seed()
            assertTrue(registry.rebind(document, location, moved) is Outcome.Success)
            assertEquals(DocumentMetadata(true, position), ready())
            assertEquals(success, repository.setFavorite(document, false))
            assertEquals(DocumentMetadata(false, position), ready())
            assertEquals(stale, repository.setReadingPosition(document, position.copy(page = PageIndex(9))))
            assertEquals(success, repository.setReadingPosition(document, null))
            assertEquals(DocumentMetadata(false), ready())
            val registration = (registry.resolve(document) as Outcome.Success).value
            assertEquals(moved, registration.storage)
            assertEquals(unknown, registration.revision)
            assertEquals(DocumentAvailability.UNKNOWN, registration.availability)
        }
    }

    @Test fun independentConcurrentFavoriteAndPositionUpdatesPreserveBothFields() {
        runBlocking {
            registry.register(document, location)
            val hint = ReadingPosition(PageIndex(5), unknown)
            val gate = CompletableDeferred<Unit>()
            val favorite = async(Dispatchers.IO) { gate.await(); repository.setFavorite(document, true) }
            val reading = async(Dispatchers.IO) { gate.await(); repository.setReadingPosition(document, hint) }
            gate.complete(Unit)
            assertEquals(listOf(success, success), awaitAll(favorite, reading))
            assertEquals(DocumentMetadata(true, hint), ready())
        }
    }

    @Test fun queuedVerifiedPositionDetectsARevisionCommittedByAnotherWriter() {
        runBlocking {
            seed()
            val writerHasChanged = CompletableDeferred<Unit>()
            val releaseWriter = CompletableDeferred<Unit>()
            val writer = launch(Dispatchers.IO) {
                db.withTransaction {
                    registry.recordObservation(document, location, nextSnapshot, DocumentAvailability.AVAILABLE)
                    writerHasChanged.complete(Unit)
                    releaseWriter.await()
                }
            }
            try {
                withTimeout(30_000) { writerHasChanged.await() }
                val attempted = CompletableDeferred<Unit>()
                val queued = async(Dispatchers.IO) {
                    attempted.complete(Unit)
                    repository.setReadingPosition(document, position.copy(page = PageIndex(9)))
                }
                withTimeout(30_000) { attempted.await() }
                releaseWriter.complete(Unit)
                writer.join()
                assertEquals(stale, queued.await())
                assertEquals(DocumentMetadata(true, position), ready())
            } finally { releaseWriter.complete(Unit); writer.cancelAndJoin() }
        }
    }

    @Test fun queuedClearUsesTheRevisionCommittedBeforeItsOwnTransaction() {
        runBlocking {
            seed()
            val changed = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            val writer = launch(Dispatchers.IO) {
                db.withTransaction {
                    registry.recordObservation(document, location, nextSnapshot, DocumentAvailability.DELETED)
                    changed.complete(Unit)
                    release.await()
                }
            }
            try {
                withTimeout(30_000) { changed.await() }
                val attempted = CompletableDeferred<Unit>()
                val clear = async(Dispatchers.IO) { attempted.complete(Unit); repository.setReadingPosition(document, null) }
                withTimeout(30_000) { attempted.await() }
                release.complete(Unit)
                writer.join()
                assertEquals(success, clear.await())
                assertEquals(DocumentMetadata(true), ready())
                assertEquals(nextSnapshot, (registry.resolve(document) as Outcome.Success).value.revision)
            } finally { release.complete(Unit); writer.cancelAndJoin() }
        }
    }

    @Test fun multipleLiveCollectorsObserveUpdatesAndCancelIndependently() {
        runBlocking {
            registry.register(document, location)
            val flow = repository.observe(document)
            val first = Channel<Observation<DocumentMetadata>>(Channel.UNLIMITED)
            val second = Channel<Observation<DocumentMetadata>>(Channel.UNLIMITED)
            val firstCollector = launch { flow.distinctUntilChanged().collect { first.send(it) } }
            val secondCollector = launch { flow.distinctUntilChanged().collect { second.send(it) } }
            try {
                for (states in listOf(first, second)) {
                    assertEquals(Observation.Loading, next(states))
                    assertEquals(Observation.Ready(DocumentMetadata()), next(states))
                }
                repository.setFavorite(document, true)
                for (states in listOf(first, second)) assertEquals(Observation.Ready(DocumentMetadata(true)), next(states))
                firstCollector.cancelAndJoin()
                val hint = ReadingPosition(PageIndex(3), unknown)
                repository.setReadingPosition(document, hint)
                assertEquals(Observation.Ready(DocumentMetadata(true, hint)), next(second))
            } finally { firstCollector.cancelAndJoin(); secondCollector.cancelAndJoin(); first.close(); second.close() }
        }
    }

    @Test fun failedMissingObservationDoesNotRetainStaleReadyAndCanRecover() {
        runBlocking {
            seed()
            val states = Channel<Observation<DocumentMetadata>>(Channel.UNLIMITED)
            val collector = launch { repository.observe(document).distinctUntilChanged().collect { states.send(it) } }
            try {
                assertEquals(Observation.Loading, next(states))
                assertEquals(Observation.Ready(DocumentMetadata(true, position)), next(states))
                db.openHelper.writableDatabase.execSQL("DELETE FROM document_registry WHERE documentId = 'logical-c03'")
                assertEquals(Observation.Failed(AppError.Access.NOT_REGISTERED), next(states))
                registry.register(document, location)
                assertEquals(Observation.Ready(DocumentMetadata()), next(states))
            } finally { collector.cancelAndJoin(); states.close() }
        }
    }

    @Test fun fileBackedCloseReopenPersistsFavoritePositionAndClearing() {
        runBlocking {
            val name = "metadata-repository-${UUID.randomUUID()}.db"
            try {
                db.close()
                db = MetadataDatabase.open(context, name)
                seed()
                db.close()
                db = MetadataDatabase.open(context, name)
                assertEquals(DocumentMetadata(true, position), ready())
                repository.setReadingPosition(document, null)
                db.close()
                db = MetadataDatabase.open(context, name)
                assertEquals(DocumentMetadata(true), ready())
            } finally { db.close(); context.deleteDatabase(name) }
        }
    }

    @Test fun corruptMetadataNeverBecomesReadyOrGetsSilentlyRepairedByWrites() {
        runBlocking {
            seed()
            db.openHelper.writableDatabase.execSQL("UPDATE document_metadata SET favorite = 4294967296 WHERE documentId = 'logical-c03'")
            val emitted = mutableListOf<Observation<DocumentMetadata>>()
            assertThrows(IllegalStateException::class.java) {
                runBlocking { repository.observe(document).collect { emitted.add(it) } }
            }
            assertEquals(listOf(Observation.Loading), emitted)
            assertThrows(IllegalStateException::class.java) { runBlocking { repository.setFavorite(document, false) } }
            assertThrows(IllegalStateException::class.java) { runBlocking { repository.setReadingPosition(document, null) } }
            assertEquals(4294967296L, dao.metadata(document.id.value)?.favorite)
        }
    }

    @Test fun missingMetadataForAnExistingRegistrationIsCorruptionNotNotRegistered() {
        runBlocking {
            registry.register(document, location)
            db.openHelper.writableDatabase.execSQL("DELETE FROM document_metadata WHERE documentId = 'logical-c03'")
            assertThrows(IllegalStateException::class.java) { runBlocking { ready() } }
            assertThrows(IllegalStateException::class.java) { runBlocking { repository.setFavorite(document, true) } }
            assertThrows(IllegalStateException::class.java) { runBlocking { repository.setReadingPosition(document, null) } }
            assertNotNull(dao.registry(document.id.value))
            assertNull(dao.metadata(document.id.value))
        }
    }

    @Test fun operationalFailureStartsLoadingThenFailedAndIsTypedForBothMutations() {
        runBlocking {
            val name = "metadata-unavailable-${UUID.randomUUID()}.db"
            val blockedPath = context.getDatabasePath(name)
            assertTrue(blockedPath.mkdirs())
            val marker = File(blockedPath, "test-owned-blocker")
            marker.writeText("Synthetic directory is not a SQLite database")
            try {
                db.close()
                db = MetadataDatabase.open(context, name)
                val flow = repository.observe(document)
                assertEquals(Observation.Loading, flow.first())
                val failed = Observation.Failed(AppError.Access.PRIVATE_STORAGE_UNAVAILABLE)
                assertEquals(listOf(Observation.Loading, failed), flow.toList())
                val failure = Outcome.Failure(AppError.Access.PRIVATE_STORAGE_UNAVAILABLE)
                assertEquals(failure, repository.setFavorite(document, true))
                assertEquals(failure, repository.setReadingPosition(document, null))
                assertTrue(marker.isFile)
            } finally { db.close(); marker.delete(); blockedPath.delete(); context.deleteDatabase(name) }
        }
    }

    @Test fun cancelledObservationDoesNotEmitATypedFailureOrCancelOtherCollectors() {
        runBlocking {
            registry.register(document, location)
            val states = Channel<Observation<DocumentMetadata>>(Channel.UNLIMITED)
            val cancelled = CompletableDeferred<Unit>()
            val collector = launch {
                try { repository.observe(document).collect { states.send(it) } }
                catch (_: CancellationException) { cancelled.complete(Unit) }
            }
            assertEquals(Observation.Loading, next(states))
            assertEquals(Observation.Ready(DocumentMetadata()), next(states))
            collector.cancelAndJoin()
            withTimeout(30_000) { cancelled.await() }
            assertTrue(states.tryReceive().isFailure)
            assertEquals(DocumentMetadata(), ready())
            states.close()
        }
    }

    @Test fun cancellationRollsBackBothMetadataFieldsInAnOuterTransaction() {
        runBlocking {
            registry.register(document, location)
            try {
                db.withTransaction {
                    assertEquals(success, repository.setFavorite(document, true))
                    assertEquals(success, repository.setReadingPosition(document, ReadingPosition(PageIndex(4), unknown)))
                    throw CancellationException("test transaction rollback")
                }
                fail("Cancellation must propagate")
            } catch (_: CancellationException) { /* Expected, not an Outcome. */ }
            assertEquals(DocumentMetadata(), ready())
        }
    }

    @Test fun cancelledQueuedMetadataWritesDoNotReturnOutcomesOrChangeFields() {
        runBlocking {
            registry.register(document, location)
            val holding = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            val holder = launch(Dispatchers.IO) { db.withTransaction { holding.complete(Unit); release.await() } }
            try {
                withTimeout(30_000) { holding.await() }
                for (operation in listOf<suspend () -> Outcome<Unit>>(
                    { repository.setFavorite(document, true) },
                    { repository.setReadingPosition(document, ReadingPosition(PageIndex(5), unknown)) },
                )) {
                    val attempted = CompletableDeferred<Unit>()
                    val cancelled = CompletableDeferred<Unit>()
                    val pending = launch(Dispatchers.IO) {
                        try { attempted.complete(Unit); operation(); fail("Cancelled write returned an Outcome") }
                        catch (_: CancellationException) { cancelled.complete(Unit) }
                    }
                    withTimeout(30_000) { attempted.await() }
                    pending.cancelAndJoin()
                    withTimeout(30_000) { cancelled.await() }
                }
            } finally { release.complete(Unit); holder.cancelAndJoin() }
            assertEquals(DocumentMetadata(), ready())
        }
    }

    private suspend fun seed() {
        assertTrue(registry.register(document, location) is Outcome.Success)
        assertEquals(success, registry.recordObservation(document, location, snapshot, DocumentAvailability.AVAILABLE))
        assertEquals(success, repository.setFavorite(document, true))
        assertEquals(success, repository.setReadingPosition(document, position))
    }

    private suspend fun ready(): DocumentMetadata = withTimeout(30_000) {
        val state = repository.observe(document).first { it is Observation.Ready }
        (state as Observation.Ready).value
    }

    private suspend fun next(states: Channel<Observation<DocumentMetadata>>): Observation<DocumentMetadata> =
        withTimeout(30_000) { states.receive() }
}
