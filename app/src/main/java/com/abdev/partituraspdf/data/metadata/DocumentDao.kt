package com.abdev.partituraspdf.data.metadata

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.abdev.partituraspdf.core.document.*
import kotlinx.coroutines.flow.Flow

/**
 * Infrastructure only; C02/C03 map missing/conflicting rows and SQLite failures to Outcome.
 * ABORT never replaces an existing identity. Compare-and-write SQL returns an affected row count.
 * Raw primitives remain internal; callers must use validated domain values for writes.
 */
@Dao
internal abstract class DocumentDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun insertRegistry(row: RegistryEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun insertMetadata(row: MetadataEntity)

    @Transaction
    open suspend fun register(document: DocumentRef, storage: StorageDocumentRef) {
        insertRegistry(MetadataMapping.registry(RegisteredDocument(
            document, storage, DocumentRevision.Unknown(document), DocumentAvailability.UNKNOWN,
        )))
        insertMetadata(MetadataEntity(document.id.value))
    }

    @Query("SELECT * FROM document_registry WHERE documentId = :id")
    abstract suspend fun registry(id: String): RegistryEntity?

    @Query("SELECT * FROM document_registry WHERE provider = :provider AND locator = :locator")
    abstract suspend fun findLocation(provider: String, locator: String): RegistryEntity?

    @Query("SELECT * FROM document_registry WHERE documentId = :id")
    abstract fun observeRegistry(id: String): Flow<RegistryEntity?>

    @Query("SELECT * FROM document_metadata WHERE documentId = :id")
    abstract suspend fun metadata(id: String): MetadataEntity?

    @Query("SELECT * FROM document_metadata WHERE documentId = :id")
    abstract fun observeMetadata(id: String): Flow<MetadataEntity?>

    @Query("""UPDATE document_registry SET provider = :provider, locator = :locator,
        revisionType = 'unknown', snapshotSha256 = NULL, snapshotByteCount = NULL, availability = 'unknown'
        WHERE documentId = :id AND provider = :expectedProvider AND locator = :expectedLocator""")
    protected abstract suspend fun rebindRow(
        id: String, expectedProvider: String, expectedLocator: String, provider: String, locator: String,
    ): Int

    suspend fun rebind(document: DocumentRef, expected: StorageDocumentRef, replacement: StorageDocumentRef): Int =
        rebindRow(document.id.value, expected.provider, expected.value, replacement.provider, replacement.value)

    @Query("""UPDATE document_registry SET revisionType = :type, snapshotSha256 = :hash,
        snapshotByteCount = :bytes, availability = :availability
        WHERE documentId = :id AND provider = :expectedProvider AND locator = :expectedLocator""")
    protected abstract suspend fun observeRow(
        id: String, expectedProvider: String, expectedLocator: String,
        type: String, hash: String?, bytes: Long?, availability: String,
    ): Int

    suspend fun recordObservation(
        document: DocumentRef, expected: StorageDocumentRef,
        revision: DocumentRevision, availability: DocumentAvailability,
    ): Int {
        val row = MetadataMapping.registry(RegisteredDocument(document, expected, revision, availability))
        return observeRow(row.documentId, expected.provider, expected.value, row.revisionType,
            row.snapshotSha256, row.snapshotByteCount, row.availability)
    }

    @Query("UPDATE document_metadata SET favorite = :favorite WHERE documentId = :id")
    protected abstract suspend fun favoriteRow(id: String, favorite: Long): Int

    suspend fun setFavorite(document: DocumentRef, favorite: Boolean): Int =
        favoriteRow(document.id.value, if (favorite) 1L else 0L)

    // Compare exact registry revision inside the write statement, including NULL-safe evidence.
    // The stored position has its own revision and is never overwritten by observation/rebind.
    @Query("""UPDATE document_metadata SET readingPage = :page, readingRevisionType = :type,
        readingSha256 = :hash, readingByteCount = :bytes WHERE documentId = :id AND EXISTS (
        SELECT 1 FROM document_registry WHERE documentId = :id AND revisionType = :expectedType
        AND snapshotSha256 IS :expectedHash AND snapshotByteCount IS :expectedBytes)""")
    protected abstract suspend fun positionRow(
        id: String, page: Long?, type: String?, hash: String?, bytes: Long?,
        expectedType: String, expectedHash: String?, expectedBytes: Long?,
    ): Int

    suspend fun setReadingPosition(
        document: DocumentRef, position: ReadingPosition?, expectedRevision: DocumentRevision,
    ): Int {
        require(expectedRevision.document == document)
        val expectedEvidence = (expectedRevision as? DocumentRevision.VerifiedSnapshot)?.evidence
        val expectedType = if (expectedEvidence == null) MetadataMapping.UNKNOWN else MetadataMapping.VERIFIED
        val row = MetadataMapping.metadata(document, DocumentMetadata(readingPosition = position))
        return positionRow(row.documentId, row.readingPage, row.readingRevisionType,
            row.readingSha256, row.readingByteCount, expectedType,
            expectedEvidence?.sha256, expectedEvidence?.byteCount)
    }
}
