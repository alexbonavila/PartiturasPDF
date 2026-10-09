package com.abdev.partituraspdf.pdf.api

import com.abdev.partituraspdf.core.document.DocumentRevision
import com.abdev.partituraspdf.core.document.PageIndex
import com.abdev.partituraspdf.core.geometry.PdfRotation
import com.abdev.partituraspdf.core.result.AppError
import com.abdev.partituraspdf.core.result.Outcome

/** Unique transient open identity, never a logical document ID. */
data class ReadSessionId(val value: String) {
    init { require(value.isNotBlank()) }
}
data class RenderRequestId(val value: String) {
    init { require(value.isNotBlank()) }
}

/** Generation is a cache boundary only; it does not detect external writes or certify bytes. */
data class ReadPageRef(
    val session: ReadSessionId,
    val sourceGeneration: Long,
    val page: PageIndex,
    val revision: DocumentRevision.VerifiedSnapshot? = null,
) {
    init { require(sourceGeneration >= 0) }
}

/** Immutable observed session bounds used by all adapters to reject mismatched requests. */
data class ReadSessionInfo(
    val id: ReadSessionId,
    val sourceGeneration: Long,
    val pageCount: Int,
    val revision: DocumentRevision.VerifiedSnapshot? = null,
) {
    init {
        require(sourceGeneration >= 0)
        require(pageCount > 0)
    }

    fun validate(page: ReadPageRef): Outcome<Unit> = when {
        page.session != id -> Outcome.Failure(AppError.Conflict.STALE_SESSION)
        page.sourceGeneration != sourceGeneration -> Outcome.Failure(AppError.Conflict.STALE_SESSION)
        page.revision != revision -> Outcome.Failure(AppError.Conflict.REVISION_CHANGED)
        page.page.value >= pageCount -> Outcome.Failure(AppError.Validation.INVALID_PAGE)
        else -> Outcome.Success(Unit)
    }
}

/** UNKNOWN forbids claiming that standard annotation appearances are fully rendered. */
enum class AnnotationRendering { UNKNOWN, NATIVE, NOT_RENDERED }

/**
 * Observed renderer dimensions in its reported page points, not requested bitmap pixels or
 * exact MediaBox/CropBox geometry. Raw PDF rotation may be unknown on API 26; never guess it
 * from portrait/landscape dimensions or require the deferred geometry inspector to read.
 */
data class PageDisplayInfo(
    val page: ReadPageRef,
    val width: Int,
    val height: Int,
    val rotation: PdfRotation? = null,
    val annotations: AnnotationRendering = AnnotationRendering.UNKNOWN,
) {
    init {
        require(width > 0)
        require(height > 0)
    }
}

/** Caller/adapter-approved allocation ceiling, independent of cache-wide memory budgets. */
data class RenderLimits(val maxDimension: Int, val maxPixels: Long) {
    init {
        require(maxDimension > 0)
        require(maxPixels > 0)
    }
}

/** Full-page render only in A1; clip/zoom transforms wait for approved consumers. */
data class RenderRequest(
    val id: RenderRequestId,
    val page: ReadPageRef,
    val width: Int,
    val height: Int,
    val viewportGeneration: Long,
) {
    init {
        require(width > 0)
        require(height > 0)
        require(viewportGeneration >= 0)
    }

    /** Long multiplication avoids Int overflow; adapters must validate before allocating. */
    fun validate(limits: RenderLimits): Outcome<Unit> = when {
        width > limits.maxDimension -> Outcome.Failure(AppError.Resource.RENDER_LIMIT)
        height > limits.maxDimension -> Outcome.Failure(AppError.Resource.RENDER_LIMIT)
        width.toLong() * height > limits.maxPixels -> Outcome.Failure(AppError.Resource.RENDER_LIMIT)
        else -> Outcome.Success(Unit)
    }
}
