package com.abdev.partituraspdf.storage.saf

import android.os.ParcelFileDescriptor
import android.system.Os
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.abdev.partituraspdf.core.document.StorageDocumentRef
import com.abdev.partituraspdf.core.result.AppError
import com.abdev.partituraspdf.core.result.Outcome
import com.abdev.partituraspdf.storage.api.ReadOwnership
import com.abdev.partituraspdf.storage.api.ReadProvenance
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SafProviderReadTest {
    private val fileUri = "content://test.documents/document/primary%3AMusic%2Fscore.pdf"
    private val treeUri = "content://test.documents/tree/primary%3AMusic/document/primary%3AMusic%2Fscore.pdf"

    private fun document(value: String = fileUri) = StorageDocumentRef("saf", value)

    private fun tempFile(): File {
        val root = InstrumentationRegistry.getInstrumentation().targetContext.cacheDir
        return File(root, "saf-provider-read-${UUID.randomUUID()}.pdf")
            .apply { writeBytes(byteArrayOf(37, 80, 68, 70)) } // %PDF
    }

    @Test fun seekableFileUsesProviderDescriptorWithoutCopying() = runBlocking {
        val file = tempFile()
        try {
            var provided: ParcelFileDescriptor? = null
            val access = SafProviderRead { uri ->
                assertEquals(android.net.Uri.parse(fileUri), uri)
                ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).also {
                    provided = it
                }
            }
            val result = access.openRead(document())
            val handle = (result as Outcome.Success).value
            try {
                assertTrue(handle.seekable)
                assertEquals(ReadProvenance.Provider(document()), handle.provenance)
                assertEquals(ReadOwnership.OWNED, handle.ownership)
                assertSame(provided, handle.descriptor)
                val bytes = ByteArray(4)
                assertEquals(4, Os.read(handle.descriptor.fileDescriptor, bytes, 0, bytes.size))
                assertArrayEquals(byteArrayOf(37, 80, 68, 70), bytes)
            } finally {
                handle.close()
            }
            assertEquals(ReadOwnership.CLOSED, handle.ownership)
            assertThrows(IllegalStateException::class.java) { provided!!.fd }
        } finally {
            file.delete()
        }
        Unit
    }

    @Test fun treeDocumentUsesTheSameDirectOpenPath() = runBlocking {
        val file = tempFile()
        try {
            val access = SafProviderRead { uri ->
                assertEquals(android.net.Uri.parse(treeUri), uri)
                ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            }
            val handle = (access.openRead(document(treeUri)) as Outcome.Success).value
            handle.use {
                assertTrue(it.seekable)
                assertEquals(ReadProvenance.Provider(document(treeUri)), it.provenance)
            }
        } finally {
            file.delete()
        }
    }

    @Test fun pipeIsReportedAsNonSeekableAndOwned() = runBlocking {
        val pair = ParcelFileDescriptor.createPipe()
        try {
            val access = SafProviderRead { _ -> pair[0] }
            val handle = (access.openRead(document()) as Outcome.Success).value
            try {
                assertFalse(handle.seekable)
                assertEquals(ReadProvenance.Provider(document()), handle.provenance)
            } finally {
                handle.close()
            }
            assertThrows(IllegalStateException::class.java) { pair[0].fd }
        } finally {
            pair.forEach { it.close() }
        }
        Unit
    }

    @Test fun rejectsInvalidRefsBeforeProviderIsInvoked() = runBlocking {
        var invoked = 0
        val access = SafProviderRead { _ -> invoked++; null }
        assertEquals(
            Outcome.Failure(AppError.Validation.INVALID_DOCUMENT),
            access.openRead(StorageDocumentRef("other", fileUri)),
        )
        assertEquals(
            Outcome.Failure(AppError.Validation.INVALID_DOCUMENT),
            access.openRead(document("file:///tmp/score.pdf")),
        )
        assertEquals(0, invoked)
    }

    @Test fun nullDescriptorDoesNotClaimDocumentWasDeleted() = runBlocking {
        val access = SafProviderRead { _ -> null }
        assertEquals(
            Outcome.Failure(AppError.Access.PROVIDER_IO_FAILURE),
            access.openRead(document()),
        )
    }

    @Test fun missingProviderFileIsNotProofOfDeletion() = runBlocking {
        val access = SafProviderRead { _ -> throw FileNotFoundException("not found") }
        assertEquals(
            Outcome.Failure(AppError.Access.PROVIDER_IO_FAILURE),
            access.openRead(document()),
        )
    }

    @Test fun securityFailureIsPermissionDenied() = runBlocking {
        val access = SafProviderRead { _ -> throw SecurityException("not authorized") }
        assertEquals(
            Outcome.Failure(AppError.Access.PERMISSION_DENIED),
            access.openRead(document()),
        )
    }

    @Test fun providerIoFailureDoesNotEscapeAsUncheckedError() = runBlocking {
        val access = SafProviderRead { _ -> throw IOException("provider failure") }
        assertEquals(
            Outcome.Failure(AppError.Access.PROVIDER_IO_FAILURE),
            access.openRead(document()),
        )
    }

    @Test fun providerIllegalArgumentIsReportedAsIoFailure() = runBlocking {
        val access = SafProviderRead { _ -> throw IllegalArgumentException("bad provider uri") }
        assertEquals(
            Outcome.Failure(AppError.Access.PROVIDER_IO_FAILURE),
            access.openRead(document()),
        )
    }

    @Test fun closedNativeDescriptorFailsSeekProbeAndDoesNotEscape() = runBlocking {
        val file = tempFile()
        try {
            val access = SafProviderRead { _ ->
                ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).also { it.close() }
            }
            assertEquals(
                Outcome.Failure(AppError.Access.PROVIDER_IO_FAILURE),
                access.openRead(document()),
            )
        } finally {
            file.delete()
        }
    }

    @Test fun cancellationAfterDescriptorAcquisitionClosesIt() {
        val pair = ParcelFileDescriptor.createPipe()
        val job = Job()
        try {
            val access = SafProviderRead { _ ->
                job.cancel()
                pair[0]
            }
            assertThrows(CancellationException::class.java) {
                runBlocking { withContext(job) { access.openRead(document()) } }
            }
            assertThrows(IllegalStateException::class.java) { pair[0].fd }
        } finally {
            pair.forEach { it.close() }
        }
    }

    @Test fun cancellationBeforeAcquisitionDoesNotOpenProvider() {
        var invoked = false
        val job = Job().apply { cancel() }
        val access = SafProviderRead { _ -> invoked = true; null }
        assertThrows(CancellationException::class.java) {
            runBlocking { withContext(job) { access.openRead(document()) } }
        }
        assertFalse(invoked)
    }

    @Test fun ownershipTransferNeverOpensANewDescriptor() = runBlocking {
        val file = tempFile()
        try {
            var opens = 0
            val access = SafProviderRead { _ ->
                opens++
                ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            }
            val first = (access.openRead(document()) as Outcome.Success).value
            val second = first.transferOwnership()
            assertEquals(ReadOwnership.TRANSFERRED, first.ownership)
            assertEquals(ReadOwnership.OWNED, second.ownership)
            assertEquals(1, opens)
            assertThrows(IllegalStateException::class.java) { first.descriptor }
            second.close()
        } finally {
            file.delete()
        }
    }
}
