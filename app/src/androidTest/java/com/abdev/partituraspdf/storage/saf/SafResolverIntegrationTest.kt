package com.abdev.partituraspdf.storage.saf

import android.net.Uri
import android.provider.DocumentsContract
import android.system.Os
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.abdev.partituraspdf.core.document.DocumentListing
import com.abdev.partituraspdf.core.document.FolderRef
import com.abdev.partituraspdf.core.document.StorageDocumentRef
import com.abdev.partituraspdf.core.result.AppError
import com.abdev.partituraspdf.core.result.Outcome
import com.abdev.partituraspdf.storage.api.ReadAccessLimits
import com.abdev.partituraspdf.storage.api.ReadHandle
import com.abdev.partituraspdf.storage.api.ReadProvenance
import java.io.File
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Exercises real Android ContentResolver IPC against an installed test-only fixture provider.
 * Picker authorization, persisted grants, and arbitrary third-party DocumentsProviders are
 * intentionally NOT covered by this fixture (separate manual/device acceptance needed).
 */
@RunWith(AndroidJUnit4::class)
class SafResolverIntegrationTest {
    private val authority = "com.abdev.partituraspdf.test.saf.fixture"
    private val root = Uri.parse("content://$authority/tree/root%3AMusic")
    private val score = DocumentsContract.buildDocumentUriUsingTree(root, "root:Music/score.pdf")
    private val concert = DocumentsContract.buildDocumentUriUsingTree(root, "root:Music/Concert")
    private val stream = DocumentsContract.buildDocumentUriUsingTree(root, "root:Music/stream.pdf")
    private val direct = DocumentsContract.buildDocumentUri(authority, "root:Music/score.pdf")
    private val expectedScore = "%PDF-1.4\nsaf-fixture-score\n".toByteArray(Charsets.UTF_8)
    private val expectedStream = "%PDF-1.4\nsaf-fixture-stream\n".toByteArray(Charsets.UTF_8)

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val resolver get() = context.contentResolver
    private fun folder(uri: Uri) = FolderRef("saf", uri.toString())
    private fun document(uri: Uri) = StorageDocumentRef("saf", uri.toString())
    private fun copyDir(): File = File(context.cacheDir, "saf-resolver-test-${UUID.randomUUID()}")
        .apply { check(mkdirs()) }
    private fun reader(dir: File) = SafDocumentReadAccess(SafProviderRead(resolver), dir)
    private fun Outcome<ReadHandle>.handle(): ReadHandle = (this as Outcome.Success<ReadHandle>).value

    @Test fun providerIsInstalledAndQueryableThroughRealContentResolver(): Unit {
        val uri = DocumentsContract.buildChildDocumentsUriUsingTree(root, "root:Music")
        resolver.query(uri, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID), null, null, null).use { cursor ->
            assertNotNull(cursor)
            assertEquals(5, cursor!!.count)
        }
    }

    @Test fun realResolverListsOnlyPdfAndFolders(): Unit = runBlocking {
        val result = SafDocumentCatalog(resolver).list(folder(root))
        val listing = (result as Outcome.Success<DocumentListing>).value
        assertTrue(listing.complete)
        assertEquals(4, listing.entries.size)
        assertEquals(listOf("Score.pdf", "Concert", "Stream.pdf", "Empty"),
            listing.entries.map { it.info.name })
        assertEquals(score.toString(), listing.entries[0].storage.value)
        assertEquals(folder(root), listing.entries[0].folder)
        assertEquals(null, listing.entries[0].document)
    }

    @Test fun realResolverNavigatesNestedFolderAndEmptyFolder(): Unit = runBlocking {
        val nested = SafDocumentCatalog(resolver).list(folder(concert))
        val entry = (nested as Outcome.Success<DocumentListing>).value.entries.single()
        assertEquals("Part.pdf", entry.info.name)
        assertEquals("root:Music/Concert/part.pdf", DocumentsContract.getDocumentId(Uri.parse(entry.storage.value)))
        val empty = DocumentsContract.buildDocumentUriUsingTree(root, "root:Music/Empty")
        val emptyListing = SafDocumentCatalog(resolver).list(folder(empty))
        assertTrue((emptyListing as Outcome.Success<DocumentListing>).value.entries.isEmpty())
    }

    @Test fun realResolverInspectsTreeAndDirectDocumentUri(): Unit = runBlocking {
        val treeInfo = (SafDocumentCatalog(resolver).inspect(document(score)) as Outcome.Success).value
        assertEquals("Score.pdf", treeInfo.name)
        assertEquals("application/pdf", treeInfo.mimeType)
        assertEquals(expectedScore.size.toLong(), treeInfo.byteSize)
        val directInfo = (SafDocumentCatalog(resolver).inspect(document(direct)) as Outcome.Success).value
        assertEquals(treeInfo, directInfo)
    }

    @Test fun directProviderDescriptorIsSeekableAndNeverCopied(): Unit {
        val dir = copyDir()
        try {
            val handle = runBlocking { reader(dir).openSeekable(document(score), ReadAccessLimits(1)).handle() }
            handle.use {
                assertTrue(it.seekable)
                assertEquals(ReadProvenance.Provider(document(score)), it.provenance)
                val result = ByteArray(expectedScore.size)
                assertEquals(result.size, Os.read(it.descriptor.fileDescriptor, result, 0, result.size))
                assertArrayEquals(expectedScore, result)
            }
            assertTrue(dir.listFiles().isNullOrEmpty())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test fun streamedProviderDocumentUsesBoundedCopyAndDeletesIt(): Unit {
        val dir = copyDir()
        try {
            val handle = runBlocking {
                reader(dir).openSeekable(document(stream), ReadAccessLimits(expectedStream.size.toLong())).handle()
            }
            assertTrue(handle.seekable)
            assertEquals(ReadProvenance.TemporaryReadCopy(document(stream)), handle.provenance)
            assertEquals(1, dir.listFiles()?.size)
            handle.use {
                val result = ByteArray(expectedStream.size)
                assertEquals(result.size, Os.read(it.descriptor.fileDescriptor, result, 0, result.size))
                assertArrayEquals(expectedStream, result)
            }
            assertTrue(dir.listFiles().isNullOrEmpty())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test fun streamingLimitFailureLeavesNoCopy(): Unit {
        val dir = copyDir()
        try {
            val result = runBlocking {
                reader(dir).openSeekable(document(stream), ReadAccessLimits(expectedStream.size.toLong() - 1L))
            }
            assertEquals(Outcome.Failure(AppError.Resource.INPUT_LIMIT), result)
            assertTrue(dir.listFiles().isNullOrEmpty())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test fun rawStreamRemainsNonSeekableAndDoesNotCopy(): Unit {
        val dir = copyDir()
        try {
            val handle = runBlocking { reader(dir).openRead(document(stream)).handle() }
            handle.use {
                assertFalse(it.seekable)
                assertEquals(ReadProvenance.Provider(document(stream)), it.provenance)
                assertTrue(dir.listFiles().isNullOrEmpty())
            }
        } finally {
            dir.deleteRecursively()
        }
    }
}
