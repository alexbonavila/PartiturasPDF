package com.abdev.partituraspdf.app.di

import com.abdev.partituraspdf.pdf.api.RenderLimits
import com.abdev.partituraspdf.storage.api.ReadAccessLimits

/**
 * Immutable, graph-lifetime allocation inputs. Validation belongs to the existing contracts.
 * read bounds bytes materialized from non-seekable input, not all PDFs or total disk usage.
 * render bounds one page bitmap's dimensions/pixels, not native peak memory or a reader cache.
 */
internal data class AppRuntimeLimits(
    val read: ReadAccessLimits,
    val render: RenderLimits,
) {
    companion object {
        /** Provisional safety ceilings, not measured tablet performance; see E_APP_COMPOSITION.md. */
        fun provisional(): AppRuntimeLimits = AppRuntimeLimits(
            read = ReadAccessLimits(maxTemporaryBytes = 64L * 1024 * 1024),
            render = RenderLimits(maxDimension = 4096, maxPixels = 4L * 1024 * 1024),
        )
    }
}
