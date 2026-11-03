package com.abdev.partituraspdf.storage.transaction.testprovider

import android.database.Cursor
import android.database.MatrixCursor
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract.Document
import android.provider.DocumentsContract.Root
import android.provider.DocumentsProvider
import java.io.File
import java.io.FileNotFoundException
import java.util.UUID

/** Disposable test APK namespace only. Never registered in the production application. */
class MNewDocumentProvider : DocumentsProvider() {
    companion object { const val AUTHORITY = "com.abdev.partituraspdf.test.m.newdocument" }
    private lateinit var directory: File
    private val pending = mutableSetOf<String>()
    override fun onCreate(): Boolean {
        directory = File(requireNotNull(context).noBackupFilesDir, "m-provider-${UUID.randomUUID()}")
        return directory.mkdir()
    }
    private fun file(id: String): File {
        val canonical = try { UUID.fromString(id).toString() } catch (_: IllegalArgumentException) { null }
        if (canonical != id) throw FileNotFoundException()
        return File(directory, id).also { if (!it.isFile) throw FileNotFoundException() }
    }
    override fun queryRoots(projection: Array<out String>?): Cursor {
        val columns = projection ?: arrayOf(Root.COLUMN_ROOT_ID, Root.COLUMN_DOCUMENT_ID, Root.COLUMN_TITLE, Root.COLUMN_FLAGS)
        return MatrixCursor(columns).apply { val row = newRow()
            for (column in columns) row.add(when (column) { Root.COLUMN_ROOT_ID, Root.COLUMN_DOCUMENT_ID -> "root"
                Root.COLUMN_TITLE -> "Disposable M tests"; Root.COLUMN_FLAGS -> Root.FLAG_SUPPORTS_CREATE; else -> null }) }
    }
    override fun queryDocument(documentId: String, projection: Array<out String>?): Cursor {
        val columns = projection ?: arrayOf(Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME, Document.COLUMN_MIME_TYPE, Document.COLUMN_FLAGS, Document.COLUMN_SIZE)
        val f = if (documentId == "root") null else file(documentId)
        return MatrixCursor(columns).apply { val row = newRow()
            for (column in columns) row.add(when (column) { Document.COLUMN_DOCUMENT_ID -> documentId
                Document.COLUMN_DISPLAY_NAME -> "disposable.pdf"
                Document.COLUMN_MIME_TYPE -> if (f == null) Document.MIME_TYPE_DIR else "application/pdf"
                Document.COLUMN_FLAGS -> if (f == null) Document.FLAG_DIR_SUPPORTS_CREATE else Document.FLAG_SUPPORTS_WRITE
                Document.COLUMN_SIZE -> f?.length() ?: 0L; else -> null }) }
    }
    override fun queryChildDocuments(parentDocumentId: String, projection: Array<out String>?, sortOrder: String?): Cursor =
        MatrixCursor(projection ?: arrayOf(Document.COLUMN_DOCUMENT_ID))
    @Synchronized override fun createDocument(parentDocumentId: String, mimeType: String, displayName: String): String {
        if (pending.size >= 16 || directory.listFiles()?.size?.let { it >= 16 } != false || parentDocumentId != "root" || mimeType != "application/pdf") throw FileNotFoundException()
        val id = UUID.randomUUID().toString()
        if (!File(directory, id).createNewFile()) throw FileNotFoundException()
        pending += id
        return id // Repeated names create distinct IDs; never replace another document.
    }
    @Synchronized override fun openDocument(documentId: String, mode: String, signal: CancellationSignal?): ParcelFileDescriptor {
        signal?.throwIfCanceled()
        val f = file(documentId)
        if (mode == "r") return ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY)
        if (mode != "w" || f.length() != 0L || !pending.remove(documentId)) throw FileNotFoundException()
        return ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_WRITE_ONLY)
    }
    override fun shutdown() {
        if (::directory.isInitialized) {
            directory.listFiles()?.filter { it.isFile && !java.nio.file.Files.isSymbolicLink(it.toPath()) }?.forEach { it.delete() }
            directory.delete()
        }
        super.shutdown()
    }
    override fun deleteDocument(documentId: String) { throw UnsupportedOperationException("No deletion") }
}
