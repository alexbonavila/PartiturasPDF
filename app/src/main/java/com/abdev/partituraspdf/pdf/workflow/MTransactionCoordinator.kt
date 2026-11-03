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
import java.io.OutputStream
import java.util.UUID

internal class MPickerHandoff internal constructor(val operation: TransactionId, internal val token: String) {
    val mime = "application/pdf"
}
internal sealed interface MPreparation {
    data object Busy : MPreparation
    data class Rejected(val reason: PdfOperationFailure, val cleanup: SnapshotCleanupStatus = SnapshotCleanupStatus.COMPLETE,
                        internal val cleanupFault: Throwable? = null) : MPreparation
    data class Ready(val session: MTransactionSession, val request: MPickerHandoff) : MPreparation
}
internal fun interface MLeaseCopier {
    suspend fun copy(lease: SealedSnapshotLease, output: OutputStream, maximum: Long)
}
internal object NativeMLeaseCopier : MLeaseCopier {
    override suspend fun copy(lease: SealedSnapshotLease, output: OutputStream, maximum: Long) {
        ParcelFileDescriptor.AutoCloseInputStream(lease.descriptor).use { source ->
            val buffer = ByteArray(64 * 1024); var count = 0L
            while (true) {
                currentCoroutineContext().ensureActive()
                val n = source.read(buffer)
                if (n < 0) break
                if (n == 0 || n > maximum - count) throw IOException("Invalid pinned bytes")
                output.write(buffer, 0, n); count += n
            }
            if (count != maximum) throw IOException("Incomplete pinned bytes")
        }
    }
}
/** Only the session owns live admission. No launch request escapes before durable intent. */
internal class MTransactionCoordinator(
    private val gate: WriteAdmissionGate, private val capturer: MSourceCapturer,
    private val store: PrivateSnapshotStore, private val journal: MPublicationJournal,
    private val engine: PdfModificationEngine = EngineApprovalRequired,
    private val validator: PdfOperationValidator = EngineApprovalRequired,
    private val publisher: NewDocumentPublisher = NewDocumentPublisher(),
    private val copier: MLeaseCopier = NativeMLeaseCopier,
) {
    suspend fun prepare(source: SnapshotSource, plan: OrderedPagePlan): MPreparation {
        val admission = gate.tryAcquire()
        if (admission !is WriteAdmissionGate.Admission.Acquired) return MPreparation.Busy
        val id = TransactionId(UUID.randomUUID().toString())
        val fingerprint = RecordCodec.hash(plan.selections.joinToString(";") { "${it.sourcePageIndex}:${it.addedClockwiseQuarterTurns}" }.toByteArray(Charsets.UTF_8))
        var input: WorkingSnapshot? = null; var output: WorkingSnapshot? = null
        var issued = false; var primary: Throwable? = null; var result: MPreparation? = null
        var pending = false
        suspend fun perform(): MPreparation {
            suspend fun checkpoint(state: MState) = journal.append(id, state, fingerprint, input?.evidence, output?.evidence) != null
            if (!checkpoint(MState.PREPARING)) return MPreparation.Rejected(PdfOperationFailure.ResourceUnavailable)
            val captured = capturer.captureForTransaction(source, SnapshotCaptureLimits(64L * 1024 * 1024), id)
            if (captured is SnapshotCaptureResult.Rejected) return MPreparation.Rejected(PdfOperationFailure.InvalidInput, captured.cleanup)
            val capturedInput = (captured as SnapshotCaptureResult.Captured).snapshot.also { input = it }
            if (!checkpoint(MState.PRIVATE_INPUT_SEALED)) return MPreparation.Rejected(PdfOperationFailure.ResourceUnavailable)
            val produced = engine.transform(capturedInput, plan, id, store)
            if (produced is EngineResult.Rejected) return MPreparation.Rejected(produced.reason)
            val producedOutput = (produced as EngineResult.Produced).output.also { output = it }
            if (producedOutput.origin != SnapshotOrigin.Produced(id) || !checkpoint(MState.PRIVATE_OUTPUT_SEALED)) return MPreparation.Rejected(PdfOperationFailure.TransformationFailure)
            val validated = validator.validate(capturedInput, producedOutput, plan)
            if (validated !is ValidationResult.Accepted) return MPreparation.Rejected(PdfOperationFailure.ValidationInconclusive)
            val artifact = validated.artifact
            check(artifact.output === output && artifact.plan === plan)
            if (!checkpoint(MState.PRIVATE_OUTPUT_VALIDATED) || !checkpoint(MState.PREPARED_FOR_PICKER) ||
                !checkpoint(MState.PICKER_MAY_HAVE_LAUNCHED)) return MPreparation.Rejected(PdfOperationFailure.ResourceUnavailable)
            val request = MPickerHandoff(id, UUID.randomUUID().toString())
            val session = MTransactionSession(request, fingerprint, capturedInput, artifact, admission, journal, publisher, copier)
            issued = true // No suspension between handoff and return. Only an issued request can be launched.
            return MPreparation.Ready(session, request)
        }
        try { result = perform() }
        catch (t: Throwable) { primary = t; throw t }
        finally {
            if (!issued) {
                try { pending = releaseSnapshots(listOfNotNull(output, input), primary) }
                catch (cleanup: Throwable) {
                    val rejected = result as? MPreparation.Rejected
                    if (rejected == null) throw cleanup
                    pending = true
                    result = rejected.copy(cleanup = SnapshotCleanupStatus.PENDING, cleanupFault = cleanup)
                } finally { admission.close() } // No host token escaped, hence no live/public writer.
            }
        }
        val actual = checkNotNull(result)
        return if (actual is MPreparation.Rejected && pending) actual.copy(cleanup = SnapshotCleanupStatus.PENDING) else actual
    }
}

