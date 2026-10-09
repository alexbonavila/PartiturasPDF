package com.abdev.partituraspdf.pdf.api

import android.graphics.Bitmap
import android.os.ParcelFileDescriptor
import com.abdev.partituraspdf.core.document.DocumentId
import com.abdev.partituraspdf.core.document.DocumentRef
import com.abdev.partituraspdf.core.document.DocumentRevision
import com.abdev.partituraspdf.core.document.PageIndex
import com.abdev.partituraspdf.core.document.SnapshotEvidence
import com.abdev.partituraspdf.core.document.StorageDocumentRef
import com.abdev.partituraspdf.core.geometry.PdfRotation
import com.abdev.partituraspdf.core.result.AppError
import com.abdev.partituraspdf.core.result.Outcome
import com.abdev.partituraspdf.storage.api.ReadHandle
import com.abdev.partituraspdf.storage.api.ReadOwnership
import com.abdev.partituraspdf.storage.api.ReadProvenance
import com.abdev.partituraspdf.storage.api.ReadResource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class PdfReadContractsTest {
    private val document = DocumentRef(DocumentId("score"))
    private val storage = StorageDocumentRef("provider", "entry")
    private val sessionId = ReadSessionId("session")
    private val page = ReadPageRef(sessionId, 1, PageIndex(0))
    private val request = RenderRequest(RenderRequestId("render"), page, 100, 200, 0)
    private val limits = RenderLimits(1000, 500_000)

    private class ProbeResource : ReadResource {
        var closures = 0
        var closeFailure: Throwable? = null
        override val descriptor: ParcelFileDescriptor get() = throw UnsupportedOperationException("No native descriptor in JVM probe")
        override fun close() { closures++; closeFailure?.let { throw it } }
    }

    private class ProbePage(override val request: RenderRequest, override val displayInfo: PageDisplayInfo) : RenderedPage {
        var closures = 0
        private var closed = false
        override val bitmap: Bitmap get() = throw UnsupportedOperationException("No native bitmap in JVM probe")
        override fun close() {
            if (!closed) { closed = true; closures++ }
        }
    }

    /** Demonstrates the public validation/ownership seam, never renders PDF bytes. */
    private class ProbeSession(
        private val input: ReadHandle,
        override val info: ReadSessionInfo,
        override val renderLimits: RenderLimits,
    ) : PdfReadSession {
        private var closed = false
        var nativeRequests = 0
        override suspend fun displayInfo(page: ReadPageRef): Outcome<PageDisplayInfo> {
            if (closed) return Outcome.Failure(AppError.Conflict.SESSION_CLOSED)
            val validated = info.validate(page)
            if (validated is Outcome.Failure) return validated
            nativeRequests++
            return Outcome.Success(PageDisplayInfo(page, 595, 842))
        }
        override suspend fun render(request: RenderRequest): Outcome<RenderedPage> {
            if (closed) return Outcome.Failure(AppError.Conflict.SESSION_CLOSED)
            val validated = info.validate(request.page)
            if (validated is Outcome.Failure) return validated
            val bounded = request.validate(renderLimits)
            if (bounded is Outcome.Failure) return bounded
            val display = (displayInfo(request.page) as Outcome.Success).value
            return Outcome.Success(ProbePage(request, display))
        }
        override fun close() {
            if (!closed) { closed = true; input.close() }
        }
    }

    private enum class OpenBehavior { SUCCESS, EXPECTED_FAILURE, CANCEL, DEFECT }

    private fun engine(behavior: OpenBehavior): PdfReadEngine = object : PdfReadEngine {
        override suspend fun open(source: PdfReadSource): Outcome<PdfReadSession> {
            val input = source.takeHandle()
            var handedOff = false
            var primaryFailure: Throwable? = null
            try {
                when (behavior) {
                    OpenBehavior.EXPECTED_FAILURE -> return Outcome.Failure(AppError.Validation.INVALID_DOCUMENT)
                    OpenBehavior.CANCEL -> throw CancellationException()
                    OpenBehavior.DEFECT -> throw IllegalStateException("open probe defect")
                    OpenBehavior.SUCCESS -> {
                        val session = ProbeSession(input, ReadSessionInfo(sessionId, 1, 2, source.revision as? DocumentRevision.VerifiedSnapshot), limits)
                        handedOff = true
                        return Outcome.Success(session)
                    }
                }
            } catch (failure: Throwable) {
                primaryFailure = failure
                throw failure
            } finally {
                if (!handedOff) {
                    val primary = primaryFailure
                    if (primary == null) input.close()
                    else try { input.close() } catch (cleanup: Throwable) { primary.addSuppressed(cleanup) }
                }
            }
        }
    }

    @Test fun directProviderAndReadCopySourcesDoNotAssertExactRevisions() {
        for (provenance in listOf(ReadProvenance.Provider(storage), ReadProvenance.TemporaryReadCopy(storage))) {
            val handle = ReadHandle(ProbeResource(), provenance, true)
            val registered = PdfReadSource(handle, document)
            assertEquals(document, registered.document)
            assertSame(handle, registered.handle)
            assertEquals(DocumentRevision.Unknown(document), registered.revision)
            val unregistered = PdfReadSource(handle)
            assertNull(unregistered.document)
            assertNull(unregistered.revision)
            handle.close()
        }
    }

    @Test fun snapshotSourceRequiresMatchingLogicalIdentityAndRetainsEvidence() {
        val exact = DocumentRevision.VerifiedSnapshot(document, SnapshotEvidence("0".repeat(64), 10))
        val handle = ReadHandle(ProbeResource(), ReadProvenance.SnapshotLease(exact), true)
        assertEquals(exact, PdfReadSource(handle, document).revision)
        assertThrows(IllegalArgumentException::class.java) { PdfReadSource(handle) }
        assertThrows(IllegalArgumentException::class.java) { PdfReadSource(handle, DocumentRef(DocumentId("other"))) }
        assertEquals(ReadOwnership.OWNED, handle.ownership)
        handle.close()
    }

    @Test fun sourceRejectsNonseekableClosedAndTransferredHandlesWithoutTakingOwnership() {
        val resource = ProbeResource()
        val nonseekable = ReadHandle(resource, ReadProvenance.Provider(storage), false)
        assertThrows(IllegalArgumentException::class.java) { PdfReadSource(nonseekable) }
        assertEquals(ReadOwnership.OWNED, nonseekable.ownership)
        nonseekable.close()
        val handle = ReadHandle(ProbeResource(), ReadProvenance.Provider(storage), true)
        val owner = handle.transferOwnership()
        assertThrows(IllegalArgumentException::class.java) { PdfReadSource(handle) }
        owner.close()
        assertThrows(IllegalArgumentException::class.java) { PdfReadSource(owner) }
    }

    @Test fun successfulOpenConsumesInputOnceAndSessionOwnsClosure() = runBlocking {
        val resource = ProbeResource()
        val source = PdfReadSource(ReadHandle(resource, ReadProvenance.Provider(storage), true))
        val session = (engine(OpenBehavior.SUCCESS).open(source) as Outcome.Success).value
        assertEquals(ReadOwnership.TRANSFERRED, source.handle.ownership)
        assertEquals(0, resource.closures)
        assertThrows(IllegalStateException::class.java) { source.takeHandle() }
        session.close()
        session.close()
        assertEquals(1, resource.closures)
    }

    @Test fun expectedOpenFailureClosesTransferredInput() = runBlocking {
        val resource = ProbeResource()
        val source = PdfReadSource(ReadHandle(resource, ReadProvenance.Provider(storage), true))
        assertEquals(Outcome.Failure(AppError.Validation.INVALID_DOCUMENT), engine(OpenBehavior.EXPECTED_FAILURE).open(source))
        assertEquals(1, resource.closures)
        assertEquals(ReadOwnership.TRANSFERRED, source.handle.ownership)
    }

    @Test fun openCancellationAndDefectsPropagateAfterClosingInput() {
        for ((behavior, exception) in listOf(
            OpenBehavior.CANCEL to CancellationException::class.java,
            OpenBehavior.DEFECT to IllegalStateException::class.java,
        )) {
            val resource = ProbeResource()
            val source = PdfReadSource(ReadHandle(resource, ReadProvenance.Provider(storage), true))
            assertThrows(exception) { runBlocking { engine(behavior).open(source) } }
            assertEquals(1, resource.closures)
        }
    }

    @Test fun failedTransferCannotReopenReleasedInput() {
        val resource = ProbeResource()
        val source = PdfReadSource(ReadHandle(resource, ReadProvenance.Provider(storage), true))
        source.handle.close()
        assertThrows(IllegalStateException::class.java) { runBlocking { engine(OpenBehavior.SUCCESS).open(source) } }
        assertEquals(1, resource.closures)
    }

    @Test fun cancellationRemainsPrimaryEvenWhenCleanupAlsoFails() {
        val cleanup = IllegalStateException("cleanup probe failure")
        val resource = ProbeResource().apply { closeFailure = cleanup }
        val source = PdfReadSource(ReadHandle(resource, ReadProvenance.Provider(storage), true))
        val cancellation = assertThrows(CancellationException::class.java) {
            runBlocking { engine(OpenBehavior.CANCEL).open(source) }
        }
        assertEquals(listOf(cleanup), cancellation.suppressed.toList())
        assertEquals(1, resource.closures)
    }

    @Test fun sessionAndRequestIdentifiersRejectBlankWithoutNormalizing() {
        for (value in listOf("", " ", "\n")) {
            assertThrows(IllegalArgumentException::class.java) { ReadSessionId(value) }
            assertThrows(IllegalArgumentException::class.java) { RenderRequestId(value) }
        }
        assertEquals("session", sessionId.value)
        assertEquals("render", request.id.value)
        assertNotEquals(ReadSessionId("A"), ReadSessionId("a"))
        assertNotEquals(RenderRequestId("A"), RenderRequestId(" A "))
    }

    @Test fun sessionBoundsAndSourceGenerationsAreValidated() {
        assertEquals(0L, page.copy(sourceGeneration = 0).sourceGeneration)
        assertThrows(IllegalArgumentException::class.java) { page.copy(sourceGeneration = -1) }
        assertThrows(IllegalArgumentException::class.java) { ReadSessionInfo(sessionId, -1, 1) }
        assertThrows(IllegalArgumentException::class.java) { ReadSessionInfo(sessionId, 0, 0) }
        assertThrows(IllegalArgumentException::class.java) { ReadSessionInfo(sessionId, 0, -1) }
        val info = ReadSessionInfo(sessionId, 1, 2)
        assertEquals(sessionId, info.id)
        assertEquals(1L, info.sourceGeneration)
        assertEquals(2, info.pageCount)
        assertNull(info.revision)
        assertEquals(Outcome.Success(Unit), info.validate(page.copy(page = PageIndex(1))))
        assertEquals(Outcome.Failure(AppError.Validation.INVALID_PAGE), info.validate(page.copy(page = PageIndex(2))))
    }

    @Test fun sameSessionDoesNotMakeAnotherGenerationOrRevisionValid() {
        val info = ReadSessionInfo(sessionId, 1, 2)
        assertEquals(Outcome.Failure(AppError.Conflict.STALE_SESSION), info.validate(page.copy(session = ReadSessionId("other"))))
        assertEquals(Outcome.Failure(AppError.Conflict.STALE_SESSION), info.validate(page.copy(sourceGeneration = 2)))
        val exact = DocumentRevision.VerifiedSnapshot(document, SnapshotEvidence("0".repeat(64), 10))
        assertEquals(Outcome.Failure(AppError.Conflict.REVISION_CHANGED), info.validate(page.copy(revision = exact)))
        val snapshotInfo = info.copy(revision = exact)
        assertEquals(Outcome.Failure(AppError.Conflict.REVISION_CHANGED), snapshotInfo.validate(page))
        assertEquals(Outcome.Success(Unit), snapshotInfo.validate(page.copy(revision = exact)))
        assertEquals(exact, snapshotInfo.revision)
    }

    @Test fun invalidDisplayAndRenderDimensionsAreRejected() {
        val display = PageDisplayInfo(page, 595, 842, PdfRotation(90))
        assertEquals(page, display.page)
        assertEquals(595, display.width)
        assertEquals(842, display.height)
        assertEquals(PdfRotation(90), display.rotation)
        assertEquals(AnnotationRendering.UNKNOWN, display.annotations)
        assertNull(PageDisplayInfo(page, 595, 842).rotation)
        for (dimension in listOf(0, -1)) {
            assertThrows(IllegalArgumentException::class.java) { display.copy(width = dimension) }
            assertThrows(IllegalArgumentException::class.java) { display.copy(height = dimension) }
            assertThrows(IllegalArgumentException::class.java) { request.copy(width = dimension) }
            assertThrows(IllegalArgumentException::class.java) { request.copy(height = dimension) }
        }
        assertThrows(IllegalArgumentException::class.java) { request.copy(viewportGeneration = -1) }
        assertEquals(100, request.width)
        assertEquals(200, request.height)
        assertEquals(0L, request.viewportGeneration)
    }

    @Test fun renderingLimitsRejectOversizeAndPreventIntegerOverflow() {
        assertEquals(1000, limits.maxDimension)
        assertEquals(500_000L, limits.maxPixels)
        assertThrows(IllegalArgumentException::class.java) { RenderLimits(0, 1) }
        assertThrows(IllegalArgumentException::class.java) { RenderLimits(1, 0) }
        assertEquals(Outcome.Success(Unit), request.validate(limits))
        assertEquals(Outcome.Success(Unit), request.copy(width = 1000, height = 500).validate(limits))
        assertEquals(Outcome.Failure(AppError.Resource.RENDER_LIMIT), request.copy(width = 1001).validate(limits))
        assertEquals(Outcome.Failure(AppError.Resource.RENDER_LIMIT), request.copy(height = 1001).validate(limits))
        assertEquals(Outcome.Failure(AppError.Resource.RENDER_LIMIT), request.copy(width = 1000, height = 501).validate(limits))
        assertEquals(Outcome.Failure(AppError.Resource.RENDER_LIMIT), request.copy(width = Int.MAX_VALUE, height = Int.MAX_VALUE).validate(RenderLimits(Int.MAX_VALUE, Int.MAX_VALUE.toLong())))
    }

    @Test fun sessionRejectsStaleInvalidAndClosedRequestsBeforeNativeAccess() = runBlocking {
        val resource = ProbeResource()
        val source = PdfReadSource(ReadHandle(resource, ReadProvenance.Provider(storage), true))
        val session = (engine(OpenBehavior.SUCCESS).open(source) as Outcome.Success).value as ProbeSession
        assertEquals(limits, session.renderLimits)
        assertEquals(Outcome.Failure(AppError.Conflict.STALE_SESSION), session.displayInfo(page.copy(sourceGeneration = 0)))
        assertEquals(Outcome.Failure(AppError.Validation.INVALID_PAGE), session.render(request.copy(page = page.copy(page = PageIndex(2)))))
        assertEquals(Outcome.Failure(AppError.Resource.RENDER_LIMIT), session.render(request.copy(width = 1001)))
        assertEquals(0, session.nativeRequests)
        session.close()
        assertEquals(Outcome.Failure(AppError.Conflict.SESSION_CLOSED), session.displayInfo(page))
        assertEquals(Outcome.Failure(AppError.Conflict.SESSION_CLOSED), session.render(request))
        assertEquals(1, resource.closures)
    }

    @Test fun renderedLeaseRetainsCacheKeyAndOutlivesSessionUntilConsumerCloses() = runBlocking {
        val source = PdfReadSource(ReadHandle(ProbeResource(), ReadProvenance.Provider(storage), true))
        val session = (engine(OpenBehavior.SUCCESS).open(source) as Outcome.Success).value
        val result = (session.render(request) as Outcome.Success).value as ProbePage
        assertEquals(request, result.request)
        assertEquals(request.page, result.displayInfo.page)
        session.close()
        assertEquals(0, result.closures)
        result.close()
        result.close()
        assertEquals(1, result.closures)
    }
}
