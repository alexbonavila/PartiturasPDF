package com.abdev.partituraspdf.data.metadata

import androidx.room.withTransaction
import com.abdev.partituraspdf.core.document.DocumentAvailability
import com.abdev.partituraspdf.core.document.DocumentRef
import com.abdev.partituraspdf.core.document.DocumentRegistry
import com.abdev.partituraspdf.core.document.DocumentRevision
import com.abdev.partituraspdf.core.document.RegisteredDocument
import com.abdev.partituraspdf.core.document.StorageDocumentRef
import com.abdev.partituraspdf.core.result.AppError
import com.abdev.partituraspdf.core.result.Outcome

/**
 * Logical registration persistence only: never opens providers or derives identities/evidence.
 * The injected C01 database is owned by the caller. Its default SupportSQLite backend takes a
 * writer transaction before checks, so uniqueness checks and post-write reads cannot race with
 * another writer, including a different Room instance using the same file.
 */
internal class RoomDocumentRegistry(private val database: MetadataDatabase) : DocumentRegistry {
    private val dao = database.documents()

    override suspend fun register(document: DocumentRef, storage: StorageDocumentRef): Outcome<RegisteredDocument> =
        metadataDatabaseOperation {
            database.withTransaction {
                // These checks run under the SQLite writer lock, not a process-global mutex.
                // Do not reinterpret every SQLiteConstraintException as a uniqueness conflict.
                if (dao.registry(document.id.value) != null || dao.findLocation(storage.provider, storage.value) != null) {
                    Outcome.Failure(AppError.Conflict.REGISTRATION_CHANGED)
                } else {
                    dao.register(document, storage)
                    Outcome.Success(persisted(document))
                }
            }
        }

    override suspend fun resolve(document: DocumentRef): Outcome<RegisteredDocument> = metadataDatabaseOperation {
        val row = dao.registry(document.id.value)
        if (row == null) Outcome.Failure(AppError.Access.NOT_REGISTERED)
        else Outcome.Success(MetadataMapping.registry(row))
    }

    override suspend fun findRegistration(storage: StorageDocumentRef): Outcome<RegisteredDocument> = metadataDatabaseOperation {
        val row = dao.findLocation(storage.provider, storage.value)
        if (row == null) Outcome.Failure(AppError.Access.NOT_REGISTERED)
        else Outcome.Success(MetadataMapping.registry(row))
    }

    override suspend fun recordObservation(
        document: DocumentRef, expected: StorageDocumentRef,
        revision: DocumentRevision, availability: DocumentAvailability,
    ): Outcome<Unit> {
        require(revision.document == document)
        return metadataDatabaseOperation {
            database.withTransaction {
                val before = dao.registry(document.id.value)
                    ?: return@withTransaction Outcome.Failure(AppError.Access.NOT_REGISTERED)
                // A write must not silently repair an invalid persisted representation.
                MetadataMapping.registry(before)
                if (dao.recordObservation(document, expected, revision, availability) == 0) {
                    missingOrChanged(document)
                } else {
                    Outcome.Success(Unit)
                }
            }
        }
    }

    override suspend fun rebind(
        document: DocumentRef, expected: StorageDocumentRef, replacement: StorageDocumentRef,
    ): Outcome<RegisteredDocument> = metadataDatabaseOperation {
        database.withTransaction {
            val before = dao.registry(document.id.value)
                ?: return@withTransaction Outcome.Failure(AppError.Access.NOT_REGISTERED)
            val current = MetadataMapping.registry(before)
            if (current.storage != expected) {
                return@withTransaction Outcome.Failure(AppError.Conflict.REGISTRATION_CHANGED)
            }
            val occupant = dao.findLocation(replacement.provider, replacement.value)
            if (occupant != null && occupant.documentId != document.id.value) {
                return@withTransaction Outcome.Failure(AppError.Conflict.REGISTRATION_CHANGED)
            }
            if (dao.rebind(document, expected, replacement) == 0) {
                missingOrChanged(document)
            } else {
                // Read back while the same writer transaction is still held; never guess state.
                Outcome.Success(persisted(document))
            }
        }
    }

    private suspend fun persisted(document: DocumentRef): RegisteredDocument =
        MetadataMapping.registry(checkNotNull(dao.registry(document.id.value)) {
            "Registration missing after successful write"
        })

    /** Must be called in the same transaction as the zero-row conditional update. */
    private suspend fun missingOrChanged(document: DocumentRef): Outcome.Failure =
        Outcome.Failure(if (dao.registry(document.id.value) == null) {
            AppError.Access.NOT_REGISTERED
        } else {
            AppError.Conflict.REGISTRATION_CHANGED
        })

}
