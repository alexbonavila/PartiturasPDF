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
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SafDocumentReadAccessTest {
    private val uri = "content://test.documents/document/primary%3AMusic%2Fscore.pdf"
    private val document = StorageDocumentRef("saf", uri)

    private fun directory(): File = File(
        InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,
        "saf-copy-test-${UUID.randomUUID()}",
    ).apply { check(mkdirs()) }

    private fun sourceFile(bytes: ByteArray): File = File(
        InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,
        "saf-input-${UUID.randomUUID()}.pdf",
    ).apply { writeBytes(bytes) }

    private fun pipe(bytes: ByteArray): ParcelFileDescriptor {
        // Small payloads are written before reading to avoid pipe buffer backpressure.
        require(bytes.size < 1024)
        val pair = ParcelFileDescriptor.createPipe()
        try {
            ParcelFileDescriptor.AutoCloseOutputStream(pair[1]).use { it.write(bytes) }
            return pair[0]
        } catch (failed: Throwable) {
            pair[0].close()
            throw failed
        }
    }

    private fun access(dir: File, open: () -> ParcelFileDescriptor?): SafDocumentReadAccess =
        SafDocumentReadAccess(SafProviderRead { _ -> open() }, dir)

    private fun Outcome<ReadHandle>.handle(): ReadHandle =
        (this as Outcome.Success<ReadHandle>).value

    private fun cachedFiles(dir: File): List<File> = dir.listFiles()?.toList().orEmpty()

    private fun clean(dir: File) {
        cachedFiles(dir).forEach { it.delete() }
        dir.delete()
    }

    @Test fun directSeekableFileReturnsProviderHandleWithoutMakingCopy() {
        val dir = directory()
        val source = sourceFile(byteArrayOf(1, 2, 3))
        try {
            val subject = access(dir) {
                ParcelFileDescriptor.open(source, ParcelFileDescriptor.MODE_READ_ONLY)
            }
            runBlocking {
                subject.openSeekable(document, ReadAccessLimits(1)).handle().use { handle ->
                    assertTrue(handle.seekable)
                    assertEquals(ReadProvenance.Provider(document), handle.provenance)
                    assertEquals(ReadOwnership.OWNED, handle.ownership)
                    assertTrue(cachedFiles(dir).isEmpty())
                }
            }
            assertTrue(cachedFiles(dir).isEmpty())
        } finally {
            source.delete()
            clean(dir)
        }
    }

    @Test fun nonSeekablePipeIsCopiedExactlyAndDeletedAtClose() {
        val dir = directory()
        val bytes = byteArrayOf(37, 80, 68, 70, 10, 1, 2, 3)
        try {
            val subject = access(dir) { pipe(bytes) }
            val handle = runBlocking {
                subject.openSeekable(document, ReadAccessLimits(100)).handle()
            }
            assertEquals(ReadProvenance.TemporaryReadCopy(document), handle.provenance)
            assertTrue(handle.seekable)
            assertEquals(1, cachedFiles(dir).size)
            try {
                val read = ByteArray(bytes.size)
                assertEquals(bytes.size, Os.read(handle.descriptor.fileDescriptor, read, 0, read.size))
                assertArrayEquals(bytes, read)
            } finally {
                handle.close()
            }
            assertEquals(ReadOwnership.CLOSED, handle.ownership)
            assertTrue(cachedFiles(dir).isEmpty())
        } finally {
            clean(dir)
        }
    }

    @Test fun exactByteLimitAllowsFullCopy() {
        val dir = directory()
        try {
            val subject = access(dir) { pipe(byteArrayOf(1, 2, 3, 4, 5)) }
            runBlocking {
                subject.openSeekable(document, ReadAccessLimits(5)).handle().use {
                    assertEquals(5L, cachedFiles(dir).single().length())
                }
            }
            assertTrue(cachedFiles(dir).isEmpty())
        } finally {
            clean(dir)
        }
    }

    @Test fun aboveByteLimitNeverReturnsPartialCopy() {
        val dir = directory()
        try {
            val subject = access(dir) { pipe(byteArrayOf(1, 2, 3, 4, 5, 6)) }
            assertEquals(
                Outcome.Failure(AppError.Resource.INPUT_LIMIT),
                runBlocking { subject.openSeekable(document, ReadAccessLimits(5)) },
            )
            assertTrue(cachedFiles(dir).isEmpty())
        } finally {
            clean(dir)
        }
    }

    @Test fun unknownProviderSizeStillChecksActualBytes() {
        val dir = directory()
        try {
            val subject = access(dir) { pipe(ByteArray(100) { 7 }) }
            assertEquals(
                Outcome.Failure(AppError.Resource.INPUT_LIMIT),
                runBlocking { subject.openSeekable(document, ReadAccessLimits(99)) },
            )
            assertTrue(cachedFiles(dir).isEmpty())
        } finally {
            clean(dir)
        }
    }

    @Test fun zeroByteInputIsOnlyAReadCopyNotAValidatedPdf() {
        val dir = directory()
        try {
            val subject = access(dir) { pipe(byteArrayOf()) }
            runBlocking {
                subject.openSeekable(document, ReadAccessLimits(1)).handle().use { handle ->
                    assertEquals(0L, cachedFiles(dir).single().length())
                    assertEquals(ReadProvenance.TemporaryReadCopy(document), handle.provenance)
                }
            }
            assertTrue(cachedFiles(dir).isEmpty())
        } finally {
            clean(dir)
        }
    }

    @Test fun openReadRemainsDirectAndDoesNotCopyPipes() {
        val dir = directory()
        try {
            val subject = access(dir) { pipe(byteArrayOf(5, 6)) }
            runBlocking {
                subject.openRead(document).handle().use { handle ->
                    assertFalse(handle.seekable)
                    assertEquals(ReadProvenance.Provider(document), handle.provenance)
                    assertTrue(cachedFiles(dir).isEmpty())
                }
            }
        } finally {
            clean(dir)
        }
    }

    @Test fun invalidReferenceIsRejectedWithoutOpeningInput() {
        val dir = directory()
        var opens = 0
        try {
            val subject = access(dir) { opens++; null }
            assertEquals(
                Outcome.Failure(AppError.Validation.INVALID_DOCUMENT),
                runBlocking {
                    subject.openSeekable(StorageDocumentRef("wrong", uri), ReadAccessLimits(10))
                },
            )
            assertEquals(0, opens)
            assertTrue(cachedFiles(dir).isEmpty())
        } finally {
            clean(dir)
        }
    }

    @Test fun permissionErrorDoesNotCreatePrivateCopy() {
        val dir = directory()
        try {
            val subject = access(dir) { throw SecurityException("access denied") }
            assertEquals(
                Outcome.Failure(AppError.Access.PERMISSION_DENIED),
                runBlocking { subject.openSeekable(document, ReadAccessLimits(10)) },
            )
            assertTrue(cachedFiles(dir).isEmpty())
        } finally {
            clean(dir)
        }
    }

    @Test fun providerIoFailureIsNotTranslatedToDeletion() {
        val dir = directory()
        try {
            val subject = access(dir) { throw IOException("provider failure") }
            assertEquals(
                Outcome.Failure(AppError.Access.PROVIDER_IO_FAILURE),
                runBlocking { subject.openSeekable(document, ReadAccessLimits(10)) },
            )
            assertTrue(cachedFiles(dir).isEmpty())
        } finally {
            clean(dir)
        }
    }

    @Test fun missingPrivateDirectoryAccessFailsAndClosesPipe() {
        val privateRoot = sourceFile(byteArrayOf(1)) // a file cannot serve as directory
        var supplied: ParcelFileDescriptor? = null
        try {
            val subject = access(privateRoot) { pipe(byteArrayOf(4, 5)).also { supplied = it } }
            assertEquals(
                Outcome.Failure(AppError.Access.PRIVATE_STORAGE_UNAVAILABLE),
                runBlocking { subject.openSeekable(document, ReadAccessLimits(100)) },
            )
            assertThrows(IllegalStateException::class.java) { supplied!!.fd }
        } finally {
            privateRoot.delete()
        }
    }

    @Test fun completedCopyHasExclusiveTransferableOwnership() {
        val dir = directory()
        try {
            val subject = access(dir) { pipe(byteArrayOf(4)) }
            val first = runBlocking { subject.openSeekable(document, ReadAccessLimits(5)).handle() }
            val owner = first.transferOwnership()
            assertEquals(ReadOwnership.TRANSFERRED, first.ownership)
            assertEquals(ReadOwnership.OWNED, owner.ownership)
            assertThrows(IllegalStateException::class.java) { first.close() }
            owner.close()
            owner.close()
            assertTrue(cachedFiles(dir).isEmpty())
        } finally {
            clean(dir)
        }
    }

    @Test fun nativeConsumerCanCloseDescriptorBeforeLeaseClose() {
        val dir = directory()
        try {
            val subject = access(dir) { pipe(byteArrayOf(1, 2)) }
            val handle = runBlocking { subject.openSeekable(document, ReadAccessLimits(10)).handle() }
            assertEquals(1, cachedFiles(dir).size)
            handle.descriptor.close() // Models PdfRenderer closing its adopted descriptor.
            handle.close()
            assertTrue(cachedFiles(dir).isEmpty())
        } finally {
            clean(dir)
        }
    }

    @Test fun independentCopiesGetDifferentPrivateFiles() {
        val dir = directory()
        try {
            val subject = access(dir) { pipe(byteArrayOf(1, 2)) }
            val first = runBlocking { subject.openSeekable(document, ReadAccessLimits(10)).handle() }
            val firstFile = cachedFiles(dir).single().name
            val second = runBlocking { subject.openSeekable(document, ReadAccessLimits(10)).handle() }
            try {
                assertEquals(2, cachedFiles(dir).size)
                val secondFile = cachedFiles(dir).map { it.name }.single { it != firstFile }
                assertNotEquals(firstFile, secondFile)
            } finally {
                second.close()
                first.close()
            }
            assertTrue(cachedFiles(dir).isEmpty())
        } finally {
            clean(dir)
        }
    }

    @Test fun cancellationBeforeAcquisitionDoesNotOpenProvider() {
        val dir = directory()
        var called = false
        try {
            val subject = access(dir) { called = true; null }
            val job = Job().apply { cancel() }
            assertThrows(CancellationException::class.java) {
                runBlocking { withContext(job) { subject.openSeekable(document, ReadAccessLimits(10)) } }
            }
            assertFalse(called)
            assertTrue(cachedFiles(dir).isEmpty())
        } finally {
            clean(dir)
        }
    }
}
