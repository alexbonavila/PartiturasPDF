package com.abdev.partituraspdf.pdf.engine.spike

import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.test.platform.app.InstrumentationRegistry
import com.abdev.partituraspdf.pdf.api.*
import com.abdev.partituraspdf.pdf.editing.*
import com.abdev.partituraspdf.storage.api.*
import com.abdev.partituraspdf.storage.transaction.PrivateSnapshotStore
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import kotlinx.coroutines.*
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.OutputStream
import java.security.MessageDigest

/** TEST ONLY: qualified synthetic fixtures, NOT arbitrary/user PDFs or production approval. */
internal class TestPdfboxModificationAdapters : PdfModificationEngine, PdfOperationValidator {
    private val testContext = InstrumentationRegistry.getInstrumentation().context
    private val fixtureEntries = JSONObject(testContext.assets.open("manifest.json").bufferedReader().use { it.readText() })
        .getJSONArray("files")
    init {
        val assets = object : ContextWrapper(testContext) { override fun getApplicationContext(): Context = this }
        // Same instrumentation-asset boundary as the existing spike, never target private assets.
        for (name in listOf("additional.txt", "glyphlist.txt")) {
            assets.assets.open("com/tom_roush/pdfbox/resources/glyphlist/$name").use {
                if (it.read() < 0) throw IOException("Missing candidate glyph resource")
            }
        }
        PDFBoxResourceLoader.init(assets)
    }
    private fun qualification(source: WorkingSnapshot): PdfOperationFailure? {
        if (source.origin !is SnapshotOrigin.Source) return PdfOperationFailure.InvalidInput
        val fixture = (0 until fixtureEntries.length()).map { fixtureEntries.getJSONObject(it) }
            .singleOrNull { it.getString("sha256") == source.evidence.sha256 && it.getLong("bytes") == source.evidence.byteCount }
            ?: return PdfOperationFailure.UnsupportedDocument
        return when (fixture.getString("expected_status")) {
            "valid" -> null
            "password_required" -> PdfOperationFailure.ProtectedDocument
            else -> PdfOperationFailure.InvalidInput
        }
    }
    private suspend fun bytes(snapshot: WorkingSnapshot): ByteArray {
        // Additional experimental harness bound, not a changed production quota or parser heap cap.
        if (snapshot.evidence.byteCount !in 1..2L * 1024 * 1024) throw IOException("Synthetic adapter byte bound")
        val lease = (snapshot.acquireReadLease() as? SnapshotLeaseResult.Acquired)?.lease
            ?: throw IOException("Sealed lease unavailable")
        return lease.use {
            ParcelFileDescriptor.AutoCloseInputStream(ParcelFileDescriptor.dup(it.descriptor.fileDescriptor)).use { input ->
                val output = ByteArrayOutputStream(); val buffer = ByteArray(64 * 1024)
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val n = input.read(buffer); if (n < 0) break
                    if (n == 0 || n > snapshot.evidence.byteCount - output.size()) throw IOException("Sealed bytes disagree")
                    output.write(buffer, 0, n)
                }
                val result = output.toByteArray()
                if (result.size.toLong() != snapshot.evidence.byteCount ||
                    SpikeSemantics.hex(MessageDigest.getInstance("SHA-256").digest(result)) != snapshot.evidence.sha256)
                    throw IOException("Sealed byte identity mismatch")
                result
            }
        }
    }
    override suspend fun transform(source: WorkingSnapshot, plan: OrderedPagePlan, transaction: TransactionId,
                                   store: PrivateSnapshotStore): EngineResult = withContext(Dispatchers.IO) {
        qualification(source)?.let { return@withContext EngineResult.Rejected(it) }
        var rejection = PdfOperationFailure.TransformationFailure
        val context = currentCoroutineContext()
        val staged = store.stage(transaction) { writer ->
            // Close parser BEFORE producer returns: a close failure prevents output sealing.
            PDDocument.load(bytes(source)).use { document ->
                if (plan.selections.any { it.sourcePageIndex >= document.numberOfPages }) {
                    rejection = PdfOperationFailure.InvalidPlan
                    throw IOException("Out-of-range synthetic plan")
                }
                val original = try { SpikeSemantics.snapshot(document) }
                catch (unsupported: IllegalArgumentException) {
                    rejection = PdfOperationFailure.UnsupportedDocument
                    throw IOException("Unsupported synthetic structure", unsupported)
                }
                val selected = plan.selections.map { document.getPage(it.sourcePageIndex) }
                for (page in selected) {
                    page.resources = page.resources; page.mediaBox = page.mediaBox
                    page.cropBox = page.cropBox; page.rotation = page.rotation
                }
                while (document.numberOfPages > 0) document.removePage(0)
                for ((i, page) in selected.withIndex()) {
                    val selection = plan.selections[i]
                    page.rotation = (original[selection.sourcePageIndex].rotation + 90 * selection.addedClockwiseQuarterTurns) % 360
                    document.addPage(page)
                }
                // PDFBox cannot own/finish the store's suspend Writer. Bridge bounded writes to
                // that SAME owner, checking the caller's cancellation before every write.
                val output = object : OutputStream() {
                    override fun write(value: Int) = write(byteArrayOf(value.toByte()), 0, 1)
                    override fun write(data: ByteArray, offset: Int, length: Int) {
                        context.ensureActive()
                        runBlocking { writer.write(data, offset, length) }
                    }
                }
                document.save(output)
                context.ensureActive()
            }
        }
        when (staged) {
            is SnapshotCaptureResult.Captured -> EngineResult.Produced(staged.snapshot)
            is SnapshotCaptureResult.Rejected -> EngineResult.Rejected(rejection, staged.cleanup)
        }
    }
    override suspend fun validate(source: WorkingSnapshot, output: WorkingSnapshot, plan: OrderedPagePlan): ValidationResult = withContext(Dispatchers.IO) {
        if (qualification(source) != null || output.origin !is SnapshotOrigin.Produced || output === source)
            return@withContext ValidationResult.Refused(PdfValidationReport.Unsupported)
        try {
            PDDocument.load(bytes(source)).use { original -> PDDocument.load(bytes(output)).use { actual ->
                if (plan.selections.any { it.sourcePageIndex >= original.numberOfPages } || actual.numberOfPages != plan.selections.size)
                    return@withContext ValidationResult.Refused(PdfValidationReport.Rejected)
                val originals = SpikeSemantics.snapshot(original)
                val expected = plan.selections.map { selection -> originals[selection.sourcePageIndex].let {
                    it.copy(rotation = (it.rotation + 90 * selection.addedClockwiseQuarterTurns) % 360)
                } }
                val persisted = (0 until actual.numberOfPages).map { actual.getPage(it).rotation }
                if (persisted != expected.map { it.rotation }) return@withContext ValidationResult.Refused(PdfValidationReport.Rejected)
                val observed = SpikeSemantics.snapshot(actual, plan.selections.map { originals[it.sourcePageIndex].rotation })
                check(persisted == (0 until actual.numberOfPages).map { actual.getPage(it).rotation })
                if (observed != expected) return@withContext ValidationResult.Refused(PdfValidationReport.Rejected)
                currentCoroutineContext().ensureActive()
                // Privileged test-only mint, after actual independent sealed reopens/checks.
                ValidationResult.Accepted(ValidatedPdfArtifact(output, plan))
            } }
        } catch (cancel: CancellationException) { throw cancel }
        catch (_: IOException) { ValidationResult.Refused(PdfValidationReport.Inconclusive) }
        catch (_: IllegalArgumentException) { ValidationResult.Refused(PdfValidationReport.Unsupported) }
    }
}
