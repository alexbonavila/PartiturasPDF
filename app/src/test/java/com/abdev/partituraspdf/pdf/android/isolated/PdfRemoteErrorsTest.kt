package com.abdev.partituraspdf.pdf.android.isolated

import com.abdev.partituraspdf.core.result.AppError
import com.abdev.partituraspdf.pdf.api.RenderLimits
import org.junit.Assert.*
import org.junit.Test

class PdfRemoteErrorsTest {
    @Test fun everyTypedErrorSurvivesWireEncodingWithoutCollapsingCategories() {
        val errors = AppError.Access.entries + AppError.Validation.entries + AppError.Unsupported.entries + AppError.Resource.entries + AppError.Conflict.entries
        for (error in errors) assertEquals(error, PdfRemoteErrors.decode(PdfRemoteErrors.encode(error)))
        assertEquals(errors.size, errors.map(PdfRemoteErrors::encode).toSet().size)
    }
    @Test fun unknownAndMalformedErrorsAreProtocolDefects() {
        for (text in listOf("", "access", "access:x:y", "unknown:X", "access:NOT_REAL", "validation:X", "unsupported:X", "resource:X", "conflict:X")) {
            assertThrows(RuntimeException::class.java) { PdfRemoteErrors.decode(text) }
        }
    }
    @Test fun isolatedPixelCeilingReservesProducerAndConsumerPlanes() {
        assertEquals(RenderLimits(2048, 2_097_152), isolatedRenderLimits(RenderLimits(2048, 4_194_304)))
        assertEquals(RenderLimits(16384, 4_194_304), isolatedRenderLimits(RenderLimits(Int.MAX_VALUE, Long.MAX_VALUE)))
        assertEquals(RenderLimits(1, 1), isolatedRenderLimits(RenderLimits(1, 1)))
        assertEquals(RenderLimits(10, 50), isolatedRenderLimits(RenderLimits(10, 101)))
    }
}
