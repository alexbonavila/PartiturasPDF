package com.abdev.partituraspdf.storage.transaction

import com.abdev.partituraspdf.storage.api.TransactionId
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.io.IOException

internal enum class RecoveryClassification { UNFINISHED, PRIVATE_OUTPUT_UNVALIDATED, CLEANUP_PENDING, BLOCKED }
internal data class RecoveredTransaction(val transaction: TransactionId, val classification: RecoveryClassification)
internal sealed interface RecoveryScan {
    data class Complete(val transactions: List<RecoveredTransaction>, val chargedBytes: Long) : RecoveryScan
    data object Blocked : RecoveryScan
}

/** Read-only scan. Unknown artifacts are retained; no guessed lease count or external lookup.
 * Block 1 deliberately refuses reserved public-effect records until a future evidence-bound API.
 */
internal class PrivateRecovery(private val budget: MStorageBudget,
                               private val dispatcher: CoroutineDispatcher = Dispatchers.IO) {
    suspend fun scan(): RecoveryScan = withContext(dispatcher) {
        val root = budget.root
        root.mutations.withLock {
            try {
                budget.refreshOccupancy() // Whole scan must be within bounds and have no symlink before classification.
                if (budget.used() > budget.policy.total) return@withLock RecoveryScan.Blocked
                val transactions = boundedChildren(root, root.directory, 256)
                val result = mutableListOf<RecoveredTransaction>()
                for (tx in transactions.sortedBy { it.name }) {
                    currentCoroutineContext().ensureActive()
                    root.check(tx)
                    if (!tx.isDirectory || !tx.name.startsWith("tx-")) return@withLock RecoveryScan.Blocked
                    val id = try { TransactionId(tx.name.removePrefix("tx-")) }
                    catch (_: IllegalArgumentException) { return@withLock RecoveryScan.Blocked }
                    val classification = try {
                        val children = boundedChildren(root, tx, 260)
                        val chain = readChain(root, tx, id)
                        val record = chain.lastOrNull()?.first ?: throw JournalFormatException()
                        // Unknown or partial files never become automatic cleanup authority.
                        for (child in children) {
                            val expected = when (child.name) {
                                "journal" -> continue
                                "source.sealed" -> record.source
                                "output.sealed" -> record.output
                                else -> throw JournalFormatException()
                            } ?: throw JournalFormatException()
                            val ceiling = if (child.name == "source.sealed") budget.policy.source else budget.policy.output
                            if (expected.byteCount > ceiling || !child.isFile || child.length() != expected.byteCount ||
                                root.io.evidence(child) != expected) throw JournalFormatException()
                        }
                        when (record.state) {
                            TransactionState.PREPARING, TransactionState.PRIVATE_INPUT_SEALED,
                            TransactionState.FAILED_RECOVERABLE -> RecoveryClassification.UNFINISHED
                            TransactionState.PRIVATE_OUTPUT_SEALED -> RecoveryClassification.PRIVATE_OUTPUT_UNVALIDATED
                            TransactionState.CLEANUP_PENDING -> RecoveryClassification.CLEANUP_PENDING
                            else -> RecoveryClassification.BLOCKED
                        }
                    } catch (_: IOException) { RecoveryClassification.BLOCKED }
                    result += RecoveredTransaction(id, classification)
                }
                RecoveryScan.Complete(result, budget.used())
            } catch (_: IOException) { RecoveryScan.Blocked }
        }
    }
}
