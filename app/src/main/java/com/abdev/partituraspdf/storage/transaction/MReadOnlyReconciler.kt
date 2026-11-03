package com.abdev.partituraspdf.storage.transaction

import com.abdev.partituraspdf.storage.api.TransactionId
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.io.IOException

/** This port has no write/create/grant operations. Caller must already hold READ authority. */
internal interface KnownDestinationReader {
    suspend fun readIfAuthorized(opaque: String, consume: suspend (java.io.InputStream) -> Unit): Boolean
}
internal enum class MRecoveryStatus { LEGACY_PRIVATE, BLOCKED, DESTINATION_UNKNOWN, NEEDS_OWNER_RESOLUTION, MATCH_OBSERVED, VERIFIED_RECORD_OBSERVED }
internal class MReadOnlyReconciler(private val budget: MStorageBudget,
                                  private val journal: MPublicationJournal, private val reader: KnownDestinationReader) {
    suspend fun inspect(id: TransactionId): MRecoveryStatus = withContext(Dispatchers.IO) {
        // Read-only provider calls run after the root mutex is released. No Reader/global monitor.
        val latest = try { budget.root.mutations.withLock {
            budget.refreshOccupancy()
            if (budget.used() > budget.policy.total) throw JournalFormatException()
            val records = journal.read(id)
            if (records.isEmpty()) {
                val tx = budget.root.check(File(budget.root.directory, "tx-${id.value}"))
                if (readChain(budget.root, tx, id).isEmpty()) throw JournalFormatException()
                return@withContext MRecoveryStatus.LEGACY_PRIVATE
            }
            val record = records.last()
            val tx = budget.root.check(File(budget.root.directory, "tx-${id.value}"))
            if (boundedChildren(budget.root, tx, 4).any { it.name !in setOf("source.sealed", "output.sealed", "journal", "publication") }) throw JournalFormatException()
            for ((name, evidence) in listOf("source.sealed" to record.source, "output.sealed" to record.output)) {
                if (evidence != null) {
                    val file = budget.root.check(File(tx, name))
                    if (!file.isFile || file.length() != evidence.byteCount || evidence.byteCount > (if (name == "source.sealed") budget.policy.source else budget.policy.output) || budget.root.io.evidence(file) != evidence) throw JournalFormatException()
                }
            }
            record
        } } catch (_: IOException) { return@withContext MRecoveryStatus.BLOCKED }
        if (latest.uri == null) return@withContext MRecoveryStatus.DESTINATION_UNKNOWN
        if (latest.state.ordinal < MState.PUBLISHING.ordinal || latest.output == null)
            return@withContext MRecoveryStatus.NEEDS_OWNER_RESOLUTION
        var matches = false
        try {
            val available = reader.readIfAuthorized(latest.uri) { matches = observeBytes(it, latest.output.byteCount) == latest.output }
            if (!available || !matches) MRecoveryStatus.NEEDS_OWNER_RESOLUTION
            else if (latest.state == MState.VERIFIED_COMMITTED) MRecoveryStatus.VERIFIED_RECORD_OBSERVED else MRecoveryStatus.MATCH_OBSERVED
        } catch (t: CancellationException) { throw t }
        catch (_: IOException) { MRecoveryStatus.NEEDS_OWNER_RESOLUTION }
        catch (_: SecurityException) { MRecoveryStatus.NEEDS_OWNER_RESOLUTION }
        // Never upgrades a matching read to commit: no new public effect, no blind replay.
    }
}
