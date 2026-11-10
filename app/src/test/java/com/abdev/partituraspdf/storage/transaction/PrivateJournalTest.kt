package com.abdev.partituraspdf.storage.transaction

import com.abdev.partituraspdf.storage.api.SnapshotLeasePort
import com.abdev.partituraspdf.storage.api.TransactionId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.*
import java.util.UUID

class PrivateJournalTest {
    @get:Rule val temporary = TemporaryFolder()
    private class Files : PrivateIo {
        var fail: String? = null
        private fun point(name: String) { if (fail == name) throw IOException("Injected") }
        override fun freeBytes(root: File) = Long.MAX_VALUE
        override fun syncFile(stream: FileOutputStream) { point("syncFile"); stream.flush(); stream.fd.sync() }
        override fun syncDirectory(directory: File) { point("syncDirectory") }
        override fun renameNew(from: File, to: File) {
            point("rename")
            if (to.exists() || !from.renameTo(to)) throw IOException("Rename rejected")
        }
        override fun delete(file: File) { point("delete"); if (!file.delete()) throw IOException("Delete rejected") }
        override fun openLease(file: File): SnapshotLeasePort = error("Not used by journal tests")
    }
    private fun setup(io: Files = Files()): Pair<MStorageBudget, Files> {
        val root = PrivateRoot(File(temporary.root.canonicalFile, UUID.randomUUID().toString()), io)
        return MStorageBudget(root) to io
    }
    @Test fun recoveryRootOpeningNeverCreatesMissingDirectoryAndRetainsConfinement() {
        val io=Files()
        val absent=File(temporary.root.canonicalFile,"absent")
        assertThrows(IOException::class.java) { PrivateRoot(absent,io,createIfMissing=false) }
        assertFalse(absent.exists())
        val root=PrivateRoot(absent,io)
        assertEquals(root.directory,PrivateRoot(absent,io,createIfMissing=false).directory)
        val link=File(temporary.root.canonicalFile,"linked")
        java.nio.file.Files.createSymbolicLink(link.toPath(),absent.toPath())
        try {
            assertThrows(IOException::class.java) { PrivateRoot(link,io,createIfMissing=false) }
            assertTrue(absent.isDirectory)
        } finally { java.nio.file.Files.delete(link.toPath()) }
    }
    @Test fun appendReopenAndRecoveryAreIdempotent() = runTest {
        val (budget, _) = setup()
        val id = TransactionId(UUID.randomUUID().toString())
        val journal = PrivateJournal(budget, Dispatchers.Unconfined)
        assertTrue(journal.appendPrivate(id, TransactionState.PREPARING) is CheckpointResult.Durable)
        val one = PrivateRecovery(budget, Dispatchers.Unconfined).scan()
        val two = PrivateRecovery(budget, Dispatchers.Unconfined).scan()
        assertEquals(one, two)
        val reopened = MStorageBudget(budget.root)
        assertEquals(budget.used(), reopened.used())
        assertTrue(journal.appendPrivate(id, TransactionState.PUBLISHING) is CheckpointResult.InvalidTransition)
    }
    @Test fun failedDirectorySyncNeverAcknowledgesAndOlderRecordSurvives() = runTest {
        val (budget, io) = setup()
        val id = TransactionId(UUID.randomUUID().toString())
        val journal = PrivateJournal(budget, Dispatchers.Unconfined)
        assertTrue(journal.appendPrivate(id, TransactionState.PREPARING) is CheckpointResult.Durable)
        val old = File(budget.root.directory, "tx-${id.value}/journal/000000000001.rec").readBytes()
        io.fail = "syncDirectory"
        assertSame(CheckpointResult.DurabilityUnavailable, journal.appendPrivate(id, TransactionState.FAILED_RECOVERABLE))
        assertArrayEquals(old, File(budget.root.directory, "tx-${id.value}/journal/000000000001.rec").readBytes())
    }
    @Test fun writeSyncAndRenameFaultsLeaveUnconfirmedArtifactsCharged() = runTest {
        for (point in listOf("syncFile", "rename")) {
            val (budget, io) = setup()
            val id = TransactionId(UUID.randomUUID().toString())
            val journal = PrivateJournal(budget, Dispatchers.Unconfined)
            journal.appendPrivate(id, TransactionState.PREPARING)
            io.fail = point
            assertFalse(journal.appendPrivate(id, TransactionState.FAILED_RECOVERABLE) is CheckpointResult.Durable)
            assertTrue(budget.used() > 0)
            val scan = PrivateRecovery(budget, Dispatchers.Unconfined).scan() as RecoveryScan.Complete
            assertEquals(RecoveryClassification.BLOCKED, scan.transactions.single().classification)
        }
    }
    @Test fun corruptPredecessorGapAndForeignFilesBlockWithoutDeletion() = runTest {
        for (kind in listOf("corrupt", "gap", "foreign", "predecessor", "duplicate")) {
            val (budget, _) = setup()
            val id = TransactionId(UUID.randomUUID().toString())
            PrivateJournal(budget, Dispatchers.Unconfined).appendPrivate(id, TransactionState.PREPARING)
            val directory = File(budget.root.directory, "tx-${id.value}/journal")
            val first = File(directory, "000000000001.rec")
            when (kind) {
                "corrupt" -> first.appendBytes(byteArrayOf(1))
                "gap" -> first.renameTo(File(directory, "000000000002.rec"))
                "predecessor" -> {
                    val previous = RecordCodec.decode(first.readBytes())
                    File(directory, "000000000002.rec").writeBytes(RecordCodec.encode(previous.copy(
                        sequence = 2, predecessor = "a".repeat(64), state = TransactionState.FAILED_RECOVERABLE)))
                }
                "duplicate" -> File(directory, "000000000002.rec").writeBytes(first.readBytes())
                else -> File(directory, "unexpected.tmp").writeText("retain")
            }
            val names = directory.list()!!.toSet()
            val recovery = PrivateRecovery(MStorageBudget(budget.root), Dispatchers.Unconfined)
            val scan = recovery.scan() as RecoveryScan.Complete
            assertEquals(RecoveryClassification.BLOCKED, scan.transactions.single().classification)
            assertEquals(names, directory.list()!!.toSet())
        }
    }
    @Test fun limitAndFailedDeletionKeepQuotaUntilConfirmedUnlink() {
        val (budget, io) = setup()
        val file = File(budget.root.newTransaction(), "output.part")
        budget.reserve(file, 3); budget.root.create(file); file.writeBytes(byteArrayOf(1, 2, 3))
        io.fail = "delete"
        assertThrows(IOException::class.java) { budget.deleteOwned(file) }
        assertEquals(3L, budget.used()); assertTrue(file.exists())
        io.fail = null; budget.deleteOwned(file); assertEquals(0L, budget.used())
    }
    @Test fun symbolicLinksAndEscapesNeverAuthorizeReadOrCleanup() {
        val (budget, _) = setup()
        val outside = temporary.newFile()
        val link = File(budget.root.directory, "linked")
        java.nio.file.Files.createSymbolicLink(link.toPath(), outside.toPath())
        assertThrows(IOException::class.java) { budget.root.check(link) }
        assertThrows(IOException::class.java) { budget.root.check(File(budget.root.directory, "../escape")) }
        assertTrue(outside.exists())
    }
    private suspend fun missingArtifactIsBlocked(missing: String) {
        val (budget, _) = setup()
        val id = TransactionId(UUID.randomUUID().toString())
        val journal = PrivateJournal(budget, Dispatchers.Unconfined)
        journal.appendPrivate(id, TransactionState.PREPARING)
        val tx = File(budget.root.directory, "tx-${id.value}")
        val source = File(tx, "source.sealed").apply { writeBytes(byteArrayOf(1)) }
        val output = File(tx, "output.sealed").apply { writeBytes(byteArrayOf(2)) }
        val sourceEvidence = digest(source)
        val outputEvidence = digest(output)
        assertTrue(journal.appendPrivate(id, TransactionState.PRIVATE_INPUT_SEALED, sourceEvidence) is CheckpointResult.Durable)
        assertTrue(journal.appendPrivate(id, TransactionState.PRIVATE_OUTPUT_SEALED, sourceEvidence, outputEvidence) is CheckpointResult.Durable)
        val before = PrivateRecovery(budget, Dispatchers.Unconfined).scan() as RecoveryScan.Complete
        assertEquals(RecoveryClassification.PRIVATE_OUTPUT_UNVALIDATED, before.transactions.single().classification)
        assertTrue(File(tx, missing).delete())
        val after = PrivateRecovery(budget, Dispatchers.Unconfined).scan() as RecoveryScan.Complete
        assertEquals(RecoveryClassification.BLOCKED, after.transactions.single().classification)
        assertTrue(File(tx, if (missing == "source.sealed") "output.sealed" else "source.sealed").exists())
    }
    @Test fun missingMandatorySourceBlocksRecovery() = runTest { missingArtifactIsBlocked("source.sealed") }
    @Test fun missingMandatoryOutputBlocksRecovery() = runTest { missingArtifactIsBlocked("output.sealed") }

}
