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

class PrivateSnapshotStoreTest {
    @get:Rule val temporary = TemporaryFolder()
    private class Io : PrivateIo {
        var deletesFail = false
        var syncFails = false
        var closes = 0
        var outputFault: String? = null
        var evidenceFault = false
        override fun evidence(file: File): com.abdev.partituraspdf.core.document.SnapshotEvidence {
            if (evidenceFault) throw IOException("Injected reread")
            return digest(file)
        }
        override fun output(file: File): FileOutputStream = object : FileOutputStream(file) {
            override fun write(bytes: ByteArray, offset: Int, length: Int) {
                if (outputFault == "write") throw IOException("Injected write")
                super.write(bytes, offset, length)
            }
            override fun close() {
                super.close()
                if (outputFault == "close") throw IOException("Injected close")
            }
        }
        override fun freeBytes(root: File) = Long.MAX_VALUE
        override fun syncFile(stream: FileOutputStream) {
            if (syncFails) throw IOException("Injected")
            stream.flush(); stream.fd.sync()
        }
        override fun syncDirectory(directory: File) = Unit
        override fun renameNew(from: File, to: File) {
            if (to.exists() || !from.renameTo(to)) throw IOException("No replace")
        }
        override fun delete(file: File) { if (deletesFail || !file.delete()) throw IOException("Retained") }
        override fun openLease(file: File): SnapshotLeasePort = object : SnapshotLeasePort {
            override val descriptor: android.os.ParcelFileDescriptor get() = error("JVM test never reads Android FD")
            override fun close() { closes++ }
        }
    }
    @Test fun stagingUsesMeasuredBytesAndSameOwnerUntilLastLeaseCloses() = runTest {
        val io = Io()
        val budget = MStorageBudget(PrivateRoot(File(temporary.root.canonicalFile, "root"), io))
        val store = PrivateSnapshotStore(budget, backgroundScope, Dispatchers.Unconfined)
        val id = TransactionId(UUID.randomUUID().toString())
        val bytes = byteArrayOf(1, 2, 3)
        val snapshot = (store.stage(id) { it.write(bytes) } as SnapshotCaptureResult.Captured).snapshot
        assertEquals(SnapshotOrigin.Produced(id), snapshot.origin)
        assertEquals(3L, snapshot.evidence.byteCount)
        assertEquals(3L, budget.used())
        val first = (snapshot.acquireReadLease() as SnapshotLeaseResult.Acquired).lease
        val second = (snapshot.acquireReadLease() as SnapshotLeaseResult.Acquired).lease
        snapshot.requestClose()
        assertEquals(SnapshotFailure.Closing, (snapshot.acquireReadLease() as SnapshotLeaseResult.Rejected).reason)
        first.close(); assertEquals(3L, budget.used())
        val moved = second.transferOwnership()
        assertThrows(IllegalStateException::class.java) { second.close() }
        moved.close(); moved.close()
        assertSame(SnapshotReleaseResult.Released, snapshot.awaitReleased())
        assertEquals(2, io.closes); assertEquals(0L, budget.used())
    }
    @Test fun emptyOverLimitAndFailedSyncNeverSeal() = runTest {
        val io = Io()
        val budget = MStorageBudget(PrivateRoot(File(temporary.root.canonicalFile, "root"), io), MStoragePolicy(output = 3))
        val store = PrivateSnapshotStore(budget, backgroundScope, Dispatchers.Unconfined)
        suspend fun attempt(body: suspend (PrivateSnapshotStore.Writer) -> Unit) =
            store.stage(TransactionId(UUID.randomUUID().toString()), body)
        assertTrue(attempt { } is SnapshotCaptureResult.Rejected)
        assertEquals(SnapshotFailure.InputLimitExceeded,
            (attempt { it.write(ByteArray(4)) } as SnapshotCaptureResult.Rejected).reason)
        io.syncFails = true
        assertTrue(attempt { it.write(byteArrayOf(1)) } is SnapshotCaptureResult.Rejected)
        assertEquals(0L, budget.used())
        assertFalse(boundedFiles(budget.root).any { it.name.endsWith(".sealed") })
    }
    @Test fun cancellationIdentitySurvivesCleanupFaultAndRetainedCharge() = runTest {
        val io = Io()
        val budget = MStorageBudget(PrivateRoot(File(temporary.root.canonicalFile, "root"), io))
        val store = PrivateSnapshotStore(budget, backgroundScope, Dispatchers.Unconfined)
        val cancel = CancellationException("Test cancellation")
        try {
            store.stage(TransactionId(UUID.randomUUID().toString())) {
                it.write(byteArrayOf(1)); io.deletesFail = true; throw cancel
            }
            fail("Cancellation must propagate")
        } catch (caught: CancellationException) {
            assertSame(cancel, caught); assertTrue(caught.suppressed.isNotEmpty())
        }
        assertTrue(budget.used() > 0)
        assertEquals(1, boundedFiles(budget.root).size)
    }
    @Test fun failedDeletionIsCleanupPendingNotReleased() = runTest {
        val io = Io()
        val budget = MStorageBudget(PrivateRoot(File(temporary.root.canonicalFile, "root"), io))
        val store = PrivateSnapshotStore(budget, backgroundScope, Dispatchers.Unconfined)
        val snapshot = (store.stage(TransactionId(UUID.randomUUID().toString())) { it.write(byteArrayOf(9)) }
            as SnapshotCaptureResult.Captured).snapshot
        io.deletesFail = true; snapshot.requestClose()
        assertTrue(snapshot.awaitReleased() is SnapshotReleaseResult.CleanupPending)
        assertEquals(1L, budget.used())
    }
    @Test fun canceledWaiterDoesNotCancelOwnerOrLiveLease() = runTest {
        val io = Io()
        val budget = MStorageBudget(PrivateRoot(File(temporary.root.canonicalFile, "root"), io))
        val store = PrivateSnapshotStore(budget, backgroundScope, Dispatchers.Unconfined)
        val snapshot = (store.stage(TransactionId(UUID.randomUUID().toString())) { it.write(byteArrayOf(1)) }
            as SnapshotCaptureResult.Captured).snapshot
        val lease = (snapshot.acquireReadLease() as SnapshotLeaseResult.Acquired).lease
        snapshot.requestClose()
        val waiter = launch { snapshot.awaitReleased() }
        waiter.cancelAndJoin()
        assertEquals(1L, budget.used())
        lease.close(); assertSame(SnapshotReleaseResult.Released, snapshot.awaitReleased())
    }
    @Test fun writeCloseAndRereadFailuresNeverEscapeAsCaptured() = runTest {
        for (fault in listOf("write", "close", "reread")) {
            val io = Io()
            val root = PrivateRoot(File(temporary.root.canonicalFile, fault), io)
            val budget = MStorageBudget(root)
            val store = PrivateSnapshotStore(budget, backgroundScope, Dispatchers.Unconfined)
            io.outputFault = fault; io.evidenceFault = fault == "reread"
            assertTrue(store.stage(TransactionId(UUID.randomUUID().toString())) { it.write(byteArrayOf(1)) }
                is SnapshotCaptureResult.Rejected)
            assertFalse(boundedFiles(root).any { it.name.endsWith(".sealed") })
        }
    }
    @Test fun originalFailureIsNotReplacedByCleanupFailure() = runTest {
        val io = Io()
        val budget = MStorageBudget(PrivateRoot(File(temporary.root.canonicalFile, "root"), io))
        val store = PrivateSnapshotStore(budget, backgroundScope, Dispatchers.Unconfined)
        val primary = IllegalStateException("Programmer defect")
        try {
            store.stage(TransactionId(UUID.randomUUID().toString())) {
                it.write(byteArrayOf(1)); io.deletesFail = true; throw primary
            }
            fail("Must propagate programmer defect")
        } catch (caught: IllegalStateException) {
            assertSame(primary, caught); assertTrue(caught.suppressed.isNotEmpty())
        }
    }

}