internal enum class MOwnerResolution { ACKNOWLEDGED, ACKNOWLEDGED_RECORD_UNCONFIRMED, ALREADY_RESOLVED }

/** Serialized callback, resolution and cleanup. Mutex may suspend; no provider I/O under a monitor. */
internal class MTransactionSession(
    private val request: MPickerHandoff, private val plan: String, private val input: WorkingSnapshot,
    private val artifact: ValidatedPdfArtifact, private val admission: WriteAdmissionGate.Admission.Acquired,
    private val journal: MPublicationJournal, private val publisher: NewDocumentPublisher,
    private val copier: MLeaseCopier = NativeMLeaseCopier,
) {
    private val mutex = Mutex(); private var consumed = false; private var resolved = false
    private var verifiedResult: PublicationResult.VerifiedNewDocument? = null
    private var cleanupDefect: Throwable? = null
    /** Private diagnostic channel: never serialize/log arbitrary exception messages. */
    suspend fun cleanupFailure(): Throwable? = mutex.withLock { cleanupDefect }
    suspend fun verifiedPublication(): PublicationResult.VerifiedNewDocument? = mutex.withLock { verifiedResult }
    suspend fun complete(token: String, destination: WriteDestinationCapability?): PublicationResult = mutex.withLock {
        if (resolved || consumed || token != request.token) return@withLock PublicationResult.NeedsOwnerResolution
        consumed = true
        try {
            suspend fun checkpoint(state: MState, cap: WriteDestinationCapability? = destination) = journal.append(request.operation, state,
                plan, input.evidence, artifact.evidence, cap?.port?.destination?.value, cap?.port?.observedFlags ?: 0)
            if (destination == null) { checkpoint(MState.DESTINATION_UNKNOWN, null); return@withLock PublicationResult.UncertainPublication }
            if (destination.port.operation != request.operation || destination.port.destination == (input.origin as? SnapshotOrigin.Source)?.source?.storage)
                return@withLock PublicationResult.NeedsOwnerResolution
            if (checkpoint(MState.DESTINATION_OBSERVED) == null || !destination.port.revalidate() ||
                checkpoint(MState.DESTINATION_BOUND) == null || checkpoint(MState.PUBLISHING) == null)
                return@withLock PublicationResult.UncertainPublication
            val lease = (artifact.acquire() as? SnapshotLeaseResult.Acquired)?.lease ?: return@withLock PublicationResult.UncertainPublication
            var primary: Throwable? = null
            val observed = try {
                publisher.publish(destination, artifact.evidence) { output -> copier.copy(lease, output, artifact.evidence.byteCount) }
            } catch (t: Throwable) { primary = t; throw t }
            finally {
                try { withContext(NonCancellable + Dispatchers.IO) { lease.close() } }
                catch (cleanup: Throwable) { if (primary == null) throw cleanup else primary.suppressSecondary(cleanup) }
            }
            if (checkpoint(MState.PUBLISHED_UNVERIFIED) == null || observed != ReadbackResult.Match) return@withLock PublicationResult.UnverifiedDestination
            if (checkpoint(MState.READBACK_OBSERVED) == null) return@withLock PublicationResult.UnverifiedDestination
            val final = checkpoint(MState.VERIFIED_COMMITTED) ?: return@withLock PublicationResult.UnverifiedDestination
            // Save the durable public outcome BEFORE any private cleanup. Cancellation or a
            // programming defect in cleanup must not erase it from the session's observation.
            val verified = PublicationResult.VerifiedNewDocument(destination.port.destination, artifact.evidence,
                final, SnapshotCleanupStatus.PENDING)
            verifiedResult = verified
            try {
                val authorized = checkpoint(MState.CLEANUP_AUTHORIZED) != null
                var pending = true
                if (authorized) {
                    pending = releaseSnapshots(listOf(artifact.output, input), null)
                    if (!pending && checkpoint(MState.CLEANED) == null) pending = true
                }
                verified.copy(cleanup = if (pending) SnapshotCleanupStatus.PENDING else SnapshotCleanupStatus.COMPLETE)
                    .also { verifiedResult = it }
            } catch (cancel: CancellationException) {
                // Caller cancellation propagates; verifiedPublication still exposes the commit.
                cleanupDefect = cancel
                throw cancel
            } catch (cleanup: Throwable) {
                cleanupDefect = cleanup
                verified // PENDING plus the explicit diagnostic channel, never false Released.
            } finally { resolved = true; admission.close() }
        } catch (cancel: CancellationException) { throw cancel }
        catch (_: IOException) { PublicationResult.UncertainPublication }
        catch (_: SecurityException) { PublicationResult.UncertainPublication }
    }

    /** Explicit owner acknowledgement of unresolved public effects. Never retry/delete/clean here.
     * Mutex acquisition waits for any in-flight publication and descriptor finally to finish.
     */
    suspend fun acknowledgeUnresolved(): MOwnerResolution = mutex.withLock {
        if (resolved) return@withLock MOwnerResolution.ALREADY_RESOLVED
        // The owner's explicit acknowledgement can release in-memory exclusion once no write
        // remains active, even when journal corruption prevents recording that acknowledgement.
        // Such a release never validates/repairs/deletes the uncertain history.
        val persisted = try {
            val latest = withContext(Dispatchers.IO) { journal.read(request.operation).lastOrNull() }
            if (latest == null) false
            else if (latest.state in setOf(MState.VERIFIED_COMMITTED, MState.CLEANUP_AUTHORIZED, MState.CLEANED, MState.OWNER_RESOLVED)) true
            else journal.append(request.operation, MState.OWNER_RESOLVED, latest.plan, latest.source, latest.output,
                latest.uri, latest.flags) != null
        } catch (_: IOException) { false }
        resolved = true; consumed = true; admission.close()
        if (persisted) MOwnerResolution.ACKNOWLEDGED else MOwnerResolution.ACKNOWLEDGED_RECORD_UNCONFIRMED
    }
}

/** Attempt all owned releases even when one fails, never erase the first exception. */
private suspend fun releaseSnapshots(snapshots: List<WorkingSnapshot>, primary: Throwable?): Boolean = withContext(NonCancellable) {
    var pending = false; var defect: Throwable? = null
    for (snapshot in snapshots.distinct()) {
        try { snapshot.requestClose()
            if (snapshot.awaitReleased() !is SnapshotReleaseResult.Released) pending = true
        } catch (cleanup: Throwable) {
            pending = true
            if (primary != null) primary.suppressSecondary(cleanup)
            else { val earlier = defect; if (earlier == null) defect = cleanup else earlier.suppressSecondary(cleanup) }
        }
    }
    if (primary != null && pending) primary.suppressSecondary(SnapshotCleanupPendingException())
    defect?.let { throw it }
    pending
}
private fun Throwable.suppressSecondary(secondary: Throwable) { if (secondary !== this) addSuppressed(secondary) }
