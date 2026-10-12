package com.abdev.partituraspdf.pdf.android

import android.graphics.Bitmap
import android.os.ParcelFileDescriptor
import com.abdev.partituraspdf.core.document.DocumentId
import com.abdev.partituraspdf.core.document.DocumentRef
import com.abdev.partituraspdf.core.document.DocumentRevision
import com.abdev.partituraspdf.core.document.PageIndex
import com.abdev.partituraspdf.core.document.SnapshotEvidence
import com.abdev.partituraspdf.core.document.StorageDocumentRef
import com.abdev.partituraspdf.core.result.AppError
import com.abdev.partituraspdf.core.result.Outcome
import com.abdev.partituraspdf.pdf.api.PdfReadSource
import com.abdev.partituraspdf.pdf.api.PdfReadSession
import com.abdev.partituraspdf.pdf.api.ReadPageRef
import com.abdev.partituraspdf.pdf.api.RenderLimits
import com.abdev.partituraspdf.pdf.api.RenderRequest
import com.abdev.partituraspdf.pdf.api.RenderRequestId
import com.abdev.partituraspdf.storage.api.ReadHandle
import com.abdev.partituraspdf.storage.api.ReadProvenance
import com.abdev.partituraspdf.storage.api.ReadResource
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** Exercises native-error and ownership edges using deterministic JVM fakes, never generated PDFs. */
class AndroidPdfCoverageEdgeCasesTest {
    private val limits = RenderLimits(maxDimension = 128, maxPixels = 8192L)

    private class Backing(private val onClose: () -> Unit = {}) : ReadResource {
        var closes = 0
        override val descriptor: ParcelFileDescriptor
            get() = error("Native JVM fakes must not request the Android descriptor")
        override fun close() {
            closes++
            onClose()
        }
    }

    private class Buffer(private val onClose: () -> Unit = {}) : RenderBuffer {
        var closes = 0
        override val bitmap: Bitmap
            get() = error("No Android bitmap is created by JVM tests")
        override fun close() {
            closes++
            onClose()
        }
    }

    private class Page(
        override val width: Int = 600,
        override val height: Int = 800,
        private val onRender: () -> Unit = {},
        private val onClose: () -> Unit = {},
    ) : NativePdfPage {
        var closes = 0
        var renders = 0
        override fun renderInto(buffer: RenderBuffer) {
            renders++
            onRender()
        }
        override fun close() {
            closes++
            onClose()
        }
    }

    private class Document(
        override val pageCount: Int = 1,
        private val pageFactory: (Int) -> NativePdfPage = { Page() },
        private val onClose: () -> Unit = {},
    ) : NativePdfDocument {
        var closes = 0
        var opens = 0
        override fun openPage(index: Int): NativePdfPage {
            opens++
            return pageFactory(index)
        }
        override fun close() {
            closes++
            onClose()
        }
    }

    private data class Harness(
        val engine: AndroidPdfReadEngine,
        val source: PdfReadSource,
        val backing: Backing,
        val buffers: MutableList<Buffer>,
    )

    private fun harness(
        document: NativePdfDocument,
        backing: Backing = Backing(),
        bufferFactory: () -> Buffer = { Buffer() },
    ): Harness {
        val buffers = mutableListOf<Buffer>()
        val handle = ReadHandle(
            backing,
            ReadProvenance.Provider(StorageDocumentRef("test", "test:owned")),
            seekable = true,
        )
        val engine = AndroidPdfReadEngine(limits, Dispatchers.Unconfined, { document }) { _, _ ->
            bufferFactory().also { buffers.add(it) }
        }
        return Harness(engine, PdfReadSource(handle), backing, buffers)
    }

    private fun page(session: PdfReadSession, index: Int = 0): ReadPageRef = ReadPageRef(
        session.info.id, session.info.sourceGeneration, PageIndex(index), session.info.revision,
    )

    private fun request(session: PdfReadSession, width: Int = 64, height: Int = 64): RenderRequest =
        RenderRequest(RenderRequestId("edge-${width}-${height}"), page(session), width, height, 0L)

