package com.abdev.partituraspdf.pdf.engine.spike

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.Build
import android.os.Debug
import android.os.ParcelFileDescriptor
import android.os.Process
import android.os.SystemClock
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.nio.ByteBuffer
import java.util.UUID
import kotlin.math.roundToInt

internal class SpikeOutputLimit : IOException("Disposable output limit exceeded")

/** Flat, unique target-private test namespace. No SAF, publication or production capability. */
internal class PdfboxSpikeSandbox(private val case: String) : AutoCloseable {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val assets = instrumentation.context.assets // Fixtures belong to the test APK.
    private val context = instrumentation.targetContext.applicationContext
    private val root = File(context.noBackupFilesDir, "m-pdfbox-spike-${UUID.randomUUID()}")
    private val retain = InstrumentationRegistry.getArguments().getString("retainPdfboxSpike") == "true"
    private val manifest = JSONObject(assets.open("manifest.json").bufferedReader().use { it.readText() })
    private val outputs = JSONArray()
    private val measurements = JSONArray()
    private val copies = mutableListOf<Pair<File, JSONObject>>()
    private var documents = 0
    private var descriptors = 0
    private var bitmaps = 0
    private var streams = 0
    private var sequence = 0

    init {
        require(case.matches(Regex("[A-Za-z0-9]+")))
        check(root.mkdir()) { "Disposable spike namespace unavailable" }
        try { PDFBoxResourceLoader.init(context) }
        catch (failure: Throwable) {
            if (!root.delete()) failure.addSuppressed(IOException("Initialization cleanup pending"))
            throw failure
        }
    }

    private fun memory() = JSONObject().apply {
        val runtime = Runtime.getRuntime()
        put("java_heap_used_bytes", runtime.totalMemory() - runtime.freeMemory())
        put("native_heap_allocated_bytes", Debug.getNativeHeapAllocatedSize())
        val info = Debug.MemoryInfo(); Debug.getMemoryInfo(info)
        put("process_pss_kib", info.totalPss)
        put("owned_documents", documents); put("owned_descriptors", descriptors)
        put("owned_bitmaps", bitmaps); put("owned_streams", streams)
        put("observed_process_fd_count", File("/proc/self/fd").list()?.size ?: JSONObject.NULL)
        put("temporary_file_bytes", bytes())
    }

    fun <T> phase(name: String, body: () -> T): T {
        val before = memory()
        val start = SystemClock.elapsedRealtimeNanos()
        var completed = false
        var primary: Throwable? = null
        try { return body().also { completed = true } }
        catch (failure: Throwable) { primary = failure; throw failure }
        finally {
            try {
                val elapsed = SystemClock.elapsedRealtimeNanos() - start
                val event = JSONObject().put("phase", name).put("elapsed_ns", elapsed)
                    .put("completed", completed).put("before", before).put("after", memory())
                measurements.put(event)
                Log.i("M_PDFBOX_SPIKE", JSONObject().put("case", case).put("event", event).toString())
            } catch (failure: Throwable) { if (primary != null) primary.addSuppressed(failure) else throw failure }
        }
    }

    fun fixture(id: String): Pair<File, JSONObject> {
        val entries = manifest.getJSONArray("files")
        val entry = (0 until entries.length()).map { entries.getJSONObject(it) }.single { it.getString("id") == id }
        val file = fresh("source")
        val allowance = minOf(SOURCE_LIMIT, AGGREGATE_LIMIT - bytes(), root.usableSpace - FREE_MARGIN)
        require(entry.getLong("bytes") in 1..allowance)
        assets.open(entry.getString("path")).use { input ->
            file.outputStream().use { output ->
                val buffer = ByteArray(64 * 1024); var total = 0L
                while (true) {
                    val n = input.read(buffer); if (n < 0) break
                    check(n > 0 && n <= allowance - total) { "Fixture copy bound exceeded" }
                    output.write(buffer, 0, n); total += n
                }
            }
        }
        copies += file to entry
        verifyCopy(file, entry)
        return file to entry
    }

