package com.abdev.partituraspdf.app.di

import com.abdev.partituraspdf.core.document.PageIndex
import com.abdev.partituraspdf.core.result.AppError
import com.abdev.partituraspdf.core.result.Outcome
import com.abdev.partituraspdf.pdf.api.*
import com.abdev.partituraspdf.storage.api.ReadAccessLimits
import org.junit.Assert.*
import org.junit.Test

class AppRuntimeLimitsTest {
    @Test fun constructionRetainsExistingValidatedContractsWithoutAndroidOrFilesystemSetup() {
        val read = ReadAccessLimits(123)
        val render = RenderLimits(17, 200)
        val limits = AppRuntimeLimits(read, render)
        assertSame(read, limits.read)
        assertSame(render, limits.render)
    }

    @Test fun provisionalCeilingsHaveDocumentedByteDimensionAndPixelUnits() {
        val limits = AppRuntimeLimits.provisional()
        assertEquals(67_108_864L, limits.read.maxTemporaryBytes)
        assertEquals(4096, limits.render.maxDimension)
        assertEquals(4_194_304L, limits.render.maxPixels)
        assertEquals(16_777_216L, limits.render.maxPixels * 4)
    }

    @Test fun zeroAndNegativeBoundsAreRejectedByExistingContracts() {
        for (invalid in listOf(0L, -1L, Long.MIN_VALUE)) {
            assertThrows(IllegalArgumentException::class.java) {
                AppRuntimeLimits(ReadAccessLimits(invalid), RenderLimits(1, 1))
            }
            assertThrows(IllegalArgumentException::class.java) {
                AppRuntimeLimits(ReadAccessLimits(1), RenderLimits(1, invalid))
            }
        }
        for (invalid in listOf(0, -1, Int.MIN_VALUE)) {
            assertThrows(IllegalArgumentException::class.java) {
                AppRuntimeLimits(ReadAccessLimits(1), RenderLimits(invalid, 1))
            }
        }
    }

    @Test fun copiesDoNotMutateExistingConfigurationOrCreateSharedMutableState() {
        val original = AppRuntimeLimits.provisional()
        val copy = original.copy(read = ReadAccessLimits(7))
        assertEquals(67_108_864L, original.read.maxTemporaryBytes)
        assertEquals(7L, copy.read.maxTemporaryBytes)
        assertSame(original.render, copy.render)
        assertEquals(original, AppRuntimeLimits.provisional())
        assertNotSame(original, AppRuntimeLimits.provisional())
    }

    @Test fun contractAllowsPositiveExtremesWithoutAllocatingThoseResources() {
        val smallest = AppRuntimeLimits(ReadAccessLimits(1), RenderLimits(1, 1))
        assertEquals(Outcome.Success(Unit), request(1, 1).validate(smallest.render))
        assertEquals(Outcome.Failure(AppError.Resource.RENDER_LIMIT), request(1, 2).validate(smallest.render))
        val largest = AppRuntimeLimits(ReadAccessLimits(Long.MAX_VALUE), RenderLimits(Int.MAX_VALUE, Long.MAX_VALUE))
        assertEquals(Long.MAX_VALUE, largest.read.maxTemporaryBytes)
        // Long multiplication in the existing contract must not overflow an Int.
        assertEquals(Outcome.Success(Unit), request(Int.MAX_VALUE, Int.MAX_VALUE).validate(largest.render))
    }

    @Test fun provisionalRenderLimitsAcceptExactAreaAndRejectEitherDimensionOrPixelExcess() {
        val limits = AppRuntimeLimits.provisional().render
        assertEquals(Outcome.Success(Unit), request(2048, 2048).validate(limits))
        assertEquals(Outcome.Success(Unit), request(4096, 1024).validate(limits))
        val rejected = Outcome.Failure(AppError.Resource.RENDER_LIMIT)
        assertEquals(rejected, request(4097, 1).validate(limits))
        assertEquals(rejected, request(1, 4097).validate(limits))
        assertEquals(rejected, request(2048, 2049).validate(limits))
        assertEquals(rejected, request(4096, 4096).validate(limits))
        assertEquals(rejected, request(65_536, 65_536).validate(limits))
    }

    private fun request(width: Int, height: Int) = RenderRequest(
        RenderRequestId("limits-only"), ReadPageRef(ReadSessionId("limits-only"), 0, PageIndex(0)),
        width, height, 0,
    )
}
