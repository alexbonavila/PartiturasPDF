package com.abdev.partituraspdf.data.metadata

import com.abdev.partituraspdf.core.document.*
import org.junit.Assert.*
import org.junit.Test

class MetadataReadingPositionPolicyTest {
    private val document = DocumentRef(DocumentId("document"))
    private val other = DocumentRef(DocumentId("other"))
    private val unknown = DocumentRevision.Unknown(document)
    private val first = DocumentRevision.VerifiedSnapshot(document, SnapshotEvidence("a".repeat(64), 42))
    private val next = DocumentRevision.VerifiedSnapshot(document, SnapshotEvidence("b".repeat(64), 43))

    @Test fun verifiedPositionSuppliesExactExpectationWithoutBeingRewritten() {
        val position = ReadingPosition(PageIndex(7), first)
        for (current in listOf(unknown, first, next)) {
            assertSame(first, expectedReadingPositionRevision(document, current, position))
            assertSame(first, position.revision)
            assertEquals(PageIndex(7), position.page)
        }
    }

    @Test fun unknownPositionGuardsCurrentRegistryWhileRemainingOnlyAHint() {
        val hint = ReadingPosition(PageIndex(0), unknown)
        for (current in listOf(unknown, first, next)) {
            assertSame(current, expectedReadingPositionRevision(document, current, hint))
            assertSame(unknown, hint.revision)
        }
    }

    @Test fun clearingUsesCurrentRevisionWithoutAHistoricalExpectedRevision() {
        validateReadingPositionOwnership(document, null)
        for (current in listOf(unknown, first, next)) {
            assertSame(current, expectedReadingPositionRevision(document, current, null))
        }
    }

    @Test fun foreignPositionAndRegistryRevisionAreRejectedAsProgrammingErrors() {
        for (revision in listOf(DocumentRevision.Unknown(other),
            DocumentRevision.VerifiedSnapshot(other, SnapshotEvidence("a".repeat(64), 42)))) {
            val foreign = ReadingPosition(PageIndex(0), revision)
            assertThrows(IllegalArgumentException::class.java) { validateReadingPositionOwnership(document, foreign) }
            assertThrows(IllegalArgumentException::class.java) { expectedReadingPositionRevision(document, first, foreign) }
            assertThrows(IllegalArgumentException::class.java) { expectedReadingPositionRevision(document, revision, null) }
        }
    }
}
