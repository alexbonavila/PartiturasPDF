package com.abdev.partituraspdf.app.integration

import android.app.Application
import android.content.ContentValues
import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import android.os.Build
import android.os.ParcelFileDescriptor
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.abdev.partituraspdf.app.di.AppGraphFactory
import com.abdev.partituraspdf.app.di.AppRuntimeLimits
import com.abdev.partituraspdf.core.document.DocumentId
import com.abdev.partituraspdf.core.document.DocumentRef
import com.abdev.partituraspdf.core.document.DocumentRevision
import com.abdev.partituraspdf.core.document.PageIndex
import com.abdev.partituraspdf.core.document.StorageDocumentRef
import com.abdev.partituraspdf.core.result.AppError
import com.abdev.partituraspdf.core.result.Outcome
import com.abdev.partituraspdf.pdf.android.AndroidPdfReadEngine
import com.abdev.partituraspdf.pdf.api.AnnotationRendering
import com.abdev.partituraspdf.pdf.api.PdfReadEngine
import com.abdev.partituraspdf.pdf.api.PdfReadSession
import com.abdev.partituraspdf.pdf.api.PdfReadSource
import com.abdev.partituraspdf.pdf.api.ReadPageRef
import com.abdev.partituraspdf.pdf.api.RenderRequest
import com.abdev.partituraspdf.pdf.api.RenderRequestId
import com.abdev.partituraspdf.storage.api.DocumentReadAccess
import com.abdev.partituraspdf.storage.api.ReadAccessLimits
import com.abdev.partituraspdf.storage.api.ReadHandle
import com.abdev.partituraspdf.storage.api.ReadOwnership
import com.abdev.partituraspdf.storage.api.ReadProvenance
import com.abdev.partituraspdf.storage.saf.SafDocumentReadAccess
import com.abdev.partituraspdf.storage.saf.SafProviderRead
import java.io.File
import java.io.FileNotFoundException
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Real resolver -> production SAF -> real PdfRenderer. No fake PDF or native renderer seam. */
@RunWith(AndroidJUnit4::class)
class SafPdfIntegrationTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val target get() = instrumentation.targetContext
    private val resolver get() = target.contentResolver
    private val limits = AppRuntimeLimits.provisional()
    private lateinit var isolated: Context
    private lateinit var root: File
    private val copies get() = File(root, "saf-read-copies")
    private val reader: DocumentReadAccess get() = SafDocumentReadAccess(resolver, isolated)
    private val engine: PdfReadEngine get() = AndroidPdfReadEngine(limits.render)

    private data class Fixture(val id: String, val path: String, val bytes: Long,
        val sha256: String, val pages: Int)
    private data class Request(val uri: Uri, val token: String, val fixture: Fixture,
        val mode: String, val complete: Boolean)
    private val requests = mutableListOf<Request>()

    @Before fun setUp() {
        root = File(target.cacheDir, "e03-${UUID.randomUUID()}").apply { check(mkdir()) }
        isolated = object : ContextWrapper(target) {
            override fun getCacheDir(): File = root
            override fun getApplicationContext(): Context = this
        }
    }

    @After fun tearDown() {
        var failure: Throwable? = null
        fun record(problem: Throwable) {
            if (failure == null) failure = problem else failure!!.addSuppressed(problem)
        }
        try {
            // Every producer is joined even after a failed assertion; no resettable shared state.
            for (request in requests) {
                try {
                    val report = checkNotNull(resolver.call(request.uri, "await", request.token, null))
                    if (request.complete) {
                        assertNull("Producer failed: ${request.uri}", report.getString("failure"))
                        assertEquals(request.fixture.bytes, report.getLong("bytes"))
                    }
                    if (request.mode == "file") assertTrue(report.getBoolean("unlinked"))
                    if (request.mode == "broken") assertNotNull(report.getString("failure"))
                } catch (problem: Throwable) {
                    record(problem)
                }
            }
            try {
                assertTrue("SAF must release complete and incomplete copies", copyFiles().isEmpty())
            } catch (problem: Throwable) {
                record(problem)
            }
        } finally {
            // Only this test's UUID cache directory is removed, never application persistence.
            if (!root.deleteRecursively()) record(AssertionError("Test cache cleanup failed"))
        }
        failure?.let { throw it }
    }

    private fun fixture(id: String): Fixture {
        val rows = instrumentation.context.assets.open("manifest.json").bufferedReader().use {
            JSONObject(it.readText()).getJSONArray("files")
        }
        val row = (0 until rows.length()).map(rows::getJSONObject).single { it.getString("id") == id }
        assertEquals("valid", row.getString("expected_status"))
        return Fixture(id, row.getString("path"), row.getLong("bytes"), row.getString("sha256"),
            row.getInt("expected_pages"))
    }

    private fun request(id: String, mode: String, complete: Boolean = true): Request {
        val token = UUID.randomUUID().toString()
        val uri = Uri.parse("content://${CanonicalPdfContentProvider.AUTHORITY}/document/$mode-$id")
            .buildUpon().appendQueryParameter("request", token).build()
        return Request(uri, token, fixture(id), mode, complete).also(requests::add)
    }

    private fun document(uri: Uri) = StorageDocumentRef("saf", uri.toString())
    private fun copyFiles() = copies.listFiles()?.toList().orEmpty()
    private fun <T> Outcome<T>.success(): T {
        assertTrue("Expected real adapter success, got $this", this is Outcome.Success)
        return (this as Outcome.Success).value
    }
    private fun hash(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it.toInt() and 255) }

    /** pread checks actual resolver-delivered bytes without advancing PdfRenderer's input. */
    private fun assertCanonical(handle: ReadHandle, fixture: Fixture) {
        val bytes = ByteArray(fixture.bytes.toInt())
        var offset = 0
        while (offset < bytes.size) {
            val count = Os.pread(handle.descriptor.fileDescriptor, bytes, offset,
                bytes.size - offset, offset.toLong())
            assertTrue("Unexpected short fixture", count > 0)
            offset += count
        }
        assertEquals(0, Os.pread(handle.descriptor.fileDescriptor, ByteArray(1), 0, 1, fixture.bytes))
        assertEquals(fixture.sha256, hash(bytes))
        val expected = instrumentation.context.assets.open(fixture.path).use { it.readBytes() }
        assertArrayEquals(expected, bytes)
    }

    private suspend fun withSession(request: Request, access: DocumentReadAccess = reader,
        pdf: PdfReadEngine = engine, action: suspend (PdfReadSession) -> Unit) {
        val reference = document(request.uri)
        val handle = access.openSeekable(reference, limits.read).success()
        val descriptor = handle.descriptor
        try {
            assertTrue(handle.seekable)
            assertEquals(ReadOwnership.OWNED, handle.ownership)
            val copy = if (request.mode == "file") {
                assertEquals(ReadProvenance.Provider(reference), handle.provenance)
                assertTrue(copyFiles().isEmpty())
                null
            } else {
                assertEquals(ReadProvenance.TemporaryReadCopy(reference), handle.provenance)
                copyFiles().single().also {
                    assertEquals(request.fixture.bytes, it.length())
                    assertEquals(request.fixture.sha256, hash(it.readBytes()))
                }
            }
            assertCanonical(handle, request.fixture)
            val logical = DocumentRef(DocumentId("e03-${request.fixture.id}"))
            val source = PdfReadSource(handle, logical)
            assertEquals(DocumentRevision.Unknown(logical), source.revision)
            val session = pdf.open(source).success()
            session.use {
                assertEquals(ReadOwnership.TRANSFERRED, handle.ownership)
                assertThrows(IllegalStateException::class.java) { handle.descriptor }
                assertThrows(IllegalStateException::class.java) { handle.transferOwnership() }
                // Never close a transferred wrapper, including from a finally/use block.
                assertEquals(request.fixture.pages, it.info.pageCount)
                assertNull(it.info.revision)
                assertEquals(limits.render, it.renderLimits)
                if (copy != null) assertTrue("Session still owns its copy", copy.exists())
                action(it)
            }
            session.close()
            assertThrows(IllegalStateException::class.java) { descriptor.fd }
            if (copy != null) assertFalse("Session close must delete its copy", copy.exists())
            assertTrue(copyFiles().isEmpty())
        } finally {
            if (handle.ownership == ReadOwnership.OWNED) handle.close()
        }
    }

    private fun page(session: PdfReadSession, index: Int = 0) =
        ReadPageRef(session.info.id, session.info.sourceGeneration, PageIndex(index))
    private fun renderRequest(ref: ReadPageRef, width: Int = 128, height: Int = 160) =
        RenderRequest(RenderRequestId(UUID.randomUUID().toString()), ref, width, height, 0L)

    private suspend fun inspectAndRenderAll(session: PdfReadSession): Set<Pair<Int, Int>> {
        val sizes = mutableSetOf<Pair<Int, Int>>()
        for (index in 0 until session.info.pageCount) {
            val ref = page(session, index)
            val info = session.displayInfo(ref).success()
            assertEquals(ref, info.page)
            assertTrue(info.width > 0 && info.height > 0)
            assertNull(info.rotation)
            assertEquals(AnnotationRendering.UNKNOWN, info.annotations)
            sizes += info.width to info.height
            val request = renderRequest(ref)
            val lease = session.render(request).success()
            val bitmap = lease.bitmap
            try {
                assertEquals(request, lease.request)
                assertEquals(info, lease.displayInfo)
                assertEquals(request.width, bitmap.width)
                assertEquals(request.height, bitmap.height)
                assertFalse(bitmap.isRecycled)
                bitmap.getPixel(64, 80)
            } finally {
                lease.close()
            }
            lease.close()
            assertTrue(bitmap.isRecycled)
            assertThrows(IllegalStateException::class.java) { lease.bitmap }
        }
        return sizes
    }

    @Test fun directA4UsesProviderDescriptorAndRendersCanonicalPage() {
        runBlocking(Dispatchers.IO) {
            withSession(request("01", "file")) { inspectAndRenderAll(it) }
        }
    }

    @Test fun pipeIsReallyNonSeekableThenMaterializesTwoCanonicalPages() {
        runBlocking(Dispatchers.IO) {
            val probe = request("02", "pipe")
            reader.openRead(document(probe.uri)).success().use { raw ->
                assertFalse(raw.seekable)
                assertEquals(ReadProvenance.Provider(document(probe.uri)), raw.provenance)
                val failure = assertThrows(ErrnoException::class.java) {
                    Os.lseek(raw.descriptor.fileDescriptor, 0, OsConstants.SEEK_CUR)
                }
                assertEquals(OsConstants.ESPIPE, failure.errno)
                val bytes = ParcelFileDescriptor.AutoCloseInputStream(raw.descriptor).use { it.readBytes() }
                assertEquals(probe.fixture.bytes, bytes.size.toLong())
                assertEquals(probe.fixture.sha256, hash(bytes))
            }
            assertTrue(copyFiles().isEmpty())
            withSession(request("02", "pipe")) { inspectAndRenderAll(it) }
        }
    }

    @Test fun mixedSizesExposeDifferentRealPageDimensions() {
        runBlocking(Dispatchers.IO) {
            withSession(request("08", "file")) { assertTrue(inspectAndRenderAll(it).size > 1) }
        }
    }

    @Test fun scannedScoreRendersThroughAsynchronousPipe() {
        runBlocking(Dispatchers.IO) {
            // 221,317 bytes exceeds ordinary pipe capacity; a synchronous producer would block.
            withSession(request("10", "pipe")) { inspectAndRenderAll(it) }
        }
    }

    @Test fun vectorScoreRendersThroughResolverAndNativeEngine() {
        runBlocking(Dispatchers.IO) {
            withSession(request("11", "file")) { inspectAndRenderAll(it) }
        }
    }

    @Test fun inputLimitRejectsCopyAndLeavesNoIncompleteFile() {
        runBlocking(Dispatchers.IO) {
            val input = request("02", "pipe", complete = false)
            assertEquals(Outcome.Failure(AppError.Resource.INPUT_LIMIT),
                reader.openSeekable(document(input.uri), ReadAccessLimits(input.fixture.bytes - 1)))
            assertTrue(copyFiles().isEmpty())
        }
    }

    @Test fun failedProviderPipeIsIoFailureRatherThanDeletionOrInvalidPdf() {
        runBlocking(Dispatchers.IO) {
            val input = request("02", "broken", complete = false)
            assertEquals(Outcome.Failure(AppError.Access.PROVIDER_IO_FAILURE),
                reader.openSeekable(document(input.uri), limits.read))
            assertTrue(copyFiles().isEmpty())
        }
    }

    @Test fun cancellationDuringRealCopyPropagatesAndDiscardsIncompleteFile() {
        runBlocking(Dispatchers.IO) {
            val input = request("10", "pipe", complete = false)
            val job = Job()
            var observedBytes = 0L
            val cancellable = SafDocumentReadAccess(SafProviderRead(resolver), copies) { bytes ->
                observedBytes = bytes
                job.cancel(CancellationException("E03 cancellation after actual copy progress"))
            }
            try {
                try {
                    withContext(job) { cancellable.openSeekable(document(input.uri), limits.read) }
                    fail("Cancellation must propagate rather than become an Outcome")
                } catch (_: CancellationException) {
                    assertTrue(observedBytes > 0)
                }
                assertTrue(copyFiles().isEmpty())
            } finally {
                job.cancel()
            }
        }
    }

    @Test fun invalidPageAndE02RenderBoundsDoNotPoisonSessionAndClosedCallsFail() {
        runBlocking(Dispatchers.IO) {
            withSession(request("02", "pipe")) { session ->
                val valid = page(session)
                val invalid = page(session, session.info.pageCount)
                assertEquals(Outcome.Failure(AppError.Validation.INVALID_PAGE), session.displayInfo(invalid))
                assertEquals(Outcome.Failure(AppError.Validation.INVALID_PAGE), session.render(renderRequest(invalid)))
                assertEquals(Outcome.Failure(AppError.Conflict.STALE_SESSION),
                    session.displayInfo(valid.copy(sourceGeneration = valid.sourceGeneration + 1)))
                assertEquals(Outcome.Failure(AppError.Resource.RENDER_LIMIT),
                    session.render(renderRequest(valid, limits.render.maxDimension + 1, 1)))
                assertEquals(Outcome.Failure(AppError.Resource.RENDER_LIMIT),
                    session.render(renderRequest(valid, limits.render.maxDimension, limits.render.maxDimension)))
                inspectAndRenderAll(session)
                session.close()
                assertEquals(Outcome.Failure(AppError.Conflict.SESSION_CLOSED), session.displayInfo(valid))
                assertEquals(Outcome.Failure(AppError.Conflict.SESSION_CLOSED), session.render(renderRequest(valid)))
            }
        }
    }

    @Test fun sessionCloseReleasesCopyWhileCallerRetainsBitmapUntilLeaseClose() {
        runBlocking(Dispatchers.IO) {
            withSession(request("02", "pipe")) { session ->
                val lease = session.render(renderRequest(page(session))).success()
                val bitmap = lease.bitmap
                try {
                    session.close()
                    assertTrue(copyFiles().isEmpty())
                    assertFalse(bitmap.isRecycled)
                    bitmap.getPixel(0, 0)
                    assertEquals(128, lease.bitmap.width)
                } finally {
                    lease.close()
                }
                lease.close()
                assertTrue(bitmap.isRecycled)
                assertThrows(IllegalStateException::class.java) { lease.bitmap }
            }
        }
    }

    @Test fun realApplicationFactoryReusesGraphAndDoesNotOwnPdfLeases() {
        runBlocking(Dispatchers.IO) {
            // Production construction accepts the actual target Application. Never replace its
            // context or touch a user's persistence; fail safely unless this is a clean emulator.
            assertTrue("Use a disposable emulator", Build.HARDWARE in setOf("ranchu", "goldfish"))
            val application = target.applicationContext as Application
            val database = application.getDatabasePath("document-metadata.db")
            val preferences = File(application.filesDir, "datastore/app_preferences.preferences_pb")
            val defaultCopies = File(application.cacheDir, "saf-read-copies")
            assertFalse("Do not use an installation with application data", database.exists())
            assertFalse(preferences.exists())
            assertTrue(defaultCopies.listFiles().isNullOrEmpty())
            val factory = AppGraphFactory(application, limits)
            try {
                val graph = factory.get()
                assertSame(graph, factory.get())
                assertSame(limits, graph.runtimeLimits)
                assertTrue(graph.readAccess is SafDocumentReadAccess)
                assertTrue(graph.pdfEngine is AndroidPdfReadEngine)
                withSession(request("01", "file"), graph.readAccess, graph.pdfEngine) { session ->
                    val lease = session.render(renderRequest(page(session))).success()
                    val bitmap = lease.bitmap
                    try {
                        factory.close() // Quiescent repositories; no Room operations started.
                        factory.close()
                        assertThrows(IllegalStateException::class.java) { factory.get() }
                        assertFalse(bitmap.isRecycled)
                        inspectAndRenderAll(session) // Factory does not own this open session.
                        session.close()
                        assertFalse(bitmap.isRecycled)
                        bitmap.getPixel(0, 0)
                    } finally {
                        lease.close()
                    }
                    assertTrue(bitmap.isRecycled)
                }
            } finally {
                factory.close()
            }
            // Room is constructed/closed lazily without creating/querying application rows.
            assertFalse(database.exists())
            assertFalse(preferences.exists())
            assertTrue(defaultCopies.listFiles().isNullOrEmpty())
        }
    }

    @Test fun providerRejectsWritesMutationsAndUnlistedFixturePaths() {
        val uri = Uri.parse("content://${CanonicalPdfContentProvider.AUTHORITY}/document/file-01")
            .buildUpon().appendQueryParameter("request", UUID.randomUUID().toString()).build()
        assertThrows(FileNotFoundException::class.java) { resolver.openFileDescriptor(uri, "rw") }
        assertThrows(UnsupportedOperationException::class.java) { resolver.insert(uri, ContentValues()) }
        assertThrows(UnsupportedOperationException::class.java) { resolver.update(uri, ContentValues(), null, null) }
        assertThrows(UnsupportedOperationException::class.java) { resolver.delete(uri, null, null) }
        assertThrows(FileNotFoundException::class.java) {
            resolver.openFileDescriptor(uri.buildUpon().path("/document/file-99").build(), "r")
        }
    }
}
