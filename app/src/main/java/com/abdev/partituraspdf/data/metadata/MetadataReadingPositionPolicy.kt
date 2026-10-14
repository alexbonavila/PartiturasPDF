package com.abdev.partituraspdf.data.metadata

import com.abdev.partituraspdf.core.document.DocumentRef
import com.abdev.partituraspdf.core.document.DocumentRevision
import com.abdev.partituraspdf.core.document.ReadingPosition

internal fun validateReadingPositionOwnership(document: DocumentRef, position: ReadingPosition?) {
    require(position == null || position.revision.document == document)
}

/**
 * An exact position supplies its own expected revision and must not be rewritten on conflict.
 * An Unknown position is only a hint: compare the registry revision read in the writer transaction
 * while storing the original Unknown position. Clearing supplies no expectation, so it uses that
 * same transaction's current revision, even when the historical position is now stale.
 */
internal fun expectedReadingPositionRevision(
    document: DocumentRef, current: DocumentRevision, position: ReadingPosition?,
): DocumentRevision {
    require(current.document == document)
    validateReadingPositionOwnership(document, position)
    return (position?.revision as? DocumentRevision.VerifiedSnapshot) ?: current
}
