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
import kotlinx.coroutines.*

/** Disposable test APK namespace only. Never registered in the production application. */
class MNewDocumentProvider : DocumentsProvider() {
    companion object { const val AUTHORITY = "com.abdev.partituraspdf.test.m.newdocument" }
    private lateinit var directory: File
    // Provider installation precedes instrumentation class-loader setup. Keep construction
    // limited to Java collections, primitive constants and null backing fields.
    private val pending = java.util.HashSet<String>()
    object Fault {
        const val NONE = 0
        const val CREATE_DENIED = 1
        const val WRITE_DENIED = 2
        const val READ_DENIED = 3
        const val CORRUPT_READBACK = 4
        const val PARTIAL_WRITE = 5
        const val CLOSE_ERROR = 6
        const val PIPE = 7
    }
    @Volatile var fault: Int = Fault.NONE
    private var observed: CompletableDeferred<Unit>? = null
    val writeObserved: CompletableDeferred<Unit>
        get() = synchronized(this) {
            observed ?: CompletableDeferred<Unit>().also { observed = it }
        }
    @Volatile var workerFailure: Throwable? = null
    private var workerScope: CoroutineScope? = null
    private fun workers(): CoroutineScope = synchronized(this) {
        workerScope ?: CoroutineScope(SupervisorJob() + Dispatchers.IO).also { workerScope = it }
    }
    private val pipes = java.util.ArrayList<ParcelFileDescriptor>()
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
        if (fault == Fault.CREATE_DENIED) throw FileNotFoundException("Create refused")
        if (pending.size >= 16 || directory.listFiles()?.size?.let { it >= 16 } != false || parentDocumentId != "root" || mimeType != "application/pdf") throw FileNotFoundException()
        val id = UUID.randomUUID().toString()
        if (!File(directory, id).createNewFile()) throw FileNotFoundException()
        pending += id
        return id // Repeated names create distinct IDs; never replace another document.
    }
    @Synchronized override fun openDocument(documentId: String, mode: String, signal: CancellationSignal?): ParcelFileDescriptor {
        signal?.throwIfCanceled()
        val f = file(documentId)
        if (mode == "r") {
            if (fault == Fault.READ_DENIED) throw FileNotFoundException("Read refused")
            if (fault == Fault.CORRUPT_READBACK) f.writeBytes(byteArrayOf(9))
            return ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY)
        }
        if (fault == Fault.WRITE_DENIED) throw FileNotFoundException("Write refused")
        if (mode != "w" || f.length() != 0L || !pending.remove(documentId)) throw FileNotFoundException()
        val activeFault = fault
        if (activeFault in setOf(Fault.PARTIAL_WRITE, Fault.CLOSE_ERROR, Fault.PIPE)) {
            val pipe = ParcelFileDescriptor.createReliablePipe()
            synchronized(pipes) { pipes.add(pipe[0]); pipes.add(pipe[1]) }
            workers().launch {
                try {
                    ParcelFileDescriptor.AutoCloseInputStream(pipe[0]).use { input ->
                        f.outputStream().use { output ->
                            val buffer = ByteArray(64 * 1024); var total = 0L
                            while (true) {
                                val n = input.read(buffer); if (n < 0) break
                                if (n == 0 || n > 128L * 1024 * 1024 - total) throw java.io.IOException("Bound exceeded")
                                if (activeFault == Fault.PARTIAL_WRITE) {
                                    output.write(buffer, 0, 1)
                                    pipe[0].closeWithError("Injected partial write"); return@launch
                                }
                                output.write(buffer, 0, n); total += n
                                if (activeFault == Fault.CLOSE_ERROR) {
                                    pipe[0].closeWithError("Injected close error"); return@launch
                                }
                            }
                        }
                    }
                } catch (failure: java.io.IOException) { workerFailure = failure }
                finally {
                    try { pipe[0].close() } catch (failure: java.io.IOException) { workerFailure = failure }
                    finally { writeObserved.complete(Unit) }
                }
            }
            return pipe[1]
        }
        return ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_WRITE_ONLY)
    }
    override fun shutdown() {
        val owned = synchronized(pipes) { pipes.toList() }
        for (fd in owned) try { fd.close() } catch (failure: java.io.IOException) { workerFailure = failure }
        val ownedWorkers = synchronized(this) { workerScope }
        if (ownedWorkers != null) runBlocking { ownedWorkers.coroutineContext[kotlinx.coroutines.Job]!!.cancelAndJoin() }
        if (::directory.isInitialized) {
            directory.listFiles()?.filter { it.isFile && !java.nio.file.Files.isSymbolicLink(it.toPath()) }?.forEach { it.delete() }
            directory.delete()
        }
        super.shutdown()
    }
    override fun deleteDocument(documentId: String) { throw UnsupportedOperationException("No deletion") }
}
