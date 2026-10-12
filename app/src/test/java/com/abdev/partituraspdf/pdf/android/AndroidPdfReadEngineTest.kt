package com.abdev.partituraspdf.pdf.android

import android.os.ParcelFileDescriptor
import com.abdev.partituraspdf.core.document.PageIndex
import com.abdev.partituraspdf.core.document.StorageDocumentRef
import com.abdev.partituraspdf.core.result.AppError
import com.abdev.partituraspdf.core.result.Outcome
import com.abdev.partituraspdf.pdf.api.PdfReadSource
import com.abdev.partituraspdf.pdf.api.ReadPageRef
import com.abdev.partituraspdf.pdf.api.ReadSessionId
import com.abdev.partituraspdf.pdf.api.RenderLimits
import com.abdev.partituraspdf.pdf.api.RenderRequest
import com.abdev.partituraspdf.pdf.api.RenderRequestId
import com.abdev.partituraspdf.storage.api.ReadHandle
import com.abdev.partituraspdf.storage.api.ReadOwnership
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

/** Pure logic tests; B2 requires separate real API 26 tests with canonical PDF fixtures. */
class AndroidPdfReadEngineTest {
    private val limits = RenderLimits(maxDimension = 1024, maxPixels = 1024L * 1024L)

    private class Resource : ReadResource {
        var closes = 0
        // Fake native opener does not access Android's ParcelFileDescriptor on the JVM.
        override val descriptor: ParcelFileDescriptor
            get() = error("A real descriptor is required for native Android tests")
        override fun close() { closes++ }
    }

    private class Page(
        override val width: Int = 600,
        override val height: Int = 800,
    ) : NativePdfPage {
        var closes = 0
        override fun close() { closes++ }
    }

    private class Document(override val pageCount: Int = 2) : NativePdfDocument {
        var closes = 0
        val indices = mutableListOf<Int>()
        val pages = mutableListOf<Page>()
        var onClose: (() -> Unit)? = null
        override fun openPage(index: Int): NativePdfPage {
            indices += index
            return Page().also { pages += it }
        }
        override fun close() { closes++; onClose?.invoke() }
    }

    private fun source(): Pair<PdfReadSource, Resource> {
        val resource = Resource()
        val handle = ReadHandle(
            resource,
            ReadProvenance.Provider(StorageDocumentRef("test", "test:canonical")),
            seekable = true,
        )
        return PdfReadSource(handle) to resource
    }

    private fun engine(opener: (ReadHandle) -> NativePdfDocument) =
        AndroidPdfReadEngine(limits, Dispatchers.Unconfined, opener)

    @Test fun opensAndReadsPageDimensionsWithoutRenderingOrGeneratingPdfs() = runBlocking {
        val (source, resource) = source()
        val native = Document(pageCount = 3)
        val opened = engine { native }.open(source)
        assertTrue(opened is Outcome.Success)
        val session = (opened as Outcome.Success).value
        assertEquals(ReadOwnership.TRANSFERRED, source.handle.ownership)
        assertEquals(3, session.info.pageCount)
        assertEquals(limits, session.renderLimits)
        val page = ReadPageRef(session.info.id, session.info.sourceGeneration, PageIndex(2))
        assertEquals(
            Outcome.Success(com.abdev.partituraspdf.pdf.api.PageDisplayInfo(page, 600, 800)),
            session.displayInfo(page),
        )
        assertEquals(listOf(2), native.indices)
        assertEquals(1, native.pages.single().closes)
        assertEquals(0, native.closes)
        assertEquals(0, resource.closes)
        session.close()
        session.close()
        assertEquals(1, native.closes)
        assertEquals(1, resource.closes)
    }

    @Test fun multipleOpenSessionsGetDistinctTransientIds() = runBlocking {
        val (first, _) = source()
        val (second, _) = source()
        val a = (engine { Document() }.open(first) as Outcome.Success).value
        val b = (engine { Document() }.open(second) as Outcome.Success).value
        try { assertNotEquals(a.info.id, b.info.id) } finally { a.close(); b.close() }
    }

    @Test fun staleSessionAndOutOfBoundsNeverReachTheNativePage() = runBlocking {
        val (source, _) = source()
        val native = Document()
        val session = (engine { native }.open(source) as Outcome.Success).value
        try {
            val good = ReadPageRef(session.info.id, 0L, PageIndex(0))
            assertEquals(
                Outcome.Failure(AppError.Conflict.STALE_SESSION),
                session.displayInfo(good.copy(session = ReadSessionId("other"))),
            )
            assertEquals(
                Outcome.Failure(AppError.Conflict.STALE_SESSION),
                session.displayInfo(good.copy(sourceGeneration = 1L)),
            )
            assertEquals(
                Outcome.Failure(AppError.Validation.INVALID_PAGE),
                session.displayInfo(good.copy(page = PageIndex(2))),
            )
            assertTrue(native.indices.isEmpty())
        } finally { session.close() }
    }

