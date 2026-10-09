package com.abdev.partituraspdf.core.document

import org.junit.Assert.*
import org.junit.Test

class DocumentModelsTest {
    private val document = DocumentRef(DocumentId("score-1"))

    @Test fun identifiersRejectBlankValuesWithoutNormalizingIdentity() {
        for (value in listOf("", " ", "\n\t")) {
            assertThrows(IllegalArgumentException::class.java) { DocumentId(value) }
            assertThrows(IllegalArgumentException::class.java) { StorageDocumentRef(value, "entry") }
            assertThrows(IllegalArgumentException::class.java) { StorageDocumentRef("provider", value) }
            assertThrows(IllegalArgumentException::class.java) { FolderRef(value, "folder") }
            assertThrows(IllegalArgumentException::class.java) { FolderRef("provider", value) }
        }
        assertNotEquals(DocumentId("Score"), DocumentId("score"))
        assertNotEquals(DocumentId("score"), DocumentId(" score "))
        assertEquals(" score ", DocumentId(" score ").value)
    }

    @Test fun logicalReferenceSurvivesConceptualRelocation() {
        val original = StorageDocumentRef("provider", "old/score")
        val moved = StorageDocumentRef("provider", "new/score")
        val registered = RegisteredDocument(document, original, DocumentRevision.Unknown(document), DocumentAvailability.UNKNOWN)
        val relocated = registered.copy(storage = moved)
        assertEquals(document, relocated.document)
        assertEquals("score-1", relocated.document.id.value)
        assertNotEquals(registered.storage, relocated.storage)
        assertEquals("provider", moved.provider)
        assertEquals("new/score", moved.value)
        assertEquals(DocumentAvailability.UNKNOWN, relocated.availability)
        assertEquals(registered.revision, relocated.revision)
    }

    @Test fun unregisteredEntriesDoNotInventIdentity() {
        val folder = FolderRef("provider", "tree")
        val info = DocumentInfo("Score", "application/pdf")
        val entry = DocumentEntry(StorageDocumentRef("provider", "entry"), info, folder)
        assertNull(entry.document)
        assertEquals(info, entry.info)
        assertEquals(folder, entry.folder)
        assertEquals("provider", folder.provider)
        assertEquals("tree", folder.value)
        assertEquals("entry", entry.storage.value)
        val listing = DocumentListing(listOf(entry), complete = false)
        assertFalse(listing.complete)
        assertEquals(listOf(entry), listing.entries)
        assertEquals(document, entry.copy(document = document).document)
    }

    @Test fun listingTakesAnImmutableCopyOfProviderResults() {
        val entries = mutableListOf(DocumentEntry(StorageDocumentRef("p", "entry"), DocumentInfo(null, null), FolderRef("p", "tree")))
        val listing = DocumentListing(entries, true)
        entries.clear()
        assertEquals(1, listing.entries.size)
        assertThrows(UnsupportedOperationException::class.java) { (listing.entries as MutableList).clear() }
        assertTrue(listing.complete)
    }

    @Test fun metadataObservationsMayBeUnknownOrZeroButNotNegative() {
        val unknown = DocumentInfo(null, null)
        assertNull(unknown.name)
        assertNull(unknown.mimeType)
        assertNull(unknown.byteSize)
        assertNull(unknown.modifiedAtMillis)
        assertEquals(DocumentAvailability.UNKNOWN, unknown.availability)
        val observed = DocumentInfo("Score", "application/pdf", 0, 0, DocumentAvailability.AVAILABLE)
        assertEquals("Score", observed.name)
        assertEquals("application/pdf", observed.mimeType)
        assertEquals(0L, observed.byteSize)
        assertEquals(0L, observed.modifiedAtMillis)
        assertThrows(IllegalArgumentException::class.java) { observed.copy(byteSize = -1) }
        assertThrows(IllegalArgumentException::class.java) { observed.copy(modifiedAtMillis = -1) }
    }

    @Test fun pageIndicesAreZeroBasedAndNonnegative() {
        assertEquals(0, PageIndex(0).value)
        assertEquals(Int.MAX_VALUE, PageIndex(Int.MAX_VALUE).value)
        assertThrows(IllegalArgumentException::class.java) { PageIndex(-1) }
    }

    @Test fun exactRevisionRequiresWellFormedSnapshotEvidence() {
        val evidence = SnapshotEvidence("0123456789abcdef".repeat(4), 123)
        val exact = DocumentRevision.VerifiedSnapshot(document, evidence)
        val unknown = DocumentRevision.Unknown(document)
        assertEquals(document, exact.document)
        assertEquals(document, unknown.document)
        assertEquals(123L, exact.evidence.byteCount)
        assertEquals("0123456789abcdef".repeat(4), exact.evidence.sha256)
        assertNotEquals(exact, unknown)
        for (digest in listOf("", "0".repeat(63), "0".repeat(65), "g".repeat(64), "A".repeat(64))) {
            assertThrows(IllegalArgumentException::class.java) { SnapshotEvidence(digest, 1) }
        }
        for (length in listOf(0L, -1L)) {
            assertThrows(IllegalArgumentException::class.java) { SnapshotEvidence("0".repeat(64), length) }
        }
        assertThrows(IllegalArgumentException::class.java) {
            RegisteredDocument(DocumentRef(DocumentId("other")), StorageDocumentRef("p", "x"), exact, DocumentAvailability.UNKNOWN)
        }
    }

    @Test fun readingMetadataHasExplicitDefaultsAndRevisionBoundPosition() {
        assertFalse(DocumentMetadata().favorite)
        assertNull(DocumentMetadata().readingPosition)
        val position = ReadingPosition(PageIndex(5), DocumentRevision.Unknown(document))
        val metadata = DocumentMetadata(true, position)
        assertTrue(metadata.favorite)
        assertEquals(PageIndex(5), metadata.readingPosition?.page)
        assertEquals(document, metadata.readingPosition?.revision?.document)
    }
}
