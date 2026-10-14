package com.abdev.partituraspdf.data.metadata

import androidx.room.withTransaction
import com.abdev.partituraspdf.core.document.DocumentMetadata
import com.abdev.partituraspdf.core.document.DocumentMetadataRepository
import com.abdev.partituraspdf.core.document.DocumentRef
import com.abdev.partituraspdf.core.document.ReadingPosition
import com.abdev.partituraspdf.core.document.RegisteredDocument
import com.abdev.partituraspdf.core.result.AppError
import com.abdev.partituraspdf.core.result.Observation
import com.abdev.partituraspdf.core.result.Outcome
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow

/** Caller owns the injected C01 database; no providers, polling, shared jobs or startup wiring. */
internal class RoomDocumentMetadataRepository(private val database: MetadataDatabase) : DocumentMetadataRepository {
    private val dao = database.documents()

    override fun observe(document: DocumentRef): Flow<Observation<DocumentMetadata>> = flow {
        emit(Observation.Loading)
        dao.observeRegisteredMetadata(document.id.value).collect { rows ->
            if (rows == null) {
                emit(Observation.Failed(AppError.Access.NOT_REGISTERED))
            } else {
                MetadataMapping.registry(rows.registration)
                val metadata = checkNotNull(rows.metadata) { "Registered document has no metadata" }
                emit(Observation.Ready(MetadataMapping.metadata(metadata)))
            }
        }
    }.catch { failure ->
        // An operational failure ends this collection with no cached Ready value. Recollection
        // starts with Loading again; absent registration remains a live, non-exceptional state.
        emit(Observation.Failed(metadataDatabaseFailure(failure)))
    }

    override suspend fun setFavorite(document: DocumentRef, favorite: Boolean): Outcome<Unit> =
        metadataDatabaseOperation {
            database.withTransaction {
                validatedRegistration(document)
                    ?: return@withTransaction Outcome.Failure(AppError.Access.NOT_REGISTERED)
                check(dao.setFavorite(document, favorite) == 1) { "Registered metadata missing during update" }
                Outcome.Success(Unit)
            }
        }

    override suspend fun setReadingPosition(document: DocumentRef, position: ReadingPosition?): Outcome<Unit> {
        validateReadingPositionOwnership(document, position)
        return metadataDatabaseOperation {
            database.withTransaction {
                val current = validatedRegistration(document)
                    ?: return@withTransaction Outcome.Failure(AppError.Access.NOT_REGISTERED)
                val expected = expectedReadingPositionRevision(document, current.revision, position)
                if (dao.setReadingPosition(document, position, expected) == 0) {
                    // Presence was checked under the same writer transaction. A different writer
                    // cannot delete the registration between that check and this conditional SQL.
                    Outcome.Failure(AppError.Conflict.REVISION_CHANGED)
                } else {
                    Outcome.Success(Unit)
                }
            }
        }
    }

    /** Only called within a writer transaction; do not silently repair corrupt metadata on write. */
    private suspend fun validatedRegistration(document: DocumentRef): RegisteredDocument? {
        val row = dao.registry(document.id.value) ?: return null
        val registration = MetadataMapping.registry(row)
        MetadataMapping.metadata(checkNotNull(dao.metadata(document.id.value)) {
            "Registered document has no metadata"
        })
        return registration
    }
}
