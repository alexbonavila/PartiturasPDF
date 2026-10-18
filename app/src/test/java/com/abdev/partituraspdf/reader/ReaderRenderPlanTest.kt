package com.abdev.partituraspdf.reader

import com.abdev.partituraspdf.core.document.PageIndex
import com.abdev.partituraspdf.core.foundation.*
import com.abdev.partituraspdf.core.preferences.PageFit
import com.abdev.partituraspdf.core.result.*
import com.abdev.partituraspdf.pdf.api.*
import org.junit.Assert.*
import org.junit.Test

class ReaderRenderPlanTest {
    private val page = ReadPageRef(ReadSessionId("native"), 9, PageIndex(0))
    private fun plan(width: Int = 600, height: Int = 900, viewport: ReaderViewport = ReaderViewport(1200, 800),
        fit: PageFit = PageFit.PAGE, host: RenderLimits = ceiling, session: RenderLimits = ceiling,
        heap: HeapAvailability = ampleHeap, generation: Long = 5) =
        planReaderRender(RenderRequestId("planned"), PageDisplayInfo(page, width, height), viewport, generation,
            fit, host, session, heap)

    @Test fun pageAndWidthFitRespectViewportAndPreservePageReference() {
        val full = (plan() as Outcome.Success).value
        assertEquals(533, full.width); assertEquals(800, full.height)
        val wide = (plan(fit = PageFit.WIDTH) as Outcome.Success).value
        assertEquals(1200, wide.width); assertEquals(1800, wide.height)
        assertEquals(page, wide.page); assertEquals(5, wide.viewportGeneration)
        assertEquals(RenderRequestId("planned"), wide.id)
    }
    @Test fun landscapePortraitAndMixedAspectRatiosRemainBounded() {
        for ((w,h) in listOf(600 to 900, 900 to 600, 400 to 400, 1 to 2)) {
            for (viewport in listOf(ReaderViewport(320, 900), ReaderViewport(1600, 600), ReaderViewport(2, 4))) {
                for (fit in PageFit.entries) {
                    val request = (plan(w,h,viewport,fit) as Outcome.Success).value
                    assertTrue(request.width > 0 && request.height > 0)
                    assertEquals(Outcome.Success(Unit), request.validate(ceiling))
                    assertTrue(kotlin.math.abs(request.width.toDouble() * h / w - request.height) <= 3)
                    if (fit == PageFit.PAGE) { assertTrue(request.width <= viewport.widthPx); assertTrue(request.height <= viewport.heightPx) }
                }
            }
        }
    }
    @Test fun hugeViewportIsLimitedBeforeIntegerConversionOrPixelMultiplication() {
        val request = (plan(Int.MAX_VALUE, Int.MAX_VALUE, ReaderViewport(Int.MAX_VALUE, Int.MAX_VALUE), PageFit.WIDTH) as Outcome.Success).value
        assertEquals(2048, request.width); assertEquals(2048, request.height)
        assertEquals(Outcome.Success(Unit), request.validate(ceiling))
        val tall = plan(1, Int.MAX_VALUE, ReaderViewport(Int.MAX_VALUE, Int.MAX_VALUE), PageFit.WIDTH)
        assertEquals(Outcome.Failure(AppError.Resource.RENDER_LIMIT), tall)
    }
    @Test fun hostAndSessionDimensionAndPixelLimitsAreBothApplied() {
        for ((host,session) in listOf(RenderLimits(400, 1_000_000) to ceiling, ceiling to RenderLimits(400, 1_000_000))) {
            val r = (plan(host = host, session = session, fit = PageFit.WIDTH) as Outcome.Success).value
            assertEquals(266,r.width); assertEquals(400,r.height)
        }
        val r = (plan(host = RenderLimits(4096, 60_000), fit = PageFit.WIDTH) as Outcome.Success).value
        assertTrue(r.width.toLong() * r.height <= 60_000)
    }
    @Test fun dynamicHeapPolicyCanBeStricterThanNativeLimits() {
        for (heap in listOf(HeapAvailability(64L * 1024 * 1024, 0), HeapAvailability(256L * 1024 * 1024, 255L * 1024 * 1024))) {
            val r = (plan(viewport = ReaderViewport(4000, 6000), fit = PageFit.WIDTH, heap = heap) as Outcome.Success).value
            assertTrue(r.width.toLong() * r.height <= ReaderMemoryPolicy.budget(heap).maxArgbPixels)
            assertTrue(r.width.toLong() * r.height < ceiling.maxPixels)
        }
    }
    @Test fun invalidViewportAndGenerationFailWithoutConstructingInvalidRenderRequest() {
        for (viewport in listOf(ReaderViewport(0, 1),ReaderViewport(1, 0),ReaderViewport(-1, 1))) {
            assertEquals(Outcome.Failure(AppError.Validation.INVALID_RENDER_REQUEST), plan(viewport = viewport))
        }
        assertEquals(Outcome.Failure(AppError.Validation.INVALID_RENDER_REQUEST), plan(generation = -1))
    }
    @Test fun insufficientBudgetAndUnrepresentableAspectFailRatherThanInventPixels() {
        assertEquals(Outcome.Failure(AppError.Resource.RENDER_LIMIT), plan(heap = HeapAvailability(10, 10)))
        assertEquals(Outcome.Failure(AppError.Resource.RENDER_LIMIT), plan(host = RenderLimits(1,1)))
        assertThrows(IllegalArgumentException::class.java) { PageDisplayInfo(page,0,10) }
        assertThrows(IllegalArgumentException::class.java) { PageIndex(-1) }
    }
}
