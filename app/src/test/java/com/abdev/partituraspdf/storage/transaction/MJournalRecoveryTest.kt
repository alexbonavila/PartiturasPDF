package com.abdev.partituraspdf.storage.transaction

import com.abdev.partituraspdf.storage.api.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.*
import java.util.UUID

class MJournalRecoveryTest {
    @get:Rule val temporary = TemporaryFolder()
    private fun rig(scope: CoroutineScope) = MTestRig(File(temporary.root.canonicalFile, UUID.randomUUID().toString()), scope)
    private val plan = "a".repeat(64)
    private fun sequence(target: MState): List<MState> {
        val normal = listOf(MState.PREPARING, MState.PRIVATE_INPUT_SEALED, MState.PRIVATE_OUTPUT_SEALED,
            MState.PRIVATE_OUTPUT_VALIDATED, MState.PREPARED_FOR_PICKER, MState.PICKER_MAY_HAVE_LAUNCHED,
            MState.DESTINATION_OBSERVED, MState.DESTINATION_BOUND, MState.PUBLISHING,
            MState.PUBLISHED_UNVERIFIED, MState.READBACK_OBSERVED, MState.VERIFIED_COMMITTED,
            MState.CLEANUP_AUTHORIZED, MState.CLEANED)
        return when (target) {
            MState.DESTINATION_UNKNOWN -> normal.take(6) + target
            MState.OWNER_RESOLVED -> normal.take(6) + MState.DESTINATION_UNKNOWN + target
            else -> normal.take(normal.indexOf(target) + 1)
        }
    }
    private suspend fun MTestRig.chain(target: MState): TransactionId {
        val id = TransactionId(UUID.randomUUID().toString())
        var source: com.abdev.partituraspdf.core.document.SnapshotEvidence? = null
        var output: com.abdev.partituraspdf.core.document.SnapshotEvidence? = null
        for (state in sequence(target)) {
            val tx = File(root.directory, "tx-${id.value}")
            if (state == MState.PRIVATE_INPUT_SEALED) {
                val f = File(tx, "source.sealed").apply { writeBytes(bytes) }; source = digest(f)
            }
            if (state == MState.PRIVATE_OUTPUT_SEALED) {
                val f = File(tx, "output.sealed").apply { writeBytes(bytes) }; output = digest(f)
            }
            if (state == MState.CLEANED) { assertTrue(File(tx, "source.sealed").delete()); assertTrue(File(tx, "output.sealed").delete()) }
            val bound = state in setOf(MState.DESTINATION_OBSERVED, MState.DESTINATION_BOUND, MState.PUBLISHING,
                MState.PUBLISHED_UNVERIFIED, MState.READBACK_OBSERVED, MState.VERIFIED_COMMITTED, MState.CLEANUP_AUTHORIZED, MState.CLEANED)
            assertNotNull(journal.append(id, state, plan, source, output, if (bound) "test-known-destination" else null, if (bound) 3 else 0))
        }
        return id
    }
    private class Reader(val bytes: ByteArray) : KnownDestinationReader {
        var reads = 0
        override suspend fun readIfAuthorized(opaque: String, consume: suspend (InputStream) -> Unit): Boolean {
            reads++; consume(ByteArrayInputStream(bytes)); return true
        }
    }
    @Test fun everyStateReconstructsConservativelyWithoutWriteOrReplay() = runTest {
        for (state in MState.entries) {
            val r = rig(backgroundScope); val id = r.chain(state); val reader = Reader(r.bytes)
            val restarted = MStorageBudget(r.root)
            val scan = MReadOnlyReconciler(restarted, MPublicationJournal(restarted, Dispatchers.Unconfined), reader)
            val result = scan.inspect(id)
            assertNotEquals(state.name, MRecoveryStatus.BLOCKED, result)
            assertEquals(result, scan.inspect(id))
            if (state in setOf(MState.VERIFIED_COMMITTED, MState.CLEANUP_AUTHORIZED, MState.CLEANED))
                assertEquals(MRecoveryStatus.VERIFIED_RECORD_OBSERVED, result)
            else assertNotEquals(MRecoveryStatus.VERIFIED_RECORD_OBSERVED, result)
            if (state.ordinal < MState.PUBLISHING.ordinal || state == MState.OWNER_RESOLVED) assertEquals(0, reader.reads)
        }
    }
    @Test fun preterminalMissingFilesBlockButAuthorizedPartialCleanupRemainsVerified() = runTest {
        for (state in listOf(MState.PRIVATE_OUTPUT_VALIDATED, MState.PUBLISHING, MState.VERIFIED_COMMITTED, MState.CLEANUP_AUTHORIZED)) {
            val r = rig(backgroundScope); val id = r.chain(state)
            assertTrue(File(r.root.directory, "tx-${id.value}/output.sealed").delete())
            val reader = Reader(r.bytes)
            val result = MReadOnlyReconciler(r.budget, r.journal, reader).inspect(id)
            assertEquals(if (state == MState.CLEANUP_AUTHORIZED) MRecoveryStatus.VERIFIED_RECORD_OBSERVED else MRecoveryStatus.BLOCKED, result)
            if (state != MState.CLEANUP_AUTHORIZED) assertEquals(0, reader.reads)
        }
    }
    @Test fun legacyChainRemainsReadableAndV2AnchorTamperingBlocks() = runTest {
        val r = rig(backgroundScope); val id = TransactionId(UUID.randomUUID().toString())
        val legacy = PrivateJournal(r.budget, Dispatchers.Unconfined)
        assertTrue(legacy.appendPrivate(id, TransactionState.PREPARING) is CheckpointResult.Durable)
        val reader = Reader(r.bytes)
        assertEquals(MRecoveryStatus.LEGACY_PRIVATE, MReadOnlyReconciler(r.budget, r.journal, reader).inspect(id))
        assertNotNull(r.journal.append(id, MState.PREPARING, plan))
        assertTrue(legacy.appendPrivate(id, TransactionState.FAILED_RECOVERABLE) is CheckpointResult.Durable)
        assertEquals(MRecoveryStatus.BLOCKED, MReadOnlyReconciler(r.budget, r.journal, reader).inspect(id))
        assertEquals(0, reader.reads)
    }
    @Test fun syncRenameCloseAndWriteFaultsNeverAcknowledgeAndRetainOldRecord() = runTest {
        for (point in listOf("write", "fsync", "close", "rename", "directory")) {
            val r = rig(backgroundScope); val id = TransactionId(UUID.randomUUID().toString())
            assertNotNull(r.journal.append(id, MState.PREPARING, plan))
            val oldFile = File(r.root.directory, "tx-${id.value}/publication/000000000001.rec")
            val old = oldFile.readBytes()
            val file = File(r.root.directory, "tx-${id.value}/source.sealed").apply { writeBytes(r.bytes) }
            r.fault = { if (it == point) throw IOException("Injected barrier") }
            assertNull(r.journal.append(id, MState.PRIVATE_INPUT_SEALED, plan, digest(file)))
            assertArrayEquals(old, oldFile.readBytes()); assertTrue(r.budget.used() > 0)
        }
    }
    @Test fun tornTemporaryGapReorderedAndChecksumChangedRecordsBlockWithoutDeletion() = runTest {
        for (fault in listOf("temporary", "gap", "reordered", "checksum", "truncated")) {
            val r = rig(backgroundScope); val id = r.chain(MState.PRIVATE_OUTPUT_SEALED)
            val dir = File(r.root.directory, "tx-${id.value}/publication")
            val first = File(dir, "000000000001.rec"); val second = File(dir, "000000000002.rec")
            when (fault) {
                "temporary" -> File(dir, "000000000004.tmp").writeBytes(byteArrayOf(1))
                "gap" -> assertTrue(second.delete())
                "reordered" -> { val bytes = first.readBytes(); first.writeBytes(second.readBytes()); second.writeBytes(bytes) }
                "checksum" -> first.appendBytes(byteArrayOf(1))
                else -> first.writeBytes(byteArrayOf(1))
            }
            val before = dir.list()!!.toSet()
            assertEquals(MRecoveryStatus.BLOCKED, MReadOnlyReconciler(r.budget, r.journal, Reader(r.bytes)).inspect(id))
            assertEquals(before, dir.list()!!.toSet())
        }
    }
    @Test fun metadataLimitsUnknownVersionAndPredecessorMutationReject() {
        val id = TransactionId(UUID.randomUUID().toString())
        val g = MRecord(id, 1, MState.PREPARING, RecordCodec.genesis, RecordCodec.genesis, null, null, plan)
        assertThrows(IllegalArgumentException::class.java) { MCodec.encode(g.copy(uri = "x".repeat(4097))) }
        assertThrows(IllegalArgumentException::class.java) { MCodec.encode(g.copy(flags = 128)) }
        val bytes = MCodec.encode(g); val body = bytes.copyOf(bytes.size - 32).also { it[7] = 3 }
        val tampered = body + java.security.MessageDigest.getInstance("SHA-256").digest(body)
        assertThrows(IOException::class.java) { MCodec.decode(tampered) }
        val next = g.copy(sequence = 2, state = MState.PRIVATE_INPUT_SEALED,
            source = com.abdev.partituraspdf.core.document.SnapshotEvidence("b".repeat(64), 1))
        assertFalse(validMTransition(g, next.copy(sequence = 3)))
    }
    @Test fun rereadTamperingNeverAcknowledgesNewRecord() = runTest {
        val r = rig(backgroundScope); val id = TransactionId(UUID.randomUUID().toString())
        assertNotNull(r.journal.append(id, MState.PREPARING, plan))
        val source = File(r.root.directory, "tx-${id.value}/source.sealed").apply { writeBytes(r.bytes) }
        r.fault = { point -> if (point == "directory") {
            val file = File(r.root.directory, "tx-${id.value}/publication/000000000002.rec")
            if (file.exists()) file.appendBytes(byteArrayOf(1))
        } }
        assertNull(r.journal.append(id, MState.PRIVATE_INPUT_SEALED, plan, digest(source)))
        assertEquals(MRecoveryStatus.BLOCKED, MReadOnlyReconciler(r.budget, r.journal, Reader(r.bytes)).inspect(id))
    }
    @Test fun legacyScannerCannotTreatV2TransactionAsOrdinaryPrivateSuccess() = runTest {
        val r = rig(backgroundScope); r.chain(MState.PUBLISHING)
        val scan = PrivateRecovery(r.budget, Dispatchers.Unconfined).scan() as RecoveryScan.Complete
        assertEquals(RecoveryClassification.BLOCKED, scan.transactions.single().classification)
    }

