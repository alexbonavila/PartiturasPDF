package com.abdev.partituraspdf.reader

import com.abdev.partituraspdf.core.foundation.HeapAvailability
import com.abdev.partituraspdf.core.foundation.ReaderMemoryPolicy
import com.abdev.partituraspdf.core.preferences.PageFit
import com.abdev.partituraspdf.core.result.AppError
import com.abdev.partituraspdf.core.result.Outcome
import com.abdev.partituraspdf.pdf.api.*
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.sqrt

/** Pure full-page planning. Reducing bitmap resolution does not change the future UI fit bounds. */
internal fun planReaderRender(
    id: RenderRequestId,
    page: PageDisplayInfo,
    viewport: ReaderViewport,
    viewportGeneration: Long,
    fit: PageFit,
    ceiling: RenderLimits,
    sessionLimits: RenderLimits,
    heap: HeapAvailability,
): Outcome<RenderRequest> {
    if (viewport.widthPx <= 0 || viewport.heightPx <= 0 || viewportGeneration < 0) {
        return Outcome.Failure(AppError.Validation.INVALID_RENDER_REQUEST)
    }
    val native = RenderLimits(min(ceiling.maxDimension, sessionLimits.maxDimension), min(ceiling.maxPixels, sessionLimits.maxPixels))
    val bounded = boundedRenderLimits(ReaderMemoryPolicy.budget(heap), native)
    if (bounded is Outcome.Failure) return bounded
    val limits = (bounded as Outcome.Success).value
    val width = page.width.toDouble()
    val height = page.height.toDouble()
    val desired = if (fit == PageFit.WIDTH) viewport.widthPx / width
        else min(viewport.widthPx / width, viewport.heightPx / height)
    // Bound before converting to Int or multiplying pixels. Int page geometry squared fits Long,
    // but Double avoids overflowing intermediate WIDTH calculations for extreme aspect ratios.
    val scale = minOf(desired, limits.maxDimension / width, limits.maxDimension / height,
        sqrt(limits.maxPixels.toDouble() / width / height))
    val targetWidth = floor(width * scale).toInt()
    val targetHeight = floor(height * scale).toInt()
    if (targetWidth <= 0 || targetHeight <= 0) return Outcome.Failure(AppError.Resource.RENDER_LIMIT)
    val request = RenderRequest(id, page.page, targetWidth, targetHeight, viewportGeneration)
    return when (val valid = request.validate(limits)) {
        is Outcome.Failure -> valid
        is Outcome.Success -> Outcome.Success(request)
    }
}
