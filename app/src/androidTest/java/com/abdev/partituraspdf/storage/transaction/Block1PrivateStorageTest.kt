package com.abdev.partituraspdf.storage.transaction

import android.os.ParcelFileDescriptor
import android.system.Os
import android.system.OsConstants
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.abdev.partituraspdf.core.document.StorageDocumentRef
import com.abdev.partituraspdf.core.result.Outcome
import com.abdev.partituraspdf.storage.api.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class Block1PrivateStorageTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val source = SnapshotSource(StorageDocumentRef("test", "opaque-source"))
    private fun access(open: () -> ParcelFileDescriptor): DocumentReadAccess = object : DocumentReadAccess {
        override suspend fun openRead(document: StorageDocumentRef): Outcome<ReadHandle> = Outcome.Success(
            ReadHandle(object : ReadResource {
                override val descriptor = open()
                override fun close() = descriptor.close()
            }, ReadProvenance.Provider(document), false))
        override suspend fun openSeekable(document: StorageDocumentRef, limits: ReadAccessLimits): Outcome<ReadHandle> =
            error("Capture must not call openSeekable")
    }
    private suspend fun sandbox(body: suspend (MStorageBudget, PrivateSnapshotStore) -> Unit) = coroutineScope {
        // Only this unpredictable test-owned subtree is eligible for test teardown.
        val directory = File(context.noBackupFilesDir.canonicalFile, "m-block1-test-${UUID.randomUUID()}")
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val root = withContext(Dispatchers.IO) { PrivateRoot(directory, AndroidPrivateIo()) }
            val budget = MStorageBudget(root)
            body(budget, PrivateSnapshotStore(budget, scope))
        } finally {
            scope.coroutineContext[Job]!!.cancelAndJoin()
            withContext(Dispatchers.IO) { directory.deleteRecursively() }
        }
    }
    @Test fun pipeCaptureRehashesImmutableBytesAndIndependentOffsets() = runBlocking {
        sandbox { budget, store ->
            val original = byteArrayOf(1, 2, 3, 4)
            val capturer = TransactionSnapshotCapturer(access {
                val pipe = ParcelFileDescriptor.createPipe()
                ParcelFileDescriptor.AutoCloseOutputStream(pipe[1]).use { it.write(original) }
                pipe[0]
            }, store)
            val snapshot = (capturer.capture(source, SnapshotCaptureLimits(4)) as SnapshotCaptureResult.Captured).snapshot
            try {
                assertEquals(SnapshotOrigin.Source(source), snapshot.origin)
                assertEquals(4L, snapshot.evidence.byteCount)
                val a = (snapshot.acquireReadLease() as SnapshotLeaseResult.Acquired).lease
                val b = (snapshot.acquireReadLease() as SnapshotLeaseResult.Acquired).lease
                try {
                    assertEquals(2L, Os.lseek(a.descriptor.fileDescriptor, 2, OsConstants.SEEK_SET))
                    assertEquals(0L, Os.lseek(b.descriptor.fileDescriptor, 0, OsConstants.SEEK_CUR))
                    assertEquals(4L, budget.used())
                    snapshot.requestClose()
                    assertTrue(snapshot.acquireReadLease() is SnapshotLeaseResult.Rejected)
                } finally { a.close(); b.close() }
            } finally { snapshot.requestClose(); assertSame(SnapshotReleaseResult.Released, snapshot.awaitReleased()) }
        }
    }
    @Test fun canonicalFixtureCapturePreservesOriginalAndRegisteredAssociationIsOptional() = runBlocking {
        sandbox { _, store ->
            val original = File(context.noBackupFilesDir, "m-source-test-${UUID.randomUUID()}")
            try {
                val assets = InstrumentationRegistry.getInstrumentation().context.assets
                assets.open("pdf/basic/02-two-pages.pdf").use { input -> original.outputStream().use { input.copyTo(it) } }
                val expected = digest(original)
                val manifest = org.json.JSONObject(assets.open("manifest.json").bufferedReader().use { it.readText() })
                val files = manifest.getJSONArray("files")
                val entry = (0 until files.length()).map { files.getJSONObject(it) }.single { it.getString("id") == "02" }
                assertEquals(entry.getString("sha256"), expected.sha256)
                assertEquals(entry.getLong("bytes"), expected.byteCount)
                val snapshot = (TransactionSnapshotCapturer(access {
                    ParcelFileDescriptor.open(original, ParcelFileDescriptor.MODE_READ_ONLY)
                }, store).capture(source, SnapshotCaptureLimits(64L * 1024 * 1024)) as SnapshotCaptureResult.Captured).snapshot
                try {
                    assertEquals(expected, snapshot.evidence)
                    assertEquals(expected, digest(original))
                    assertNull((snapshot.origin as SnapshotOrigin.Source).source.registered)
                } finally { snapshot.requestClose(); assertSame(SnapshotReleaseResult.Released, snapshot.awaitReleased()) }
            } finally { original.delete() }
        }
    }
    @Test fun actualDirectorySyncCheckpointReopenAndCorruptionFailClosed() = runBlocking {
        sandbox { budget, _ ->
            assertTrue(budget.root.directory.canonicalPath.startsWith(context.noBackupFilesDir.canonicalPath + File.separator))
            val id = TransactionId(UUID.randomUUID().toString())
            val checkpoint = PrivateJournal(budget).appendPrivate(id, TransactionState.PREPARING)
            // Unsupported fsync is a real failure, never an assumed runtime PASS.
            assertTrue("API26 filesystem must confirm required directory sync", checkpoint is CheckpointResult.Durable)
            val restarted = MStorageBudget(budget.root)
            assertEquals(budget.used(), restarted.used())
            val recoverer = PrivateRecovery(restarted)
            assertEquals(recoverer.scan(), recoverer.scan())
            File(budget.root.directory, "tx-${id.value}/journal/000000000001.rec").appendBytes(byteArrayOf(1))
            val result = PrivateRecovery(MStorageBudget(budget.root)).scan() as RecoveryScan.Complete
            assertEquals(RecoveryClassification.BLOCKED, result.transactions.single().classification)
        }
    }
    @Test fun observedLimitNotProviderMetadataControlsPipeCopy() = runBlocking {
        sandbox { _, store ->
            val capturer = TransactionSnapshotCapturer(access {
                val pipe = ParcelFileDescriptor.createPipe()
                ParcelFileDescriptor.AutoCloseOutputStream(pipe[1]).use { it.write(byteArrayOf(1, 2, 3, 4, 5)) }
                pipe[0]
            }, store)
            assertEquals(SnapshotFailure.InputLimitExceeded,
                (capturer.capture(source, SnapshotCaptureLimits(4)) as SnapshotCaptureResult.Rejected).reason)
        }
    }
}
