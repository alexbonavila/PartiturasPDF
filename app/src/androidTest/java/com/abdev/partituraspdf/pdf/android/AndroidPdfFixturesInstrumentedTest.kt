package com.abdev.partituraspdf.pdf.android

import android.os.ParcelFileDescriptor
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.abdev.partituraspdf.core.document.PageIndex
import com.abdev.partituraspdf.core.document.StorageDocumentRef
import com.abdev.partituraspdf.core.result.AppError
import com.abdev.partituraspdf.core.result.Outcome
import com.abdev.partituraspdf.pdf.api.AnnotationRendering
import com.abdev.partituraspdf.pdf.api.PdfReadSource
import com.abdev.partituraspdf.pdf.api.ReadPageRef
import com.abdev.partituraspdf.pdf.api.RenderLimits
import com.abdev.partituraspdf.pdf.api.RenderRequest
import com.abdev.partituraspdf.pdf.api.RenderRequestId
import com.abdev.partituraspdf.storage.api.ReadHandle
import com.abdev.partituraspdf.storage.api.ReadResource
import com.abdev.partituraspdf.storage.api.ReadProvenance
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.FixMethodOrder
import org.junit.runners.MethodSorters
import org.junit.runner.RunWith

/** Runs the real Android API 26 PdfRenderer against the immutable repository PDF corpus. */
// Android 8 Pdfium can retain failure state after corrupt/encrypted input.
// Always exercise readable fixtures before opening either negative case.
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@RunWith(AndroidJUnit4::class)
class AndroidPdfFixturesInstrumentedTest {
    private val testContext get() = InstrumentationRegistry.getInstrumentation().context
    private val appContext get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val limits = RenderLimits(maxDimension = 512, maxPixels = 512L * 512L)

    private data class Fixture(
        val id: String,
        val path: String,
        val status: String,
        val expectedPages: Int?,
        val sha256: String,
    )

    private fun fixtures(): List<Fixture> {
        val manifest = testContext.assets.open("manifest.json").bufferedReader().use { reader ->
            JSONObject(reader.readText()).getJSONArray("files")
        }
        return (0 until manifest.length()).map { index ->
            val row = manifest.getJSONObject(index)
            Fixture(
                id = row.getString("id"),
                path = row.getString("path"),
                status = row.getString("expected_status"),
                expectedPages = if (row.isNull("expected_pages")) null else row.getInt("expected_pages"),
                sha256 = row.getString("sha256"),
            )
        }
    }

