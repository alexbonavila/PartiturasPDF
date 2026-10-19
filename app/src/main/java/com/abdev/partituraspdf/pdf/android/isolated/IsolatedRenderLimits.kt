package com.abdev.partituraspdf.pdf.android.isolated

import com.abdev.partituraspdf.pdf.api.RenderLimits

/** Reserve two simultaneous pixel planes (worker + client), with no full-page byte array.
 * Native parser/process overhead is additional and must be measured on target devices.
 */
internal fun isolatedRenderLimits(ceiling: RenderLimits): RenderLimits = RenderLimits(
    minOf(ceiling.maxDimension, 16384), minOf((ceiling.maxPixels / 2).coerceAtLeast(1), 4_194_304),
)
