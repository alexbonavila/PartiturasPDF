package com.abdev.partituraspdf.storage.transaction

import com.abdev.partituraspdf.storage.api.SnapshotLeasePort
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.*

class MStorageBudgetTest {
    @get:Rule val temporary = TemporaryFolder()
    private var free = Long.MAX_VALUE
    private val io = object : PrivateIo {
        override fun freeBytes(root: File) = free
        override fun syncFile(stream: FileOutputStream) = Unit
        override fun syncDirectory(directory: File) = Unit
        override fun renameNew(from: File, to: File) = error("Unused")
        override fun delete(file: File) { check(file.delete()) }
        override fun openLease(file: File): SnapshotLeasePort = error("Unused")
    }
    @Test fun exactAggregateReservationAcceptsButNextByteRejects() {
        val root = PrivateRoot(File(temporary.root, "root"), io)
        val budget = MStorageBudget(root, MStoragePolicy(source = 3, output = 4, total = 7))
        val tx = root.newTransaction()
        budget.reserve(File(tx, "source.part"), 3)
        budget.reserve(File(tx, "output.part"), 4)
        assertEquals(7L, budget.used())
        assertThrows(StorageFault::class.java) { budget.reserve(File(tx, "checkpoint.tmp"), 1) }
    }
    @Test fun freeMarginAndOutstandingReservationsBothConstrainAdmission() {
        val root = PrivateRoot(File(temporary.root, "root"), io)
        val budget = MStorageBudget(root)
        val tx = root.newTransaction()
        free = 64L * 1024 * 1024 + 4
        budget.reserve(File(tx, "a.part"), 4)
        assertThrows(StorageFault::class.java) { budget.reserve(File(tx, "b.part"), 1) }
        free = Long.MAX_VALUE
        budget.reserve(File(tx, "b.part"), 1)
        assertEquals(5L, budget.used())
    }
    @Test fun unknownFilesAreChargedAcrossRestartAndNeverSwept() = runTest {
        val root = PrivateRoot(File(temporary.root, "root"), io)
        val tx = root.newTransaction()
        val unknown = File(tx, "unknown.part").apply { writeBytes(ByteArray(6)) }
        val budget = MStorageBudget(root, MStoragePolicy(total = 7))
        assertEquals(6L, budget.used())
        assertThrows(StorageFault::class.java) { budget.reserve(File(tx, "new.part"), 2) }
        val before = unknown.readBytes()
        PrivateRecovery(budget, Dispatchers.Unconfined).scan()
        assertArrayEquals(before, unknown.readBytes())
    }
    @Test fun corruptScanBlocksNewAdmissionInsteadOfIgnoringOccupancy() {
        val root = PrivateRoot(File(temporary.root, "root"), io)
        val link = File(root.directory, "foreign")
        java.nio.file.Files.createSymbolicLink(link.toPath(), temporary.newFile().toPath())
        val budget = MStorageBudget(root)
        assertThrows(StorageFault::class.java) { budget.reserve(File(root.directory, "new.part"), 1) }
    }
}
