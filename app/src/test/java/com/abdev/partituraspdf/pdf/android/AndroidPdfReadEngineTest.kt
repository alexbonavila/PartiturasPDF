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
        var renders = 0
        var renderAction: (() -> Unit)? = null
        override fun renderInto(buffer: RenderBuffer) {
            renders++
            renderAction?.invoke()
        }
        override fun close() { closes++ }
    }

    private class FakeBuffer : RenderBuffer {
        var closes = 0
        // Android graphics are unavailable in ordinary JVM tests.
        override val bitmap: android.graphics.Bitmap
            get() = error("A real bitmap is required only for native instrumentation")
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
        AndroidPdfReadEngine(limits, Dispatchers.Unconfined, opener) { _, _ -> FakeBuffer() }

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

    @Test fun renderValidatesPageAndLimits() = runBlocking {
        val (source, _) = source()
        val native = Document()
        val session = (engine { native }.open(source) as Outcome.Success).value
        try {
            val good = ReadPageRef(session.info.id, 0L, PageIndex(0))
            val request = RenderRequest(RenderRequestId("render-1"), good, 100, 150, 0L)
            val rendered = (session.render(request) as Outcome.Success).value
            assertEquals(request, rendered.request)
            assertEquals(100, rendered.request.width)
            rendered.close()
            assertEquals(
                Outcome.Failure(AppError.Resource.RENDER_LIMIT),
                session.render(request.copy(width = 1025)),
            )
            assertEquals(
                Outcome.Failure(AppError.Conflict.STALE_SESSION),
                session.render(request.copy(page = good.copy(sourceGeneration = 7L))),
            )
            assertEquals(listOf(0), native.indices)
            assertEquals(1, native.pages.single().renders)
            assertEquals(1, native.pages.single().closes)
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

    @Test fun renderClosesPageButBitmapRemainsOwnedUntilLeaseClose() = runBlocking {
        val (source, resource) = source()
        val native = Document(pageCount = 1)
        val buffers = mutableListOf<FakeBuffer>()
        val subject = AndroidPdfReadEngine(limits, Dispatchers.Unconfined, { native }) { _, _ ->
            FakeBuffer().also { buffers += it }
        }
        val session = (subject.open(source) as Outcome.Success).value
        val page = ReadPageRef(session.info.id, 0L, PageIndex(0))
        val request = RenderRequest(RenderRequestId("lease"), page, 200, 250, 0L)
        val rendered = (session.render(request) as Outcome.Success).value
        assertEquals(page, rendered.displayInfo.page)
        assertEquals(600, rendered.displayInfo.width)
        assertEquals(800, rendered.displayInfo.height)
        assertEquals(1, native.pages.single().renders)
        assertEquals(1, native.pages.single().closes)
        assertEquals(0, buffers.single().closes)
        session.close()
        assertEquals(1, resource.closes)
        assertEquals(0, buffers.single().closes)
        rendered.close()
        rendered.close()
        assertEquals(1, buffers.single().closes)
        try {
            rendered.bitmap
            fail("Closed bitmap access must be rejected")
        } catch (_: IllegalStateException) { }
    }

    @Test fun rejectedRenderDoesNotAllocateOrOpenNativePage() = runBlocking {
        val (source, _) = source()
        val native = Document()
        var allocations = 0
        val subject = AndroidPdfReadEngine(limits, Dispatchers.Unconfined, { native }) { _, _ ->
            allocations++
            FakeBuffer()
        }
        val session = (subject.open(source) as Outcome.Success).value
        try {
            val good = ReadPageRef(session.info.id, 0L, PageIndex(0))
            val request = RenderRequest(RenderRequestId("limit"), good, 1025, 100, 0L)
            assertEquals(Outcome.Failure(AppError.Resource.RENDER_LIMIT), session.render(request))
            assertEquals(
                Outcome.Failure(AppError.Conflict.STALE_SESSION),
                session.render(request.copy(page = good.copy(sourceGeneration = 1L))),
            )
            assertEquals(0, allocations)
            assertTrue(native.indices.isEmpty())
        } finally { session.close() }
    }

    @Test fun bitmapAllocationOutOfMemoryReturnsTypedFailureWithoutOpeningPage() = runBlocking {
        val (source, _) = source()
        val native = Document()
        val subject = AndroidPdfReadEngine(limits, Dispatchers.Unconfined, { native }) { _, _ ->
            throw OutOfMemoryError("fake bitmap allocation")
        }
        val session = (subject.open(source) as Outcome.Success).value
        try {
            val page = ReadPageRef(session.info.id, 0L, PageIndex(0))
            val request = RenderRequest(RenderRequestId("oom"), page, 64, 64, 0L)
            assertEquals(Outcome.Failure(AppError.Resource.RENDER_LIMIT), session.render(request))
            assertTrue(native.indices.isEmpty())
        } finally { session.close() }
    }

    @Test fun nativeRenderFailureClosesPageAndBitmapAndReturnsTypedError() = runBlocking {
        val (source, _) = source()
        val fake = object : NativePdfDocument {
            override val pageCount = 1
            val opened = Page().apply { renderAction = { throw IOException("native failure") } }
            override fun openPage(index: Int): NativePdfPage = opened
            override fun close() {}
        }
        val buffers = mutableListOf<FakeBuffer>()
        val subject = AndroidPdfReadEngine(limits, Dispatchers.Unconfined, { fake }) { _, _ ->
            FakeBuffer().also { buffers += it }
        }
        val session = (subject.open(source) as Outcome.Success).value
        try {
            val page = ReadPageRef(session.info.id, 0L, PageIndex(0))
            val request = RenderRequest(RenderRequestId("failure"), page, 100, 150, 0L)
            assertEquals(
                Outcome.Failure(AppError.Access.PROVIDER_IO_FAILURE),
                session.render(request),
            )
            assertEquals(1, fake.opened.closes)
            assertEquals(1, buffers.single().closes)
        } finally { session.close() }
    }

    @Test fun nativeInvalidPageDimensionsReleaseBitmap() = runBlocking {
        val (source, _) = source()
        val fake = object : NativePdfDocument {
            override val pageCount = 1
            val opened = Page(width = 0)
            override fun openPage(index: Int): NativePdfPage = opened
            override fun close() {}
        }
        val buffers = mutableListOf<FakeBuffer>()
        val subject = AndroidPdfReadEngine(limits, Dispatchers.Unconfined, { fake }) { _, _ ->
            FakeBuffer().also { buffers += it }
        }
        val session = (subject.open(source) as Outcome.Success).value
        try {
            val page = ReadPageRef(session.info.id, 0L, PageIndex(0))
            assertEquals(
                Outcome.Failure(AppError.Validation.INVALID_DOCUMENT),
                session.render(RenderRequest(RenderRequestId("bad-page"), page, 64, 64, 0L)),
            )
            assertEquals(1, fake.opened.closes)
            assertEquals(0, fake.opened.renders)
            assertEquals(1, buffers.single().closes)
        } finally { session.close() }
    }

    @Test fun cancellationDuringNativeRenderReleasesBitmapAndNativePage() = runBlocking {
        val (source, _) = source()
        val fake = object : NativePdfDocument {
            override val pageCount = 1
            val opened = Page().apply {
                renderAction = { throw CancellationException("render cancelled") }
            }
            override fun openPage(index: Int): NativePdfPage = opened
            override fun close() {}
        }
        val buffers = mutableListOf<FakeBuffer>()
        val subject = AndroidPdfReadEngine(limits, Dispatchers.Unconfined, { fake }) { _, _ ->
            FakeBuffer().also { buffers += it }
        }
        val session = (subject.open(source) as Outcome.Success).value
        try {
            val page = ReadPageRef(session.info.id, 0L, PageIndex(0))
            try {
                session.render(RenderRequest(RenderRequestId("cancel"), page, 64, 64, 0L))
                fail("Cancellation must propagate")
            } catch (_: CancellationException) {
                assertEquals(1, fake.opened.closes)
                assertEquals(1, buffers.single().closes)
            }
        } finally { session.close() }
    }

    @Test fun cancellationDuringDisplayInfoIsNotSwallowedAsProviderIo() = runBlocking {
        val (source, _) = source()
        val fake = object : NativePdfDocument {
            override val pageCount = 1
            override fun openPage(index: Int): NativePdfPage =
                throw CancellationException("cancelled while reading page")
            override fun close() {}
        }
        val session = (engine { fake }.open(source) as Outcome.Success).value
        try {
            val page = ReadPageRef(session.info.id, 0L, PageIndex(0))
            try {
                session.displayInfo(page)
                fail("Cancellation must propagate")
            } catch (_: CancellationException) { }
        } finally { session.close() }
    }

    @Test fun bitmapIsReleasedIfOpeningNativePageFails() = runBlocking {
        val (source, _) = source()
        val fake = object : NativePdfDocument {
            override val pageCount = 1
            override fun openPage(index: Int): NativePdfPage = throw IOException("page access failed")
            override fun close() {}
        }
        val buffers = mutableListOf<FakeBuffer>()
        val subject = AndroidPdfReadEngine(limits, Dispatchers.Unconfined, { fake }) { _, _ ->
            FakeBuffer().also { buffers += it }
        }
        val session = (subject.open(source) as Outcome.Success).value
        try {
            val page = ReadPageRef(session.info.id, 0L, PageIndex(0))
            assertEquals(
                Outcome.Failure(AppError.Access.PROVIDER_IO_FAILURE),
                session.render(RenderRequest(RenderRequestId("native-open"), page, 64, 64, 0L)),
            )
            assertEquals(1, buffers.single().closes)
        } finally { session.close() }
    }


    @Test fun consecutiveRendersClosePagesAndPreserveIndependentBitmapLeases() = runBlocking {
        val (source, _) = source()
        val native = Document(pageCount = 2)
        val buffers = mutableListOf<FakeBuffer>()
        val subject = AndroidPdfReadEngine(limits, Dispatchers.Unconfined, { native }) { _, _ ->
            FakeBuffer().also { buffers += it }
        }
        val session = (subject.open(source) as Outcome.Success).value
        try {
            val first = ReadPageRef(session.info.id, 0L, PageIndex(0))
            val second = first.copy(page = PageIndex(1))
            val a = (session.render(RenderRequest(RenderRequestId("a"), first, 64, 64, 0L)) as Outcome.Success).value
            val b = (session.render(RenderRequest(RenderRequestId("b"), second, 64, 64, 0L)) as Outcome.Success).value
            assertEquals(listOf(0, 1), native.indices)
            assertTrue(native.pages.all { it.closes == 1 && it.renders == 1 })
            assertTrue(buffers.all { it.closes == 0 })
            a.close()
            assertEquals(1, buffers[0].closes)
            assertEquals(0, buffers[1].closes)
            b.close()
            assertEquals(1, buffers[1].closes)
        } finally { session.close() }
    }
    @Test fun pageCountReadFailureIsTypedAndClosesBothOwners() = runBlocking {
        val cases = listOf(
            IOException("page-count IO") to AppError.Access.PROVIDER_IO_FAILURE,
            SecurityException("permission revoked") to AppError.Access.PERMISSION_REVOKED,
            IllegalStateException("native renderer invalid") to AppError.Access.PROVIDER_IO_FAILURE,
        )
        for ((failure, expected) in cases) {
            val (source, resource) = source()
            var nativeCloses = 0
            val native = object : NativePdfDocument {
                override val pageCount: Int get() = throw failure
                override fun openPage(index: Int): NativePdfPage = error("never reached")
                override fun close() { nativeCloses++ }
            }
            assertEquals(Outcome.Failure(expected), engine { native }.open(source))
            assertEquals(1, nativeCloses)
            assertEquals(1, resource.closes)
        }
    }

    @Test fun pageInfoIoFailureRetiresSessionAndFreshOpenRecovers() = runBlocking {
        val (brokenSource, brokenResource) = source()
        var nativeCloses = 0
        val brokenNative = object : NativePdfDocument {
            override val pageCount = 1
            override fun openPage(index: Int): NativePdfPage = throw IOException("read failed")
            override fun close() { nativeCloses++ }
        }
        val brokenSession = (engine { brokenNative }.open(brokenSource) as Outcome.Success).value
        val oldPage = ReadPageRef(brokenSession.info.id, 0L, PageIndex(0))
        assertEquals(
            Outcome.Failure(AppError.Access.PROVIDER_IO_FAILURE),
            brokenSession.displayInfo(oldPage),
        )
        assertEquals(1, nativeCloses)
        assertEquals(1, brokenResource.closes)
        assertEquals(
            Outcome.Failure(AppError.Conflict.SESSION_CLOSED),
            brokenSession.displayInfo(oldPage),
        )
        brokenSession.close()

        // Recovery means opening a NEW session, not reusing compromised native state.
        val (freshSource, freshResource) = source()
        val fresh = (engine { Document(pageCount = 1) }.open(freshSource) as Outcome.Success).value
        try {
            assertNotEquals(brokenSession.info.id, fresh.info.id)
            val newPage = ReadPageRef(fresh.info.id, 0L, PageIndex(0))
            assertTrue(fresh.displayInfo(newPage) is Outcome.Success)
        } finally {
            fresh.close()
        }
        assertEquals(1, freshResource.closes)
    }

    @Test fun pageInfoPermissionFailureRetiresSession() = runBlocking {
        val (source, resource) = source()
        var nativeCloses = 0
        val native = object : NativePdfDocument {
            override val pageCount = 1
            override fun openPage(index: Int): NativePdfPage = throw SecurityException("revoked")
            override fun close() { nativeCloses++ }
        }
        val session = (engine { native }.open(source) as Outcome.Success).value
        val page = ReadPageRef(session.info.id, 0L, PageIndex(0))
        assertEquals(
            Outcome.Failure(AppError.Access.PERMISSION_REVOKED),
            session.displayInfo(page),
        )
        assertEquals(1, nativeCloses)
        assertEquals(1, resource.closes)
        assertEquals(
            Outcome.Failure(AppError.Conflict.SESSION_CLOSED),
            session.displayInfo(page),
        )
    }

    @Test fun renderIoFailureRetiresSessionAndReleasesBitmap() = runBlocking {
        val (source, resource) = source()
        val buffers = mutableListOf<FakeBuffer>()
        var nativeCloses = 0
        var pageCloses = 0
        val native = object : NativePdfDocument {
            override val pageCount = 1
            override fun openPage(index: Int): NativePdfPage = object : NativePdfPage {
                override val width = 600
                override val height = 800
                override fun renderInto(buffer: RenderBuffer) { throw IOException("render IO") }
                override fun close() { pageCloses++ }
            }
            override fun close() { nativeCloses++ }
        }
        val subject = AndroidPdfReadEngine(limits, Dispatchers.Unconfined, { native }) { _, _ ->
            FakeBuffer().also { buffers += it }
        }
        val session = (subject.open(source) as Outcome.Success).value
        val page = ReadPageRef(session.info.id, 0L, PageIndex(0))
        val request = RenderRequest(RenderRequestId("io"), page, 64, 64, 0L)
        assertEquals(
            Outcome.Failure(AppError.Access.PROVIDER_IO_FAILURE),
            session.render(request),
        )
        assertEquals(1, pageCloses)
        assertEquals(1, nativeCloses)
        assertEquals(1, resource.closes)
        assertEquals(1, buffers.single().closes)
        assertEquals(
            Outcome.Failure(AppError.Conflict.SESSION_CLOSED),
            session.render(request),
        )
        session.close()
    }

    @Test fun unexpectedPageCountDefectPreservesPrimaryWhenBothClosersFail() = runBlocking {
        var ownerCloses = 0
        val resource = object : ReadResource {
            override val descriptor: ParcelFileDescriptor
                get() = error("native fake does not require a descriptor")
            override fun close() {
                ownerCloses++
                throw IOException("owner close failed")
            }
        }
        val handle = ReadHandle(
            resource,
            ReadProvenance.Provider(StorageDocumentRef("test", "test:static")),
            seekable = true,
        )
        val native = object : NativePdfDocument {
            override val pageCount: Int get() = throw IllegalArgumentException("programmer failure")
            override fun openPage(index: Int): NativePdfPage = error("never reached")
            override fun close() { throw IOException("native close failed") }
        }
        try {
            engine { native }.open(PdfReadSource(handle))
            fail("Expected unhandled programming error")
        } catch (expected: IllegalArgumentException) {
            assertEquals("programmer failure", expected.message)
            assertEquals(1, expected.suppressed.size)
            assertEquals("native close failed", expected.suppressed.single().message)
            assertEquals("owner close failed", expected.suppressed.single().suppressed.single().message)
            assertEquals(1, ownerCloses)
        }
    }
}
