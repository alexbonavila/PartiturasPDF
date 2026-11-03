package com.abdev.partituraspdf.storage.transaction

import com.abdev.partituraspdf.core.document.SnapshotEvidence
import com.abdev.partituraspdf.storage.api.TransactionId
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.withLock
import java.io.*

internal enum class MState {
    PREPARING, PRIVATE_INPUT_SEALED, PRIVATE_OUTPUT_SEALED, PRIVATE_OUTPUT_VALIDATED,
    PREPARED_FOR_PICKER, PICKER_MAY_HAVE_LAUNCHED, DESTINATION_UNKNOWN,
    DESTINATION_OBSERVED, DESTINATION_BOUND, PUBLISHING, PUBLISHED_UNVERIFIED,
    READBACK_OBSERVED, VERIFIED_COMMITTED,
}
internal data class MRecord(val id: TransactionId, val sequence: Int, val state: MState,
    val previous: String, val legacyAnchor: String, val source: SnapshotEvidence?, val output: SnapshotEvidence?,
    val plan: String, val uri: String? = null, val flags: Int = 0)
internal object MCodec {
    fun encode(record: MRecord): ByteArray {
        require(record.sequence in 1..256); require(record.uri == null || record.uri.length <= 4096)
        require(record.plan.matches(Regex("[0-9a-f]{64}")))
        require(record.previous.matches(Regex("[0-9a-f]{64}")) && record.legacyAnchor.matches(Regex("[0-9a-f]{64}")))
        require(record.flags and 67.inv() == 0)
        val body = ByteArrayOutputStream().also { bytes -> DataOutputStream(bytes).use { out ->
            out.writeInt(0x50444d32); out.writeInt(2); out.writeUTF(record.id.value); out.writeInt(record.sequence)
            out.writeUTF(record.state.name); out.writeUTF(record.previous); out.writeUTF(record.legacyAnchor)
            for (e in listOf(record.source, record.output)) { out.writeBoolean(e != null)
                if (e != null) { out.writeUTF(e.sha256); out.writeLong(e.byteCount) } }
            out.writeUTF(record.plan); out.writeBoolean(record.uri != null)
            record.uri?.let { out.writeUTF(it) }; out.writeInt(record.flags)
        } }.toByteArray()
        require(body.size <= 64 * 1024)
        return body + java.security.MessageDigest.getInstance("SHA-256").digest(body)
    }
    fun decode(bytes: ByteArray): MRecord {
        if (bytes.size !in 32..(64 * 1024 + 32)) throw JournalFormatException()
        val body = bytes.copyOf(bytes.size - 32)
        if (!(java.security.MessageDigest.getInstance("SHA-256").digest(body).contentEquals(bytes.takeLast(32).toByteArray()))) throw JournalFormatException()
        try {
            val input = DataInputStream(ByteArrayInputStream(body))
            if (input.readInt() != 0x50444d32 || input.readInt() != 2) throw JournalFormatException()
            fun bool() = when (input.readUnsignedByte()) { 0 -> false; 1 -> true; else -> throw JournalFormatException() }
            val id = TransactionId(input.readUTF()); val sequence = input.readInt(); val state = MState.valueOf(input.readUTF())
            val previous = input.readUTF(); val anchor = input.readUTF()
            fun evidence() = if (bool()) SnapshotEvidence(input.readUTF(), input.readLong()) else null
            val source = evidence(); val output = evidence(); val plan = input.readUTF()
            val uri = if (bool()) input.readUTF() else null
            val result = MRecord(id, sequence, state, previous, anchor, source, output, plan, uri, input.readInt())
            if (input.available() != 0 || !encode(result).contentEquals(bytes)) throw JournalFormatException()
            return result
        } catch (_: IllegalArgumentException) { throw JournalFormatException() }
    }
}
internal fun validMTransition(old: MRecord?, next: MRecord): Boolean {
    if (old == null) return next.state == MState.PREPARING && next.sequence == 1 && next.source == null && next.output == null && next.uri == null && next.flags == 0
    if (old.id != next.id || old.plan != next.plan || old.legacyAnchor != next.legacyAnchor || next.sequence != old.sequence + 1) return false
    if (old.source != null && old.source != next.source || old.output != null && old.output != next.output) return false
    if (old.uri != null && (old.uri != next.uri || old.flags != next.flags)) return false
    if (next.state.ordinal >= MState.DESTINATION_OBSERVED.ordinal && next.uri == null) return false
    if (next.state.ordinal < MState.DESTINATION_OBSERVED.ordinal && (next.uri != null || next.flags != 0)) return false
    return when (next.state) {
        MState.PRIVATE_INPUT_SEALED -> old.state == MState.PREPARING && next.source != null && next.output == null
        MState.PRIVATE_OUTPUT_SEALED -> old.state == MState.PRIVATE_INPUT_SEALED && next.output != null
        MState.PRIVATE_OUTPUT_VALIDATED -> old.state == MState.PRIVATE_OUTPUT_SEALED
        MState.PREPARED_FOR_PICKER -> old.state == MState.PRIVATE_OUTPUT_VALIDATED
        MState.PICKER_MAY_HAVE_LAUNCHED -> old.state == MState.PREPARED_FOR_PICKER
        MState.DESTINATION_UNKNOWN -> old.state == MState.PICKER_MAY_HAVE_LAUNCHED
        MState.DESTINATION_OBSERVED -> old.state == MState.PICKER_MAY_HAVE_LAUNCHED
        MState.DESTINATION_BOUND -> old.state == MState.DESTINATION_OBSERVED
        MState.PUBLISHING -> old.state == MState.DESTINATION_BOUND
        MState.PUBLISHED_UNVERIFIED -> old.state == MState.PUBLISHING
        MState.READBACK_OBSERVED -> old.state == MState.PUBLISHED_UNVERIFIED
        MState.VERIFIED_COMMITTED -> old.state == MState.READBACK_OBSERVED
        else -> false
    }
}

