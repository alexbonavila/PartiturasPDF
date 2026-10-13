package com.abdev.partituraspdf.pdf.android

import android.graphics.Bitmap
import android.os.ParcelFileDescriptor
import com.abdev.partituraspdf.core.document.PageIndex
import com.abdev.partituraspdf.core.document.StorageDocumentRef
import com.abdev.partituraspdf.core.result.AppError
import com.abdev.partituraspdf.core.result.Outcome
import com.abdev.partituraspdf.pdf.api.PdfReadSession
import com.abdev.partituraspdf.pdf.api.PdfReadSource
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
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** Additional real error-path coverage, using native seams without creating PDF files. */
class AndroidPdfCoverageFinalGapsTest {
    private val limits = RenderLimits(maxDimension = 256, maxPixels = 256L * 256L)

    private class Backing(private val onClose: () -> Unit = {}) : ReadResource {
        var closes = 0
        override val descriptor: ParcelFileDescriptor
            get() = error("JVM tests must never open native platform descriptors")
        override fun close() {
            closes++
            onClose()
        }
    }

    private class Buffer(private val onClose: () -> Unit = {}) : RenderBuffer {
        var closes = 0
        override val bitmap: Bitmap
            get() = error("JVM tests must never allocate platform bitmaps")
        override fun close() {
            closes++
            onClose()
        }
    }

    private class Page(
        private val renderAction: () -> Unit = {},
        private val closeAction: () -> Unit = {},
    ) : NativePdfPage {
        var closes = 0
        override val width = 300
        override val height = 420
        override fun renderInto(buffer: RenderBuffer) = renderAction()
        override fun close() {
            closes++
            closeAction()
        }
    }

    private class Document(
        override val pageCount: Int = 1,
        private val pageFactory: () -> NativePdfPage = { Page() },
        private val closeAction: () -> Unit = {},
    ) : NativePdfDocument {
        var closes = 0
        var opens = 0
        override fun openPage(index: Int): NativePdfPage {
            opens++
            return pageFactory()
        }
        override fun close() {
            closes++
            closeAction()
        }
    }

    private fun source(backing: Backing): PdfReadSource = PdfReadSource(
        ReadHandle(backing, ReadProvenance.Provider(StorageDocumentRef("test", "test:edge")), true),
    )

    private fun engine(
        opener: (ReadHandle) -> NativePdfDocument,
        bufferFactory: () -> RenderBuffer = { Buffer() },
    ) = AndroidPdfReadEngine(limits, Dispatchers.Unconfined, opener) { _, _ -> bufferFactory() }

    private fun request(session: PdfReadSession): RenderRequest {
        val ref = ReadPageRef(session.info.id, session.info.sourceGeneration, PageIndex(0))
        return RenderRequest(RenderRequestId("extra-coverage"), ref, 64, 64, 0L)
    }

    @Test fun zeroPageNativeDocumentWithFailingCloseStillReleasesBacking() = runBlocking {
        val backing = Backing()
        val native = Document(pageCount = 0, closeAction = { throw IOException("renderer cleanup") })
        try {
            engine({ native }).open(source(backing))
            fail("Failed resource cleanup must be observable")
        } catch (expected: IOException) {
            assertEquals("renderer cleanup", expected.message)
        }
        assertEquals(1, native.closes)
        assertEquals(1, backing.closes)
    }

    @Test fun rejectedConstructorWithFailingBackingClosePropagatesCleanupFailure() = runBlocking {
        val backing = Backing { throw IOException("backing cleanup") }
        try {
            engine({ throw IOException("invalid input") }).open(source(backing))
            fail("Close errors must not disappear behind a typed failure")
        } catch (expected: IOException) {
            assertEquals("backing cleanup", expected.message)
        }
        assertEquals(1, backing.closes)
    }

    @Test fun unexpectedConstructorDefectPreservesPrimaryWhenOwnerCleanupFails() = runBlocking {
        val backing = Backing { throw IOException("failed resource close") }
        try {
            engine({ throw UnsupportedOperationException("programming defect") }).open(source(backing))
            fail("Unexpected exceptions must propagate")
        } catch (expected: UnsupportedOperationException) {
            assertEquals("programming defect", expected.message)
            assertEquals(1, expected.suppressed.size)
            assertEquals("failed resource close", expected.suppressed.single().message)
        }
        assertEquals(1, backing.closes)
    }

    @Test fun unexpectedNativeRenderDefectPropagatesAndReleasesTemporaryBuffer() = runBlocking {
        val nativePage = Page(renderAction = { throw UnsupportedOperationException("render defect") })
        val native = Document(pageFactory = { nativePage })
        val backing = Backing()
        val buffer = Buffer()
        val session = (engine({ native }, { buffer }).open(source(backing)) as Outcome.Success).value
        try {
            try {
                session.render(request(session))
                fail("Unexpected native exception must escape")
            } catch (expected: UnsupportedOperationException) {
                assertEquals("render defect", expected.message)
            }
            assertEquals(1, nativePage.closes)
            assertEquals(1, buffer.closes)
            // Programming defects do not silently become a provider failure.
            assertEquals(0, native.closes)
        } finally {
            session.close()
        }
        assertEquals(1, backing.closes)
    }