    @Test fun cancellationDuringCheckpointNeverReturnsDurableAcknowledgement() = runTest {
        val r = rig(backgroundScope); val id = TransactionId(UUID.randomUUID().toString())
        val cancellation = CancellationException("Canceled acknowledgement")
        var returned = false
        val job = launch {
            val owner = currentCoroutineContext()[Job]!!
            r.fault = { if (it == "fsync") owner.cancel(cancellation) }
            try {
                r.journal.append(id, MState.PREPARING, plan); returned = true
            } catch (caught: CancellationException) { assertSame(cancellation, caught); throw caught }
        }
        job.join(); assertTrue(job.isCancelled); assertFalse(returned)
        assertTrue(r.budget.used() > 0)
        // A complete record may have reached disk, but cancellation never delivered an ack.
        assertTrue(File(r.root.directory, "tx-${id.value}/publication").isDirectory)
    }

    @Test fun absentRightsAndMismatchedReadbackNeverUpgradeVerifiedHistory() = runTest {
        for (mode in listOf("absent", "revoked", "mismatch")) {
            val r = rig(backgroundScope); val id = r.chain(MState.VERIFIED_COMMITTED)
            val reader = object : KnownDestinationReader {
                override suspend fun readIfAuthorized(opaque: String, consume: suspend (InputStream) -> Unit): Boolean {
                    if (mode == "absent") return false
                    if (mode == "revoked") throw SecurityException("Read unavailable")
                    consume(ByteArrayInputStream(byteArrayOf(9))); return true
                }
            }
            assertEquals(MRecoveryStatus.NEEDS_OWNER_RESOLUTION, MReadOnlyReconciler(r.budget, r.journal, reader).inspect(id))
        }
    }
    @Test fun authenticatedPredecessorRewriteStillFailsChainBinding() = runTest {
        val r = rig(backgroundScope); val id = r.chain(MState.PRIVATE_OUTPUT_SEALED)
        val second = File(r.root.directory, "tx-${id.value}/publication/000000000002.rec")
        val record = MCodec.decode(second.readBytes())
        second.writeBytes(MCodec.encode(record.copy(previous = "b".repeat(64))))
        val reader = Reader(r.bytes)
        assertEquals(MRecoveryStatus.BLOCKED, MReadOnlyReconciler(r.budget, r.journal, reader).inspect(id))
        assertEquals(0, reader.reads)
    }
    @Test fun unexpectedSidecarAndSymbolicLinkBlockBeforeProviderRead() = runTest {
        for (mode in listOf("unexpected", "link")) {
            val r = rig(backgroundScope); val id = r.chain(MState.PUBLISHING)
            val file = File(r.root.directory, "tx-${id.value}/foreign")
            if (mode == "link") java.nio.file.Files.createSymbolicLink(file.toPath(), temporary.newFile().toPath())
            else file.writeBytes(byteArrayOf(1))
            val reader = Reader(r.bytes)
            assertEquals(MRecoveryStatus.BLOCKED, MReadOnlyReconciler(r.budget, r.journal, reader).inspect(id))
            assertEquals(0, reader.reads)
        }
    }

}