    @Test fun openClassifiesAllExpectedNativeConstructorErrorsAndClosesBacking() = runBlocking {
        val cases = listOf(
            IOException("not PDF") to AppError.Validation.INVALID_DOCUMENT,
            SecurityException("encrypted") to AppError.Unsupported.DOCUMENT_FORMAT,
            IllegalArgumentException("nonseekable") to AppError.Access.PROVIDER_IO_FAILURE,
        )
        for ((failure, expected) in cases) {
            val backing = Backing()
            val handle = ReadHandle(
                backing,
                ReadProvenance.Provider(StorageDocumentRef("test", "test:unreadable")),
                true,
            )
            val engine = AndroidPdfReadEngine(limits, Dispatchers.Unconfined, { throw failure }) {
                    _, _ -> error("Must never allocate a buffer")
            }
            assertEquals(Outcome.Failure(expected), engine.open(PdfReadSource(handle)))
            assertEquals(1, backing.closes)
        }
    }

    @Test fun snapshotProvenanceIsPreservedInSessionAndRejectsMismatchedRevision() = runBlocking {
        val logical = DocumentRef(DocumentId("logical-score"))
        val snapshot = DocumentRevision.VerifiedSnapshot(
            logical, SnapshotEvidence("a".repeat(64), 15L),
        )
        val backing = Backing()
        val handle = ReadHandle(backing, ReadProvenance.SnapshotLease(snapshot), true)
        val engine = AndroidPdfReadEngine(limits, Dispatchers.Unconfined, { Document() }) {
                _, _ -> Buffer()
        }
        val session = (engine.open(PdfReadSource(handle, logical)) as Outcome.Success).value
        try {
            assertEquals(snapshot, session.info.revision)
            val bound = page(session)
            assertTrue(session.displayInfo(bound) is Outcome.Success)
            assertEquals(
                Outcome.Failure(AppError.Conflict.REVISION_CHANGED),
                session.displayInfo(bound.copy(revision = null)),
            )
        } finally {
            session.close()
        }
        assertEquals(1, backing.closes)
    }

    @Test fun heightAndPixelLimitsRejectWithoutAllocatingOrOpeningPages() = runBlocking {
        val document = Document()
        val h = harness(document)
        val session = (h.engine.open(h.source) as Outcome.Success).value
        try {
            assertEquals(
                Outcome.Failure(AppError.Resource.RENDER_LIMIT),
                session.render(request(session, 64, 129)),
            )
            assertEquals(
                Outcome.Failure(AppError.Resource.RENDER_LIMIT),
                session.render(request(session, 128, 128)),
            )
            assertEquals(0, document.opens)
            assertTrue(h.buffers.isEmpty())
        } finally {
            session.close()
        }
    }

    @Test fun invalidHeightInPageInfoReturnsValidationWithoutRetiringSession() = runBlocking {
        val invalid = Page(height = 0)
        val valid = Page()
        val document = Document(pageFactory = { if (it == 0) invalid else valid }, pageCount = 2)
        val h = harness(document)
        val session = (h.engine.open(h.source) as Outcome.Success).value
        try {
            assertEquals(
                Outcome.Failure(AppError.Validation.INVALID_DOCUMENT),
                session.displayInfo(page(session, 0)),
            )
            assertTrue(session.displayInfo(page(session, 1)) is Outcome.Success)
            assertEquals(1, invalid.closes)
            assertEquals(1, valid.closes)
        } finally {
            session.close()
        }
    }

    @Test fun invalidHeightInRenderClosesItsBufferAndKeepsSessionAvailable() = runBlocking {
        val invalid = Page(height = 0)
        val document = Document(pageFactory = { invalid })
        val h = harness(document)
        val session = (h.engine.open(h.source) as Outcome.Success).value
        try {
            assertEquals(
                Outcome.Failure(AppError.Validation.INVALID_DOCUMENT),
                session.render(request(session)),
            )
            assertEquals(0, invalid.renders)
            assertEquals(1, invalid.closes)
            assertEquals(1, h.buffers.single().closes)
            assertEquals(0, document.closes)
        } finally {
            session.close()
        }
    }

