package com.abdev.partituraspdf.core.geometry

/** Unrotated PDF default user space, never viewport pixels. */
data class PdfPoint(val x: Double, val y: Double) {
    init {
        require(x.isFinite())
        require(y.isFinite())
    }
}

/** Ordered bounds retain offset/negative origins. Degenerate boxes are valid values. */
data class PdfRect(val min: PdfPoint, val max: PdfPoint) {
    init {
        require(max.x >= min.x)
        require(max.y >= min.y)
        require((max.x - min.x).isFinite())
        require((max.y - min.y).isFinite())
    }
    val width: Double get() = max.x - min.x
    val height: Double get() = max.y - min.y
}

/** Normalized clockwise page rotation; adapters normalize raw PDF values before construction. */
data class PdfRotation(val degrees: Int) {
    init { require(degrees in 0..270 && degrees % 90 == 0) }
}
