package com.abdev.partituraspdf.storage.transaction

import com.abdev.partituraspdf.core.document.SnapshotEvidence
import com.abdev.partituraspdf.storage.api.TransactionId
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.withLock
import java.io.*
import java.security.MessageDigest

/** Reserved states are encodable for future schema compatibility, not reachable by this writer. */
internal enum class TransactionState {
    PREPARING, PRIVATE_INPUT_SEALED, PRIVATE_OUTPUT_SEALED, PREPARED_FOR_PICKER,
    PICKER_MAY_HAVE_LAUNCHED, DESTINATION_UNKNOWN, DESTINATION_OBSERVED, DESTINATION_BOUND,
    PUBLISHING, PUBLISHED_UNVERIFIED, UNCERTAIN_PUBLICATION, VERIFIED_COMMITTED,
    ABORTED_CLEAN, FAILED_RECOVERABLE, CLEANUP_PENDING,
}
internal sealed interface DestinationState { data object Unknown : DestinationState }

internal data class PrivateRecord(
    val transaction: TransactionId,
    val sequence: Long,
    val state: TransactionState,
    val predecessor: String,
    val source: SnapshotEvidence? = null,
    val output: SnapshotEvidence? = null,
    // Block 1 has no binding authority. True does not mean a picker actually launched.
    val destination: DestinationState = DestinationState.Unknown,
)
internal class JournalFormatException : IOException("Blocked journal")

/** Deterministic schema v1; limits checked before record/payload allocation. */
internal object RecordCodec {
    const val MAX_PAYLOAD = 64 * 1024
    const val MAX_RECORD = MAX_PAYLOAD + 256
    const val MAX_CHECKPOINTS = 256
    val genesis = "0".repeat(64)
    fun hash(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).hex()
    fun encode(record: PrivateRecord): ByteArray {
        require(record.sequence in 1..MAX_CHECKPOINTS.toLong())
        require(record.predecessor.matches(Regex("[0-9a-f]{64}")))
        require(record.destination == DestinationState.Unknown)
        val payload = ByteArrayOutputStream().also { bytes ->
            DataOutputStream(bytes).use { out ->
                out.writeBoolean(true)
                for (evidence in listOf(record.source, record.output)) {
                    out.writeBoolean(evidence != null)
                    if (evidence != null) { out.writeUTF(evidence.sha256); out.writeLong(evidence.byteCount) }
                }
            }
        }.toByteArray()
        require(payload.size <= MAX_PAYLOAD)
        val body = ByteArrayOutputStream().also { bytes ->
            DataOutputStream(bytes).use { out ->
                out.writeInt(0x50444d31); out.writeInt(1)
                out.writeUTF(record.transaction.value); out.writeLong(record.sequence)
                out.writeUTF(record.state.name); out.writeUTF(record.predecessor)
                out.writeInt(payload.size); out.write(payload)
            }
        }.toByteArray()
        return body + MessageDigest.getInstance("SHA-256").digest(body)
    }
    fun decode(bytes: ByteArray): PrivateRecord {
        if (bytes.size !in 32..MAX_RECORD) throw JournalFormatException()
        val body = bytes.copyOf(bytes.size - 32)
        if (!MessageDigest.isEqual(MessageDigest.getInstance("SHA-256").digest(body), bytes.takeLast(32).toByteArray())) throw JournalFormatException()
        try {
            val input = DataInputStream(ByteArrayInputStream(body))
            if (input.readInt() != 0x50444d31 || input.readInt() != 1) throw JournalFormatException()
            val id = TransactionId(input.readUTF())
            val sequence = input.readLong()
            val state = TransactionState.valueOf(input.readUTF())
            val predecessor = input.readUTF()
            val length = input.readInt()
            if (length !in 1..MAX_PAYLOAD || length != input.available()) throw JournalFormatException()
            // Decode booleans strictly: DataInputStream.readBoolean otherwise accepts any nonzero byte.
            fun boolean(): Boolean = when (input.readUnsignedByte()) {
                0 -> false; 1 -> true; else -> throw JournalFormatException()
            }
            if (!boolean()) throw JournalFormatException()
            fun evidence(): SnapshotEvidence? = if (!boolean()) null else SnapshotEvidence(input.readUTF(), input.readLong())
            val result = PrivateRecord(id, sequence, state, predecessor, evidence(), evidence(), DestinationState.Unknown)
            if (input.available() != 0 || !encode(result).contentEquals(bytes)) throw JournalFormatException()
            return result
        } catch (_: IllegalArgumentException) { throw JournalFormatException() }
        catch (_: EOFException) { throw JournalFormatException() }
    }
    fun read(file: File): ByteArray {
        if (file.length() !in 32..MAX_RECORD.toLong()) throw JournalFormatException()
        FileInputStream(file).use { input ->
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(4096)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                if (count == 0 || count > MAX_RECORD - out.size()) throw JournalFormatException()
                out.write(buffer, 0, count)
            }
            return out.toByteArray()
        }
    }
}

internal sealed interface CheckpointResult {
    data class Durable(val record: PrivateRecord, val hash: String) : CheckpointResult
    data object InvalidTransition : CheckpointResult
    data object Blocked : CheckpointResult
    data object CheckpointUnconfirmed : CheckpointResult
    data object DurabilityUnavailable : CheckpointResult
}

