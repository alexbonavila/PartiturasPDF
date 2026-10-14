package com.abdev.partituraspdf.data.metadata

import android.database.sqlite.SQLiteException
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.abdev.partituraspdf.core.document.*
import com.abdev.partituraspdf.core.result.Observation
import com.abdev.partituraspdf.core.result.Outcome
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/** C05 gaps: independent database writers and metadata-specific error boundaries. */
@RunWith(AndroidJUnit4::class)
class PersistenceRegressionTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val document = DocumentRef(DocumentId("c05"))
    private val storage = StorageDocumentRef("test-provider", "test-location")

    @Test fun independentRoomInstancesPreserveConcurrentMetadataFields() {
        runBlocking {
            val name = "c05-${UUID.randomUUID()}.db"
            val first = MetadataDatabase.open(context, name)
            val second = MetadataDatabase.open(context, name)
            try {
                assertTrue(RoomDocumentRegistry(first).register(document, storage) is Outcome.Success)
                second.openHelper.writableDatabase
                val hint = ReadingPosition(PageIndex(4), DocumentRevision.Unknown(document))
                val gate = CompletableDeferred<Unit>()
                val favorite = async(Dispatchers.IO) { gate.await(); RoomDocumentMetadataRepository(first).setFavorite(document, true) }
                val position = async(Dispatchers.IO) { gate.await(); RoomDocumentMetadataRepository(second).setReadingPosition(document, hint) }
                gate.complete(Unit)
                assertEquals(listOf(Outcome.Success(Unit), Outcome.Success(Unit)), awaitAll(favorite, position))
                for (database in listOf(first, second)) {
                    assertEquals(DocumentMetadata(true, hint), MetadataMapping.metadata(requireNotNull(database.documents().metadata(document.id.value))))
                }
            } finally { first.close(); second.close(); context.deleteDatabase(name) }
        }
    }

    @Test fun metadataSqlDefectsPropagateThroughObservationAndBothWrites() {
        runBlocking {
            val db = Room.inMemoryDatabaseBuilder(context, MetadataDatabase::class.java).build()
            try {
                RoomDocumentRegistry(db).register(document, storage)
                db.openHelper.writableDatabase.execSQL("DROP TABLE document_metadata")
                val repository = RoomDocumentMetadataRepository(db)
                val emitted = mutableListOf<Observation<DocumentMetadata>>()
                assertThrows(SQLiteException::class.java) { runBlocking { repository.observe(document).collect { emitted.add(it) } } }
                assertEquals(listOf(Observation.Loading), emitted)
                assertThrows(SQLiteException::class.java) { runBlocking { repository.setFavorite(document, true) } }
                assertThrows(SQLiteException::class.java) { runBlocking { repository.setReadingPosition(document, null) } }
            } finally { db.close() }
        }
    }

    @Test fun metadataDownstreamReadyFailurePropagatesByIdentity() {
        runBlocking {
            val db = Room.inMemoryDatabaseBuilder(context, MetadataDatabase::class.java).build()
            try {
                RoomDocumentRegistry(db).register(document, storage)
                val repository = RoomDocumentMetadataRepository(db)
                val defect = IllegalArgumentException("test collector defect")
                val emitted = mutableListOf<Observation<DocumentMetadata>>()
                try {
                    repository.observe(document).collect {
                        emitted.add(it)
                        if (it is Observation.Ready) throw defect
                    }
                    fail("Collector failure must propagate")
                } catch (actual: IllegalArgumentException) { assertSame(defect, actual) }
                assertEquals(listOf(Observation.Loading, Observation.Ready(DocumentMetadata())), emitted)
                assertEquals(Observation.Ready(DocumentMetadata()), repository.observe(document).first { it is Observation.Ready })
            } finally { db.close() }
        }
    }
}
