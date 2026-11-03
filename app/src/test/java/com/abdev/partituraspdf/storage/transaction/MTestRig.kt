package com.abdev.partituraspdf.storage.transaction

import com.abdev.partituraspdf.core.document.*
import com.abdev.partituraspdf.storage.api.*
import com.abdev.partituraspdf.pdf.api.*
import com.abdev.partituraspdf.pdf.editing.*
import com.abdev.partituraspdf.pdf.workflow.*
import kotlinx.coroutines.*
import java.io.*

/** Privileged TEST ONLY byte-plumbing validation. Not PDF semantic evidence or a production mint. */
internal class MTestRig(directory: File, scope: CoroutineScope) {
    val bytes = byteArrayOf(1, 2, 3)
    var fault: (String) -> Unit = {}
    val io = object : PrivateIo {
        override fun freeBytes(root: File) = Long.MAX_VALUE
        override fun output(file: File): FileOutputStream = object : FileOutputStream(file) {
            override fun write(bytes: ByteArray) { fault("write"); super.write(bytes) }
            override fun close() { super.close(); fault("close") }
        }
        override fun syncFile(stream: FileOutputStream) { fault("fsync"); stream.flush(); stream.fd.sync() }
        override fun syncDirectory(directory: File) { fault("directory") }
        override fun renameNew(from: File, to: File) { fault("rename"); if (to.exists() || !from.renameTo(to)) throw IOException("Rename refused") }
        override fun delete(file: File) { fault("delete"); if (!file.delete()) throw IOException("Delete refused") }
        override fun openLease(file: File): SnapshotLeasePort = object : SnapshotLeasePort {
            override val descriptor: android.os.ParcelFileDescriptor get() = error("No native FD in JVM seam")
            override fun close() { fault("leaseClose") }
        }
    }
    val root = PrivateRoot(directory.canonicalFile, io)
    val budget = MStorageBudget(root)
    val store = PrivateSnapshotStore(budget, scope, Dispatchers.Unconfined)
    val gate = WriteAdmissionGate()
    val journal = MPublicationJournal(budget, Dispatchers.Unconfined)
    val source = SnapshotSource(StorageDocumentRef("test", "source"))
    val plan = OrderedPagePlan(listOf(PageSelection(0, 0)))
    val capture = MSourceCapturer { source, limits, id ->
        store.produce(SnapshotOrigin.Source(source), limits.maxInputBytes, "source", id) { it.write(bytes) }
    }
    val engine = object : PdfModificationEngine {
        override suspend fun transform(source: WorkingSnapshot, plan: OrderedPagePlan, transaction: TransactionId,
                                       store: PrivateSnapshotStore): EngineResult {
            val result = store.stage(transaction) { it.write(bytes) }
            return when (result) {
                is SnapshotCaptureResult.Captured -> EngineResult.Produced(result.snapshot)
                is SnapshotCaptureResult.Rejected -> EngineResult.Rejected(PdfOperationFailure.ResourceUnavailable)
            }
        }
    }
    val validator = object : PdfOperationValidator {
        override suspend fun validate(source: WorkingSnapshot, output: WorkingSnapshot, plan: OrderedPagePlan) =
            ValidationResult.Accepted(ValidatedPdfArtifact(output, plan))
    }
    fun coordinator(validator: PdfOperationValidator = this.validator) = MTransactionCoordinator(gate, capture, store,
        journal, engine, validator, NewDocumentPublisher(Dispatchers.Unconfined), MLeaseCopier { _, out, _ -> out.write(bytes) })
    fun destination(id: TransactionId) = Destination(id)
    inner class Destination(override val operation: TransactionId) : NewDestinationPort {
        var target = StorageDocumentRef("test", "new-disposable")
        override val destination get() = target
        override val observedFlags = 3
        var writes = 0; var reads = 0; var stored = byteArrayOf()
        var fail: String? = null
        var validations = 0; var denyAtValidation: Int? = null
        var blockWrite: CompletableDeferred<Unit>? = null
        val entered = CompletableDeferred<Unit>()
        override suspend fun revalidate(): Boolean { validations++; if (denyAtValidation == validations) return false; if (fail == "revalidate") throw SecurityException("Revoked"); return fail != "denied" }
        override suspend fun writeOnce(copy: suspend (OutputStream) -> Unit) {
            writes++; entered.complete(Unit); blockWrite?.await()
            if (fail == "open") throw IOException("Open refused")
            val out = object : ByteArrayOutputStream() {
                override fun write(bytes: ByteArray, offset: Int, length: Int) {
                    if (fail == "partial") { super.write(bytes, offset, 1); stored = toByteArray(); throw IOException("ENOSPC simulation") }
                    super.write(bytes, offset, length)
                }
            }
            copy(out); stored = out.toByteArray()
            if (fail == "close") throw IOException("Close uncertain")
        }
        override suspend fun readBack(copy: suspend (InputStream) -> Unit) {
            reads++
            if (fail == "read") throw SecurityException("Read denied")
            copy(ByteArrayInputStream(when (fail) { "corrupt" -> byteArrayOf(9); "short" -> byteArrayOf(1); else -> stored }))
        }
    }
}
