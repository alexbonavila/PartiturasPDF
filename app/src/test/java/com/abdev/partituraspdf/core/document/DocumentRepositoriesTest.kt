package com.abdev.partituraspdf.core.document

import com.abdev.partituraspdf.core.result.AppError
import com.abdev.partituraspdf.core.result.Observation
import com.abdev.partituraspdf.core.result.Outcome
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class DocumentRepositoriesTest {
    private val document = DocumentRef(DocumentId("score"))
    private val storage = StorageDocumentRef("provider", "entry")

    /** Test-only persistence seam, never a provider adapter or production repository. */
    private class MemoryRegistry : DocumentRegistry {
        val records = mutableMapOf<DocumentRef, RegisteredDocument>()
        override suspend fun register(document: DocumentRef, storage: StorageDocumentRef): Outcome<RegisteredDocument> {
            if (records.containsKey(document) || records.values.any { it.storage == storage }) {
                return Outcome.Failure(AppError.Conflict.REGISTRATION_CHANGED)
            }
            val record = RegisteredDocument(document, storage, DocumentRevision.Unknown(document), DocumentAvailability.UNKNOWN)
            records[document] = record
            return Outcome.Success(record)
        }
        override suspend fun resolve(document: DocumentRef): Outcome<RegisteredDocument> {
            val record = records[document] ?: return Outcome.Failure(AppError.Access.NOT_REGISTERED)
            return when (record.availability) {
                DocumentAvailability.PERMISSION_REVOKED -> Outcome.Failure(AppError.Access.PERMISSION_REVOKED)
                DocumentAvailability.PROVIDER_UNAVAILABLE -> Outcome.Failure(AppError.Access.PROVIDER_UNAVAILABLE)
                DocumentAvailability.DELETED -> Outcome.Failure(AppError.Access.DOCUMENT_DELETED)
                else -> Outcome.Success(record)
            }
        }
        override suspend fun findRegistration(storage: StorageDocumentRef): Outcome<RegisteredDocument> =
            records.values.find { it.storage == storage }?.let { Outcome.Success(it) }
                ?: Outcome.Failure(AppError.Access.NOT_REGISTERED)
        override suspend fun recordObservation(document: DocumentRef, expected: StorageDocumentRef, revision: DocumentRevision, availability: DocumentAvailability): Outcome<Unit> {
            val record = records[document] ?: return Outcome.Failure(AppError.Access.NOT_REGISTERED)
            if (record.storage != expected) return Outcome.Failure(AppError.Conflict.REGISTRATION_CHANGED)
            if (revision.document != document) return Outcome.Failure(AppError.Validation.INVALID_DOCUMENT)
            records[document] = record.copy(revision = revision, availability = availability)
            return Outcome.Success(Unit)
        }
        override suspend fun rebind(document: DocumentRef, expected: StorageDocumentRef, replacement: StorageDocumentRef): Outcome<RegisteredDocument> {
            val record = records[document] ?: return Outcome.Failure(AppError.Access.NOT_REGISTERED)
            if (record.storage != expected) return Outcome.Failure(AppError.Conflict.REGISTRATION_CHANGED)
            val rebound = record.copy(storage = replacement, revision = DocumentRevision.Unknown(document), availability = DocumentAvailability.UNKNOWN)
            records[document] = rebound
            return Outcome.Success(rebound)
        }
    }

    private class MemoryMetadata(private val document: DocumentRef, private val revision: DocumentRevision) : DocumentMetadataRepository {
        private var metadata = DocumentMetadata()
        var error: AppError? = null
        override fun observe(document: DocumentRef): Flow<Observation<DocumentMetadata>> = flowOf(
            Observation.Loading,
            if (document != this.document) Observation.Failed(AppError.Access.NOT_REGISTERED)
            else error?.let { Observation.Failed(it) } ?: Observation.Ready(metadata),
        )
        override suspend fun setFavorite(document: DocumentRef, favorite: Boolean): Outcome<Unit> {
            if (document != this.document) return Outcome.Failure(AppError.Access.NOT_REGISTERED)
            error?.let { return Outcome.Failure(it) }
            metadata = metadata.copy(favorite = favorite)
            return Outcome.Success(Unit)
        }
        override suspend fun setReadingPosition(document: DocumentRef, position: ReadingPosition?): Outcome<Unit> {
            if (document != this.document) return Outcome.Failure(AppError.Access.NOT_REGISTERED)
            error?.let { return Outcome.Failure(it) }
            if (position != null) {
                if (position.revision.document != document) return Outcome.Failure(AppError.Validation.INVALID_DOCUMENT)
                if (position.revision is DocumentRevision.VerifiedSnapshot && position.revision != revision) {
                    return Outcome.Failure(AppError.Conflict.REVISION_CHANGED)
                }
            }
            metadata = metadata.copy(readingPosition = position)
            return Outcome.Success(Unit)
        }
    }

    @Test fun registrationAndRebindingRetainLogicalIdentityButInvalidateObservations() = runBlocking {
        val registry = MemoryRegistry()
        assertEquals(Outcome.Failure(AppError.Access.NOT_REGISTERED), registry.resolve(document))
        assertTrue(registry.register(document, storage) is Outcome.Success)
        assertEquals(Outcome.Failure(AppError.Conflict.REGISTRATION_CHANGED), registry.register(document, storage))
        val snapshot = DocumentRevision.VerifiedSnapshot(document, SnapshotEvidence("0".repeat(64), 10))
        assertEquals(Outcome.Success(Unit), registry.recordObservation(document, storage, snapshot, DocumentAvailability.AVAILABLE))
        val moved = StorageDocumentRef("provider", "moved")
        val rebound = registry.rebind(document, storage, moved) as Outcome.Success
        assertEquals(document, rebound.value.document)
        assertEquals(moved, rebound.value.storage)
        assertEquals(DocumentRevision.Unknown(document), rebound.value.revision)
        assertEquals(DocumentAvailability.UNKNOWN, rebound.value.availability)
        assertEquals(Outcome.Failure(AppError.Conflict.REGISTRATION_CHANGED), registry.rebind(document, storage, moved))
        assertEquals(Outcome.Failure(AppError.Access.NOT_REGISTERED), registry.rebind(DocumentRef(DocumentId("missing")), storage, moved))
    }

    @Test fun missingRevokedUnavailableAndConfirmedDeletionAreDistinct() = runBlocking {
        val registry = MemoryRegistry()
        registry.register(document, storage)
        for ((availability, error) in listOf(
            DocumentAvailability.PERMISSION_REVOKED to AppError.Access.PERMISSION_REVOKED,
            DocumentAvailability.PROVIDER_UNAVAILABLE to AppError.Access.PROVIDER_UNAVAILABLE,
            DocumentAvailability.DELETED to AppError.Access.DOCUMENT_DELETED,
        )) {
            registry.recordObservation(document, storage, DocumentRevision.Unknown(document), availability)
            assertEquals(Outcome.Failure(error), registry.resolve(document))
        }
        assertEquals(Outcome.Failure(AppError.Access.NOT_REGISTERED), registry.resolve(DocumentRef(DocumentId("absent"))))
    }

    @Test fun observationsCannotOverwriteAnotherRegistrationOrLogicalRevision() = runBlocking {
        val registry = MemoryRegistry()
        val unknown = DocumentRevision.Unknown(document)
        assertEquals(Outcome.Failure(AppError.Access.NOT_REGISTERED), registry.recordObservation(document, storage, unknown, DocumentAvailability.UNKNOWN))
        registry.register(document, storage)
        assertEquals(Outcome.Failure(AppError.Conflict.REGISTRATION_CHANGED), registry.recordObservation(document, StorageDocumentRef("p", "moved"), unknown, DocumentAvailability.DELETED))
        assertEquals(Outcome.Failure(AppError.Validation.INVALID_DOCUMENT), registry.recordObservation(document, storage, DocumentRevision.Unknown(DocumentRef(DocumentId("other"))), DocumentAvailability.AVAILABLE))
        assertEquals(Outcome.Success(registry.records.getValue(document)), registry.resolve(document))
    }

    @Test fun catalogCoordinationConfirmsExistingIdentityWithoutInventingOrDuplicatingIt() = runBlocking {
        val registry: DocumentRegistry = MemoryRegistry()
        assertEquals(Outcome.Failure(AppError.Access.NOT_REGISTERED), registry.findRegistration(storage))
        registry.register(document, storage)
        val association = (registry.findRegistration(storage) as Outcome.Success).value
        assertEquals(document, association.document)
        assertEquals(Outcome.Failure(AppError.Conflict.REGISTRATION_CHANGED), registry.register(DocumentRef(DocumentId("new")), storage))
        registry.recordObservation(document, storage, DocumentRevision.Unknown(document), DocumentAvailability.PERMISSION_REVOKED)
        assertEquals(DocumentAvailability.PERMISSION_REVOKED, (registry.findRegistration(storage) as Outcome.Success).value.availability)
        assertEquals(Outcome.Failure(AppError.Access.PERMISSION_REVOKED), registry.resolve(document))
    }

    @Test fun providerCatalogCanListUnregisteredEntriesAndReportIncompleteResults() = runBlocking {
        val folder = FolderRef("provider", "tree")
        val info = DocumentInfo("Score", "application/pdf", availability = DocumentAvailability.AVAILABLE)
        val catalog: DocumentCatalog = object : DocumentCatalog {
            override suspend fun list(folder: FolderRef): Outcome<DocumentListing> = Outcome.Success(
                DocumentListing(listOf(DocumentEntry(storage, info, folder)), complete = false),
            )
            override suspend fun inspect(document: StorageDocumentRef): Outcome<DocumentInfo> =
                if (document == storage) Outcome.Success(info) else Outcome.Failure(AppError.Access.PROVIDER_UNAVAILABLE)
        }
        val listing = (catalog.list(folder) as Outcome.Success).value
        assertFalse(listing.complete)
        assertNull(listing.entries.single().document)
        assertEquals(Outcome.Success(info), catalog.inspect(storage))
        assertEquals(Outcome.Failure(AppError.Access.PROVIDER_UNAVAILABLE), catalog.inspect(StorageDocumentRef("provider", "absent")))
    }

    @Test fun metadataFieldsAreUpdatedIndependentlyAndUnknownPositionsRemainHints() = runBlocking {
        val repository: DocumentMetadataRepository = MemoryMetadata(document, DocumentRevision.Unknown(document))
        assertEquals(listOf(Observation.Loading, Observation.Ready(DocumentMetadata())), repository.observe(document).toList())
        val position = ReadingPosition(PageIndex(7), DocumentRevision.Unknown(document))
        assertEquals(Outcome.Success(Unit), repository.setFavorite(document, true))
        assertEquals(Outcome.Success(Unit), repository.setReadingPosition(document, position))
        assertEquals(Observation.Ready(DocumentMetadata(true, position)), repository.observe(document).toList()[1])
        repository.setFavorite(document, false)
        assertEquals(Observation.Ready(DocumentMetadata(false, position)), repository.observe(document).toList()[1])
        repository.setReadingPosition(document, null)
        assertEquals(Observation.Ready(DocumentMetadata()), repository.observe(document).toList()[1])
    }

    @Test fun metadataRejectsForeignOrStaleRevisionAndReportsStorageFailure() = runBlocking {
        val exact = DocumentRevision.VerifiedSnapshot(document, SnapshotEvidence("0".repeat(64), 10))
        val repository = MemoryMetadata(document, exact)
        val stale = exact.copy(evidence = SnapshotEvidence("1".repeat(64), 10))
        assertEquals(Outcome.Failure(AppError.Conflict.REVISION_CHANGED), repository.setReadingPosition(document, ReadingPosition(PageIndex(0), stale)))
        val foreign = DocumentRef(DocumentId("foreign"))
        assertEquals(Outcome.Failure(AppError.Validation.INVALID_DOCUMENT), repository.setReadingPosition(document, ReadingPosition(PageIndex(0), DocumentRevision.Unknown(foreign))))
        assertEquals(Outcome.Failure(AppError.Access.NOT_REGISTERED), repository.setFavorite(foreign, true))
        assertEquals(Observation.Failed(AppError.Access.NOT_REGISTERED), repository.observe(foreign).toList()[1])
        repository.error = AppError.Access.PRIVATE_STORAGE_UNAVAILABLE
        assertEquals(Outcome.Failure(AppError.Access.PRIVATE_STORAGE_UNAVAILABLE), repository.setFavorite(document, true))
        assertEquals(Observation.Failed(AppError.Access.PRIVATE_STORAGE_UNAVAILABLE), repository.observe(document).toList()[1])
    }
}
