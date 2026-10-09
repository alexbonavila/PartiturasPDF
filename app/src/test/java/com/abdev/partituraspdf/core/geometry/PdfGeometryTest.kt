package com.abdev.partituraspdf.core.geometry

import org.junit.Assert.*
import org.junit.Test

class PdfGeometryTest {
    @Test fun finiteCoordinatesRetainNegativeAndOffsetOrigins() {
        val rect = PdfRect(PdfPoint(-20.5, 15.0), PdfPoint(40.0, 70.5))
        assertEquals(-20.5, rect.min.x, 0.0)
        assertEquals(70.5, rect.max.y, 0.0)
        assertEquals(60.5, rect.width, 0.0)
        assertEquals(55.5, rect.height, 0.0)
        val point = PdfPoint(3.0, -2.0)
        val degenerate = PdfRect(point, point)
        assertEquals(0.0, degenerate.width, 0.0)
        assertEquals(0.0, degenerate.height, 0.0)
    }

    @Test fun nonfiniteCoordinatesAreRejectedOnEachAxis() {
        for (value in listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY)) {
            assertThrows(IllegalArgumentException::class.java) { PdfPoint(value, 0.0) }
            assertThrows(IllegalArgumentException::class.java) { PdfPoint(0.0, value) }
        }
    }

    @Test fun reversedAndOverflowingBoundsAreRejected() {
        assertThrows(IllegalArgumentException::class.java) { PdfRect(PdfPoint(2.0, 0.0), PdfPoint(1.0, 0.0)) }
        assertThrows(IllegalArgumentException::class.java) { PdfRect(PdfPoint(0.0, 2.0), PdfPoint(0.0, 1.0)) }
        assertThrows(IllegalArgumentException::class.java) {
            PdfRect(PdfPoint(-Double.MAX_VALUE, 0.0), PdfPoint(Double.MAX_VALUE, 0.0))
        }
        assertThrows(IllegalArgumentException::class.java) {
            PdfRect(PdfPoint(0.0, -Double.MAX_VALUE), PdfPoint(0.0, Double.MAX_VALUE))
        }
    }

    @Test fun rotationsAreNormalizedClockwiseQuarterTurns() {
        for (rotation in listOf(0, 90, 180, 270)) assertEquals(rotation, PdfRotation(rotation).degrees)
        for (rotation in listOf(-90, -1, 1, 45, 271, 360, Int.MAX_VALUE)) {
            assertThrows(IllegalArgumentException::class.java) { PdfRotation(rotation) }
        }
    }
}
