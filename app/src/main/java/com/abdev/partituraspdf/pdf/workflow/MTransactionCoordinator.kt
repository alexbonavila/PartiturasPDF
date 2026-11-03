package com.abdev.partituraspdf.pdf.workflow

import android.os.ParcelFileDescriptor
import com.abdev.partituraspdf.pdf.api.*
import com.abdev.partituraspdf.pdf.editing.*
import com.abdev.partituraspdf.storage.api.*
import com.abdev.partituraspdf.storage.transaction.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException
import java.util.UUID

/** Opaque one-shot future-host request. No ActivityResult launcher lives in M. */
internal class MPickerHandoff internal constructor(val operation: TransactionId, internal val token: String) {
    val mime = "application/pdf"
}
internal sealed interface MPreparation {
    data object Busy : MPreparation
    data class Rejected(val reason: PdfOperationFailure, val cleanup: SnapshotCleanupStatus = SnapshotCleanupStatus.COMPLETE) : MPreparation
    data class Ready(val session: MTransactionSession, val request: MPickerHandoff) : MPreparation
}
internal class MTransactionCoordinator(
    private val gate: WriteAdmissionGate, private val capturer: TransactionSnapshotCapturer,
    private val store: PrivateSnapshotStore, private val journal: MPublicationJournal,
    private val engine: PdfModificationEngine = EngineApprovalRequired,
    private val validator: PdfOperationValidator = EngineApprovalRequired,
    private val publisher: NewDocumentPublisher = NewDocumentPublisher(),
) {
    suspend fun prepare(source: SnapshotSource, plan: OrderedPagePlan): MPreparation {
        val admission = gate.tryAcquire()
        if (admission !is WriteAdmissionGate.Admission.Acquired) return MPreparation.Busy
        val id = TransactionId(UUID.randomUUID().toString())
        val fingerprint = RecordCodec.hash(plan.selections.joinToString(";") { "${it.sourcePageIndex}:${it.addedClockwiseQuarterTurns}" }.toByteArray(Charsets.UTF_8))
        var input: WorkingSnapshot? = null; var output: WorkingSnapshot? = null; var issued = false
        var possibleLaunch = false; var primary: Throwable? = null
        try {
            suspend fun checkpoint(state: MState) = journal.append(id, state, fingerprint, input?.evidence, output?.evidence) != null
            if (!checkpoint(MState.PREPARING)) return MPreparation.Rejected(PdfOperationFailure.ResourceUnavailable)
            val captured = capturer.captureForTransaction(source, SnapshotCaptureLimits(64L * 1024 * 1024), id)
            if (captured is SnapshotCaptureResult.Rejected) return MPreparation.Rejected(PdfOperationFailure.InvalidInput, captured.cleanup)
            input = (captured as SnapshotCaptureResult.Captured).snapshot
            if (!checkpoint(MState.PRIVATE_INPUT_SEALED)) return MPreparation.Rejected(PdfOperationFailure.ResourceUnavailable)
            val capturedInput = checkNotNull(input)
            val produced = engine.transform(capturedInput, plan, id, store)
            if (produced is EngineResult.Rejected) return MPreparation.Rejected(produced.reason)
            output = (produced as EngineResult.Produced).output
            if (output.origin != SnapshotOrigin.Produced(id) || !checkpoint(MState.PRIVATE_OUTPUT_SEALED)) return MPreparation.Rejected(PdfOperationFailure.TransformationFailure)
            val producedOutput = checkNotNull(output)
            val validated = validator.validate(capturedInput, producedOutput, plan)
            if (validated !is ValidationResult.Accepted) return MPreparation.Rejected(PdfOperationFailure.ValidationInconclusive)
            val artifact = validated.artifact
            check(artifact.output === output && artifact.plan === plan)
            if (!checkpoint(MState.PRIVATE_OUTPUT_VALIDATED) || !checkpoint(MState.PREPARED_FOR_PICKER)) return MPreparation.Rejected(PdfOperationFailure.ResourceUnavailable)
            // Once attempting this checkpoint, cancellation/failure may have persisted launch intent.
            possibleLaunch = true
            if (!checkpoint(MState.PICKER_MAY_HAVE_LAUNCHED)) return MPreparation.Rejected(PdfOperationFailure.ResourceUnavailable)
            val request = MPickerHandoff(id, UUID.randomUUID().toString())
            val session = MTransactionSession(request, fingerprint, capturedInput, artifact, admission, journal, publisher)
            issued = true
            return MPreparation.Ready(session, request)
        } catch (t: Throwable) { primary = t; throw t }
        finally {
            if (!issued && !possibleLaunch) {
                try { withContext(NonCancellable) {
                    for (snapshot in listOfNotNull(output, input)) { snapshot.requestClose(); snapshot.awaitReleased() }
                } } catch (cleanup: Throwable) { if (primary == null) throw cleanup else if (primary !== cleanup) primary.addSuppressed(cleanup) }
                finally { admission.close() }
            }
            // Unknown public state retains its admission and disk artifacts; restart recovery, never retry.
        }
    }
}
internal class MTransactionSession(
    private val request: MPickerHandoff, private val plan: String, private val input: WorkingSnapshot,
    private val artifact: ValidatedPdfArtifact, private val admission: WriteAdmissionGate.Admission.Acquired,
    private val journal: MPublicationJournal, private val publisher: NewDocumentPublisher,
) {
    private val mutex = Mutex(); private var consumed = false
    suspend fun complete(token: String, destination: WriteDestinationCapability?): PublicationResult = mutex.withLock {
        if (consumed || token != request.token) return@withLock PublicationResult.NeedsOwnerResolution
        consumed = true
        val id = request.operation
        suspend fun checkpoint(state: MState, cap: WriteDestinationCapability? = destination) = journal.append(id, state,
            plan, input.evidence, artifact.evidence, cap?.port?.destination?.value, cap?.port?.observedFlags ?: 0)
        if (destination == null) { checkpoint(MState.DESTINATION_UNKNOWN, null); return@withLock PublicationResult.UncertainPublication }
        if (destination.port.operation != id || destination.port.destination == (input.origin as? SnapshotOrigin.Source)?.source?.storage)
            return@withLock PublicationResult.NeedsOwnerResolution
        if (checkpoint(MState.DESTINATION_OBSERVED) == null || !destination.port.revalidate() ||
            checkpoint(MState.DESTINATION_BOUND) == null || checkpoint(MState.PUBLISHING) == null)
            return@withLock PublicationResult.UncertainPublication
        val lease = (artifact.acquire() as? SnapshotLeaseResult.Acquired)?.lease ?: return@withLock PublicationResult.UncertainPublication
        var primary: Throwable? = null
        val observed = try {
            publisher.publish(destination, artifact.evidence) { output ->
                ParcelFileDescriptor.AutoCloseInputStream(lease.descriptor).use { source ->
                    val buffer = ByteArray(64 * 1024); var count = 0L
                    while (true) { currentCoroutineContext().ensureActive(); val n = source.read(buffer)
                        if (n < 0) break
                        if (n == 0 || n > artifact.evidence.byteCount - count) throw IOException("Invalid pinned bytes")
                        output.write(buffer, 0, n); count += n
                    }
                    if (count != artifact.evidence.byteCount) throw IOException("Incomplete pinned bytes")
                }
            }
        } catch (t: Throwable) { primary = t; throw t }
        finally {
            try { withContext(NonCancellable + Dispatchers.IO) { lease.close() } }
            catch (cleanup: Throwable) { if (primary == null) throw cleanup else if (primary !== cleanup) primary.addSuppressed(cleanup) }
        }
        if (checkpoint(MState.PUBLISHED_UNVERIFIED) == null || observed != ReadbackResult.Match) return@withLock PublicationResult.UnverifiedDestination
        if (checkpoint(MState.READBACK_OBSERVED) == null) return@withLock PublicationResult.UnverifiedDestination
        val final = checkpoint(MState.VERIFIED_COMMITTED) ?: return@withLock PublicationResult.UnverifiedDestination
        // Verified receipt is allowed only after the durable terminal ack. Keep cleanup visibility.
        withContext(NonCancellable) { artifact.output.requestClose(); input.requestClose()
            if (artifact.output.awaitReleased() !is SnapshotReleaseResult.Released || input.awaitReleased() !is SnapshotReleaseResult.Released)
                return@withContext
            admission.close()
        }
        PublicationResult.VerifiedNewDocument(destination.port.destination, artifact.evidence, final)
    }
}
