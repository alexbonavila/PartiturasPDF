package com.abdev.partituraspdf.storage.transaction

import com.abdev.partituraspdf.core.document.SnapshotEvidence
import com.abdev.partituraspdf.storage.api.*
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class Block1ContractsTest {
    private val id = TransactionId(UUID.randomUUID().toString())
    private val evidence = SnapshotEvidence("a".repeat(64), 3)
    private fun record(sequence: Long = 1, predecessor: String = RecordCodec.genesis,
                       state: TransactionState = TransactionState.PREPARING) =
        PrivateRecord(id, sequence, state, predecessor)
    @Test fun canonicalIdentifiersRejectAmbiguousAndNonCanonicalText() {
        for (invalid in listOf("", "1-1-1-1-1", "ABCDEFAB-1234-1234-1234-ABCDEFABCDEF", "../private")) {
            assertThrows(IllegalArgumentException::class.java) { TransactionId(invalid) }
            assertThrows(IllegalArgumentException::class.java) { SnapshotId(invalid) }
        }
        assertEquals(id.value, SnapshotId(id.value).value)
    }
    @Test fun inputCeilingCannotBeRaisedByCaller() {
        SnapshotCaptureLimits(64L * 1024 * 1024)
        for (size in listOf(0L, -1, 64L * 1024 * 1024 + 1))
            assertThrows(IllegalArgumentException::class.java) { SnapshotCaptureLimits(size) }
    }
    @Test fun productionBudgetCeilingsCannotBeRaisedOrMarginRemoved() {
        assertThrows(IllegalArgumentException::class.java) { MStoragePolicy(total = 257L * 1024 * 1024) }
        assertThrows(IllegalArgumentException::class.java) { MStoragePolicy(output = 129L * 1024 * 1024) }
        assertThrows(IllegalArgumentException::class.java) { MStoragePolicy(source = 65L * 1024 * 1024) }
        assertThrows(IllegalArgumentException::class.java) { MStoragePolicy(margin = 0) }
    }
    @Test fun schemaRoundTripIsExactAndDeterministic() {
        val original = record()
        val bytes = RecordCodec.encode(original)
        assertEquals(original, RecordCodec.decode(bytes))
        assertArrayEquals(bytes, RecordCodec.encode(original))
    }
    @Test fun checksumTruncationAndUnboundedPayloadReject() {
        val bytes = RecordCodec.encode(record())
        assertThrows(java.io.IOException::class.java) { RecordCodec.decode(bytes.copyOf(bytes.size - 1)) }
        val corrupt = bytes.copyOf().also { it[15] = (it[15].toInt() xor 1).toByte() }
        assertThrows(java.io.IOException::class.java) { RecordCodec.decode(corrupt) }
        assertThrows(java.io.IOException::class.java) { RecordCodec.decode(ByteArray(RecordCodec.MAX_RECORD + 1)) }
    }
    @Test fun unknownVersionRejectsEvenWithRecomputedChecksum() {
        val bytes = RecordCodec.encode(record())
        val body = bytes.copyOf(bytes.size - 32).also { it[7] = 2 }
        val hash = java.security.MessageDigest.getInstance("SHA-256").digest(body)
        assertThrows(java.io.IOException::class.java) { RecordCodec.decode(body + hash) }
    }
    @Test fun sequenceAndGenesisSyntaxAreStrict() {
        assertThrows(IllegalArgumentException::class.java) { RecordCodec.encode(record(sequence = 0)) }
        assertThrows(IllegalArgumentException::class.java) { RecordCodec.encode(record(sequence = 257)) }
        assertThrows(IllegalArgumentException::class.java) { RecordCodec.encode(record(predecessor = "bad")) }
    }
    @Test fun publicEffectStatesAreNotReachableFromPrivateWriter() {
        val previous = record()
        for (next in TransactionState.entries) {
            if (next !in setOf(TransactionState.PRIVATE_INPUT_SEALED, TransactionState.FAILED_RECOVERABLE,
                    TransactionState.CLEANUP_PENDING)) {
                assertFalse(next.name, privateTransition(previous, next, evidence, null))
            }
        }
    }
    @Test fun privateOutputIsUnvalidatedAndEvidenceCannotChange() {
        val first = record()
        assertTrue(privateTransition(first, TransactionState.PRIVATE_INPUT_SEALED, evidence, null))
        val input = first.copy(sequence = 2, state = TransactionState.PRIVATE_INPUT_SEALED, source = evidence)
        assertTrue(privateTransition(input, TransactionState.PRIVATE_OUTPUT_SEALED, evidence, evidence))
        assertFalse(privateTransition(input, TransactionState.PRIVATE_OUTPUT_SEALED, evidence.copy(byteCount = 4), evidence))
        assertFalse(privateTransition(input, TransactionState.PRIVATE_OUTPUT_SEALED, evidence, null))
    }
}
