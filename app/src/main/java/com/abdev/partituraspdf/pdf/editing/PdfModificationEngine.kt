package com.abdev.partituraspdf.pdf.editing

import com.abdev.partituraspdf.pdf.api.*
import com.abdev.partituraspdf.storage.api.*
import com.abdev.partituraspdf.storage.transaction.PrivateSnapshotStore

internal sealed interface EngineResult {
    data class Produced(val output: WorkingSnapshot) : EngineResult
    data class Rejected(val reason: PdfOperationFailure, val cleanup: SnapshotCleanupStatus = SnapshotCleanupStatus.COMPLETE) : EngineResult
}
internal interface PdfModificationEngine {
    suspend fun transform(source: WorkingSnapshot, plan: OrderedPagePlan, transaction: TransactionId,
                          store: PrivateSnapshotStore): EngineResult
}
internal sealed interface ValidationResult {
    data class Accepted(val artifact: ValidatedPdfArtifact) : ValidationResult
    data class Refused(val report: PdfValidationReport) : ValidationResult
}
internal interface PdfOperationValidator {
    suspend fun validate(source: WorkingSnapshot, output: WorkingSnapshot, plan: OrderedPagePlan): ValidationResult
}
internal interface PdfModificationAdapters : PdfModificationEngine, PdfOperationValidator

/** Explicit unavailable adapters: no copy fallback and no renderer/hash-based semantic claim. */
internal object EngineApprovalRequired : PdfModificationEngine, PdfOperationValidator {
    override suspend fun transform(source: WorkingSnapshot, plan: OrderedPagePlan, transaction: TransactionId,
                                   store: PrivateSnapshotStore) = EngineResult.Rejected(PdfOperationFailure.UnsupportedDocument)
    override suspend fun validate(source: WorkingSnapshot, output: WorkingSnapshot, plan: OrderedPagePlan) =
        ValidationResult.Refused(PdfValidationReport.Unsupported)
}
