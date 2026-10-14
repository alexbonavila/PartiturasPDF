package com.abdev.partituraspdf.data.metadata

import com.abdev.partituraspdf.core.document.*
import org.junit.Assert.*
import org.junit.Test

class MetadataMappingTest {
    private val document = DocumentRef(DocumentId(" Score "))
    private val other = DocumentRef(DocumentId("other"))
    private val storage = StorageDocumentRef("opaque/provider", "provider:locator")
    private val unknown = DocumentRevision.Unknown(document)
    private val verified = DocumentRevision.VerifiedSnapshot(document, SnapshotEvidence("a".repeat(64), 7))
    private val row = MetadataMapping.registry(RegisteredDocument(document, storage, unknown, DocumentAvailability.UNKNOWN))

    @Test fun availabilityKeysAreExplicitAndRoundTrip() {
        val keys = listOf("unknown", "available", "permission_revoked", "provider_unavailable", "deleted")
        val values = listOf(DocumentAvailability.UNKNOWN, DocumentAvailability.AVAILABLE,
            DocumentAvailability.PERMISSION_REVOKED, DocumentAvailability.PROVIDER_UNAVAILABLE, DocumentAvailability.DELETED)
        for ((value, key) in values.zip(keys)) {
            assertEquals(key, MetadataMapping.availability(value))
            assertEquals(value, MetadataMapping.availability(key))
        }
        for (invalid in listOf("", "UNKNOWN", "future", "0")) {
            assertThrows(IllegalStateException::class.java) { MetadataMapping.availability(invalid) }
        }
    }

    @Test fun registryRoundTripsOpaqueIdentityAndAllObservations() {
        for (revision in listOf(unknown, verified)) {
            for (availability in DocumentAvailability.entries) {
                val value = RegisteredDocument(document, storage, revision, availability)
                val persisted = MetadataMapping.registry(value)
                assertEquals(" Score ", persisted.documentId)
                assertEquals(storage.provider, persisted.provider)
                assertEquals(storage.value, persisted.locator)
                assertEquals(if (revision == unknown) "unknown" else "verified_snapshot", persisted.revisionType)
                assertEquals(value, MetadataMapping.registry(persisted))
            }
        }
        assertNull(row.snapshotSha256)
        assertNull(row.snapshotByteCount)
        val snapshot = MetadataMapping.registry(RegisteredDocument(document, storage, verified, DocumentAvailability.AVAILABLE))
        assertEquals(verified.evidence.sha256, snapshot.snapshotSha256)
        assertEquals(7L, snapshot.snapshotByteCount)
    }

    @Test fun inconsistentOrUnrecognizedRevisionsCannotBecomeEvidence() {
        val corrupt = listOf(
            row.copy(revisionType = "future"), row.copy(revisionType = "UNKNOWN"),
            row.copy(snapshotSha256 = "a".repeat(64)), row.copy(snapshotByteCount = 1),
            row.copy(snapshotSha256 = "a".repeat(64), snapshotByteCount = 1),
            row.copy(revisionType = "verified_snapshot"),
            row.copy(revisionType = "verified_snapshot", snapshotSha256 = "a".repeat(64)),
            row.copy(revisionType = "verified_snapshot", snapshotByteCount = 1),
            row.copy(revisionType = "verified_snapshot", snapshotSha256 = "A".repeat(64), snapshotByteCount = 1),
            row.copy(revisionType = "verified_snapshot", snapshotSha256 = "a".repeat(64), snapshotByteCount = 0),
            row.copy(revisionType = "verified_snapshot", snapshotSha256 = "a".repeat(64), snapshotByteCount = -1),
            row.copy(revisionType = "verified_snapshot", snapshotSha256 = "bad", snapshotByteCount = 1),
        )
        for (invalid in corrupt) assertThrows(RuntimeException::class.java) { MetadataMapping.registry(invalid) }
    }

    @Test fun invalidIdentityLocationAndAvailabilityAreRejected() {
        for (invalid in listOf(row.copy(documentId = " "), row.copy(provider = ""),
            row.copy(locator = "\t"), row.copy(availability = "future"))) {
            assertThrows(RuntimeException::class.java) { MetadataMapping.registry(invalid) }
        }
    }

    @Test fun metadataRoundTripsAbsentUnknownAndVerifiedPositionsWithoutRegistryRevision() {
        for (favorite in listOf(false, true)) {
            for (position in listOf(null, ReadingPosition(PageIndex(0), unknown), ReadingPosition(PageIndex(9), verified))) {
                val value = DocumentMetadata(favorite, position)
                val persisted = MetadataMapping.metadata(document, value)
                assertEquals(if (favorite) 1L else 0L, persisted.favorite)
                assertEquals(value, MetadataMapping.metadata(persisted))
            }
        }
        val maximum = DocumentMetadata(readingPosition = ReadingPosition(PageIndex(Int.MAX_VALUE), unknown))
        assertEquals(maximum, MetadataMapping.metadata(MetadataMapping.metadata(document, maximum)))
        assertEquals(MetadataEntity(document.id.value), MetadataMapping.metadata(document, DocumentMetadata()))
        assertThrows(IllegalArgumentException::class.java) {
            MetadataMapping.metadata(document, DocumentMetadata(readingPosition = ReadingPosition(PageIndex(0), DocumentRevision.Unknown(other))))
        }
    }

    @Test fun corruptMetadataNeverSilentlyBecomesDefaultsOrEvidence() {
        val base = MetadataEntity(document.id.value)
        val corrupt = listOf(
            base.copy(documentId = ""), base.copy(favorite = -1), base.copy(favorite = 2),
            base.copy(favorite = 1L shl 32), base.copy(readingPage = 1L shl 32, readingRevisionType = "unknown"),
            base.copy(readingPage = Int.MAX_VALUE.toLong() + 1, readingRevisionType = "unknown"),
            base.copy(readingRevisionType = "unknown"), base.copy(readingSha256 = "a".repeat(64)),
            base.copy(readingByteCount = 1), base.copy(readingPage = -1, readingRevisionType = "unknown"),
            base.copy(readingPage = 0), base.copy(readingPage = 0, readingRevisionType = "future"),
            base.copy(readingPage = 0, readingRevisionType = "unknown", readingSha256 = "a".repeat(64)),
            base.copy(readingPage = 0, readingRevisionType = "unknown", readingByteCount = 1),
            base.copy(readingPage = 0, readingRevisionType = "verified_snapshot"),
            base.copy(readingPage = 0, readingRevisionType = "verified_snapshot", readingSha256 = "a".repeat(64), readingByteCount = 0),
        )
        for (invalid in corrupt) assertThrows(RuntimeException::class.java) { MetadataMapping.metadata(invalid) }
    }
}