    fun fresh(kind: String): File {
        check(++sequence <= 64 && bytes() < AGGREGATE_LIMIT && root.usableSpace >= FREE_MARGIN)
        return File(root, "$kind-$sequence.pdf").also { check(it.createNewFile()) }
    }

    fun <T> document(file: File, opening: String = "parse", body: (PDDocument) -> T): T {
        var acquired: PDDocument? = null
        val document = try {
            phase(opening) { PDDocument.load(file).also { acquired = it; documents++ } }
        } catch (failure: Throwable) {
            acquired?.let {
                try { it.close(); documents-- } catch (cleanup: Throwable) { failure.addSuppressed(cleanup) }
            }
            throw failure
        }
        return owned(document, body)
    }

    fun <T> emptyDocument(body: (PDDocument) -> T): T {
        val document = PDDocument(); documents++
        return owned(document, body)
    }

    private fun <T> owned(document: PDDocument, body: (PDDocument) -> T): T {
        var primary: Throwable? = null
        try { return body(document) }
        catch (failure: Throwable) { primary = failure; throw failure }
        finally {
            var attempted = false
            try { phase("close") { attempted = true; document.close(); documents-- } }
            catch (failure: Throwable) {
                // A pre-close measurement error must not prevent the one owned close attempt.
                if (!attempted) {
                    try { document.close(); documents-- } catch (cleanup: Throwable) { failure.addSuppressed(cleanup) }
                }
                if (primary != null) primary.addSuppressed(failure) else throw failure
            }
        }
    }

    fun save(document: PDDocument, file: File, limit: Long = OUTPUT_LIMIT) {
        require(limit in 1..OUTPUT_LIMIT)
        phase("save") {
            check(root.usableSpace >= FREE_MARGIN && bytes() <= AGGREGATE_LIMIT)
            val available = minOf(limit, AGGREGATE_LIMIT - bytes(), root.usableSpace - FREE_MARGIN)
            val raw = FileOutputStream(file); streams++
            var closed = false
            val bounded = object : OutputStream() {
                var count = 0L
                override fun write(value: Int) { write(byteArrayOf(value.toByte()), 0, 1) }
                override fun write(buffer: ByteArray, offset: Int, length: Int) {
                    if (length < 0 || length.toLong() > available - count) throw SpikeOutputLimit()
                    raw.write(buffer, offset, length); count += length
                }
                override fun flush() = raw.flush()
                override fun close() {
                    if (!closed) { raw.close(); closed = true; streams-- }
                }
            }
            bounded.use { document.save(it) }
        }
    }

    fun verifyManifest(document: PDDocument, entry: JSONObject, pages: List<SpikeSemantics.Page>) {
        assertFalse(document.isEncrypted)
        assertEquals(entry.getInt("expected_pages"), pages.size)
        for ((index, page) in pages.withIndex()) {
            val expected = entry.getJSONArray("page_sizes_pt").getJSONArray(index)
            val width = page.crop[2] - page.crop[0]; val height = page.crop[3] - page.crop[1]
            val rotated = page.rotation % 180 != 0
            assertEquals(expected.getDouble(0), (if (rotated) height else width).toDouble(), 0.15)
            assertEquals(expected.getDouble(1), (if (rotated) width else height).toDouble(), 0.15)
            assertEquals(entry.getJSONArray("page_rotations_degrees").getInt(index), page.rotation)
            assertEquals(entry.getJSONArray("annotation_counts").getInt(index), page.annotations.size)
            assertEquals(entry.getJSONArray("searchable_text").getBoolean(index), page.text.isNotBlank())
        }
    }

