package com.abdev.partituraspdf.storage.api

import android.os.ParcelFileDescriptor
import com.abdev.partituraspdf.core.document.DocumentRef
import com.abdev.partituraspdf.core.document.SnapshotEvidence
import com.abdev.partituraspdf.core.document.StorageDocumentRef
import java.io.Closeable
import java.util.UUID

internal fun requireCanonicalUuid(value: String) {
    val canonical = try { UUID.fromString(value).toString() } catch (_: IllegalArgumentException) { null }
    require(canonical == value)
}

/** Value identity, never authority to mint or publish an artifact. */
@JvmInline value class SnapshotId(val value: String) {
    init { requireCanonicalUuid(value) }
}

data class SnapshotSource(val storage: StorageDocumentRef, val registered: DocumentRef? = null)
sealed interface SnapshotOrigin {
    data class Source(val source: SnapshotSource) : SnapshotOrigin
    data class Produced(val transactionId: TransactionId) : SnapshotOrigin
}
data class SnapshotCaptureLimits(val maxInputBytes: Long) {
    init { require(maxInputBytes in 1..64L * 1024 * 1024) }
}
sealed interface SnapshotFailure {
    data object AccessDenied : SnapshotFailure
    data object AccessRevoked : SnapshotFailure
    data object SourceUnavailable : SnapshotFailure
    data object SourceReadFailure : SnapshotFailure
    data object InputLimitExceeded : SnapshotFailure
    data object InsufficientPrivateSpace : SnapshotFailure
    data object PrivateStorageUnavailable : SnapshotFailure
    data object SealFailure : SnapshotFailure
    data object Closing : SnapshotFailure
    data object Closed : SnapshotFailure
    data object CleanupFailure : SnapshotFailure
}
sealed interface SnapshotCaptureResult {
    data class Captured(val snapshot: WorkingSnapshot) : SnapshotCaptureResult
    data class Rejected(val reason: SnapshotFailure) : SnapshotCaptureResult
}
sealed interface SnapshotLeaseResult {
    data class Acquired(val lease: SealedSnapshotLease) : SnapshotLeaseResult
    data class Rejected(val reason: SnapshotFailure) : SnapshotLeaseResult
}
sealed interface SnapshotReleaseResult {
    data object Released : SnapshotReleaseResult
    data class CleanupPending(val reason: SnapshotFailure.CleanupFailure) : SnapshotReleaseResult
}
interface SnapshotCapturer {
    suspend fun capture(source: SnapshotSource, limits: SnapshotCaptureLimits): SnapshotCaptureResult
}

internal interface SnapshotOwnerPort {
    val id: SnapshotId
    val origin: SnapshotOrigin
    val evidence: SnapshotEvidence
    suspend fun acquire(): SnapshotLeaseResult
    fun requestClose()
    suspend fun awaitReleased(): SnapshotReleaseResult
}
internal interface SnapshotLeasePort : Closeable { val descriptor: ParcelFileDescriptor }

/** Trusted creation is confined to storage.transaction's verified sealing path. */
class WorkingSnapshot internal constructor(private val owner: SnapshotOwnerPort) {
    val id: SnapshotId get() = owner.id
    val origin: SnapshotOrigin get() = owner.origin
    val evidence: SnapshotEvidence get() = owner.evidence
    suspend fun acquireReadLease(): SnapshotLeaseResult = owner.acquire()
    fun requestClose() = owner.requestClose()
    suspend fun awaitReleased(): SnapshotReleaseResult = owner.awaitReleased()
}

/** Exactly one wrapper owns a pin. A transfer does not duplicate its descriptor. */
class SealedSnapshotLease internal constructor(private val port: SnapshotLeasePort) : Closeable {
    private enum class State { OWNED, TRANSFERRED, CLOSED }
    private var state = State.OWNED
    val descriptor: ParcelFileDescriptor @Synchronized get() {
        check(state == State.OWNED)
        return port.descriptor
    }
    @Synchronized fun transferOwnership(): SealedSnapshotLease {
        check(state == State.OWNED)
        state = State.TRANSFERRED
        return SealedSnapshotLease(port)
    }
    override fun close() {
        val release = synchronized(this) {
            check(state != State.TRANSFERRED)
            if (state == State.CLOSED) false else { state = State.CLOSED; true }
        }
        // Descriptor I/O and snapshot cleanup never run while holding the wrapper monitor.
        if (release) port.close()
    }
}
