package com.abdev.partituraspdf.storage.saf

import android.content.ContentResolver
import android.database.Cursor
import android.net.Uri
import android.provider.DocumentsContract
import com.abdev.partituraspdf.core.document.DocumentAvailability
import com.abdev.partituraspdf.core.document.DocumentCatalog
import com.abdev.partituraspdf.core.document.DocumentEntry
import com.abdev.partituraspdf.core.document.DocumentInfo
import com.abdev.partituraspdf.core.document.DocumentListing
import com.abdev.partituraspdf.core.document.FolderRef
import com.abdev.partituraspdf.core.document.StorageDocumentRef
import com.abdev.partituraspdf.core.result.AppError
import com.abdev.partituraspdf.core.result.Outcome
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** Read-only listing and metadata observation of user-authorized SAF documents. */
internal class SafDocumentCatalog(
    private val queryDocuments: (Uri, Array<String>) -> Cursor?,
) : DocumentCatalog {

    constructor(resolver: ContentResolver) : this(
        { uri, columns -> resolver.query(uri, columns, null, null, null) }
    )

    override suspend fun list(folder: FolderRef): Outcome<DocumentListing> =
        withContext(Dispatchers.IO) {
            currentCoroutineContext().ensureActive()
            val folderUri = SafUriCodec.decodeFolder(folder)
                ?: return@withContext Outcome.Failure(AppError.Validation.INVALID_DOCUMENT)
            val folderId = SafUriCodec.getFolderDocumentId(folderUri)
                ?: return@withContext Outcome.Failure(AppError.Validation.INVALID_DOCUMENT)
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(folderUri, folderId)

            try {
                val cursor = queryDocuments(childrenUri, COLUMNS.copyOf())
                    ?: return@withContext Outcome.Failure(AppError.Access.PROVIDER_IO_FAILURE)
                cursor.use { rows ->
                    val idColumn = rows.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                    val mimeColumn = rows.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
                    if (idColumn < 0 || mimeColumn < 0) {
                        return@withContext Outcome.Failure(AppError.Access.PROVIDER_IO_FAILURE)
                    }

                    val entries = mutableListOf<DocumentEntry>()
                    while (rows.moveToNext()) {
                        currentCoroutineContext().ensureActive()
                        val id = rows.stringOrNull(idColumn)
                        if (id.isNullOrBlank()) {
                            return@withContext Outcome.Failure(AppError.Access.PROVIDER_IO_FAILURE)
                        }
                        val mime = rows.stringOrNull(mimeColumn)
                        if (mime != DocumentsContract.Document.MIME_TYPE_DIR &&
                            !mime.equals(PDF_MIME_TYPE, ignoreCase = true)
                        ) continue

                        val childUri = SafUriCodec.childDocumentUri(folderUri, id)
                        entries += DocumentEntry(
                            storage = StorageDocumentRef(SafUriCodec.PROVIDER_ID, childUri.toString()),
                            info = rows.documentInfo(),
                            folder = folder,
                        )
                    }
                    currentCoroutineContext().ensureActive()
                    Outcome.Success(DocumentListing(entries, complete = true))
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (denied: SecurityException) {
                Outcome.Failure(AppError.Access.PERMISSION_DENIED)
            } catch (failed: IOException) {
                Outcome.Failure(AppError.Access.PROVIDER_IO_FAILURE)
            } catch (failed: IllegalArgumentException) {
                Outcome.Failure(AppError.Access.PROVIDER_IO_FAILURE)
            }
        }

    override suspend fun inspect(document: StorageDocumentRef): Outcome<DocumentInfo> =
        withContext(Dispatchers.IO) {
            currentCoroutineContext().ensureActive()
            val documentUri = SafUriCodec.decodeDocument(document)
                ?: return@withContext Outcome.Failure(AppError.Validation.INVALID_DOCUMENT)

            try {
                val cursor = queryDocuments(documentUri, COLUMNS.copyOf())
                    ?: return@withContext Outcome.Failure(AppError.Access.PROVIDER_IO_FAILURE)
                cursor.use { rows ->
                    if (!rows.moveToNext()) {
                        return@withContext Outcome.Failure(AppError.Access.PROVIDER_IO_FAILURE)
                    }
                    currentCoroutineContext().ensureActive()
                    val info = rows.documentInfo()
                    if (rows.moveToNext()) {
                        return@withContext Outcome.Failure(AppError.Access.PROVIDER_IO_FAILURE)
                    }
                    currentCoroutineContext().ensureActive()
                    Outcome.Success(info)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (denied: SecurityException) {
                Outcome.Failure(AppError.Access.PERMISSION_DENIED)
            } catch (failed: IOException) {
                Outcome.Failure(AppError.Access.PROVIDER_IO_FAILURE)
            } catch (failed: IllegalArgumentException) {
                Outcome.Failure(AppError.Access.PROVIDER_IO_FAILURE)
            }
        }

    private fun Cursor.documentInfo(): DocumentInfo = DocumentInfo(
        name = text(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
        mimeType = text(DocumentsContract.Document.COLUMN_MIME_TYPE),
        byteSize = number(DocumentsContract.Document.COLUMN_SIZE)?.takeIf { it >= 0L },
        modifiedAtMillis = number(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
            ?.takeIf { it > 0L },
        availability = DocumentAvailability.AVAILABLE,
    )

    private fun Cursor.stringOrNull(index: Int): String? =
        if (index < 0 || isNull(index)) null else getString(index)

    private fun Cursor.text(column: String): String? =
        stringOrNull(getColumnIndex(column))?.takeIf { it.isNotBlank() }

    private fun Cursor.number(column: String): Long? {
        val index = getColumnIndex(column)
        return if (index < 0 || isNull(index)) null else getLong(index)
    }

    private companion object {
        const val PDF_MIME_TYPE = "application/pdf"
        val COLUMNS = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
        )
    }
}
