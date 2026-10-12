package com.abdev.partituraspdf.storage.saf

import android.os.ParcelFileDescriptor
import android.system.Os
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.abdev.partituraspdf.core.document.StorageDocumentRef
import com.abdev.partituraspdf.core.result.AppError
import com.abdev.partituraspdf.core.result.Outcome
import com.abdev.partituraspdf.storage.api.ReadAccessLimits
import com.abdev.partituraspdf.storage.api.ReadHandle
import com.abdev.partituraspdf.storage.api.ReadOwnership
import com.abdev.partituraspdf.storage.api.ReadProvenance
import java.io.File
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Deterministic failure injection for SAF read-copy lifecycle on Android API 26. */
@RunWith(AndroidJUnit4::class)
class SafReadRobustnessTest {
    private val doc = StorageDocumentRef(
        "saf", "content://test.documents/document/primary%3AMusic%2Fscore.pdf"
    )

    private fun directory(): File = File(
        InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,
        "saf-robustness-${UUID.randomUUID()}",
    ).apply { check(mkdirs()) }

    private fun source(bytes: ByteArray): ParcelFileDescriptor {
        require(bytes.size < 1024)
        val pipe = ParcelFileDescriptor.createPipe()
        try {
            ParcelFileDescriptor.AutoCloseOutputStream(pipe[1]).use { it.write(bytes) }
            return pipe[0]
        } catch (failed: Throwable) {
            pipe[0].close()
            throw failed
        }
    }

    private fun cached(dir: File): List<File> = dir.listFiles()?.toList().orEmpty()

    private fun cleanup(dir: File) {
        dir.deleteRecursively()
    }

    private fun access(
        dir: File,
        input: () -> ParcelFileDescriptor?,
        onCopied: (Long) -> Unit = {},
    ): SafDocumentReadAccess = SafDocumentReadAccess(
        SafProviderRead { _ -> input() },
        dir,
        onCopied,
    )

    private fun Outcome<ReadHandle>.handle(): ReadHandle =
        (this as Outcome.Success<ReadHandle>).value

    @Test fun cancellationDuringCopyClosesProviderAndRemovesIncompleteBytes() {
        val dir = directory()
        val job = Job()
        var descriptor: ParcelFileDescriptor? = null
        var observed = 0
        try {
            val reader = access(dir, {
                source(byteArrayOf(1, 2, 3)).also { descriptor = it }
            }, {
                observed++
                job.cancel()
            })
            assertThrows(CancellationException::class.java) {
                runBlocking {
                    withContext(job) { reader.openSeekable(doc, ReadAccessLimits(64)) }
                }
            }
            assertEquals(1, observed)
            assertTrue(cached(dir).isEmpty())
            assertThrows(IllegalStateException::class.java) { descriptor!!.fd }
        } finally {
            descriptor?.close()
            cleanup(dir)
        }
    }

    @Test fun unexpectedProgressObserverFailureStillClosesInputAndClearsPartialCopy() {
        val dir = directory()
        var descriptor: ParcelFileDescriptor? = null
        try {
            val reader = access(dir, {
                source(byteArrayOf(7, 8, 9)).also { descriptor = it }
            }, {
                throw IllegalStateException("injected observer defect")
            })
            assertThrows(IllegalStateException::class.java) {
                runBlocking { reader.openSeekable(doc, ReadAccessLimits(64)) }
            }
            assertTrue(cached(dir).isEmpty())
            assertThrows(IllegalStateException::class.java) { descriptor!!.fd }
        } finally {
            descriptor?.close()
            cleanup(dir)
        }
    }

    @Test fun providerReadFailureAfterFirstChunkKeepsProviderIoClassification() {
        val dir = directory()
        var descriptor: ParcelFileDescriptor? = null
        try {
            val reader = access(dir, {
                source(byteArrayOf(1, 2, 3)).also { descriptor = it }
            }, {
                // Closing the input fd makes the next read fail with EBADF.
                descriptor!!.close()
            })
            assertEquals(
                Outcome.Failure(AppError.Access.PROVIDER_IO_FAILURE),
                runBlocking { reader.openSeekable(doc, ReadAccessLimits(64)) },
            )
            assertTrue(cached(dir).isEmpty())
            assertThrows(IllegalStateException::class.java) { descriptor!!.fd }
        } finally {
            descriptor?.close()
            cleanup(dir)
        }
    }

    @Test fun successfulCopyReportsOnlyActualWrittenBytesAndRemovesCopyAtClose() {
        val dir = directory()
        val bytes = byteArrayOf(37, 80, 68, 70, 1, 2, 3, 4)
        val totals = mutableListOf<Long>()
        try {
            val reader = access(dir, { source(bytes) }, { totals += it })
            val handle = runBlocking { reader.openSeekable(doc, ReadAccessLimits(8)).handle() }
            try {
                assertEquals(ReadProvenance.TemporaryReadCopy(doc), handle.provenance)
                assertTrue(handle.seekable)
                assertEquals(8L, totals.last())
                assertTrue(totals.zipWithNext().all { (a, b) -> b > a })
                assertEquals(1, cached(dir).size)
                val actual = ByteArray(bytes.size)
                assertEquals(bytes.size, Os.read(handle.descriptor.fileDescriptor, actual, 0, actual.size))
                assertArrayEquals(bytes, actual)
            } finally {
                handle.close()
            }
            assertTrue(cached(dir).isEmpty())
        } finally {
            cleanup(dir)
        }
    }

    @Test fun directSeekablePathNeverInvokesCopyObserver() {
        val dir = directory()
        val file = File(dir, "original-${UUID.randomUUID()}.pdf")
        file.writeBytes(byteArrayOf(1, 2, 3))
        var observed = false
        try {
            val reader = access(dir, {
                ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            }, { observed = true })
            runBlocking { reader.openSeekable(doc, ReadAccessLimits(1)).handle().use { handle ->
                assertEquals(ReadProvenance.Provider(doc), handle.provenance)
                assertTrue(handle.seekable)
            } }
            assertFalse(observed)
            assertArrayEquals(byteArrayOf(1, 2, 3), file.readBytes())
            assertEquals(listOf(file), cached(dir))
        } finally {
            cleanup(dir)
        }
    }

    @Test fun unremovableTemporaryFileReportsCleanupFailureAndHandleClosesOnce() {
        val dir = directory()
        try {
            val reader = access(dir, { source(byteArrayOf(9, 8)) })
            val handle = runBlocking { reader.openSeekable(doc, ReadAccessLimits(10)).handle() }
            val temp = cached(dir).single()
            // Force delete() failure without relying on device storage permissions.
            assertTrue(temp.delete())
            assertTrue(temp.mkdir())
            File(temp, "sentinel").writeText("retain")
            val failure = assertThrows(IOException::class.java) { handle.close() }
            assertTrue(failure.message.orEmpty().contains("Unable to delete"))
            assertEquals(ReadOwnership.CLOSED, handle.ownership)
            handle.close() // No repeated native release after a reported cleanup failure.
            assertTrue(temp.exists())
        } finally {
            cleanup(dir)
        }
    }
}
