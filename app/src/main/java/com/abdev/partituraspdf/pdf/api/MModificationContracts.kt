package com.abdev.partituraspdf.pdf.api

import com.abdev.partituraspdf.storage.api.WorkingSnapshot
import com.abdev.partituraspdf.storage.api.SnapshotLeaseResult
import java.util.Collections

/** Syntactic selection, not evidence of a page's existence in a parsed PDF. */
data class PageSelection(val sourcePageIndex: Int, val addedClockwiseQuarterTurns: Int) {
    init { require(sourcePageIndex >= 0); require(addedClockwiseQuarterTurns in 0..3) }
}
class OrderedPagePlan(selections: List<PageSelection>) {
    val selections: List<PageSelection> = Collections.unmodifiableList(selections.toList())
    init { require(this.selections.isNotEmpty()); require(this.selections.size <= 10000)
        require(this.selections.map { it.sourcePageIndex }.distinct().size == this.selections.size) }
}
enum class PdfOperationFailure {
    InvalidPlan, UnsupportedDocument, ProtectedDocument, InvalidInput, TransformationFailure,
    OutputLimit, ResourceUnavailable, ValidationRejected, ValidationInconclusive, Closing,
}
sealed interface PdfValidationReport {
    data object Supported : PdfValidationReport
    data object Rejected : PdfValidationReport
    data object Unsupported : PdfValidationReport
    data object Inconclusive : PdfValidationReport
}
/** Only a qualified semantic validator may construct this. Tests are explicit privileged seams.
 * Block A's feature-private production validator is selected by an explicit coordinator factory;
 * default/host activation remains unavailable pending the recorded owner acceptance gates.
 */
class ValidatedPdfArtifact internal constructor(
    internal val output: WorkingSnapshot,
    val plan: OrderedPagePlan,
) {
    val evidence get() = output.evidence
    internal suspend fun acquire(): SnapshotLeaseResult = output.acquireReadLease()
}
