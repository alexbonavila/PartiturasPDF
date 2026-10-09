package com.abdev.partituraspdf.core.document

import com.abdev.partituraspdf.core.result.Observation
import com.abdev.partituraspdf.core.result.Outcome
import kotlinx.coroutines.flow.Flow

/**
 * Implemented by data.metadata; never opens providers. IDs are issued by the registration
 * coordinator, not inferred from names, paths or hashes. Expected failures use Outcome;
 * cancellation propagates. A missing row is NOT_REGISTERED, never DOCUMENT_DELETED.
 */
interface DocumentRegistry {
    /** Register a newly issued ID; an already registered ID/location returns REGISTRATION_CHANGED. */
    suspend fun register(document: DocumentRef, storage: StorageDocumentRef): Outcome<RegisteredDocument>
    suspend fun resolve(document: DocumentRef): Outcome<RegisteredDocument>

    /**
     * Confirm catalog associations before assigning identity. Returns the stored association
     * including its observations (even revoked/unavailable), not a successful access claim.
     * Unregistered location returns NOT_REGISTERED. Never infer a match from name or digest.
     */
    suspend fun findRegistration(storage: StorageDocumentRef): Outcome<RegisteredDocument>

    /**
     * Coordinator records confirmed observations without making storage call the registry.
     * Compare expected location atomically; validate revision.document. This records captured
     * evidence, never guarantees current provider bytes or performs a physical file mutation.
     */
    suspend fun recordObservation(
        document: DocumentRef,
        expected: StorageDocumentRef,
        revision: DocumentRevision,
        availability: DocumentAvailability,
    ): Outcome<Unit>

    /**
     * Caller has confirmed relocation; adapter compares expected location atomically and returns
     * REGISTRATION_CHANGED on mismatch. Preserve identity, invalidate revision/access observations.
     */
    suspend fun rebind(
        document: DocumentRef,
        expected: StorageDocumentRef,
        replacement: StorageDocumentRef,
    ): Outcome<RegisteredDocument>
}

/**
 * Implemented by storage.saf. Cancellable background queries, no registry dependency. A failed
 * query is not proof of deletion; report revoked grants, provider unavailability and confirmed
 * deletion separately. Listings contain unregistered entries unless identity was confirmed.
 */
interface DocumentCatalog {
    suspend fun list(folder: FolderRef): Outcome<DocumentListing>
    suspend fun inspect(document: StorageDocumentRef): Outcome<DocumentInfo>
}

/**
 * Implemented by data.metadata. Observation follows [Observation]; no row gives NOT_REGISTERED.
 * Successful updates acknowledge durable persistence and preserve unrelated fields. Position
 * revision must belong to the supplied document; an exact expected revision mismatch returns
 * REVISION_CHANGED. Unknown positions remain hints, not permission to edit current content.
 * Cancellation propagates; consumers reconcile possibly committed cancelled writes by observing.
 */
interface DocumentMetadataRepository {
    fun observe(document: DocumentRef): Flow<Observation<DocumentMetadata>>
    suspend fun setFavorite(document: DocumentRef, favorite: Boolean): Outcome<Unit>
    suspend fun setReadingPosition(document: DocumentRef, position: ReadingPosition?): Outcome<Unit>
}
