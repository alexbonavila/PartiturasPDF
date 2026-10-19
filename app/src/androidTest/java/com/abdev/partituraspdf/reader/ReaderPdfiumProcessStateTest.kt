package com.abdev.partituraspdf.reader

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.os.Process
import android.os.Bundle
import android.system.Os
import android.util.Log
import com.abdev.partituraspdf.pdf.android.FreshPdfiumProcess
import com.abdev.partituraspdf.pdf.android.FreshPdfiumIdentity
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.abdev.partituraspdf.core.result.AppError
import com.abdev.partituraspdf.core.result.Outcome
import com.abdev.partituraspdf.pdf.android.AndroidPdfReadEngine
import com.abdev.partituraspdf.pdf.api.*
import com.abdev.partituraspdf.storage.api.*
import com.abdev.partituraspdf.core.document.StorageDocumentRef
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestName
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters

/** Process freshness comes from separate runner invocations, NEVER from this method ordering.
 * See PDFIUM_PROCESS_EXPERIMENTS.md. A failed valid reopen remains a failing compatibility test.
 */
@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class ReaderPdfiumProcessStateTest {
    @get:Rule val freshPdfiumIdentity = FreshPdfiumIdentity()
    @get:Rule val testName = TestName()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private lateinit var directory: File
    private lateinit var rows: List<JSONObject>
    private var sequence = 0
    private val attempts = org.json.JSONArray()
    private val limits = RenderLimits(128, 128L * 128)

    @Before fun setup() {
        directory = File(instrumentation.targetContext.cacheDir, "reader-pdfium-${UUID.randomUUID()}")
        check(directory.mkdir())
        val manifest = instrumentation.context.assets.open("manifest.json").bufferedReader().use {
            JSONObject(it.readText()).getJSONArray("files")
        }
        rows = (0 until manifest.length()).map(manifest::getJSONObject)
    }
    @After fun cleanup() {
        try { assertTrue("Only test-owned copies must be removed", directory.deleteRecursively()) }
        finally {
            // Machine-readable evidence survives a failing assertion and is tied to this one
            // runner invocation; host automation must still prove process replacement.
            instrumentation.addResults(Bundle().apply { putString("readerPdfiumEvidence", attempts.toString()) })
        }
    }

    private fun hash(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it.toInt() and 255) }

    private data class Attempt(val rendered: Boolean, val typed: AppError?, val native: Throwable?)

    private suspend fun attempt(id: String, adapter: Boolean): Attempt {
        val row = rows.single { it.getString("id") == id }
        val copy = File(directory, "${++sequence}-$id.pdf")
        val bytes = instrumentation.context.assets.open(row.getString("path")).use { it.readBytes() }
        copy.writeBytes(bytes)
        assertEquals(row.getLong("bytes"), copy.length())
        assertEquals(row.getString("sha256"), hash(copy.readBytes()))
        val fd = ParcelFileDescriptor.open(copy, ParcelFileDescriptor.MODE_READ_ONLY)
        val raw = fd.fileDescriptor
        val evidence = JSONObject().apply {
            put("api", android.os.Build.VERSION.SDK_INT); put("pid", Process.myPid())
            put("processStartElapsedMs", Process.getStartElapsedRealtime())
            put("invocation", InstrumentationRegistry.getArguments().getString("readerProcessRun", "unspecified-full-suite"))
            put("class", this@ReaderPdfiumProcessStateTest.javaClass.name); put("test", testName.methodName); put("sequence", sequence); put("fixture", id)
            put("engine", if (adapter) "AndroidPdfReadEngine" else "PdfRenderer")
            put("sha256", hash(copy.readBytes())); put("size", copy.length()); put("sha256Verified", true); put("descriptorValid", raw.valid())
            put("constructed", false); put("pageCountRead", false); put("rendered", false)
            put("typedOutcome", "not applicable to direct PdfRenderer")
        }
        var renderer: PdfRenderer? = null
        var session: PdfReadSession? = null
        var handle: ReadHandle? = null
        var resourceCloses = 0
        var nativeFailure: Throwable? = null
        var typedFailure: AppError? = null
        var rendered = false
        var primary: Throwable? = null
        try {
            assertTrue(raw.valid())
            evidence.put("operation", "verify descriptor bytes")
            evidence.put("descriptorBytes", Os.fstat(raw).st_size)
            assertEquals(row.getLong("bytes"), Os.fstat(raw).st_size)
            val observed = ByteArray(bytes.size)
            var offset = 0
            while (offset < observed.size) {
                val read = Os.pread(raw, observed, offset, observed.size - offset, offset.toLong())
                assertTrue(read > 0); offset += read
            }
            assertArrayEquals(bytes, observed)
            assertEquals(0L, Os.lseek(raw, 0, android.system.OsConstants.SEEK_CUR))
            if (adapter) {
                val resource = object : ReadResource {
                    override val descriptor = fd
                    override fun close() { resourceCloses++; fd.close() }
                }
                val input = ReadHandle(resource, ReadProvenance.Provider(StorageDocumentRef("canonical", id)), true)
                handle = input
                evidence.put("operation", "engine.open")
                when (val opened = AndroidPdfReadEngine(limits).open(PdfReadSource(input))) {
                    is Outcome.Failure -> { typedFailure = opened.error; evidence.put("typedOutcome", opened.toString()) }
                    is Outcome.Success -> {
                        session = opened.value
                        evidence.put("constructed", true); evidence.put("pageCountRead", true)
                        evidence.put("pageCount", opened.value.info.pageCount)
                        assertEquals(row.getInt("expected_pages"), opened.value.info.pageCount)
                        val request = RenderRequest(RenderRequestId("process-$sequence"), ReadPageRef(opened.value.info.id, opened.value.info.sourceGeneration, com.abdev.partituraspdf.core.document.PageIndex(0)), 128, 128, 1)
                        evidence.put("operation", "engine.render")
                        when (val output = opened.value.render(request)) {
                            is Outcome.Failure -> { typedFailure = output.error; evidence.put("typedOutcome", output.toString()) }
                            is Outcome.Success -> {
                                val bitmap = output.value.bitmap
                                try { assertFalse(bitmap.isRecycled); bitmap.getPixel(0, 0); rendered = true }
                                finally { output.value.close() }
                                assertTrue("Lease must recycle its native bitmap", bitmap.isRecycled)
                                evidence.put("typedOutcome", "Success(open and render)")
                            }
                        }
                    }
                }
            } else {
                try {
                    evidence.put("operation", "PdfRenderer constructor")
                    renderer = PdfRenderer(fd)
                    evidence.put("constructed", true)
                    evidence.put("operation", "PdfRenderer.pageCount")
                    val pages = renderer.pageCount
                    evidence.put("pageCountRead", true); evidence.put("pageCount", pages)
                    assertEquals(row.getInt("expected_pages"), pages)
                    evidence.put("operation", "PdfRenderer.openPage")
                    renderer.openPage(0).use { page ->
                        val bitmap = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888)
                        try {
                            evidence.put("operation", "PdfRenderer.Page.render")
                            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            bitmap.getPixel(0, 0); rendered = true
                        }
                        finally { bitmap.recycle() }
                    }
                } catch (failure: IOException) { nativeFailure = failure }
                catch (failure: SecurityException) { nativeFailure = failure }
            }
            return Attempt(rendered, typedFailure, nativeFailure)
        } catch (failure: Throwable) { primary = failure; throw failure }
        finally {
            var cleanupFailure: Throwable? = null
            fun close(action: () -> Unit) {
                try { action() } catch (failure: Throwable) {
                    if (cleanupFailure == null) cleanupFailure = failure else cleanupFailure!!.addSuppressed(failure)
                }
            }
            close { session?.close() }
            close { renderer?.close() }
            close { if (handle?.ownership == ReadOwnership.OWNED) handle!!.close() }
            // A returned renderer/transferred handle is the sole descriptor owner. Do not hide
            // a failed owner close by forcibly closing its descriptor. Constructor/preflight
            // failures which acquired no owner leave the original PFD with this fixture.
            if (renderer == null && handle == null) close { fd.close() }
            close { assertFalse("Descriptor leaked", raw.valid()) }
            if (adapter && handle != null) close { assertEquals("Transferred resource closes once", 1, resourceCloses) }
            evidence.put("rendered", rendered); evidence.put("descriptorClosed", !raw.valid())
            evidence.put("resourceCloses", resourceCloses); evidence.put("ownership", handle?.ownership?.name ?: "direct")
            evidence.put("exception", nativeFailure?.javaClass?.name ?: primary?.javaClass?.name ?: JSONObject.NULL)
            evidence.put("message", nativeFailure?.message ?: primary?.message ?: JSONObject.NULL)
            evidence.put("cleanupException", cleanupFailure?.toString() ?: JSONObject.NULL)
            evidence.put("ownerCloseAttempted", session != null || renderer != null)
            evidence.put("ownerCloseSucceeded", if (session != null || renderer != null) cleanupFailure == null else JSONObject.NULL)
            attempts.put(evidence)
            Log.i("ReaderPdfiumProcess", evidence.toString())
            if (cleanupFailure != null) {
                if (primary != null) primary.addSuppressed(cleanupFailure!!) else throw cleanupFailure!!
            }
        }
    }

    private suspend fun valid(adapter: Boolean, id: String = "01") {
        val result = attempt(id, adapter)
        assertTrue("Valid fixture $id did not open/render; inspect ReaderPdfiumProcess PID/sequence: $result", result.rendered)
    }
    private suspend fun sequence(adapter: Boolean, vararg negatives: String) {
        valid(adapter)
        for (id in negatives) {
            val result = attempt(id, adapter)
            assertFalse("Negative input unexpectedly rendered", result.rendered)
            if (adapter) {
                if (id == "15") assertEquals(AppError.Validation.INVALID_DOCUMENT, result.typed)
                else assertTrue(result.typed == AppError.Validation.INVALID_DOCUMENT || result.typed == AppError.Unsupported.DOCUMENT_FORMAT)
            } else assertTrue("Expected native rejection", result.native is IOException || result.native is SecurityException)
        }
        valid(adapter) // Deliberately fails if a negative input poisons subsequent valid opens.
    }
    @FreshPdfiumProcess
    @Test fun a_cleanDirectRepeatedValidOpens() { runBlocking(Dispatchers.IO) { valid(false); valid(false); valid(false, "02") } }
    @FreshPdfiumProcess
    @Test fun b_corruptThenValidDirect() { runBlocking(Dispatchers.IO) { sequence(false, "15") } }
    @FreshPdfiumProcess
    @Test fun c_encryptedThenValidDirect() { runBlocking(Dispatchers.IO) { sequence(false, "14") } }
    @FreshPdfiumProcess
    @Test fun d_freshProcessRecoveryDirect() { runBlocking(Dispatchers.IO) { valid(false); valid(false, "02") } }
    @FreshPdfiumProcess
    @Test fun e_corruptThenValidAdapter() { runBlocking(Dispatchers.IO) { sequence(true, "15") } }
    @FreshPdfiumProcess
    @Test fun f_encryptedThenValidAdapter() { runBlocking(Dispatchers.IO) { sequence(true, "14") } }
    @FreshPdfiumProcess
    @Test fun g_corruptEncryptedThenValidDirect() { runBlocking(Dispatchers.IO) { sequence(false, "15", "14") } }
    @FreshPdfiumProcess
    @Test fun h_corruptEncryptedThenValidAdapter() { runBlocking(Dispatchers.IO) { sequence(true, "15", "14") } }
    @FreshPdfiumProcess
    @Test fun i_encryptedCorruptThenValidDirect() { runBlocking(Dispatchers.IO) { sequence(false, "14", "15") } }
    @FreshPdfiumProcess
    @Test fun j_encryptedCorruptThenValidAdapter() { runBlocking(Dispatchers.IO) { sequence(true, "14", "15") } }
    @FreshPdfiumProcess
    @Test fun k_cleanAdapterRepeatedValidOpens() { runBlocking(Dispatchers.IO) { valid(true); valid(true); valid(true, "02") } }

}
