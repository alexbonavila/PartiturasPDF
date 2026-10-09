package com.abdev.partituraspdf.pdf.api

import android.graphics.Bitmap
import com.abdev.partituraspdf.core.result.Outcome
import java.io.Closeable

/**
 * Implemented by pdf.android. On entry, take source ownership exactly once. Failure/cancellation
 * closes acquired native objects and handle; success gives ownership to the returned session.
 * Preserve a primary exception/cancellation if cleanup fails, attaching the cleanup failure
 * as suppressed; resource adapters still finish their remaining cleanup in finally.
 * Cancellation propagates, never becomes Failure. Expected malformed/protected/unsupported
 * input and access/resource errors use typed Outcome; programming defects propagate.
 */
interface PdfReadEngine {
    suspend fun open(source: PdfReadSource): Outcome<PdfReadSession>
}

/**
 * Implemented by pdf.android. Owns its input and serialized native renderer; API 26 permits
 * one open native page at a time, closed in finally. Call info.validate before native access;
 * closed calls return SESSION_CLOSED. Render also validates adapter-owned renderLimits.
 * Session close is idempotent: reject new work, wait for native work, close page/renderer/input.
 * Caller must invoke close off the main thread; native cancellation need not be immediate.
 *
 * Observed/suspected external changes, revocation or refresh require discarding pending output,
 * closing and reopening under a new generation. Undetected writes remain possible for direct
 * input. Invalidate caches by session/generation/page/viewport and verified revision if present.
 * Ordinary sessions cannot authorize revision-sensitive annotation or editing operations.
 */
interface PdfReadSession : Closeable {
    val info: ReadSessionInfo
    val renderLimits: RenderLimits
    suspend fun displayInfo(page: ReadPageRef): Outcome<PageDisplayInfo>
    suspend fun render(request: RenderRequest): Outcome<RenderedPage>
}

/**
 * Exclusive bitmap lease implemented by pdf.android. request preserves the full cache key;
 * displayInfo.page must equal request.page and bitmap dimensions must match request dimensions.
 * Success transfers buffer ownership to caller/cache, independent of session closure. Close
 * releases exactly once after all consumers stop using it; never recycle an in-use buffer.
 * Failed/cancelled/discarded render closes any unpublished buffer. Access after close is invalid.
 */
interface RenderedPage : Closeable {
    val request: RenderRequest
    val displayInfo: PageDisplayInfo
    val bitmap: Bitmap
}
