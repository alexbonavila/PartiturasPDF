package com.abdev.partituraspdf.storage.transaction

import com.abdev.partituraspdf.storage.api.TransactionId
import com.abdev.partituraspdf.core.document.SnapshotEvidence
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class MJournalStateTest {
    private fun genesis() = MRecord(TransactionId(UUID.randomUUID().toString()), 1, MState.PREPARING,
        RecordCodec.genesis, RecordCodec.genesis, null, null, "a".repeat(64))
    @Test fun versionTwoRoundTripAndTamperRefusal() {
        val record = genesis(); val bytes = MCodec.encode(record)
        assertEquals(record, MCodec.decode(bytes))
        bytes[10] = (bytes[10].toInt() xor 1).toByte()
        assertThrows(java.io.IOException::class.java) { MCodec.decode(bytes) }
    }
    @Test fun cannotSkipValidationOrBindUnknownDestination() {
        val g = genesis(); val e = SnapshotEvidence("b".repeat(64), 1)
        val source = g.copy(sequence = 2, state = MState.PRIVATE_INPUT_SEALED, source = e)
        assertTrue(validMTransition(g, source))
        val staged = source.copy(sequence = 3, state = MState.PRIVATE_OUTPUT_SEALED, output = e)
        assertTrue(validMTransition(source, staged))
        assertFalse(validMTransition(staged, staged.copy(sequence = 4, state = MState.PUBLISHING)))
        assertFalse(validMTransition(staged, staged.copy(sequence = 4, state = MState.VERIFIED_COMMITTED)))
        assertFalse(validMTransition(staged, staged.copy(sequence = 4, state = MState.DESTINATION_BOUND)))
    }
    @Test fun planSourceDestinationAndSequenceCannotBeRewritten() {
        val g = genesis(); val e = SnapshotEvidence("b".repeat(64), 1)
        val old = g.copy(sequence = 8, state = MState.DESTINATION_OBSERVED, source = e, output = e, uri = "opaque-test-only", flags = 3)
        val next = old.copy(sequence = 9, state = MState.DESTINATION_BOUND)
        assertTrue(validMTransition(old, next))
        assertFalse(validMTransition(old, next.copy(uri = "different")))
        assertFalse(validMTransition(old, next.copy(plan = "c".repeat(64))))
        assertFalse(validMTransition(old, next.copy(sequence = 10)))
        assertFalse(validMTransition(old, next.copy(output = e.copy(byteCount = 2))))
    }
}
