package com.abdev.partituraspdf.data.metadata

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.abdev.partituraspdf.core.document.*
import com.abdev.partituraspdf.core.result.AppError
import com.abdev.partituraspdf.core.result.Outcome
import kotlinx.coroutines.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

/** All persistence and concurrency assertions use real Room/SQLite, never DAO mocks. */
@RunWith(AndroidJUnit4::class)
class RoomDocumentRegistryTest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var db: MetadataDatabase
    private val registry get() = RoomDocumentRegistry(db)
    private val dao get() = db.documents()
    private val document = DocumentRef(DocumentId(" App-issued ID "))
    private val other = DocumentRef(DocumentId("other-id"))
    private val location = StorageDocumentRef("opaque/provider", "opaque:locator")
    private val moved = StorageDocumentRef("opaque/provider", "new:locator")
    private val unknown = DocumentRevision.Unknown(document)
    private val snapshot = DocumentRevision.VerifiedSnapshot(document, SnapshotEvidence("a".repeat(64), 42))
    private val privateFailure = Outcome.Failure(AppError.Access.PRIVATE_STORAGE_UNAVAILABLE)
    private val conflict = Outcome.Failure(AppError.Conflict.REGISTRATION_CHANGED)
    private val missing = Outcome.Failure(AppError.Access.NOT_REGISTERED)

    @Before fun open() { db = Room.inMemoryDatabaseBuilder(context, MetadataDatabase::class.java).build() }
    @After fun close() { db.close() }

    @Test fun registersCallerIdentityAndBothDurableDefaults() {
        runBlocking {
            val value = success(registry.register(document, location))
            assertEquals(RegisteredDocument(document, location, unknown, DocumentAvailability.UNKNOWN), value)
            assertEquals(value, success(registry.resolve(document)))
            assertEquals(DocumentMetadata(), metadata())
            assertNull(dao.registry(location.value))
        }
    }

    @Test fun duplicateIdentityDoesNotReplaceLocationObservationsOrMetadata() {
        runBlocking {
            seedObservedMetadata()
            val before = success(registry.resolve(document))
            val beforeMetadata = metadata()
            assertEquals(conflict, registry.register(document, moved))
            assertEquals(before, success(registry.resolve(document)))
            assertEquals(beforeMetadata, metadata())
            assertEquals(missing, registry.findRegistration(moved))
        }
    }

    @Test fun duplicateProviderLocationDoesNotCreateAnOrphanOrChangeIdentity() {
        runBlocking {
            registry.register(document, location)
            assertEquals(conflict, registry.register(other, location))
            assertEquals(missing, registry.resolve(other))
            assertNull(dao.metadata(other.id.value))
            assertEquals(document, success(registry.findRegistration(location)).document)
            val differentNamespace = StorageDocumentRef("different-provider", location.value)
            assertEquals(other, success(registry.register(other, differentNamespace)).document)
        }
    }

    @Test fun nonUniqueConstraintFailurePropagatesAndRollsBackBothTables() {
        runBlocking {
            db.openHelper.writableDatabase.execSQL("""CREATE TEMP TRIGGER reject_metadata BEFORE INSERT ON document_metadata
                BEGIN SELECT RAISE(ABORT, 'test metadata constraint'); END""")
            assertThrows(SQLiteConstraintException::class.java) {
                runBlocking { registry.register(document, location) }
            }
            assertEquals(missing, registry.resolve(document))
            assertEquals(missing, registry.findRegistration(location))
            assertNull(dao.metadata(document.id.value))
            db.openHelper.writableDatabase.execSQL("DROP TRIGGER reject_metadata")
            assertEquals(document, success(registry.register(document, location)).document)
        }
    }

    @Test fun resolveAndLookupMissingReturnNotRegisteredWithoutCreatingRows() {
        runBlocking {
            assertEquals(missing, registry.resolve(document))
            assertEquals(missing, registry.findRegistration(location))
            assertNull(dao.metadata(document.id.value))
            assertNull(dao.registry(document.id.value))
        }
    }

    @Test fun resolvePreservesEveryAvailabilityEvenWhenAccessWasLost() {
        runBlocking {
            registry.register(document, location)
            for (availability in DocumentAvailability.entries) {
                assertEquals(Outcome.Success(Unit), registry.recordObservation(document, location, unknown, availability))
                assertEquals(availability, success(registry.resolve(document)).availability)
                assertEquals(document, success(registry.findRegistration(location)).document)
            }
        }
    }

    @Test fun lookupUsesExactOpaqueNamespaceAndLocatorOnly() {
        runBlocking {
            registry.register(document, location)
            assertEquals(document, success(registry.findRegistration(location)).document)
            assertEquals(missing, registry.findRegistration(StorageDocumentRef("opaque/Provider", location.value)))
            assertEquals(missing, registry.findRegistration(StorageDocumentRef(location.provider, "opaque:Locator")))
            assertEquals(missing, registry.findRegistration(StorageDocumentRef(location.provider, " ${location.value}")))
            assertEquals(missing, registry.findRegistration(StorageDocumentRef(location.provider, document.id.value)))
        }
    }

    @Test fun observationsStoreCallerEvidenceAndCanReturnToUnknownWithoutTouchingMetadata() {
        runBlocking {
            seedObservedMetadata()
            val before = metadata()
            assertEquals(snapshot, success(registry.resolve(document)).revision)
            assertEquals(Outcome.Success(Unit), registry.recordObservation(document, location, unknown, DocumentAvailability.PROVIDER_UNAVAILABLE))
            val observed = success(registry.resolve(document))
            assertEquals(unknown, observed.revision)
            assertEquals(DocumentAvailability.PROVIDER_UNAVAILABLE, observed.availability)
            assertEquals(before, metadata())
            val persisted = requireNotNull(dao.registry(document.id.value))
            assertNull(persisted.snapshotSha256)
            assertNull(persisted.snapshotByteCount)
        }
    }

    @Test fun mismatchedRevisionOwnershipIsAProgrammingErrorAndLeavesStateUntouched() {
        runBlocking {
            registry.register(document, location)
            val before = success(registry.resolve(document))
            for (revision in listOf(DocumentRevision.Unknown(other),
                DocumentRevision.VerifiedSnapshot(other, SnapshotEvidence("b".repeat(64), 43)))) {
                assertThrows(IllegalArgumentException::class.java) {
                    runBlocking { registry.recordObservation(document, location, revision, DocumentAvailability.DELETED) }
                }
                assertEquals(before, success(registry.resolve(document)))
            }
        }
    }

    @Test fun staleObservationAndRebindCannotModifyExistingState() {
        runBlocking {
            seedObservedMetadata()
            val before = success(registry.resolve(document))
            val beforeMetadata = metadata()
            assertEquals(conflict, registry.recordObservation(document, moved, unknown, DocumentAvailability.DELETED))
            assertEquals(conflict, registry.rebind(document, moved, StorageDocumentRef("p", "third")))
            assertEquals(before, success(registry.resolve(document)))
            assertEquals(beforeMetadata, metadata())
        }
    }

    @Test fun missingObservationAndRebindAreNotStaleRegistrationConflicts() {
        runBlocking {
            assertEquals(missing, registry.recordObservation(document, location, unknown, DocumentAvailability.DELETED))
            assertEquals(missing, registry.rebind(document, location, moved))
            registry.register(other, moved)
            // Missing source takes priority even when the replacement is occupied.
            assertEquals(missing, registry.rebind(document, location, moved))
        }
    }

    @Test fun rebindReturnsPersistedUnknownStateAndPreservesHistoricalMetadata() {
        runBlocking {
            seedObservedMetadata()
            val beforeMetadata = metadata()
            val relocated = success(registry.rebind(document, location, moved))
            assertEquals(RegisteredDocument(document, moved, unknown, DocumentAvailability.UNKNOWN), relocated)
            assertEquals(relocated, success(registry.resolve(document)))
            assertEquals(relocated, success(registry.findRegistration(moved)))
            assertEquals(missing, registry.findRegistration(location))
            assertEquals(beforeMetadata, metadata())
        }
    }

    @Test fun occupiedReplacementIsAConflictAndSameIdentityLocationIsAllowed() {
        runBlocking {
            seedObservedMetadata()
            registry.register(other, moved)
            val before = success(registry.resolve(document))
            val beforeMetadata = metadata()
            assertEquals(conflict, registry.rebind(document, location, moved))
            assertEquals(before, success(registry.resolve(document)))
            assertEquals(beforeMetadata, metadata())
            val same = success(registry.rebind(document, location, location))
            assertEquals(unknown, same.revision)
            assertEquals(DocumentAvailability.UNKNOWN, same.availability)
            assertEquals(beforeMetadata, metadata())
        }
    }

    @Test fun concurrentRegistrationOfSameIdHasOneWinnerWithoutOverwrite() {
        runBlocking {
            val gate = CompletableDeferred<Unit>()
            val results = listOf(location, moved).map { storage ->
                async(Dispatchers.IO) { gate.await(); registry.register(document, storage) }
            }
            gate.complete(Unit)
            val outcomes = results.awaitAll()
            val winner = outcomes.filterIsInstance<Outcome.Success<RegisteredDocument>>().single().value
            assertEquals(1, outcomes.count { it == conflict })
            assertEquals(winner, success(registry.resolve(document)))
            assertEquals(DocumentMetadata(), metadata())
        }
    }

    @Test fun concurrentRebindWithOneExpectedLocationHasOneWinner() {
        runBlocking {
            registry.register(document, location)
            val gate = CompletableDeferred<Unit>()
            val results = listOf(moved, StorageDocumentRef("second-provider", "third")).map { storage ->
                async(Dispatchers.IO) { gate.await(); registry.rebind(document, location, storage) }
            }
            gate.complete(Unit)
            val outcomes = results.awaitAll()
            assertEquals(1, outcomes.count { it == conflict })
            val winner = outcomes.filterIsInstance<Outcome.Success<RegisteredDocument>>().single().value
            assertEquals(winner, success(registry.resolve(document)))
        }
    }

    @Test fun observationRacingRebindIsLinearizableAndNeverRestoresOldEvidence() {
        runBlocking {
            seedObservedMetadata()
            val beforeMetadata = metadata()
            val gate = CompletableDeferred<Unit>()
            val next = DocumentRevision.VerifiedSnapshot(document, SnapshotEvidence("b".repeat(64), 43))
            val observation = async(Dispatchers.IO) {
                gate.await(); registry.recordObservation(document, location, next, DocumentAvailability.DELETED)
            }
            val relocation = async(Dispatchers.IO) { gate.await(); registry.rebind(document, location, moved) }
            gate.complete(Unit)
            val observed = observation.await()
            assertTrue(observed == Outcome.Success(Unit) || observed == conflict)
            val relocated = success(relocation.await())
            assertEquals(RegisteredDocument(document, moved, unknown, DocumentAvailability.UNKNOWN), relocated)
            assertEquals(relocated, success(registry.resolve(document)))
            assertEquals(beforeMetadata, metadata())
        }
    }

    @Test fun concurrentRegistrationAcrossTwoRoomInstancesUsesSqliteUniqueness() {
        runBlocking {
            val name = "registry-concurrency-${UUID.randomUUID()}.db"
            db.close()
            db = MetadataDatabase.open(context, name)
            db.openHelper.writableDatabase
            val secondDb = MetadataDatabase.open(context, name)
            try {
                secondDb.openHelper.writableDatabase
                val secondRegistry = RoomDocumentRegistry(secondDb)
                val gate = CompletableDeferred<Unit>()
                val first = async(Dispatchers.IO) { gate.await(); registry.register(document, location) }
                val second = async(Dispatchers.IO) { gate.await(); secondRegistry.register(other, location) }
                gate.complete(Unit)
                val outcomes = awaitAll(first, second)
                assertEquals(1, outcomes.count { it == conflict })
                val winner = outcomes.filterIsInstance<Outcome.Success<RegisteredDocument>>().single().value
                assertEquals(winner, success(registry.findRegistration(location)))
                assertNotNull(dao.metadata(winner.document.id.value))
                val loser = if (winner.document == document) other else document
                assertEquals(missing, registry.resolve(loser))
                assertNull(dao.metadata(loser.id.value))
            } finally {
                secondDb.close()
                db.close()
                context.deleteDatabase(name)
            }
        }
    }

    @Test fun fileBackedReopenRetainsIdentityAvailabilityAndSeparateMetadataRevision() {
        runBlocking {
            val name = "registry-persistence-${UUID.randomUUID()}.db"
            try {
                db.close()
                db = MetadataDatabase.open(context, name)
                seedObservedMetadata()
                val beforeMetadata = metadata()
                val relocated = success(registry.rebind(document, location, moved))
                registry.recordObservation(document, moved, unknown, DocumentAvailability.PERMISSION_REVOKED)
                db.close()
                db = MetadataDatabase.open(context, name)
                val reopened = success(registry.resolve(document))
                assertEquals(relocated.copy(availability = DocumentAvailability.PERMISSION_REVOKED), reopened)
                assertEquals(reopened, success(registry.findRegistration(moved)))
                assertEquals(beforeMetadata, metadata())
                assertEquals(missing, registry.findRegistration(location))
            } finally {
                db.close()
                context.deleteDatabase(name)
            }
        }
    }

    @Test fun actualCantOpenDatabaseFailureMapsSafelyForAllFiveMethods() {
        runBlocking {
            val name = "registry-unavailable-${UUID.randomUUID()}.db"
            val blockedPath = context.getDatabasePath(name)
            assertTrue(blockedPath.mkdirs())
            val marker = File(blockedPath, "test-owned-blocker")
            marker.writeText("Synthetic test directory is not a SQLite database file")
            try {
                db.close()
                db = MetadataDatabase.open(context, name)
                assertEquals(privateFailure, registry.register(document, location))
                assertEquals(privateFailure, registry.resolve(document))
                assertEquals(privateFailure, registry.findRegistration(location))
                assertEquals(privateFailure, registry.recordObservation(document, location, unknown, DocumentAvailability.AVAILABLE))
                assertEquals(privateFailure, registry.rebind(document, location, moved))
                assertTrue(marker.isFile)
            } finally {
                db.close()
                marker.delete()
                blockedPath.delete()
                context.deleteDatabase(name)
            }
        }
    }

    @Test fun invalidPersistedRepresentationPropagatesForReadsAndMutations() {
        runBlocking {
            registry.register(document, location)
            db.openHelper.writableDatabase.execSQL("UPDATE document_registry SET availability = 'invalid_key'")
            for (operation in listOf<suspend () -> Unit>(
                { registry.resolve(document); Unit },
                { registry.findRegistration(location); Unit },
                { registry.recordObservation(document, location, unknown, DocumentAvailability.AVAILABLE); Unit },
                { registry.rebind(document, location, moved); Unit },
            )) {
                assertThrows(IllegalStateException::class.java) { runBlocking { operation() } }
            }
            assertEquals("invalid_key", dao.registry(document.id.value)?.availability)
        }
    }

    @Test fun unexpectedSqlDefectPropagatesRatherThanClaimingStorageUnavailable() {
        runBlocking {
            db.openHelper.writableDatabase.execSQL("DROP TABLE document_registry")
            assertThrows(android.database.sqlite.SQLiteException::class.java) {
                runBlocking { registry.resolve(document) }
            }
        }
    }

    @Test fun cancellationExceptionEscapesAnOuterTransactionAndRollsBackRegistration() {
        runBlocking {
            val reason = CancellationException("test rollback")
            try {
                db.withTransaction {
                    assertTrue(registry.register(document, location) is Outcome.Success)
                    throw reason
                }
                fail("Cancellation must propagate")
            } catch (_: CancellationException) {
                // A cancelled transaction never becomes an Outcome failure/success acknowledgment.
            }
            assertEquals(missing, registry.resolve(document))
            assertNull(dao.metadata(document.id.value))
        }
    }

    @Test fun cancellationWhileQueuedForWriterDoesNotCommitOrReturnAnOutcome() {
        runBlocking {
            val holdingWriter = CompletableDeferred<Unit>()
            val releaseWriter = CompletableDeferred<Unit>()
            val holder = launch(Dispatchers.IO) {
                db.withTransaction { holdingWriter.complete(Unit); releaseWriter.await() }
            }
            val attempted = CompletableDeferred<Unit>()
            val cancellationSeen = CompletableDeferred<Unit>()
            try {
                withTimeout(30_000) { holdingWriter.await() }
                val pending = launch(Dispatchers.IO) {
                    try {
                        attempted.complete(Unit)
                        registry.register(document, location)
                        fail("A cancelled queued write must not return an Outcome")
                    } catch (_: CancellationException) { cancellationSeen.complete(Unit) }
                }
                withTimeout(30_000) { attempted.await() }
                pending.cancelAndJoin()
                withTimeout(30_000) { cancellationSeen.await() }
            } finally {
                releaseWriter.complete(Unit)
                holder.cancelAndJoin()
            }
            assertEquals(missing, registry.resolve(document))
            assertNull(dao.metadata(document.id.value))
        }
    }

    private suspend fun seedObservedMetadata() {
        assertTrue(registry.register(document, location) is Outcome.Success)
        assertEquals(Outcome.Success(Unit), registry.recordObservation(document, location, snapshot, DocumentAvailability.AVAILABLE))
        assertEquals(1, dao.setFavorite(document, true))
        assertEquals(1, dao.setReadingPosition(document, ReadingPosition(PageIndex(7), snapshot), snapshot))
    }

    private suspend fun metadata(): DocumentMetadata = MetadataMapping.metadata(checkNotNull(dao.metadata(document.id.value)))

    private fun <T> success(outcome: Outcome<T>): T {
        assertTrue("Expected successful registry outcome, got $outcome", outcome is Outcome.Success)
        return (outcome as Outcome.Success<T>).value
    }
}
