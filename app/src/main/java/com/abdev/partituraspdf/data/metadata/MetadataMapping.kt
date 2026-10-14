package com.abdev.partituraspdf.data.metadata

import com.abdev.partituraspdf.core.document.*

/** Stable storage keys. Unknown keys and inconsistent evidence are corruption, not defaults. */
internal object MetadataMapping {
    const val UNKNOWN = "unknown"
    const val VERIFIED = "verified_snapshot"

    fun availability(value: DocumentAvailability): String = when (value) {
        DocumentAvailability.UNKNOWN -> UNKNOWN
        DocumentAvailability.AVAILABLE -> "available"
        DocumentAvailability.PERMISSION_REVOKED -> "permission_revoked"
        DocumentAvailability.PROVIDER_UNAVAILABLE -> "provider_unavailable"
        DocumentAvailability.DELETED -> "deleted"
    }

    fun availability(key: String): DocumentAvailability = when (key) {
        UNKNOWN -> DocumentAvailability.UNKNOWN
        "available" -> DocumentAvailability.AVAILABLE
        "permission_revoked" -> DocumentAvailability.PERMISSION_REVOKED
        "provider_unavailable" -> DocumentAvailability.PROVIDER_UNAVAILABLE
        "deleted" -> DocumentAvailability.DELETED
        else -> error("Invalid persisted availability")
    }

    fun revision(document: DocumentRef, key: String, hash: String?, bytes: Long?): DocumentRevision =
        when (key) {
            UNKNOWN -> {
                check(hash == null && bytes == null) { "Unknown revision contains evidence" }
                DocumentRevision.Unknown(document)
            }
            VERIFIED -> DocumentRevision.VerifiedSnapshot(
                document, SnapshotEvidence(checkNotNull(hash), checkNotNull(bytes)),
            )
            else -> error("Invalid persisted revision")
        }

    fun registry(value: RegisteredDocument): RegistryEntity {
        val evidence = (value.revision as? DocumentRevision.VerifiedSnapshot)?.evidence
        return RegistryEntity(
            value.document.id.value, value.storage.provider, value.storage.value,
            availability(value.availability), if (evidence == null) UNKNOWN else VERIFIED,
            evidence?.sha256, evidence?.byteCount,
        )
    }

    fun registry(row: RegistryEntity): RegisteredDocument {
        val document = DocumentRef(DocumentId(row.documentId))
        return RegisteredDocument(
            document, StorageDocumentRef(row.provider, row.locator),
            revision(document, row.revisionType, row.snapshotSha256, row.snapshotByteCount),
            availability(row.availability),
        )
    }

    fun metadata(document: DocumentRef, value: DocumentMetadata): MetadataEntity {
        val position = value.readingPosition
        require(position == null || position.revision.document == document)
        val evidence = (position?.revision as? DocumentRevision.VerifiedSnapshot)?.evidence
        return MetadataEntity(
            document.id.value, if (value.favorite) 1L else 0L, position?.page?.value?.toLong(),
            if (position == null) null else if (evidence == null) UNKNOWN else VERIFIED,
            evidence?.sha256, evidence?.byteCount,
        )
    }

    fun metadata(row: MetadataEntity): DocumentMetadata {
        val document = DocumentRef(DocumentId(row.documentId))
        check(row.favorite == 0L || row.favorite == 1L) { "Invalid persisted favorite" }
        val position = if (row.readingPage == null) {
            check(row.readingRevisionType == null && row.readingSha256 == null && row.readingByteCount == null) {
                "Absent position contains revision evidence"
            }
            null
        } else {
            check(row.readingPage in 0..Int.MAX_VALUE.toLong()) { "Invalid persisted page index" }
            ReadingPosition(PageIndex(row.readingPage.toInt()), revision(
                document, checkNotNull(row.readingRevisionType), row.readingSha256, row.readingByteCount,
            ))
        }
        return DocumentMetadata(row.favorite == 1L, position)
    }
}