    @Test fun allocationProgrammingDefectPropagatesWithoutOpeningPage() = runBlocking {
        val native = Document()
        val backing = Backing()
        val session = (engine({ native }, { throw IllegalArgumentException("allocator defect") })
            .open(source(backing)) as Outcome.Success).value
        try {
            try {
                session.render(request(session))
                fail("Only allocation OOM should map to RENDER_LIMIT")
            } catch (expected: IllegalArgumentException) {
                assertEquals("allocator defect", expected.message)
            }
            assertEquals(0, native.opens)
        } finally {
            session.close()
        }
        assertEquals(1, backing.closes)
    }

    @Test fun nativeRenderOutOfMemoryPropagatesInsteadOfMasqueradingAsAllocationLimit() = runBlocking {
        val nativePage = Page(renderAction = { throw OutOfMemoryError("native rendering OOM") })
        val native = Document(pageFactory = { nativePage })
        val backing = Backing()
        val buffer = Buffer()
        val session = (engine({ native }, { buffer }).open(source(backing)) as Outcome.Success).value
        try {
            try {
                session.render(request(session))
                fail("Native OOM must propagate")
            } catch (expected: OutOfMemoryError) {
                assertEquals("native rendering OOM", expected.message)
            }
            assertEquals(1, nativePage.closes)
            assertEquals(1, buffer.closes)
        } finally {
            session.close()
        }
        assertEquals(1, native.closes)
        assertEquals(1, backing.closes)
    }

    @Test fun nativeStateFailureWhileReadingPageRetiresSession() = runBlocking {
        val native = Document(pageFactory = { throw IllegalStateException("renderer unusable") })
        val backing = Backing()
        val session = (engine({ native }).open(source(backing)) as Outcome.Success).value
        val ref = request(session).page
        assertEquals(
            Outcome.Failure(AppError.Access.PROVIDER_IO_FAILURE),
            session.displayInfo(ref),
        )
        assertEquals(1, native.closes)
        assertEquals(1, backing.closes)
        assertEquals(Outcome.Failure(AppError.Conflict.SESSION_CLOSED), session.displayInfo(ref))
        session.close()
    }

    @Test fun bitmapLeaseAccessBeforeReleaseForwardsBufferExceptionAndReleaseIsIdempotent() = runBlocking {
        val native = Document()
        val backing = Backing()
        val buffer = Buffer()
        val session = (engine({ native }, { buffer }).open(source(backing)) as Outcome.Success).value
        val rendered = (session.render(request(session)) as Outcome.Success).value
        try {
            try {
                rendered.bitmap
                fail("Pure JVM fake deliberately cannot expose Android Bitmap")
            } catch (expected: IllegalStateException) {
                assertTrue(expected.message!!.contains("never allocate platform bitmaps"))
            }
        } finally {
            session.close()
            rendered.close()
            rendered.close()
        }
        assertEquals(1, buffer.closes)
        assertEquals(1, native.closes)
        assertEquals(1, backing.closes)
    }

    @Test fun cancelledNativePageClosePropagatesAndDoesNotRetireHealthySession() = runBlocking {
        var pageOpens = 0
        val cancelledPage = Page(closeAction = { throw CancellationException("cancel while closing page") })
        val native = Document(pageFactory = {
            pageOpens++
            if (pageOpens == 1) cancelledPage else Page()
        })
        val backing = Backing()
        val session = (engine({ native }).open(source(backing)) as Outcome.Success).value
        val ref = request(session).page
        try {
            try {
                session.displayInfo(ref)
                fail("Native cancellation must remain cancellation")
            } catch (expected: CancellationException) {
                assertEquals("cancel while closing page", expected.message)
            }
            assertTrue(session.displayInfo(ref) is Outcome.Success)
            assertEquals(2, pageOpens)
        } finally {
            session.close()
        }
        assertEquals(1, backing.closes)
    }

    @Test fun failureDuringRetirementStillClosesBackingAndDisposesBuffer() = runBlocking {
        val nativePage = Page(renderAction = { throw IOException("page IO") })
        val native = Document(pageFactory = { nativePage }, closeAction = { throw IOException("renderer close") })
        val backing = Backing()
        val buffer = Buffer()
        val session = (engine({ native }, { buffer }).open(source(backing)) as Outcome.Success).value
        try {
            session.render(request(session))
            fail("Retirement close failure must propagate")
        } catch (expected: IOException) {
            assertEquals("renderer close", expected.message)
        }
        assertEquals(1, native.closes)
        assertEquals(1, backing.closes)
        assertEquals(1, nativePage.closes)
        assertEquals(1, buffer.closes)
        session.close()
    }
}
