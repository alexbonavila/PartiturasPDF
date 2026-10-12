package com.abdev.partituraspdf.storage.saf

import android.os.ParcelFileDescriptor
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.abdev.partituraspdf.core.document.StorageDocumentRef
import com.abdev.partituraspdf.core.result.AppError
import com.abdev.partituraspdf.core.result.Outcome
import com.abdev.partituraspdf.storage.api.ReadAccessLimits
import com.abdev.partituraspdf.storage.api.ReadHandle
import com.abdev.partituraspdf.storage.api.ReadProvenance
import java.io.File
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Simulates abandoned files left by a killed process; all fixtures are app-private. */
@RunWith(AndroidJUnit4::class)
class SafReadCopyRecoveryTest {
    private val document = StorageDocumentRef(
        "saf", "content://test.documents/document/root%3AMusic%2Fscore.pdf"
    )

    private fun directory(): File = File(
        InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,
        "saf-recovery-test-${UUID.randomUUID()}",
    ).apply { check(mkdirs()) }

    private fun pipe(bytes: ByteArray): ParcelFileDescriptor {
        require(bytes.size < 1024)
        val pair = ParcelFileDescriptor.createPipe()
        try {
            ParcelFileDescriptor.AutoCloseOutputStream(pair[1]).use { it.write(bytes) }
            return pair[0]
        } catch (failure: Throwable) {
            pair[0].close()
            throw failure
        }
    }

    private fun reader(
        dir: File,
        opener: () -> ParcelFileDescriptor?,
        onCopied: (Long) -> Unit = {},
    ) = SafDocumentReadAccess(SafProviderRead { _ -> opener() }, dir, onCopied)

    // Keep the opener as the trailing lambda for single-callback test fixtures.
    private fun reader(dir: File, opener: () -> ParcelFileDescriptor?) =
        reader(dir, opener, onCopied = {})

    private fun Outcome<ReadHandle>.handle(): ReadHandle =
        (this as Outcome.Success<ReadHandle>).value

    @Test fun orphanIsRemovedOnNextDirectReadAfterSimulatedRestart() {
        val dir = directory()
        try {
            val orphan = File(dir, "saf-read-orphan.tmp").apply { writeText("old copy") }
            val unrelated = File(dir, "keep-this.pdf").apply { writeText("original") }
            val nextInstance = reader(dir) { null }
            assertEquals(
                Outcome.Failure(AppError.Access.PROVIDER_IO_FAILURE),
                runBlocking { nextInstance.openRead(document) },
            )
            assertFalse(orphan.exists())
            assertEquals("original", unrelated.readText())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test fun directSeekableReadReclaimsOrphansWithoutCreatingCopies() {
        val dir = directory()
        val source = File(dir, "user-source.pdf").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        val orphan = File(dir, "saf-read-abandoned.tmp").apply { writeBytes(byteArrayOf(4)) }
        try {
            val access = reader(dir) {
                ParcelFileDescriptor.open(source, ParcelFileDescriptor.MODE_READ_ONLY)
            }
            runBlocking {
                access.openSeekable(document, ReadAccessLimits(1)).handle().use { handle ->
                    assertEquals(ReadProvenance.Provider(document), handle.provenance)
                    assertTrue(handle.seekable)
                }
            }
            assertFalse(orphan.exists())
            assertArrayEquals(byteArrayOf(1, 2, 3), source.readBytes())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test fun activeLeaseSurvivesSweepingFromAnotherAdapterInstance() {
        val dir = directory()
        try {
            val first = reader(dir) { pipe(byteArrayOf(5, 6, 7)) }
            val handle = runBlocking {
                first.openSeekable(document, ReadAccessLimits(16)).handle()
            }
            val active = dir.listFiles()!!.single()
            try {
                val second = reader(dir) { null }
                assertEquals(
                    Outcome.Failure(AppError.Access.PROVIDER_IO_FAILURE),
                    runBlocking { second.openRead(document) },
                )
                assertTrue(active.isFile)
                assertArrayEquals(byteArrayOf(5, 6, 7), active.readBytes())
            } finally {
                handle.close()
            }
            assertFalse(active.exists())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test fun unfinishedCopyIsProtectedFromReentrantOrphanSweep() {
        val dir = directory()
        var observed = 0
        try {
            val nested = reader(dir) { null }
            val first = reader(dir, { pipe(byteArrayOf(1, 2, 3, 4)) }) { _ ->
                observed++
                // Run a separate adapter instance while the first copy is still writing.
                assertEquals(
                    Outcome.Failure(AppError.Access.PROVIDER_IO_FAILURE),
                    runBlocking { nested.openRead(document) },
                )
                assertEquals(1, dir.listFiles()!!.size)
            }
            runBlocking {
                first.openSeekable(document, ReadAccessLimits(16)).handle().use { handle ->
                    assertEquals(ReadProvenance.TemporaryReadCopy(document), handle.provenance)
                }
            }
            assertEquals(1, observed)
            assertTrue(dir.listFiles().isNullOrEmpty())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test fun failedLeaseDeletionCanBeRetriedOnNextAccess() {
        val dir = directory()
        try {
            val first = reader(dir) { pipe(byteArrayOf(9)) }
            val handle = runBlocking {
                first.openSeekable(document, ReadAccessLimits(16)).handle()
            }
            val temp = dir.listFiles()!!.single()
            // Reproduce a deletion failure using a nonempty directory at the same path.
            assertTrue(temp.delete())
            assertTrue(temp.mkdir())
            val marker = File(temp, "marker").apply { writeText("keep") }
            assertThrows(IOException::class.java) { handle.close() }
            assertTrue(marker.isFile)
            // Repair simulated filesystem obstruction, leaving an orphan regular file.
            assertTrue(marker.delete())
            assertTrue(temp.delete())
            temp.writeBytes(byteArrayOf(7))
            val restarted = reader(dir) { null }
            runBlocking { restarted.openRead(document) }
            assertFalse(temp.exists())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test fun unmanagedNamesAndDirectoriesAreNeverDeleted() {
        val dir = directory()
        try {
            val differentPrefix = File(dir, "unrelated.tmp").apply { writeText("a") }
            val differentSuffix = File(dir, "saf-read-important.pdf").apply { writeText("b") }
            val matchingDirectory = File(dir, "saf-read-folder.tmp").apply { check(mkdirs()) }
            val child = File(matchingDirectory, "important").apply { writeText("c") }
            SafReadCopyRecovery.cleanupOrphans(dir)
            assertEquals("a", differentPrefix.readText())
            assertEquals("b", differentSuffix.readText())
            assertTrue(matchingDirectory.isDirectory)
            assertEquals("c", child.readText())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test fun cleanupOfMissingDirectoryDoesNotCreatePrivateStorage() {
        val dir = File(
            InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,
            "saf-recovery-missing-${UUID.randomUUID()}",
        )
        assertFalse(dir.exists())
        SafReadCopyRecovery.cleanupOrphans(dir)
        assertFalse(dir.exists())
    }
}