    /** Independent Android/PDFium raster observation at fixed bounded resolution, not complete semantics.
     * Identity/reorder only: extra rotations use structural + owner Python oracles instead.
     */
    fun raster(file: File, index: Int): String = phase("pdfiumRender") {
        val fd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY); descriptors++
        try {
            PdfRenderer(fd).use { renderer ->
                renderer.openPage(index).use { page ->
                    val scale = 256.0 / maxOf(page.width, page.height)
                    val width = maxOf(1, (page.width * scale).roundToInt())
                    val height = maxOf(1, (page.height * scale).roundToInt())
                    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888); bitmaps++
                    try {
                        bitmap.eraseColor(Color.WHITE)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        val pixels = IntArray(width * height); bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
                        assertTrue("Nonempty rendered fixture", pixels.any { it != Color.WHITE && it != Color.TRANSPARENT })
                        val bytes = ByteBuffer.allocate(pixels.size * 4)
                        pixels.forEach { bytes.putInt(it) }
                        "$width:$height:" + java.security.MessageDigest.getInstance("SHA-256")
                            .digest(bytes.array()).let(SpikeSemantics::hex)
                    } finally { bitmap.recycle(); bitmaps-- }
                }
            }
        } finally { fd.close(); descriptors-- }
    }

    fun record(file: File, entry: JSONObject, indices: List<Int>, turns: Int, approach: String) {
        check(file.isFile && file.length() in 1..OUTPUT_LIMIT)
        val recipe = JSONObject().put("file", file.name).put("sha256", file.inputStream().use(SpikeSemantics::digest))
            .put("bytes", file.length()).put("fixture", entry.getString("id"))
            .put("source_sha256", entry.getString("sha256")).put("indices", JSONArray(indices))
            .put("extra_quarter_turns", turns).put("approach", approach).put("pages", indices.size)
        outputs.put(recipe)
        Log.i("M_PDFBOX_SPIKE", JSONObject().put("case", case).put("output_recipe", recipe).toString())
    }

    fun assertNoOwnedHandles() {
        assertEquals(0, documents); assertEquals(0, descriptors); assertEquals(0, bitmaps); assertEquals(0, streams)
    }

    private fun bytes(): Long = root.listFiles()?.sumOf { it.length() } ?: error("Disposable namespace unavailable")
    private fun verifyCopy(file: File, entry: JSONObject) {
        assertEquals(entry.getLong("bytes"), file.length())
        assertEquals(entry.getString("sha256"), file.inputStream().use(SpikeSemantics::digest))
        assertEquals(entry.getString("sha256"), assets.open(entry.getString("path")).use(SpikeSemantics::digest))
    }

    override fun close() {
        var primary: Throwable? = null
        try { copies.forEach { (file, entry) -> verifyCopy(file, entry) }; assertNoOwnedHandles() }
        catch (failure: Throwable) { primary = failure; throw failure }
        finally {
            try {
                check(documents == 0 && descriptors == 0 && bitmaps == 0 && streams == 0) {
                    "Unconfirmed owned handles: namespace retained, cleanup pending"
                }
                for ((file, _) in copies) check(file.delete()) { "Source-copy cleanup pending" }
                if (retain) {
                    File(root, "evidence.json").writeText(JSONObject().put("case", case).put("api", Build.VERSION.SDK_INT)
                        .put("pid", Process.myPid()).put("candidate", "2.0.27.0")
                        .put("manifest_sha256", assets.open("manifest.json").use(SpikeSemantics::digest))
                        .put("outputs", outputs).put("measurements", measurements)
                        .put("cleanup", "GENERATED_OUTPUTS_RETAINED_BY_OWNER_REQUEST").toString(2))
                    Log.i("M_PDFBOX_SPIKE", JSONObject().put("retained_namespace", root.name).toString())
                } else {
                    for (file in requireNotNull(root.listFiles())) {
                        check(file.isFile && !java.nio.file.Files.isSymbolicLink(file.toPath()) && file.delete()) {
                            "Disposable output cleanup pending"
                        }
                    }
                    check(root.delete()) { "Disposable directory cleanup pending" }
                    Log.i("M_PDFBOX_SPIKE", JSONObject().put("case", case).put("cleanup", "COMPLETE").toString())
                }
            } catch (failure: Throwable) { if (primary != null) primary.addSuppressed(failure) else throw failure }
        }
    }

    companion object {
        const val SOURCE_LIMIT = 64L * 1024 * 1024
        const val OUTPUT_LIMIT = 128L * 1024 * 1024
        const val AGGREGATE_LIMIT = 256L * 1024 * 1024
        const val FREE_MARGIN = 64L * 1024 * 1024
    }
}