/** No public mutation entry point, provider capability, picker launcher or validated-artifact API. */
internal class PrivateJournal(
    private val budget: MStorageBudget,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val root get() = budget.root
    suspend fun appendPrivate(
        transaction: TransactionId, state: TransactionState,
        source: SnapshotEvidence? = null, output: SnapshotEvidence? = null,
    ): CheckpointResult = withContext(dispatcher) {
        root.mutations.withLock {
            currentCoroutineContext().ensureActive()
            var renamed = false
            var syncingDirectory = false
            try {
                val tx = root.check(File(root.directory, "tx-${transaction.value}"))
                if (!tx.exists()) {
                    if (!tx.mkdir()) throw IOException("Private transaction unavailable")
                    syncingDirectory = true
                    root.io.syncDirectory(tx); root.io.syncDirectory(root.directory)
                    syncingDirectory = false
                }
                val journal = root.check(File(tx, "journal"))
                if (!journal.exists()) {
                    if (!journal.mkdir()) throw IOException("Private journal unavailable")
                    syncingDirectory = true
                    root.io.syncDirectory(journal); root.io.syncDirectory(tx)
                    syncingDirectory = false
                }
                val history = readChain(root, tx, transaction)
                val previous = history.lastOrNull()?.first
                if (!privateTransition(previous, state, source, output)) return@withLock CheckpointResult.InvalidTransition
                val sequence = (previous?.sequence ?: 0) + 1
                if (sequence > RecordCodec.MAX_CHECKPOINTS) return@withLock CheckpointResult.Blocked
                val record = PrivateRecord(transaction, sequence, state,
                    history.lastOrNull()?.second ?: RecordCodec.genesis, source, output)
                val bytes = RecordCodec.encode(record)
                val name = sequence.toString().padStart(12, '0')
                val temporary = root.check(File(journal, "$name.tmp"))
                val published = root.check(File(journal, "$name.rec"))
                budget.reserve(temporary, bytes.size.toLong())
                root.create(temporary)
                // The critical filesystem sequence is finite local I/O, not a global detached job.
                // Cancellation is observed before and after it; no canceled call returns Durable.
                withContext(NonCancellable) {
                    root.io.output(temporary).use { out -> out.write(bytes); root.io.syncFile(out) }
                    root.io.renameNew(temporary, published)
                    budget.renamed(temporary, published); renamed = true
                    syncingDirectory = true
                    root.io.syncDirectory(journal)
                    syncingDirectory = false
                    val reread = RecordCodec.read(root.check(published))
                    if (!reread.contentEquals(bytes) || RecordCodec.decode(reread) != record) throw JournalFormatException()
                    budget.settle(published, bytes.size.toLong())
                }
                currentCoroutineContext().ensureActive()
                CheckpointResult.Durable(record, RecordCodec.hash(bytes))
            } catch (t: CancellationException) { throw t }
            catch (_: IOException) {
                if (syncingDirectory) CheckpointResult.DurabilityUnavailable
                else if (renamed) CheckpointResult.CheckpointUnconfirmed else CheckpointResult.Blocked
            } catch (_: android.system.ErrnoException) {
                if (syncingDirectory) CheckpointResult.DurabilityUnavailable else CheckpointResult.CheckpointUnconfirmed
            }
        }
    }
}

/** All private transitions retain immutable evidence once introduced. No picker/public state reachable. */
internal fun privateTransition(previous: PrivateRecord?, next: TransactionState,
                               source: SnapshotEvidence?, output: SnapshotEvidence?): Boolean {
    if (previous == null) return next == TransactionState.PREPARING && source == null && output == null
    if (previous.source != null && previous.source != source) return false
    if (previous.output != null && previous.output != output) return false
    return when (next) {
        TransactionState.PRIVATE_INPUT_SEALED -> previous.state == TransactionState.PREPARING && source != null && output == null
        TransactionState.PRIVATE_OUTPUT_SEALED -> previous.state == TransactionState.PRIVATE_INPUT_SEALED && source != null && output != null
        TransactionState.FAILED_RECOVERABLE, TransactionState.CLEANUP_PENDING -> previous.state in setOf(
            TransactionState.PREPARING, TransactionState.PRIVATE_INPUT_SEALED, TransactionState.PRIVATE_OUTPUT_SEALED) && source == previous.source && output == previous.output
        // ABORTED_CLEAN also needs proved artifact deletion: not mintable from this record-only API.
        else -> false
    }
}

internal fun readChain(root: PrivateRoot, tx: File, id: TransactionId): List<Pair<PrivateRecord, String>> {
    root.check(tx)
    val journal = root.check(File(tx, "journal"))
    if (!journal.exists()) return emptyList()
    val files = boundedChildren(root, journal, RecordCodec.MAX_CHECKPOINTS)
    val records = mutableListOf<Pair<PrivateRecord, String>>()
    for (file in files.sortedBy { it.name }) {
        root.check(file)
        if (!file.isFile || !file.name.matches(Regex("[0-9]{12}\\.rec"))) throw JournalFormatException()
        val bytes = RecordCodec.read(file)
        val record = RecordCodec.decode(bytes)
        val previous = records.lastOrNull()?.first
        if (record.transaction != id || record.sequence != records.size + 1L ||
            file.name != record.sequence.toString().padStart(12, '0') + ".rec" ||
            record.predecessor != (records.lastOrNull()?.second ?: RecordCodec.genesis) ||
            !privateTransition(previous, record.state, record.source, record.output)) throw JournalFormatException()
        records += record to RecordCodec.hash(bytes)
    }
    return records
}
