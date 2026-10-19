package com.abdev.partituraspdf.reader

import com.abdev.partituraspdf.core.document.PageIndex
import com.abdev.partituraspdf.core.preferences.PageFit
import com.abdev.partituraspdf.core.result.*
import com.abdev.partituraspdf.pdf.api.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReaderSchedulingTest {
    @Test fun rapidRelativeUiNavigationDoesNotLoseTapsAgainstAnOldComposeSnapshot() = runTest { withReader {
        open();repeat(20){controller.dispatch(ReaderAction.Step(1))};runCurrent()
        assertEquals(PageIndex(7),state.requestedPage)
        repeat(20){controller.dispatch(ReaderAction.Step(-1))};runCurrent()
        assertEquals(PageIndex(0),state.requestedPage)
        controller.dispatch(ReaderAction.Step(-1));runCurrent();assertEquals(PageIndex(0),state.requestedPage)
        assertTrue(session.requests.size<=3);assertEquals(0,session.closes)
        assertThrows(IllegalArgumentException::class.java){ReaderAction.Step(0)}
    } }
    @Test fun relativeNavigationWithoutSessionOrWhilePausedDoesNotInventAPage() = runTest { withReader {
        controller.dispatch(ReaderAction.Step(1));runCurrent();assertNull(state.requestedPage)
        open();controller.quiesce(com.abdev.partituraspdf.core.foundation.ReaderTrim.LOW_MEMORY)
        controller.dispatch(ReaderAction.Step(1));runCurrent();assertEquals(PageIndex(2),state.requestedPage)
    } }
    @Test fun rapidRequestsKeepOneActiveRenderAndOnlyTheLatestPendingPage() = runTest { withReader {
        val gate=gate(); var activeRenders=0; var maximum=0
        session.render={ request ->
            activeRenders++; maximum=maxOf(maximum,activeRenders)
            try {
                if(session.requests.size==1) withContext(NonCancellable){gate.await()}
                Outcome.Success(TestPage(request).also { session.pages+=it })
            } finally { activeRenders-- }
        }
        open()
        repeat(1000){ controller.dispatch(ReaderAction.Page(it%8)) }
        controller.dispatch(ReaderAction.Page(6)); runCurrent()
        assertEquals(1,session.requests.size); assertEquals(1,activeRenders)
        gate.complete(Unit); runCurrent()
        assertEquals(1,maximum); assertEquals(listOf(2,6),session.requests.map{it.page.page.value})
        assertEquals(listOf(1,0),session.pages.map{it.closes})
        assertEquals(PageIndex(6),state.frame!!.request.page.page); assertTrue(displayed.isEmpty())
    } }
    @Test fun identicalPageViewportAndFitDoNotRenderAgainOrPrefetch() = runTest { withReader {
        open(); val frame=state.frame
        repeat(10){ controller.dispatch(ReaderAction.Page(2)); controller.dispatch(ReaderAction.Viewport(ReaderViewport(600,900))); controller.dispatch(ReaderAction.Fit(PageFit.PAGE)) }
        runCurrent(); advanceTimeBy(10_000); runCurrent()
        assertEquals(1,session.requests.size); assertSame(frame,state.frame)
        assertEquals(ReaderPhase.RENDERED,state.phase); assertNull(state.presentedPage); assertTrue(displayed.isEmpty())
    } }
    @Test fun viewportAndFitReplacementDiscardOldResultsAndUseNewGenerations() = runTest {
        for (fitChange in listOf(false,true)) withReader {
            val gate=gate()
            session.render={ request ->
                if(session.requests.size==1) withContext(NonCancellable){gate.await()}
                Outcome.Success(TestPage(request).also {session.pages+=it})
            }
            open(); val initial=state.viewportGeneration
            if(fitChange) controller.dispatch(ReaderAction.Fit(PageFit.WIDTH))
            else controller.dispatch(ReaderAction.Viewport(ReaderViewport(800,600)))
            runCurrent(); assertEquals(1,session.requests.size)
            gate.complete(Unit); runCurrent()
            assertEquals(2,session.requests.size); assertEquals(1,session.pages.first().closes)
            assertTrue(state.viewportGeneration>initial)
            assertEquals(state.viewportGeneration,state.frame!!.request.viewportGeneration)
            assertNotEquals(session.requests.first().id,session.requests.last().id)
        }
    }
    @Test fun replacingDocumentWaitsForNativeSlotAndDiscardsItsOldBitmap() = runTest { withReader {
        val gate=gate()
        session.render={ request ->
            if(session.requests.size==1) withContext(NonCancellable){gate.await()}
            Outcome.Success(TestPage(request).also {session.pages+=it})
        }
        open(); controller.open(ReaderOpenIntent(2,transientRequest)); runCurrent()
        assertEquals(1,opens.size); assertNull(state.frame)
        gate.complete(Unit); runCurrent()
        assertEquals(listOf(registeredRequest,transientRequest),opens)
        assertEquals(1,session.pages.first().closes); assertEquals(0,session.closes)
        assertEquals(transientRequest,state.intent!!.request)
    } }
    @Test fun unusableViewportAndInsufficientHeapNeverAllocateOrLoseRequestedPage() = runTest {
        for(zeroHeap in listOf(false,true)) withReader {
            if(zeroHeap) heap=com.abdev.partituraspdf.core.foundation.HeapAvailability(100,100)
            else controller.dispatch(ReaderAction.Viewport(ReaderViewport(0,900)))
            open(viewport=zeroHeap)
            assertEquals(ReaderPhase.ERROR,state.phase); assertEquals(PageIndex(2),state.requestedPage)
            assertTrue(session.requests.isEmpty()); assertEquals(ReaderStage.PLAN,state.failure!!.stage)
            heap=ampleHeap; controller.dispatch(ReaderAction.Viewport(ReaderViewport(300,600)))
            controller.dispatch(ReaderAction.Retry); runCurrent()
            assertEquals(ReaderPhase.RENDERED,state.phase)
        }
    }
    @Test fun pageReferenceUsesTheActualNativeIdentityGenerationAndRevision() = runTest { withReader {
        open()
        val page=session.requests.single().page
        assertEquals(session.info.id,page.session); assertEquals(7,page.sourceGeneration)
        assertEquals(session.info.revision,page.revision)
        assertEquals(Outcome.Success(Unit),session.info.validate(page))
        assertEquals(Outcome.Failure(AppError.Conflict.STALE_SESSION),session.info.validate(page.copy(sourceGeneration=8)))
    } }
    @Test fun invalidPageDoesNotWrapCloseOrInventAnotherPage() = runTest { withReader {
        open(); val frame=state.frame
        for(index in listOf(-1,8,Int.MAX_VALUE)) {
            controller.dispatch(ReaderAction.Page(index)); runCurrent()
            assertEquals(AppError.Validation.INVALID_PAGE,state.failure!!.error)
            assertEquals(PageIndex(2),state.requestedPage); assertSame(frame,state.frame)
        }
        controller.dispatch(ReaderAction.Page(0)); runCurrent()
        assertEquals(PageIndex(0),state.requestedPage)
        controller.dispatch(ReaderAction.Page(7)); runCurrent()
        assertEquals(PageIndex(7),state.requestedPage); assertEquals(listOf(2,0,7),session.requests.map{it.page.page.value})
    } }
    @Test fun wrongDisplayPageIsRejectedBeforeRendering() = runTest { withReader {
        session.inspect={ Outcome.Success(PageDisplayInfo(it.copy(session=ReadSessionId("foreign")),600,900)) }
        open(); assertEquals(AppError.Conflict.STALE_SESSION,state.failure!!.error)
        assertTrue(session.requests.isEmpty()); assertNull(state.frame)
    } }
    @Test fun mismatchedRenderIdentityOrDimensionsAreClosedRatherThanPublished() = runTest {
        for(mode in 0..4) withReader {
            var closes=0
            session.render={ request ->
                val wrong=when(mode) {
                    0->request.copy(id=RenderRequestId("foreign"))
                    1->request.copy(width=request.width+1)
                    2->request.copy(page=request.page.copy(sourceGeneration=99))
                    3->request.copy(viewportGeneration=99)
                    else->request
                }
                Outcome.Success(object: RenderedPage {
                    override val request=wrong
                    override val displayInfo=PageDisplayInfo(if(mode==4) wrong.page.copy(page=PageIndex(0)) else wrong.page,600,900)
                    override val bitmap: android.graphics.Bitmap get()=error("Not used")
                    override fun close(){closes++}
                })
            }
            open(); assertNull(state.frame); assertEquals(1,closes)
            assertEquals(AppError.Conflict.STALE_SESSION,state.failure!!.error)
        }
    }
    @Test fun closedOrStaleSessionRequiresExplicitRouteReopen() = runTest {
        for(error in listOf(AppError.Conflict.SESSION_CLOSED,AppError.Conflict.STALE_SESSION,AppError.Conflict.REVISION_CHANGED)) withReader {
            session.inspect={ Outcome.Failure(error) }; open()
            assertEquals(ReaderRetry.OPEN,state.failure!!.retry)
            session.inspect={ Outcome.Success(PageDisplayInfo(it,600,900)) }
            controller.dispatch(ReaderAction.Retry); runCurrent()
            assertEquals(2,opens.size); assertEquals(ReaderPhase.RENDERED,state.phase)
        }
    }
    @Test fun expectedRenderFailureIsRetryableAndCancellationIsDistinct() = runTest {
        for(cancel in listOf(false,true)) withReader {
            session.render={ if(cancel) throw CancellationException("render canceled") else Outcome.Failure(AppError.Resource.RENDER_LIMIT) }
            open()
            if(cancel){ assertEquals(ReaderPhase.CANCELED,state.phase); assertNull(state.failure) }
            else { assertEquals(ReaderStage.RENDER,state.failure!!.stage); assertEquals(ReaderRetry.RENDER,state.failure!!.retry) }
            session.render={ Outcome.Success(TestPage(it).also{page->session.pages+=page}) }
            controller.dispatch(ReaderAction.Retry); runCurrent()
            assertEquals(1,opens.size); assertEquals(ReaderPhase.RENDERED,state.phase)
        }
    }
}