    @Test fun nativeIllegalStateWhileReadingPageRetiresSessionAndClosesBacking() = runBlocking {
        val document = Document(pageFactory = { throw IllegalStateException("renderer busy") })
        val h = harness(document)
        val session = (h.engine.open(h.source) as Outcome.Success).value
        val target = page(session)
        assertEquals(
            Outcome.Failure(AppError.Access.PROVIDER_IO_FAILURE),
            session.displayInfo(target),
        )
        assertEquals(1, document.closes)
        assertEquals(1, h.backing.closes)
        assertEquals(Outcome.Failure(AppError.Conflict.SESSION_CLOSED), session.displayInfo(target))
    }

    @Test fun nativeSecurityFailureInRenderClosesPageBufferAndSession() = runBlocking {
        val failedPage = Page(onRender = { throw SecurityException("permission revoked") })
        val document = Document(pageFactory = { failedPage })
        val h = harness(document)
        val session = (h.engine.open(h.source) as Outcome.Success).value
        assertEquals(
            Outcome.Failure(AppError.Access.PERMISSION_REVOKED),
            session.render(request(session)),
        )
        assertEquals(1, failedPage.closes)
        assertEquals(1, h.buffers.single().closes)
        assertEquals(1, document.closes)
        assertEquals(1, h.backing.closes)
        assertEquals(Outcome.Failure(AppError.Conflict.SESSION_CLOSED), session.render(request(session)))
    }

    @Test fun nativeIllegalStateInRenderClosesPageBufferAndSession() = runBlocking {
        val failedPage = Page(onRender = { throw IllegalStateException("bad native page") })
        val document = Document(pageFactory = { failedPage })
        val h = harness(document)
        val session = (h.engine.open(h.source) as Outcome.Success).value
        assertEquals(
            Outcome.Failure(AppError.Access.PROVIDER_IO_FAILURE),
            session.render(request(session)),
        )
        assertEquals(1, failedPage.closes)
        assertEquals(1, h.buffers.single().closes)
        assertEquals(1, document.closes)
        assertEquals(1, h.backing.closes)
    }

    @Test fun pageCloseIoFailureRetiresSessionDuringDimensionsRead() = runBlocking {
        val opened = Page(onClose = { throw IOException("close native page failed") })
        val document = Document(pageFactory = { opened })
        val h = harness(document)
        val session = (h.engine.open(h.source) as Outcome.Success).value
        assertEquals(
            Outcome.Failure(AppError.Access.PROVIDER_IO_FAILURE),
            session.displayInfo(page(session)),
        )
        assertEquals(1, opened.closes)
        assertEquals(1, document.closes)
        assertEquals(1, h.backing.closes)
    }

    @Test fun pageCloseIoFailureRetiresSessionDuringRenderAndDisposesBuffer() = runBlocking {
        val opened = Page(onClose = { throw IOException("close native page failed") })
        val document = Document(pageFactory = { opened })
        val h = harness(document)
        val session = (h.engine.open(h.source) as Outcome.Success).value
        assertEquals(
            Outcome.Failure(AppError.Access.PROVIDER_IO_FAILURE),
            session.render(request(session)),
        )
        assertEquals(1, opened.renders)
        assertEquals(1, opened.closes)
        assertEquals(1, h.buffers.single().closes)
        assertEquals(1, document.closes)
        assertEquals(1, h.backing.closes)
    }

    @Test fun bitmapCleanupFailureAfterNativeIoRetainsPrimaryAndSuppressedException() = runBlocking {
        val document = Document(pageFactory = {
            Page(onRender = { throw IOException("native render failure") })
        })
        val h = harness(document, bufferFactory = {
            Buffer(onClose = { throw IllegalStateException("bitmap cleanup failure") })
        })
        val session = (h.engine.open(h.source) as Outcome.Success).value
        try {
            session.render(request(session))
            fail("Original native failure must propagate when cleanup also fails")
        } catch (expected: IOException) {
            assertEquals("native render failure", expected.message)
            assertEquals(1, expected.suppressed.size)
            assertEquals("bitmap cleanup failure", expected.suppressed.single().message)
        }
        assertEquals(1, document.closes)
        assertEquals(1, h.backing.closes)
        assertEquals(1, h.buffers.single().closes)
    }

