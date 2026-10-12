package com.abdev.partituraspdf.storage.saf

import android.net.Uri
import android.os.DeadObjectException
import android.os.ParcelFileDescriptor
import android.system.Os
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.abdev.partituraspdf.core.document.FolderRef
import com.abdev.partituraspdf.core.document.StorageDocumentRef
import com.abdev.partituraspdf.core.result.AppError
import com.abdev.partituraspdf.core.result.Outcome
import com.abdev.partituraspdf.storage.api.ReadAccessLimits
import com.abdev.partituraspdf.storage.api.ReadHandle
import java.io.File
import java.io.FileNotFoundException
import java.util.UUID
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Targeted API 26 regression tests: idle pipes, Binder death and known-grant loss. */
@RunWith(AndroidJUnit4::class)
class SafFinalHardeningTest {
    private val document = StorageDocumentRef(
        "saf", "content://test.documents/document/primary%3AMusic%2Fscore.pdf"
    )
    private val folder = FolderRef("saf", "content://test.documents/tree/primary%3AMusic")

    private fun copyDir() = File(
        InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,
        "saf-final-hardening-${UUID.randomUUID()}",
    ).apply { check(mkdirs()) }

    @Test fun idleOpenPipeCancelsPromptlyWithoutLeavingAPartialCopy(): Unit = runBlocking {
        val dir = copyDir()
        val pipe = ParcelFileDescriptor.createPipe()
        val writer = ParcelFileDescriptor.AutoCloseOutputStream(pipe[1])
        val firstChunk = CompletableDeferred<Unit>()
        try {
            val access = SafDocumentReadAccess(
                SafProviderRead { _ -> pipe[0] }, dir,
                { total -> if (total > 0L) firstChunk.complete(Unit) },
            )
            val worker = async { access.openSeekable(document, ReadAccessLimits(1024)) }
            try {
                writer.write(byteArrayOf(37, 80, 68, 70))
                withTimeout(3_000L) { firstChunk.await() }
                // The writer remains open and silent: the next provider read would block.
                delay(450L)
                assertFalse(worker.isCompleted)
                worker.cancel()
                withTimeout(3_000L) { worker.join() }
                assertTrue(dir.listFiles().isNullOrEmpty())
                assertThrows(IllegalStateException::class.java) { pipe[0].fd }
            } finally {
                worker.cancel()
            }
        } finally {
            writer.close()
            pipe[0].close()
            dir.deleteRecursively()
        }
    }

    @Test fun idleOpenPipeCanResumeAndReadExactBytes(): Unit = runBlocking {
        val dir = copyDir()
        val pipe = ParcelFileDescriptor.createPipe()
        val writer = ParcelFileDescriptor.AutoCloseOutputStream(pipe[1])
        val expected = byteArrayOf(37, 80, 68, 70, 10, 1, 2)
        try {
            val access = SafDocumentReadAccess(SafProviderRead { _ -> pipe[0] }, dir)
            val worker = async { access.openSeekable(document, ReadAccessLimits(1024)) }
            try {
                delay(450L)
                assertFalse(worker.isCompleted)
                writer.write(expected)
                writer.close()
                val outcome = withTimeout(3_000L) { worker.await() }
                val handle = (outcome as Outcome.Success<ReadHandle>).value
                handle.use {
                    val actual = ByteArray(expected.size)
                    assertEquals(expected.size,
                        Os.read(it.descriptor.fileDescriptor, actual, 0, actual.size))
                    assertArrayEquals(expected, actual)
                }
                assertTrue(dir.listFiles().isNullOrEmpty())
            } finally {
                worker.cancel()
            }
        } finally {
            writer.close()
            pipe[0].close()
            dir.deleteRecursively()
        }
    }

    @Test fun binderDeathIsProviderUnavailableForDirectRead(): Unit = runBlocking {
        val access = SafProviderRead { _ -> throw DeadObjectException("provider died") }
        assertEquals(
            Outcome.Failure(AppError.Access.PROVIDER_UNAVAILABLE),
            access.openRead(document),
        )
    }

    @Test fun binderDeathIsProviderUnavailableForCatalogQueries(): Unit = runBlocking {
        val catalog = SafDocumentCatalog { _, _ -> throw DeadObjectException("provider died") }
        assertEquals(
            Outcome.Failure(AppError.Access.PROVIDER_UNAVAILABLE),
            catalog.list(folder),
        )
        assertEquals(
            Outcome.Failure(AppError.Access.PROVIDER_UNAVAILABLE),
            catalog.inspect(document),
        )
    }

    @Test fun ambiguousMissingFileRemainsProviderIoFailureNotDeletion(): Unit = runBlocking {
        val access = SafProviderRead { _ -> throw FileNotFoundException("not reachable") }
        assertEquals(
            Outcome.Failure(AppError.Access.PROVIDER_IO_FAILURE),
            access.openRead(document),
        )
    }

    @Test fun provenPriorGrantLossIsRevokedButNoGrantAloneIsNot(): Unit {
        val tree = Uri.parse(folder.value)
        val persisted = mutableSetOf(tree)
        val access = SafTreeGrantAccess(
            { _, _ -> Unit }, { _, _ -> Unit }, { persisted.toList() },
        )
        assertEquals(Outcome.Success(Unit), access.requirePreviouslyPersistedReadGrant(folder))
        persisted.clear() // Models a confirmed formerly persisted OS grant disappearing.
        assertEquals(Outcome.Success(false), access.hasPersistedReadGrant(folder))
        assertEquals(
            Outcome.Failure(AppError.Access.PERMISSION_REVOKED),
            access.requirePreviouslyPersistedReadGrant(folder),
        )
    }

    @Test fun failedGrantLookupNeverInventsRevocation(): Unit {
        val access = SafTreeGrantAccess(
            { _, _ -> Unit }, { _, _ -> Unit }, { throw SecurityException("unavailable") },
        )
        assertEquals(
            Outcome.Failure(AppError.Access.PERMISSION_DENIED),
            access.requirePreviouslyPersistedReadGrant(folder),
        )
    }
}