    /** Temporary byte-for-byte copy only: PdfRenderer requires a seekable file descriptor. */
    private suspend fun <T> withFixture(fixture: Fixture, action: suspend (File) -> T): T {
        val temporary = File.createTempFile("native-fixture-${fixture.id}-", ".pdf", appContext.cacheDir)
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            testContext.assets.open(fixture.path).use { input ->
                FileOutputStream(temporary).use { output ->
                    val chunk = ByteArray(8192)
                    while (true) {
                        val read = input.read(chunk)
                        if (read == -1) break
                        if (read > 0) {
                            digest.update(chunk, 0, read)
                            output.write(chunk, 0, read)
                        }
                    }
                }
            }
            val actualHash = digest.digest().joinToString("") { byte ->
                "%02x".format(byte.toInt() and 0xff)
            }
            assertEquals("Fixture bytes must equal their immutable manifest entry: ${fixture.path}", fixture.sha256, actualHash)
            return action(temporary)
        } finally {
            assertTrue("Temporary fixture copy must be deleted", temporary.delete())
        }
    }

    private class TrackedResource(override val descriptor: ParcelFileDescriptor) : ReadResource {
        var closes = 0
            private set

        override fun close() {
            closes++
            descriptor.close()
        }
    }

    private fun openSource(file: File): Pair<PdfReadSource, TrackedResource> {
        val resource = TrackedResource(ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY))
        val handle = ReadHandle(
            resource,
            ReadProvenance.Provider(StorageDocumentRef("fixture", file.name)),
            seekable = true,
        )
        return PdfReadSource(handle) to resource
    }

    @Test
    fun a_allFifteenAuthoritativeFixturesAreIncludedInTheTestApk() = runBlocking {
        val all = fixtures()
        assertEquals(15, all.size)
        assertEquals((1..15).map { "%02d".format(it) }, all.map { it.id })
        assertEquals(13, all.count { it.status == "valid" })
        assertEquals(1, all.count { it.status == "password_required" })
        assertEquals(1, all.count { it.status == "corrupted" })
        for (fixture in all) {
            withFixture(fixture) { file ->
                assertTrue("Fixture must be present and nonempty: ${fixture.path}", file.length() > 0L)
            }
        }
    }

    @Test
    fun b_thirteenReadableFixturesOpenAndRenderOnTheRealPlatform() = runBlocking {
        val engine = AndroidPdfReadEngine(limits)
        for (fixture in fixtures().filter { it.status == "valid" }) {
            withFixture(fixture) { file ->
                val (source, resource) = openSource(file)
                val opened = engine.open(source)
                assertTrue("Native open failed for ${fixture.path}: $opened", opened is Outcome.Success)
                val session = (opened as Outcome.Success).value
                try {
                    assertEquals("Page count for ${fixture.path}", fixture.expectedPages, session.info.pageCount)
                    val indices = listOf(0, session.info.pageCount - 1).distinct()
                    for (index in indices) {
                        val ref = ReadPageRef(session.info.id, session.info.sourceGeneration, PageIndex(index))
                        val observed = session.displayInfo(ref)
                        assertTrue("Native page info failed for ${fixture.path} page $index: $observed", observed is Outcome.Success)
                        val display = (observed as Outcome.Success).value
                        assertTrue(display.width > 0)
                        assertTrue(display.height > 0)
                        assertEquals(ref, display.page)
                        assertEquals(null, display.rotation)
                        assertEquals(AnnotationRendering.UNKNOWN, display.annotations)
                        val request = RenderRequest(RenderRequestId("fixture-${fixture.id}-$index"), ref, 256, 320, 0L)
                        val outcome = session.render(request)
                        assertTrue("Native render failed for ${fixture.path} page $index: $outcome", outcome is Outcome.Success)
                        val result = (outcome as Outcome.Success).value
                        try {
                            assertEquals(request, result.request)
                            assertEquals(display, result.displayInfo)
                            assertEquals(256, result.bitmap.width)
                            assertEquals(320, result.bitmap.height)
                            assertFalse(result.bitmap.isRecycled)
                            // Reading pixels verifies that Android supplied a usable bitmap, not merely a fake.
                            result.bitmap.getPixel(128, 160)
                        } finally {
                            result.close()
                        }
                    }
                } finally {
                    session.close()
                }
                assertEquals("Source descriptor must be released exactly once: ${fixture.path}", 1, resource.closes)
            }
        }
    }

    @Test
    fun z_encryptedFixtureRequiresPasswordAndDoesNotLeakDescriptor() = runBlocking {
        val fixture = fixtures().single { it.id == "14" }
        assertEquals("password_required", fixture.status)
        withFixture(fixture) { file ->
            val (source, resource) = openSource(file)
            val opened = AndroidPdfReadEngine(limits).open(source)
            assertTrue("API 26 must not accept an encrypted document without a password: $opened", opened is Outcome.Failure)
            val failure = (opened as Outcome.Failure).error
            assertTrue(
                "Unexpected failure for encrypted document: $failure",
                failure == AppError.Unsupported.DOCUMENT_FORMAT ||
                    failure == AppError.Validation.INVALID_DOCUMENT,
            )
            assertEquals(1, resource.closes)
        }
    }

    /** Inspect every page of the canonical 100-page document, not only its endpoints. */
    @Test
    fun c_allPagesOfHundredPageFixtureAreReadableWithoutLeakingNativePages() = runBlocking {
        val fixture = fixtures().single { it.id == "05" }
        withFixture(fixture) { file ->
            val (source, resource) = openSource(file)
            val opened = AndroidPdfReadEngine(limits).open(source)
            assertTrue("100-page fixture must open on Android 8: $opened", opened is Outcome.Success)
            val session = (opened as Outcome.Success).value
            try {
                assertEquals(100, session.info.pageCount)
                for (index in 0 until session.info.pageCount) {
                    val ref = ReadPageRef(session.info.id, session.info.sourceGeneration, PageIndex(index))
                    val info = session.displayInfo(ref)
                    assertTrue("Page $index must be readable: $info", info is Outcome.Success)
                    val dimensions = (info as Outcome.Success).value
                    assertEquals(ref, dimensions.page)
                    assertTrue(dimensions.width > 0 && dimensions.height > 0)
                }
                // Sample interior pages with real bitmap rendering after a long navigation sequence.
                for (index in listOf(1, 24, 49, 74, 98)) {
                    val ref = ReadPageRef(session.info.id, session.info.sourceGeneration, PageIndex(index))
                    val result = session.render(RenderRequest(RenderRequestId("hundred-$index"), ref, 96, 128, 0L))
                    assertTrue("Page $index must render: $result", result is Outcome.Success)
                    (result as Outcome.Success).value.use { lease ->
                        assertEquals(96, lease.bitmap.width)
                        assertEquals(128, lease.bitmap.height)
                        lease.bitmap.getPixel(48, 64)
                    }
                }
            } finally {
                session.close()
            }
            assertEquals(1, resource.closes)
        }
    }

    /** The caller owns a bitmap independently of the original PDF descriptor. */
    @Test
    fun d_realBitmapLeaseRemainsValidAfterSessionCloseThenRecyclesExactlyOnce() = runBlocking {
        val fixture = fixtures().single { it.id == "01" }
        withFixture(fixture) { file ->
            val (source, resource) = openSource(file)
            val session = (AndroidPdfReadEngine(limits).open(source) as Outcome.Success).value
            val ref = ReadPageRef(session.info.id, session.info.sourceGeneration, PageIndex(0))
            val request = RenderRequest(RenderRequestId("lease-lifetime"), ref, 128, 192, 0L)
            val lease = try {
                (session.render(request) as Outcome.Success).value
            } finally {
                session.close()
            }
            assertEquals(1, resource.closes)
            try {
                assertFalse(lease.bitmap.isRecycled)
                assertEquals(128, lease.bitmap.width)
                assertEquals(192, lease.bitmap.height)
                lease.bitmap.getPixel(32, 48)
            } finally {
                lease.close()
            }
            lease.close()
            var rejected = false
            try {
                lease.bitmap
            } catch (_: IllegalStateException) {
                rejected = true
            }
            assertTrue("Use-after-close must be rejected", rejected)
        }
    }

    /** Prevent invalid reads and oversized allocations before entering native PdfRenderer. */
    @Test
    fun e_invalidAndStaleRealRequestsAreTypedAndDoNotPoisonAValidSession() = runBlocking {
        val fixture = fixtures().single { it.id == "02" }
        withFixture(fixture) { file ->
            val (source, resource) = openSource(file)
            val session = (AndroidPdfReadEngine(limits).open(source) as Outcome.Success).value
            val good = ReadPageRef(session.info.id, session.info.sourceGeneration, PageIndex(0))
            try {
                val outOfRange = good.copy(page = PageIndex(session.info.pageCount))
                assertEquals(Outcome.Failure(AppError.Validation.INVALID_PAGE), session.displayInfo(outOfRange))
                assertEquals(
                    Outcome.Failure(AppError.Conflict.STALE_SESSION),
                    session.displayInfo(good.copy(sourceGeneration = session.info.sourceGeneration + 1)),
                )
                assertEquals(
                    Outcome.Failure(AppError.Resource.RENDER_LIMIT),
                    session.render(RenderRequest(RenderRequestId("too-wide"), good, 513, 32, 0L)),
                )
                assertEquals(
                    Outcome.Failure(AppError.Validation.INVALID_PAGE),
                    session.render(RenderRequest(RenderRequestId("invalid-page"), outOfRange, 32, 32, 0L)),
                )
                assertTrue(session.displayInfo(good) is Outcome.Success)
                val success = session.render(RenderRequest(RenderRequestId("still-valid"), good, 64, 96, 0L))
                assertTrue(success is Outcome.Success)
                (success as Outcome.Success).value.use { it.bitmap.getPixel(0, 0) }
            } finally {
                session.close()
            }
            assertEquals(1, resource.closes)
        }
    }

    /** Two simultaneous readers use distinct descriptor ownership and session identities. */
    @Test
    fun f_realSessionsAreIndependentAndRejectEachOthersPageReferences() = runBlocking {
        val fixture = fixtures().single { it.id == "02" }
        withFixture(fixture) { file ->
            val (firstSource, firstResource) = openSource(file)
            val (secondSource, secondResource) = openSource(file)
            val engine = AndroidPdfReadEngine(limits)
            val first = (engine.open(firstSource) as Outcome.Success).value
            try {
                val second = (engine.open(secondSource) as Outcome.Success).value
                try {
                    assertFalse(first.info.id == second.info.id)
                    val firstRef = ReadPageRef(first.info.id, first.info.sourceGeneration, PageIndex(0))
                    val secondRef = ReadPageRef(second.info.id, second.info.sourceGeneration, PageIndex(1))
                    assertEquals(Outcome.Failure(AppError.Conflict.STALE_SESSION), second.displayInfo(firstRef))
                    assertEquals(Outcome.Failure(AppError.Conflict.STALE_SESSION), first.displayInfo(secondRef))
                    assertTrue(first.displayInfo(firstRef) is Outcome.Success)
                    assertTrue(second.displayInfo(secondRef) is Outcome.Success)
                    first.close()
                    assertTrue(second.displayInfo(secondRef) is Outcome.Success)
                } finally {
                    second.close()
                }
            } finally {
                first.close()
            }
            assertEquals(1, firstResource.closes)
            assertEquals(1, secondResource.closes)
        }
    }

    /** Check that mixed paper geometry is actually observed rather than fabricated. */
    @Test
    fun g_mixedPageSizesAndRotatedSourcesHaveObservedDimensionsOnly() = runBlocking {
        for (id in listOf("08", "09")) {
            val fixture = fixtures().single { it.id == id }
            withFixture(fixture) { file ->
                val (source, resource) = openSource(file)
                val session = (AndroidPdfReadEngine(limits).open(source) as Outcome.Success).value
                try {
                    val sizes = mutableSetOf<Pair<Int, Int>>()
                    for (index in 0 until session.info.pageCount) {
                        val ref = ReadPageRef(session.info.id, session.info.sourceGeneration, PageIndex(index))
                        val observed = session.displayInfo(ref)
                        assertTrue("Fixture $id page $index: $observed", observed is Outcome.Success)
                        val info = (observed as Outcome.Success).value
                        sizes += info.width to info.height
                        assertEquals(null, info.rotation)
                        assertEquals(AnnotationRendering.UNKNOWN, info.annotations)
                        val rendered = session.render(RenderRequest(RenderRequestId("geometry-$id-$index"), ref, 80, 96, 0L))
                        assertTrue("Fixture $id page $index render: $rendered", rendered is Outcome.Success)
                        (rendered as Outcome.Success).value.use { lease ->
                            assertEquals(info, lease.displayInfo)
                            assertEquals(80, lease.bitmap.width)
                            assertEquals(96, lease.bitmap.height)
                        }
                    }
                    if (id == "08") assertTrue("Mixed-size PDF must have multiple paper geometries", sizes.size > 1)
                } finally {
                    session.close()
                }
                assertEquals(1, resource.closes)
            }
        }
    }

    /** Repeated rendering must not reuse or prematurely recycle a caller-owned bitmap. */
    @Test
    fun h_consecutiveNativeRendersProduceIndependentLiveBitmaps() = runBlocking {
        val fixture = fixtures().single { it.id == "01" }
        withFixture(fixture) { file ->
            val (source, resource) = openSource(file)
            val session = (AndroidPdfReadEngine(limits).open(source) as Outcome.Success).value
            try {
                val ref = ReadPageRef(session.info.id, session.info.sourceGeneration, PageIndex(0))
                val first = (session.render(RenderRequest(RenderRequestId("first"), ref, 96, 128, 0L)) as Outcome.Success).value
                try {
                    val second = (session.render(RenderRequest(RenderRequestId("second"), ref, 160, 192, 1L)) as Outcome.Success).value
                    try {
                        assertFalse(first.bitmap === second.bitmap)
                        assertEquals(96, first.bitmap.width)
                        assertEquals(160, second.bitmap.width)
                        first.close()
                        assertFalse(second.bitmap.isRecycled)
                        second.bitmap.getPixel(80, 96)
                    } finally {
                        second.close()
                    }
                } finally {
                    first.close()
                }
            } finally {
                session.close()
            }
            assertEquals(1, resource.closes)
        }
    }

    @Test
    fun y_deliberatelyCorruptedFixtureIsRejectedAndDoesNotLeakDescriptor() = runBlocking {
        val fixture = fixtures().single { it.id == "15" }
        assertEquals("corrupted", fixture.status)
        withFixture(fixture) { file ->
            val (source, resource) = openSource(file)
            val opened = AndroidPdfReadEngine(limits).open(source)
            assertEquals(Outcome.Failure(AppError.Validation.INVALID_DOCUMENT), opened)
            assertEquals(1, resource.closes)
        }
    }
}