    @Test fun cancelledNativeRenderWithFailedBufferCleanupPreservesCancellation() = runBlocking {
        val document = Document(pageFactory = {
            Page(onRender = { throw CancellationException("cancel while rendering") })
        })
        val h = harness(document, bufferFactory = {
            Buffer(onClose = { throw IOException("bitmap release failed") })
        })
        val session = (h.engine.open(h.source) as Outcome.Success).value
        try {
            session.render(request(session))
            fail("Cancellation must propagate")
        } catch (expected: CancellationException) {
            assertEquals("cancel while rendering", expected.message)
            assertEquals(1, expected.suppressed.size)
            assertEquals("bitmap release failed", expected.suppressed.single().message)
        } finally {
            session.close()
        }
        assertEquals(1, h.backing.closes)
        assertEquals(1, h.buffers.single().closes)
    }

    @Test fun releasingBitmapAfterInvalidGeometryCanReportCleanupFailure() = runBlocking {
        val page = Page(width = 0)
        val document = Document(pageFactory = { page })
        val h = harness(document, bufferFactory = {
            Buffer(onClose = { throw IOException("bitmap release failed") })
        })
        val session = (h.engine.open(h.source) as Outcome.Success).value
        try {
            try {
                session.render(request(session))
                fail("Cleanup failure must not be hidden")
            } catch (expected: IOException) {
                assertEquals("bitmap release failed", expected.message)
            }
            assertEquals(1, page.closes)
            assertEquals(1, h.buffers.single().closes)
        } finally {
            session.close()
        }
    }

    @Test fun closePropagatesBackingReleaseFailureWithoutRepeatingCleanup() = runBlocking {
        val document = Document()
        val backing = Backing(onClose = { throw IOException("backing release failed") })
        val h = harness(document, backing = backing)
        val session = (h.engine.open(h.source) as Outcome.Success).value
        try {
            session.close()
            fail("Backing close failure must propagate")
        } catch (expected: IOException) {
            assertEquals("backing release failed", expected.message)
        }
        session.close()
        assertEquals(1, document.closes)
        assertEquals(1, backing.closes)
    }

    @Test fun failedOpenPreservesCancellationWhenBackingCleanupAlsoFails() = runBlocking {
        val backing = Backing(onClose = { throw IOException("backing close failed") })
        val handle = ReadHandle(
            backing,
            ReadProvenance.Provider(StorageDocumentRef("test", "test:cancel")),
            true,
        )
        val engine = AndroidPdfReadEngine(limits, Dispatchers.Unconfined, {
            throw CancellationException("opening cancelled")
        }) { _, _ -> Buffer() }
        try {
            engine.open(PdfReadSource(handle))
            fail("Cancellation must propagate")
        } catch (expected: CancellationException) {
            assertEquals("opening cancelled", expected.message)
            assertEquals(1, expected.suppressed.size)
            assertEquals("backing close failed", expected.suppressed.single().message)
        }
        assertEquals(1, backing.closes)
    }

    @Test fun closingBothNativeOwnersRetainsNativeFailureAndSuppressedBackingFailure() = runBlocking {
        val backing = Backing(onClose = { throw IOException("backing failure") })
        val document = Document(onClose = { throw IOException("renderer failure") })
        val h = harness(document, backing)
        val session = (h.engine.open(h.source) as Outcome.Success).value
        try {
            session.close()
            fail("Renderer cleanup failure must propagate")
        } catch (expected: IOException) {
            assertEquals("renderer failure", expected.message)
            assertEquals(1, expected.suppressed.size)
            assertEquals("backing failure", expected.suppressed.single().message)
        }
        assertEquals(1, document.closes)
        assertEquals(1, backing.closes)
    }

    @Test fun successfulBitmapLeaseAndSessionHaveIndependentCloseLifetimes() = runBlocking {
        val document = Document()
        val h = harness(document)
        val session = (h.engine.open(h.source) as Outcome.Success).value
        val req = request(session)
        val result = (session.render(req) as Outcome.Success).value
        assertEquals(req, result.request)
        assertEquals(page(session), result.displayInfo.page)
        session.close()
        assertEquals(0, h.buffers.single().closes)
        assertNotEquals(0, document.closes)
        result.close()
        result.close()
        assertEquals(1, h.buffers.single().closes)
    }
}