    @Test fun unsupportedRenderStillValidatesPageAndLimits() = runBlocking {
        val (source, _) = source()
        val native = Document()
        val session = (engine { native }.open(source) as Outcome.Success).value
        try {
            val good = ReadPageRef(session.info.id, 0L, PageIndex(0))
            val request = RenderRequest(RenderRequestId("render-1"), good, 100, 150, 0L)
            assertEquals(
                Outcome.Failure(AppError.Unsupported.OPERATION), session.render(request),
            )
            assertEquals(
                Outcome.Failure(AppError.Resource.RENDER_LIMIT),
                session.render(request.copy(width = 1025)),
            )
            assertEquals(
                Outcome.Failure(AppError.Conflict.STALE_SESSION),
                session.render(request.copy(page = good.copy(sourceGeneration = 7L))),
            )
            assertTrue(native.indices.isEmpty())
        } finally { session.close() }
    }

    @Test fun invalidNativePageSizeReturnsTypedFailureAndClosesPage() = runBlocking {
        val (source, _) = source()
        val bad = object : NativePdfDocument {
            val page = Page(width = 0)
            override val pageCount = 1
            override fun openPage(index: Int): NativePdfPage = page
            override fun close() {}
        }
        val session = (engine { bad }.open(source) as Outcome.Success).value
        try {
            val page = ReadPageRef(session.info.id, 0L, PageIndex(0))
            assertEquals(
                Outcome.Failure(AppError.Validation.INVALID_DOCUMENT), session.displayInfo(page),
            )
            assertEquals(1, bad.page.closes)
        } finally { session.close() }
    }

    @Test fun closeRejectsFurtherWorkAndReleasesOwnersOnce() = runBlocking {
        val (source, resource) = source()
        val native = Document()
        val session = (engine { native }.open(source) as Outcome.Success).value
        session.close()
        session.close()
        val page = ReadPageRef(session.info.id, 0L, PageIndex(0))
        assertEquals(
            Outcome.Failure(AppError.Conflict.SESSION_CLOSED), session.displayInfo(page),
        )
        assertEquals(1, native.closes)
        assertEquals(1, resource.closes)
        assertTrue(native.indices.isEmpty())
    }

    @Test fun invalidDescriptorFailureIsNotMisreportedAsCorruptInput() = runBlocking {
        val (source, resource) = source()
        assertEquals(
            Outcome.Failure(AppError.Access.PROVIDER_IO_FAILURE),
            engine { throw IllegalArgumentException("not seekable") }.open(source),
        )
        assertEquals(1, resource.closes)
    }

    @Test fun nativePageIoErrorIsTypedAndNoSessionResourcesAreLeaked() = runBlocking {
        val (source, resource) = source()
        val native = object : NativePdfDocument {
            override val pageCount = 1
            override fun openPage(index: Int): NativePdfPage = throw IOException("failed to read page")
            override fun close() {}
        }
        val session = (engine { native }.open(source) as Outcome.Success).value
        val page = ReadPageRef(session.info.id, 0L, PageIndex(0))
        try {
            assertEquals(
                Outcome.Failure(AppError.Access.PROVIDER_IO_FAILURE),
                session.displayInfo(page),
            )
        } finally { session.close() }
        assertEquals(1, resource.closes)
    }

    @Test fun closedSessionRejectsRenderBeforeLookingAtRequest() = runBlocking {
        val (source, _) = source()
        val session = (engine { Document() }.open(source) as Outcome.Success).value
        session.close()
        val page = ReadPageRef(session.info.id, 0L, PageIndex(0))
        val request = RenderRequest(RenderRequestId("r"), page, 80, 100, 0L)
        assertEquals(
            Outcome.Failure(AppError.Conflict.SESSION_CLOSED),
            session.render(request),
        )
    }

    @Test fun corruptPdfOpenFailureReleasesOwnedHandle() = runBlocking {
        val (source, resource) = source()
        val result = engine { throw IOException("synthetic native open failure") }.open(source)
        assertEquals(Outcome.Failure(AppError.Validation.INVALID_DOCUMENT), result)
        assertEquals(ReadOwnership.TRANSFERRED, source.handle.ownership)
        assertEquals(1, resource.closes)
    }

    @Test fun unsupportedNativeSecurityFailureReleasesOwnedHandle() = runBlocking {
        val (source, resource) = source()
        assertEquals(
            Outcome.Failure(AppError.Unsupported.DOCUMENT_FORMAT),
            engine { throw SecurityException("synthetic security restriction") }.open(source),
        )
        assertEquals(1, resource.closes)
    }

    @Test fun zeroPageNativeDocumentIsRejectedAndBothOwnersClose() = runBlocking {
        val (source, resource) = source()
        val native = Document(pageCount = 0)
        assertEquals(
            Outcome.Failure(AppError.Validation.INVALID_DOCUMENT),
            engine { native }.open(source),
        )
        assertEquals(1, native.closes)
        assertEquals(1, resource.closes)
    }

    @Test fun cancelledOpenPropagatesAndReleasesOwnedHandle() = runBlocking {
        val (source, resource) = source()
        try {
            engine { throw CancellationException("cancelled") }.open(source)
            fail("Cancellation must propagate")
        } catch (_: CancellationException) {
            assertEquals(1, resource.closes)
        }
    }

    @Test fun nativeCleanupFailureStillClosesBackingHandle() = runBlocking {
        val (source, resource) = source()
        val native = Document().apply { onClose = { throw IOException("native close failed") } }
        val session = (engine { native }.open(source) as Outcome.Success).value
        try {
            session.close()
            fail("Expected the native close failure")
        } catch (expected: IOException) {
            assertEquals("native close failed", expected.message)
            assertEquals(1, resource.closes)
        }
    }
}
