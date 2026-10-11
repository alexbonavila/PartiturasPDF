package com.abdev.partituraspdf.core.document

import java.util.Collections

/** App-issued persistent identity; case and whitespace are preserved, never derived from a URI. */
data class DocumentId(val value: String) {
    init { require(value.isNotBlank()) }
}

/** Registered logical identity only. Resolve its current provider location through the registry. */
data class DocumentRef(val id: DocumentId)

/** Only the storage adapter interprets these opaque values, including unregistered entries. */
data class StorageDocumentRef(val provider: String, val value: String) {
    init {
        require(provider.isNotBlank())
        require(value.isNotBlank())
    }
}

/** Opaque provider tree/folder location, independent of document identity and filesystem paths. */
data class FolderRef(val provider: String, val value: String) {
    init {
        require(provider.isNotBlank())
        require(value.isNotBlank())
    }
}

data class PageIndex(val value: Int) {
    init { require(value >= 0) }
}

enum class DocumentAvailability { UNKNOWN, AVAILABLE, PERMISSION_REVOKED, PROVIDER_UNAVAILABLE, DELETED }

/** Provider observations are hints, not proof of current access or an exact content version. */
data class DocumentInfo(
    val name: String?,
    val mimeType: String?,
    val byteSize: Long? = null,
    val modifiedAtMillis: Long? = null,
    val availability: DocumentAvailability = DocumentAvailability.UNKNOWN,
) {
    init {
        require(byteSize == null || byteSize >= 0)
        require(modifiedAtMillis == null || modifiedAtMillis >= 0)
    }
}

/** Catalog membership never assigns identity. Only confirmed registry coordination supplies it. */
data class DocumentEntry(
    val storage: StorageDocumentRef,
    val info: DocumentInfo,
    val folder: FolderRef,
    val document: DocumentRef? = null,
)

/** Partial listings explicitly cannot establish deletion of an omitted entry. */
class DocumentListing(entries: List<DocumentEntry>, val complete: Boolean) {
    val entries: List<DocumentEntry> = Collections.unmodifiableList(entries.toList())
}

/**
 * Evidence supplied ONLY by a future trusted snapshot adapter after hashing complete, sealed,
 * immutable bytes. This value validates representation, not cryptographic truth or PDF validity.
 * Provider size/time and ordinary temporary copies cannot supply this attestation.
 */
data class SnapshotEvidence(val sha256: String, val byteCount: Long) {
    init {
        require(sha256.matches(Regex("[0-9a-f]{64}")))
        require(byteCount > 0)
    }
}

sealed interface DocumentRevision {
    val document: DocumentRef

    data class Unknown(override val document: DocumentRef) : DocumentRevision
    /** Captured bytes only; never a claim that the provider's current bytes still match. */
    data class VerifiedSnapshot(
        override val document: DocumentRef,
        val evidence: SnapshotEvidence,
    ) : DocumentRevision
}

data class RegisteredDocument(
    val document: DocumentRef,
    val storage: StorageDocumentRef,
    val revision: DocumentRevision,
    val availability: DocumentAvailability,
) {
    init { require(revision.document == document) }
}

/** A position from unknown content is only a resume hint, rechecked against a new session. */
data class ReadingPosition(val page: PageIndex, val revision: DocumentRevision)

data class DocumentMetadata(val favorite: Boolean = false, val readingPosition: ReadingPosition? = null)
