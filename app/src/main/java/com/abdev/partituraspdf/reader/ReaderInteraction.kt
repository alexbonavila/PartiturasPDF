package com.abdev.partituraspdf.reader

import com.abdev.partituraspdf.core.preferences.PageFit
import kotlin.math.max
import kotlin.math.min

/** Visual magnification only; never changes a native render size or creates another bitmap. */
internal data class ReaderTransform(val zoom: Float = 1f, val panX: Float = 0f, val panY: Float = 0f)
internal data class ReaderImageLayout(val left: Float, val top: Float, val width: Float, val height: Float,
    val transform: ReaderTransform, val verticalRange: Float)

internal fun readerImageLayout(pageWidth: Int, pageHeight: Int, viewport: ReaderViewport,
    fit: PageFit, transform: ReaderTransform): ReaderImageLayout? {
    if (pageWidth <= 0 || pageHeight <= 0 || viewport.widthPx <= 0 || viewport.heightPx <= 0) return null
    if (!transform.zoom.isFinite() || !transform.panX.isFinite() || !transform.panY.isFinite()) return null
    val scale = if (fit == PageFit.WIDTH) viewport.widthPx.toDouble() / pageWidth
        else min(viewport.widthPx.toDouble() / pageWidth, viewport.heightPx.toDouble() / pageHeight)
    val zoom = transform.zoom.coerceIn(1f, 4f)
    val width = (pageWidth * scale * zoom).toFloat()
    val height = (pageHeight * scale * zoom).toFloat()
    val horizontal = max(0f, (width - viewport.widthPx) / 2)
    val vertical = max(0f, height - viewport.heightPx)
    val clamped = ReaderTransform(zoom, if (horizontal == 0f) 0f else transform.panX.coerceIn(-horizontal, horizontal),
        if (vertical == 0f) 0f else transform.panY.coerceIn(-vertical, 0f))
    return ReaderImageLayout((viewport.widthPx - width) / 2 + clamped.panX,
        (if (vertical == 0f) (viewport.heightPx - height) / 2 else 0f) + clamped.panY,
        width, height, clamped, vertical)
}

/** A tap stays a tap only if the whole gesture was single-pointer, unconsumed and within slop. */
internal class ReaderGestureGate(private val slop: Float) {
    private var distanceX = 0f
    private var distanceY = 0f
    private var transformed = false
    private var blocked = false
    fun update(pointers: Int, dx: Float, dy: Float, zoom: Float, consumed: Boolean): Boolean {
        if (consumed || !dx.isFinite() || !dy.isFinite() || !zoom.isFinite()) blocked = true
        if (pointers > 1 || zoom != 1f) transformed = true
        distanceX += dx; distanceY += dy
        if (distanceX * distanceX + distanceY * distanceY > slop * slop) transformed = true
        return transformed && !blocked
    }
    fun isTap(): Boolean = !transformed && !blocked
}

/** 15% bands, at least the caller's 48dp in pixels, capped at half width to avoid overlap. */
internal fun readerEdgeStep(x: Float, width: Int, minimumEdgePx: Float = 0f): Int? {
    if (!x.isFinite() || !minimumEdgePx.isFinite() || width <= 0 || x < 0 || x > width) return null
    val band = min(width / 2f, max(width * .15f, minimumEdgePx))
    if (x == width / 2f) return null
    return when {
        x <= band -> -1
        x >= width - band -> 1
        else -> null
    }
}