/** Separate authenticated v2 sidecar, anchored to the exact unchanged v1 chain (or genesis).
 * Failed/temporary writes are retained and cause subsequent scans to fail closed.
 */
internal class MPublicationJournal(private val budget: MStorageBudget) {
    private val root get() = budget.root
    private fun tx(id: TransactionId) = root.check(File(root.directory, "tx-${id.value}"))
    private fun anchor(id: TransactionId) = readChain(root, tx(id), id).lastOrNull()?.second ?: RecordCodec.genesis
    internal fun read(id: TransactionId): List<MRecord> {
        val directory = root.check(File(tx(id), "publication"))
        if (!directory.exists()) return emptyList()
        var previous = RecordCodec.genesis
        var old: MRecord? = null
        val entries = boundedChildren(root, directory, 256)
        if (readChain(root, tx(id), id).size + entries.size > 256) throw JournalFormatException()
        return entries.sortedBy { it.name }.mapIndexed { index, file ->
            if (file.name != "${(index + 1).toString().padStart(12, '0')}.rec") throw JournalFormatException()
            val bytes = RecordCodec.read(file)
            val record = MCodec.decode(bytes)
            if (record.id != id || record.previous != previous || record.legacyAnchor != anchor(id) || !validMTransition(old, record)) throw JournalFormatException()
            old = record; previous = RecordCodec.hash(bytes); record
        }
    }
    suspend fun append(id: TransactionId, state: MState, plan: String,
                       source: SnapshotEvidence? = null, output: SnapshotEvidence? = null,
                       uri: String? = null, flags: Int = 0): String? = withContext(Dispatchers.IO) {
        root.mutations.withLock {
            try {
                val tx = tx(id)
                if (!tx.exists()) { if (!tx.mkdir()) throw IOException("Private transaction unavailable")
                    root.io.syncDirectory(tx); root.io.syncDirectory(root.directory) }
                val directory = root.check(File(tx, "publication"))
                if (!directory.exists()) { if (!directory.mkdir()) throw IOException("Private journal unavailable")
                    root.io.syncDirectory(directory); root.io.syncDirectory(tx) }
                val history = read(id); val old = history.lastOrNull()
                if (readChain(root, tx, id).size + history.size >= 256) return@withLock null
                val previousFile = old?.let { File(directory, "${it.sequence.toString().padStart(12, '0')}.rec") }
                val previous = previousFile?.let { RecordCodec.hash(RecordCodec.read(it)) } ?: RecordCodec.genesis
                val record = MRecord(id, history.size + 1, state, previous, anchor(id), source, output, plan, uri, flags)
                if (!validMTransition(old, record)) return@withLock null
                val bytes = MCodec.encode(record)
                val tmp = root.check(File(directory, "${record.sequence.toString().padStart(12, '0')}.tmp"))
                val final = root.check(File(directory, tmp.name.removeSuffix(".tmp") + ".rec"))
                budget.reserve(tmp, bytes.size.toLong()); root.create(tmp)
                withContext(NonCancellable) {
                    root.io.output(tmp).use { it.write(bytes); root.io.syncFile(it) }
                    root.io.renameNew(tmp, final); budget.renamed(tmp, final)
                    root.io.syncDirectory(directory)
                    if (!RecordCodec.read(final).contentEquals(bytes)) throw JournalFormatException()
                    budget.settle(final, bytes.size.toLong())
                }
                currentCoroutineContext().ensureActive()
                RecordCodec.hash(bytes)
            } catch (t: CancellationException) { throw t }
            catch (_: IOException) { null }
            catch (_: android.system.ErrnoException) { null }
        }
    }
}
