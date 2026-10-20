package com.abdev.partituraspdf.reader

import com.abdev.partituraspdf.core.document.PageIndex
import com.abdev.partituraspdf.core.foundation.ReaderTrim
import com.abdev.partituraspdf.core.preferences.PageFit
import com.abdev.partituraspdf.core.result.AppError
import com.abdev.partituraspdf.core.result.Outcome
import com.abdev.partituraspdf.pdf.api.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

/** Models the adapter contract: cancellation of an active native call invalidates its session. */
@OptIn(ExperimentalCoroutinesApi::class)
class ReaderSessionLifetimeTest {
    private suspend fun TestScope.supersession(planning: Boolean, rapid: Boolean) = withReader {
        val gate = gate()
        var invalid = false
        var activeCalls = 0
        var maximum = 0
        var first = true
        suspend fun nativeCall() {
            check(!invalid) { "Retired session borrowed" }
            activeCalls++; maximum = maxOf(maximum, activeCalls)
            try {
                if (first) { first = false; gate.await() }
            } catch (cancelled: CancellationException) {
                invalid = true
                throw cancelled
            } finally { activeCalls-- }
        }
        if (planning) session.inspect = { page ->
            nativeCall(); Outcome.Success(PageDisplayInfo(page, 600, 900))
        } else session.render = { request ->
            nativeCall(); Outcome.Success(TestPage(request).also { session.pages += it })
        }
        open()
        assertEquals(1, activeCalls)
        if (rapid) repeat(100) { index ->
            controller.dispatch(ReaderAction.Viewport(ReaderViewport(700 + index, 900)))
            controller.dispatch(ReaderAction.Fit(if (index % 2 == 0) PageFit.WIDTH else PageFit.PAGE))
            controller.dispatch(ReaderAction.Page(index % 8))
        } else {
            controller.dispatch(ReaderAction.Viewport(ReaderViewport(800, 600)))
            controller.dispatch(ReaderAction.Fit(PageFit.WIDTH))
        }
        controller.dispatch(ReaderAction.Page(6))
        val generation = state.viewportGeneration
        runCurrent()
        assertFalse(invalid); assertEquals(1, activeCalls)
        assertNull(state.frame); assertNull(state.presentedPage)
        gate.complete(Unit); runCurrent()
        assertFalse(invalid); assertEquals(1, maximum); assertEquals(0, activeCalls)
        assertEquals(1, opens.size)
        assertEquals(2, session.requests.size)
        assertEquals(1, session.pages.first().closes)
        assertEquals(0, session.pages.last().closes)
        assertEquals(PageIndex(6), state.frame!!.request.page.page)
        assertEquals(generation, state.frame!!.request.viewportGeneration)
        assertTrue(displayed.isEmpty())
        val consumer = attach()
        controller.dispatch(ReaderAction.Presented(consumer)); runCurrent()
        assertEquals(ReaderPhase.VISIBLE, state.phase)
        assertEquals(listOf(PageIndex(6)), displayed.map { it.second })
    }

    @Test fun supersessionDuringDisplayInfoKeepsHealthySession() = runTest {
        supersession(planning = true, rapid = false)
    }
    @Test fun supersessionDuringRenderDiscardsOldLeaseWithoutNativeCancellation() = runTest {
        supersession(planning = false, rapid = false)
    }
    @Test fun rapidPageFitAndViewportChangesKeepOnlyLatestPublication() = runTest {
        supersession(planning = false, rapid = true)
    }
    @Test fun destructiveCancellationNeverReborrowsInvalidSessionAndWaitsForExplicitResume() = runTest {
        for (trim in ReaderTrim.entries) for (planning in listOf(true, false)) withReader {
            var invalid = false
            var cleanupFinished = false
            var callsAfterInvalidation = 0
            suspend fun pendingNative() {
                if (invalid) callsAfterInvalidation++
                try { awaitCancellation() }
                finally { invalid = true; cleanupFinished = true }
            }
            if (planning) session.inspect = { pendingNative(); error("Unreachable") }
            else session.render = { pendingNative(); error("Unreachable") }
            open()
            val paused = async { controller.quiesce(trim) }
            runCurrent(); paused.await()
            assertTrue(invalid); assertTrue(cleanupFinished)
            assertNull(state.frame); assertNull(state.session)
            assertEquals(ReaderPhase.QUIESCED, state.phase)
            repeat(10) { controller.dispatch(ReaderAction.Viewport(ReaderViewport(800 + it, 600))) }
            runCurrent(); assertEquals(1, opens.size)
            if (trim != ReaderTrim.LOW_MEMORY) { route.background(); route.close() }
            val replacement = TestSession("replacement-$trim-$planning")
            opening = { Outcome.Success(ReaderReady(it, replacement, PageIndex(2))) }
            controller.resume(); runCurrent()
            assertEquals(2, opens.size); assertEquals(0, callsAfterInvalidation)
            assertEquals(replacement.info, state.session)
            assertEquals(ReaderPhase.RENDERED, state.phase)
        }
    }
    @Test fun obsoleteNativeFailureInvalidatesBorrowedSessionInsteadOfSchedulingItAgain() = runTest { withReader {
        val gate = gate()
        session.inspect = { gate.await(); Outcome.Failure(AppError.Conflict.SESSION_CLOSED) }
        open()
        controller.dispatch(ReaderAction.Fit(PageFit.WIDTH)); runCurrent()
        gate.complete(Unit); runCurrent()
        assertEquals(1, session.inspections.size); assertNull(state.session)
        assertEquals(AppError.Conflict.SESSION_CLOSED, state.failure!!.error)
        assertEquals(ReaderRetry.OPEN, state.failure!!.retry)
        assertTrue(session.requests.isEmpty()); assertTrue(displayed.isEmpty())
    } }
    @Test fun retirementDrainsDestructiveCancellationWithoutReopeningOrPublishing() = runTest { withReader {
        var canceled = false
        session.render = { try { awaitCancellation() } finally { canceled = true } }
        open(); controller.retire(); runCurrent()
        assertTrue(canceled); assertEquals(1, opens.size)
        assertEquals(ReaderPhase.RETIRED, state.phase)
        assertNull(state.frame); assertTrue(displayed.isEmpty())
    } }
}
