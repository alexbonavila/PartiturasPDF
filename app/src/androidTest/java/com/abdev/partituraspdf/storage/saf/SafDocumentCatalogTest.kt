package com.abdev.partituraspdf.storage.saf

import android.database.MatrixCursor
import android.net.Uri
import android.provider.DocumentsContract
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.abdev.partituraspdf.core.document.DocumentAvailability
import com.abdev.partituraspdf.core.document.DocumentListing
import com.abdev.partituraspdf.core.document.FolderRef
import com.abdev.partituraspdf.core.document.StorageDocumentRef
import com.abdev.partituraspdf.core.result.AppError
import com.abdev.partituraspdf.core.result.Outcome
import java.io.IOException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SafDocumentCatalogTest {
    private val root = "content://test.documents/tree/primary%3AMusic"
    private val nested = "$root/document/primary%3AMusic%2FConcert"
    private val pdf = "$root/document/primary%3AMusic%2Fscore.pdf"
    private val columns = arrayOf(
        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
        DocumentsContract.Document.COLUMN_DISPLAY_NAME,
        DocumentsContract.Document.COLUMN_MIME_TYPE,
        DocumentsContract.Document.COLUMN_SIZE,
        DocumentsContract.Document.COLUMN_LAST_MODIFIED,
    )

    private fun cursor(vararg rows: Array<out Any?>): MatrixCursor =
        MatrixCursor(columns).apply { rows.forEach { addRow(it) } }

    private fun folder(value: String = root) = FolderRef("saf", value)
    private fun document(value: String = pdf) = StorageDocumentRef("saf", value)

    @Test fun listsPdfAndImmediateDirectoriesButNotOtherFiles() = runBlocking {
        var requested: Uri? = null
        val rows = cursor(
            arrayOf<Any?>("primary:Music/score.pdf", "Score", "application/pdf", 512L, 1234L),
            arrayOf<Any?>("primary:Music/Concert", "Concert", DocumentsContract.Document.MIME_TYPE_DIR, -1L, 0L),
            arrayOf<Any?>("primary:Music/photo.jpg", "Photo", "image/jpeg", 50L, 20L),
        )
        val catalog = SafDocumentCatalog { uri, projection ->
            requested = uri
            assertTrue(projection.contains(DocumentsContract.Document.COLUMN_DOCUMENT_ID))
            rows
        }
        val result = catalog.list(folder())
        val listing = (result as Outcome.Success<DocumentListing>).value
        assertTrue(listing.complete)
        assertEquals(2, listing.entries.size)
        assertEquals(
            DocumentsContract.buildChildDocumentsUriUsingTree(Uri.parse(root), "primary:Music"),
            requested,
        )
        assertEquals(pdf, listing.entries[0].storage.value)
        assertEquals(folder(), listing.entries[0].folder)
        assertNull(listing.entries[0].document)
        assertEquals("Score", listing.entries[0].info.name)
        assertEquals(512L, listing.entries[0].info.byteSize)
        assertEquals(1234L, listing.entries[0].info.modifiedAtMillis)
        assertEquals(DocumentAvailability.AVAILABLE, listing.entries[0].info.availability)
        assertEquals(nested, listing.entries[1].storage.value)
        assertNull(listing.entries[1].info.byteSize)
        assertNull(listing.entries[1].info.modifiedAtMillis)
        assertTrue(rows.isClosed)
    }

    @Test fun nestedFolderListsItsChildrenWithoutRecursion() = runBlocking {
        var requested: Uri? = null
        val catalog = SafDocumentCatalog { uri, _ ->
            requested = uri
            cursor(arrayOf<Any?>("primary:Music/Concert/part.pdf", "Part", "application/pdf", 12L, 99L))
        }
        val listing = (catalog.list(folder(nested)) as Outcome.Success<DocumentListing>).value
        assertEquals(
            DocumentsContract.buildChildDocumentsUriUsingTree(
                Uri.parse(nested), "primary:Music/Concert"
            ), requested,
        )
        assertEquals(1, listing.entries.size)
        assertEquals(folder(nested), listing.entries.single().folder)
        assertEquals(
            "$root/document/primary%3AMusic%2FConcert%2Fpart.pdf",
            listing.entries.single().storage.value,
        )
    }

    @Test fun emptyDirectoryReturnsCompleteEmptyListing() = runBlocking {
        val catalog = SafDocumentCatalog { _, _ -> cursor() }
        val listing = (catalog.list(folder()) as Outcome.Success<DocumentListing>).value
        assertTrue(listing.complete)
        assertTrue(listing.entries.isEmpty())
    }

    @Test fun missingOptionalMetadataRemainsUnknown() = runBlocking {
        val rows = MatrixCursor(arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
        )).apply { addRow(arrayOf("primary:Music/unknown.pdf", "application/pdf")) }
        val catalog = SafDocumentCatalog { _, _ -> rows }
        val listing = (catalog.list(folder()) as Outcome.Success<DocumentListing>).value
        val info = listing.entries.single().info
        assertNull(info.name)
        assertNull(info.byteSize)
        assertNull(info.modifiedAtMillis)
    }

    @Test fun malformedRowOrMissingRequiredColumnIsFailure() = runBlocking {
        val noIdColumn = MatrixCursor(arrayOf(DocumentsContract.Document.COLUMN_MIME_TYPE))
        val missingId = cursor(arrayOf<Any?>(null, "PDF", "application/pdf", 5L, 4L))
        assertEquals(
            Outcome.Failure(AppError.Access.PROVIDER_IO_FAILURE),
            SafDocumentCatalog { _, _ -> noIdColumn }.list(folder()),
        )
        assertEquals(
            Outcome.Failure(AppError.Access.PROVIDER_IO_FAILURE),
            SafDocumentCatalog { _, _ -> missingId }.list(folder()),
        )
    }

    @Test fun invalidFolderDoesNotQueryProvider() = runBlocking {
        var called = false
        val catalog = SafDocumentCatalog { _, _ -> called = true; cursor() }
        assertEquals(
            Outcome.Failure(AppError.Validation.INVALID_DOCUMENT),
            catalog.list(FolderRef("other", root)),
        )
        assertFalse(called)
    }

    @Test fun nullCursorIsProviderIoFailureNotEmptyDirectory() = runBlocking {
        val catalog = SafDocumentCatalog { _, _ -> null }
        assertEquals(
            Outcome.Failure(AppError.Access.PROVIDER_IO_FAILURE),
            catalog.list(folder()),
        )
    }

    @Test fun providerSecurityExceptionIsPermissionDenied() = runBlocking {
        val catalog = SafDocumentCatalog { _, _ -> throw SecurityException("denied") }
        assertEquals(
            Outcome.Failure(AppError.Access.PERMISSION_DENIED),
            catalog.list(folder()),
        )
        assertEquals(
            Outcome.Failure(AppError.Access.PERMISSION_DENIED),
            catalog.inspect(document()),
        )
    }

    @Test fun providerIoExceptionIsDistinctFromDeletion() = runBlocking {
        val catalog = SafDocumentCatalog { _, _ -> throw IOException("failed") }
        assertEquals(
            Outcome.Failure(AppError.Access.PROVIDER_IO_FAILURE),
            catalog.list(folder()),
        )
        assertEquals(
            Outcome.Failure(AppError.Access.PROVIDER_IO_FAILURE),
            catalog.inspect(document()),
        )
    }

    @Test fun inspectReturnsObservedMetadataAndClosesCursor() = runBlocking {
        val rows = cursor(arrayOf<Any?>("primary:Music/score.pdf", "Score", "application/pdf", 0L, 0L))
        val catalog = SafDocumentCatalog { uri, _ ->
            assertEquals(Uri.parse(pdf), uri)
            rows
        }
        val info = (catalog.inspect(document()) as Outcome.Success).value
        assertEquals("Score", info.name)
        assertEquals(0L, info.byteSize)
        assertNull(info.modifiedAtMillis)
        assertEquals(DocumentAvailability.AVAILABLE, info.availability)
        assertTrue(rows.isClosed)
    }

    @Test fun inspectSupportsNullMetadataAndDirectUri() = runBlocking {
        val direct = "content://test.documents/document/file%3A123"
        val rows = MatrixCursor(arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME))
            .apply { addRow(arrayOf<Any?>(null)) }
        val catalog = SafDocumentCatalog { uri, _ ->
            assertEquals(Uri.parse(direct), uri)
            rows
        }
        val info = (catalog.inspect(document(direct)) as Outcome.Success).value
        assertNull(info.name)
        assertNull(info.mimeType)
        assertNull(info.byteSize)
        assertNull(info.modifiedAtMillis)
    }

    @Test fun inspectMissingOrDuplicatedRowsIsNotConfirmedDeletion() = runBlocking {
        val zero = cursor()
        val two = cursor(
            arrayOf<Any?>("a", "First", "application/pdf", 1L, 10L),
            arrayOf<Any?>("b", "Second", "application/pdf", 2L, 20L),
        )
        assertEquals(
            Outcome.Failure(AppError.Access.PROVIDER_IO_FAILURE),
            SafDocumentCatalog { _, _ -> zero }.inspect(document()),
        )
        assertEquals(
            Outcome.Failure(AppError.Access.PROVIDER_IO_FAILURE),
            SafDocumentCatalog { _, _ -> two }.inspect(document()),
        )
    }

    @Test fun invalidDocumentDoesNotQueryProvider() = runBlocking {
        var called = false
        val catalog = SafDocumentCatalog { _, _ -> called = true; cursor() }
        assertEquals(
            Outcome.Failure(AppError.Validation.INVALID_DOCUMENT),
            catalog.inspect(document("file:///tmp/score.pdf")),
        )
        assertFalse(called)
    }
}
