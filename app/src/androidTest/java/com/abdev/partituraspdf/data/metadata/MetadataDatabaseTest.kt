package com.abdev.partituraspdf.data.metadata

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.abdev.partituraspdf.core.document.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/** Real SQLite/Room tests; compilation on a host is not evidence of their execution. */
@RunWith(AndroidJUnit4::class)
class MetadataDatabaseTest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var db: MetadataDatabase
    private val document = DocumentRef(DocumentId("logical-id"))
    private val other = DocumentRef(DocumentId("other-id"))
    private val location = StorageDocumentRef("provider", "opaque-location")
    private val replacement = StorageDocumentRef("provider", "new-location")
    private val unknown = DocumentRevision.Unknown(document)
    private val revision = DocumentRevision.VerifiedSnapshot(document, SnapshotEvidence("a".repeat(64), 42))
    private val dao get() = db.documents()

    @Before fun open() { db = Room.inMemoryDatabaseBuilder(context, MetadataDatabase::class.java).build() }
    @After fun close() { db.close() }

    @Test fun registrationCreatesUnknownObservationsAndDefaultMetadataAtomically() = runBlocking {
        dao.register(document, location)
        assertEquals(RegisteredDocument(document, location, unknown, DocumentAvailability.UNKNOWN),
            MetadataMapping.registry(requireNotNull(dao.registry(document.id.value))))
        assertEquals(DocumentMetadata(), MetadataMapping.metadata(requireNotNull(dao.metadata(document.id.value))))
        assertEquals(dao.registry(document.id.value), dao.findLocation(location.provider, location.value))
        assertNull(dao.registry("missing"))
        assertNull(dao.metadata("missing"))
        assertNull(dao.findLocation("missing", "missing"))
    }

    @Test fun duplicateLogicalIdAndProviderLocationAbortWithoutReplacingRows() = runBlocking {
        dao.register(document, location)
        constraint { dao.register(document, replacement) }
        constraint { dao.register(other, location) }
        assertEquals(location, MetadataMapping.registry(requireNotNull(dao.registry(document.id.value))).storage)
        assertNull(dao.registry(other.id.value))
        assertNull(dao.metadata(other.id.value))
        // Same locator in a different opaque provider namespace is a different association.
        dao.register(other, StorageDocumentRef("other-provider", location.value))
        assertNotNull(dao.registry(other.id.value))
    }

    @Test fun concurrentRegistrationOfOneLocationCreatesOnlyOneCompleteIdentity() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        val contenders = listOf(document, other).map { ref ->
            async(Dispatchers.IO) {
                gate.await()
                try { dao.register(ref, location); ref }
                catch (_: SQLiteConstraintException) { null }
            }
        }
        gate.complete(Unit)
        val results = contenders.awaitAll()
        val winner = results.filterNotNull().single()
        assertNotNull(dao.metadata(winner.id.value))
        val loser = if (winner == document) other else document
        assertNull(dao.registry(loser.id.value))
        assertNull(dao.metadata(loser.id.value))
        assertEquals(winner.id.value, dao.findLocation(location.provider, location.value)?.documentId)
    }

    @Test fun failureOfMetadataInsertRollsBackRegistryInsert() = runBlocking {
        db.openHelper.writableDatabase.execSQL("""CREATE TEMP TRIGGER reject_metadata BEFORE INSERT ON document_metadata
            BEGIN SELECT RAISE(ABORT, 'test insert failure'); END""")
        constraint { dao.register(document, location) }
        assertNull(dao.registry(document.id.value))
        assertNull(dao.findLocation(location.provider, location.value))
    }

    @Test fun metadataForeignKeyAndPrimaryKeyAreEnforced() = runBlocking {
        val sql = db.openHelper.writableDatabase
        assertThrows(SQLiteConstraintException::class.java) {
            sql.execSQL("INSERT INTO document_metadata(documentId, favorite) VALUES ('missing', 0)")
        }
        dao.register(document, location)
        assertThrows(SQLiteConstraintException::class.java) {
            sql.execSQL("INSERT INTO document_metadata(documentId, favorite) VALUES ('logical-id', 0)")
        }
        sql.execSQL("DELETE FROM document_registry WHERE documentId = 'logical-id'")
        assertNull(dao.metadata(document.id.value))
    }

    @Test fun rebindComparesLocationProtectsUniquenessAndInvalidatesOnlyRegistryObservations() = runBlocking {
        dao.register(document, location)
        dao.register(other, replacement)
        assertEquals(1, dao.recordObservation(document, location, revision, DocumentAvailability.AVAILABLE))
        val position = ReadingPosition(PageIndex(8), revision)
        assertEquals(1, dao.setReadingPosition(document, position, revision))
        constraint { dao.rebind(document, location, replacement) }
        assertEquals(revision, MetadataMapping.registry(requireNotNull(dao.registry(document.id.value))).revision)
        assertEquals(0, dao.rebind(document, replacement, StorageDocumentRef("p", "stale")))
        val moved = StorageDocumentRef("provider", "moved")
        assertEquals(1, dao.rebind(document, location, moved))
        assertEquals(RegisteredDocument(document, moved, unknown, DocumentAvailability.UNKNOWN),
            MetadataMapping.registry(requireNotNull(dao.registry(document.id.value))))
        assertEquals(position, MetadataMapping.metadata(requireNotNull(dao.metadata(document.id.value))).readingPosition)
        assertEquals(0, dao.recordObservation(document, location, revision, DocumentAvailability.DELETED))
    }

    @Test fun concurrentExpectedLocationRebindHasExactlyOneWinner() = runBlocking {
        dao.register(document, location)
        val gate = CompletableDeferred<Unit>()
        val results = listOf("one", "two").map { locator ->
            async(Dispatchers.IO) { gate.await(); dao.rebind(document, location, StorageDocumentRef("provider", locator)) }
        }
        gate.complete(Unit)
        assertEquals(listOf(0, 1), results.awaitAll().sorted())
        assertTrue(requireNotNull(dao.registry(document.id.value)).locator in listOf("one", "two"))
    }

    @Test fun independentConcurrentMetadataWritesAndSeparateRevisionsSurviveObservations() = runBlocking {
        dao.register(document, location)
        assertEquals(1, dao.recordObservation(document, location, revision, DocumentAvailability.AVAILABLE))
        val position = ReadingPosition(PageIndex(3), revision)
        val gate = CompletableDeferred<Unit>()
        val favorite = async(Dispatchers.IO) { gate.await(); dao.setFavorite(document, true) }
        val reading = async(Dispatchers.IO) { gate.await(); dao.setReadingPosition(document, position, revision) }
        gate.complete(Unit)
        assertEquals(listOf(1, 1), awaitAll(favorite, reading))
        assertEquals(DocumentMetadata(true, position), MetadataMapping.metadata(requireNotNull(dao.metadata(document.id.value))))
        val next = DocumentRevision.VerifiedSnapshot(document, SnapshotEvidence("b".repeat(64), 43))
        assertEquals(1, dao.recordObservation(document, location, next, DocumentAvailability.PERMISSION_REVOKED))
        assertEquals(0, dao.setReadingPosition(document, ReadingPosition(PageIndex(4), revision), revision))
        assertEquals(0, dao.setReadingPosition(document, null, revision))
        assertEquals(position, MetadataMapping.metadata(requireNotNull(dao.metadata(document.id.value))).readingPosition)
        assertEquals(1, dao.setReadingPosition(document, null, next))
        assertEquals(DocumentMetadata(true), MetadataMapping.metadata(requireNotNull(dao.metadata(document.id.value))))
        assertEquals(1, dao.setFavorite(document, false))
    }

    @Test fun expectedRevisionComparisonIncludesTypeDigestAndByteCount() = runBlocking {
        dao.register(document, location)
        dao.recordObservation(document, location, revision, DocumentAvailability.AVAILABLE)
        val position = ReadingPosition(PageIndex(0), revision)
        assertEquals(0, dao.setReadingPosition(document, position, unknown))
        val differentLength = DocumentRevision.VerifiedSnapshot(document, SnapshotEvidence("a".repeat(64), 43))
        val differentDigest = DocumentRevision.VerifiedSnapshot(document, SnapshotEvidence("b".repeat(64), 42))
        assertEquals(0, dao.setReadingPosition(document, position, differentLength))
        assertEquals(0, dao.setReadingPosition(document, position, differentDigest))
        assertNull(dao.metadata(document.id.value)?.readingPage)
        assertEquals(1, dao.setReadingPosition(document, position, revision))
    }

    @Test fun unknownPositionsRemainHintsAndMismatchedDocumentsAreRejected() = runBlocking {
        dao.register(document, location)
        val hint = ReadingPosition(PageIndex(0), unknown)
        assertEquals(1, dao.setReadingPosition(document, hint, unknown))
        assertEquals(hint, MetadataMapping.metadata(requireNotNull(dao.metadata(document.id.value))).readingPosition)
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { dao.recordObservation(document, location, DocumentRevision.Unknown(other), DocumentAvailability.AVAILABLE) }
        }
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { dao.setReadingPosition(document, hint, DocumentRevision.Unknown(other)) }
        }
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { dao.setReadingPosition(document, ReadingPosition(PageIndex(1), DocumentRevision.Unknown(other)), unknown) }
        }
        assertEquals(0, dao.setFavorite(other, true))
        assertEquals(0, dao.setReadingPosition(other, null, DocumentRevision.Unknown(other)))
        assertEquals(0, dao.rebind(other, location, replacement))
    }

    @Test fun flowsEmitMissingRegistrationAndCommittedUpdates() = runBlocking {
        val metadataUpdates = Channel<MetadataEntity?>(Channel.UNLIMITED)
        val registryUpdates = Channel<RegistryEntity?>(Channel.UNLIMITED)
        val metadataCollector = launch { dao.observeMetadata(document.id.value).distinctUntilChanged().collect { metadataUpdates.send(it) } }
        val registryCollector = launch { dao.observeRegistry(document.id.value).distinctUntilChanged().collect { registryUpdates.send(it) } }
        try {
            // Await initial results before writing: the same live collectors must invalidate.
            assertNull(withTimeout(30_000) { metadataUpdates.receive() })
            assertNull(withTimeout(30_000) { registryUpdates.receive() })
            dao.register(document, location)
            assertEquals(location.value, withTimeout(30_000) { registryUpdates.receive() }?.locator)
            assertEquals(0L, withTimeout(30_000) { metadataUpdates.receive() }?.favorite)
            dao.setFavorite(document, true)
            assertEquals(1L, withTimeout(30_000) { metadataUpdates.receive() }?.favorite)
            dao.setReadingPosition(document, ReadingPosition(PageIndex(0), unknown), unknown)
            val position = withTimeout(30_000) { metadataUpdates.receive() }
            assertEquals(0L, position?.readingPage)
            assertEquals(1L, position?.favorite)
            dao.recordObservation(document, location, unknown, DocumentAvailability.DELETED)
            assertEquals("deleted", withTimeout(30_000) { registryUpdates.receive() }?.availability)
        } finally {
            metadataCollector.cancelAndJoin()
            registryCollector.cancelAndJoin()
            metadataUpdates.close()
            registryUpdates.close()
        }
    }

    @Test fun corruptPersistedEvidenceIsRejectedAfterRealSqlRead() = runBlocking {
        dao.register(document, location)
        db.openHelper.writableDatabase.execSQL("UPDATE document_registry SET snapshotByteCount = 1 WHERE documentId = 'logical-id'")
        assertThrows(IllegalStateException::class.java) {
            MetadataMapping.registry(runBlocking { requireNotNull(dao.registry(document.id.value)) })
        }
        db.openHelper.writableDatabase.execSQL("UPDATE document_metadata SET favorite = 4294967296 WHERE documentId = 'logical-id'")
        assertThrows(IllegalStateException::class.java) {
            MetadataMapping.metadata(runBlocking { requireNotNull(dao.metadata(document.id.value)) })
        }
        db.openHelper.writableDatabase.execSQL("""UPDATE document_metadata SET favorite = 0,
            readingPage = 4294967296, readingRevisionType = 'unknown' WHERE documentId = 'logical-id'""")
        assertThrows(IllegalStateException::class.java) {
            MetadataMapping.metadata(runBlocking { requireNotNull(dao.metadata(document.id.value)) })
        }
    }

    @Test fun fileBackedDatabaseRetainsBothIndependentRevisionsAcrossCloseAndReopen() = runBlocking {
        val name = "metadata-test-${UUID.randomUUID()}.db"
        try {
            db.close()
            db = MetadataDatabase.open(context, name)
            dao.register(document, location)
            dao.recordObservation(document, location, revision, DocumentAvailability.AVAILABLE)
            val position = ReadingPosition(PageIndex(5), revision)
            dao.setReadingPosition(document, position, revision)
            dao.setFavorite(document, true)
            dao.recordObservation(document, location, unknown, DocumentAvailability.PROVIDER_UNAVAILABLE)
            db.close()
            db = MetadataDatabase.open(context, name)
            assertEquals(RegisteredDocument(document, location, unknown, DocumentAvailability.PROVIDER_UNAVAILABLE),
                MetadataMapping.registry(requireNotNull(dao.registry(document.id.value))))
            assertEquals(DocumentMetadata(true, position), MetadataMapping.metadata(requireNotNull(dao.metadata(document.id.value))))
        } finally {
            db.close()
            context.deleteDatabase(name)
        }
    }

    private suspend fun constraint(block: suspend () -> Unit) {
        try { block(); fail("Expected SQLite uniqueness/referential constraint failure") }
        catch (_: SQLiteConstraintException) { /* Expected ABORT, not replacement. */ }
    }
}
