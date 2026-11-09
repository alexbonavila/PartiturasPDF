package com.abdev.partituraspdf.storage.transaction

import android.content.pm.ProviderInfo
import android.os.ParcelFileDescriptor
import androidx.test.platform.app.InstrumentationRegistry
import com.abdev.partituraspdf.core.document.*
import com.abdev.partituraspdf.core.result.Outcome
import com.abdev.partituraspdf.pdf.api.*
import com.abdev.partituraspdf.pdf.editing.*
import com.abdev.partituraspdf.pdf.engine.spike.TestPdfboxModificationAdapters
import com.abdev.partituraspdf.pdf.workflow.*
import com.abdev.partituraspdf.storage.api.*
import com.abdev.partituraspdf.storage.transaction.testprovider.MNewDocumentProvider
import kotlinx.coroutines.*
import org.json.JSONObject
import org.junit.Assert.*
import java.io.*
import java.util.UUID

/** Direct provider invocation, NOT resolver grants/picker acceptance. All files synthetic/owned. */
internal class MPdfboxIntegrationRig(fixtureId: String = "02", useProduction: Boolean = false) {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    // Resolve only the trusted framework base, as PrivateRoot.forContext does.
    // PrivateRoot must still reject links in arbitrary caller-supplied paths.
    private val trustedBase = context.noBackupFilesDir.canonicalFile
    val directory = File(trustedBase, "m-pdfbox-integration-${UUID.randomUUID()}").apply { check(mkdir()) }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val original = File(directory, "input.pdf")
    val fixture: JSONObject
    var failSeal = false
    var failCheckpoint: String? = null
    var cleanupFault: Throwable? = null
    private val androidIo = AndroidPrivateIo()
    private val fileStreams = java.util.IdentityHashMap<FileOutputStream, File>()
    val io = object : PrivateIo by androidIo {
        override fun output(file: File): FileOutputStream {
            if (file.parentFile?.name == "publication" && file.name == failCheckpoint) throw IOException("Injected checkpoint fault")
            return androidIo.output(file).also { synchronized(fileStreams) { fileStreams[it] = file } }
        }
        override fun syncFile(stream: FileOutputStream) {
            if (failSeal && synchronized(fileStreams) { fileStreams[stream]?.name } == "output.part") throw IOException("Injected output seal failure")
            androidIo.syncFile(stream)
        }
        override fun delete(file: File) {
            if (file.name.endsWith(".sealed")) cleanupFault?.let { throw it }
            androidIo.delete(file)
        }
    }
    val root: PrivateRoot
    val budget: MStorageBudget
    val store: PrivateSnapshotStore
    val gate = WriteAdmissionGate()
    val journal: MPublicationJournal
    val adapters: PdfModificationAdapters
    val source = SnapshotSource(StorageDocumentRef("test", "immutable-synthetic-source"))
    val plan = OrderedPagePlan(listOf(PageSelection(1, 1), PageSelection(0, 3)))
    val provider = MNewDocumentProvider()
    init {
        try {
        adapters = if (useProduction) ProductionPdfAdapters(context) else TestPdfboxModificationAdapters()
        val manifest = JSONObject(instrumentation.context.assets.open("manifest.json").bufferedReader().use { it.readText() })
        fixture = (0 until manifest.getJSONArray("files").length()).map { manifest.getJSONArray("files").getJSONObject(it) }
            .single { it.getString("id") == fixtureId }
        instrumentation.context.assets.open(fixture.getString("path")).use { input -> original.outputStream().use { input.copyTo(it, 64 * 1024) } }
        root = PrivateRoot(File(directory, "transactions"), io)
        budget = MStorageBudget(root); store = PrivateSnapshotStore(budget, scope)
        journal = MPublicationJournal(budget)
        provider.attachInfo(context, ProviderInfo().apply {
            authority = MNewDocumentProvider.AUTHORITY; exported = true; grantUriPermissions = true
            readPermission = "android.permission.MANAGE_DOCUMENTS"; writePermission = readPermission
        })
        } catch (failure: Throwable) {
            try { provider.shutdown() } catch (cleanup: Throwable) { failure.addSuppressed(cleanup) }
            scope.cancel() // No work was launched during construction.
            try { if (!directory.deleteRecursively()) throw IOException("Constructor cleanup pending") }
            catch (cleanup: Throwable) { failure.addSuppressed(cleanup) }
            throw failure
        }
    }
    val access = object : DocumentReadAccess {
        override suspend fun openRead(document: StorageDocumentRef): Outcome<ReadHandle> = Outcome.Success(
            ReadHandle(object : ReadResource {
                override val descriptor = ParcelFileDescriptor.open(original, ParcelFileDescriptor.MODE_READ_ONLY)
                override fun close() = descriptor.close()
            }, ReadProvenance.Provider(document), true))
        override suspend fun openSeekable(document: StorageDocumentRef, limits: ReadAccessLimits): Outcome<ReadHandle> = error("Not used by capture")
    }
    fun coordinator(validator: PdfOperationValidator = adapters, engine: PdfModificationEngine = adapters) =
        MTransactionCoordinator(gate, TransactionSnapshotCapturer(access, store), store, journal, engine, validator)
    suspend fun ready(): MPreparation.Ready = coordinator().prepare(source, plan) as MPreparation.Ready
    inner class Destination(override val operation: TransactionId) : NewDestinationPort {
        // Create happens explicitly in disposable provider, before injected capability binding.
        val id = provider.createDocument("root", "application/pdf", "score.pdf")
        override val destination = StorageDocumentRef("test", "disposable-$id")
        override val observedFlags = 3
        var writes = 0; var reads = 0
        var denied = false
        var shortRead = false
        var closeFailure = false
        var waitBeforeWrite: CompletableDeferred<Unit>? = null
        var waitAfterCopy: CompletableDeferred<Unit>? = null
        val entered = CompletableDeferred<Unit>()
        val copied = CompletableDeferred<Unit>()
        override suspend fun revalidate() = !denied
        override suspend fun writeOnce(copy: suspend (OutputStream) -> Unit) {
            check(++writes == 1)
            entered.complete(Unit); waitBeforeWrite?.await()
            writeNewDescriptor(provider.openDocument(id, "w", null), copy = {
                copy(it); copied.complete(Unit); waitAfterCopy?.await()
                if (provider.fault in listOf(MNewDocumentProvider.Fault.PARTIAL_WRITE, MNewDocumentProvider.Fault.CLOSE_ERROR)) provider.writeObserved.await()
            })
            if (closeFailure) throw IOException("Injected close uncertainty after actual closure")
        }
        override suspend fun readBack(copy: suspend (InputStream) -> Unit) {
            reads++
            if (shortRead) copy(ByteArrayInputStream(byteArrayOf(1)))
            else ParcelFileDescriptor.AutoCloseInputStream(provider.openDocument(id, "r", null)).use { copy(it) }
        }
        fun capability() = WriteDestinationCapability(this)
        suspend fun evidence() = ParcelFileDescriptor.AutoCloseInputStream(provider.openDocument(id, "r", null)).use {
            observeBytes(it, 128L * 1024 * 1024)
        }
    }
    suspend fun unchanged() {
        val evidence = original.inputStream().use { observeBytes(it, 64L * 1024 * 1024) }
        assertEquals(fixture.getLong("bytes"), evidence.byteCount)
        assertEquals(fixture.getString("sha256"), evidence.sha256)
    }
    suspend fun close() {
        var failure: Throwable? = null
        try { unchanged() } catch (t: Throwable) { failure = t }
        try { provider.shutdown() } catch (t: Throwable) { if (failure == null) failure = t else failure.addSuppressed(t) }
        scope.coroutineContext[Job]!!.cancelAndJoin()
        // Only the unique disposable harness subtree, after all owners have been joined.
        if (!directory.deleteRecursively()) {
            val cleanup = IOException("Disposable integration cleanup pending")
            if (failure == null) failure = cleanup else failure.addSuppressed(cleanup)
        }
        failure?.let { throw it }
    }
}
