package com.abdev.partituraspdf.reader

import com.abdev.partituraspdf.core.document.PageIndex
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReaderPresenterTest {
    @Test fun attachmentAndRecompositionDoNotAcknowledgeWithoutSuccessfulDraw() = runTest { withReader {
        open();val presenter=ReaderPresenter()
        try {
            val frame=state.frame!!;repeat(20){presenter.bind(frame)}
            assertTrue(displayed.isEmpty());assertNull(frame.attach());assertTrue(presenter.owns(frame))
            val first=presenter.draw(true){assertEquals(0,session.pages.single().closes)}!!
            assertTrue(presenter.acknowledge(first));assertFalse(presenter.acknowledge(first))
            controller.dispatch(ReaderAction.Presented(first));runCurrent()
            repeat(20){presenter.bind(frame);assertNull(presenter.draw(true){})};runCurrent()
            assertEquals(1,displayed.size);assertEquals(PageIndex(2),state.presentedPage)
        } finally {presenter.detach()}
    } }
    @Test fun invisibleOrDetachedConsumerCannotDrawOrAcknowledge() = runTest { withReader {
        open();val presenter=ReaderPresenter()
        try {
            presenter.bind(state.frame);assertNull(presenter.draw(false){fail("Hidden draw")})
            presenter.detach();presenter.detach();assertNull(presenter.draw(true){fail("Detached draw")})
            assertTrue(displayed.isEmpty());assertTrue(state.frame!!.availability.value)
            presenter.bind(null);assertFalse(presenter.owns(null))
        } finally {presenter.detach()}
    } }
    @Test fun sourceLeaseCannotCloseDuringSynchronousConsumptionEvenIfRetirementBegins() = runTest { withReader {
        open();val presenter=ReaderPresenter();presenter.bind(state.frame)
        try {
            val token=presenter.draw(true){
                controller.dispatch(ReaderAction.Page(3))
                presenter.detach() // Deferred until this draw callback has finished using the source.
                assertEquals(0,session.pages.single().closes)
            }
            assertNull(token);assertEquals(1,session.pages.first().closes)
            runCurrent();assertEquals(PageIndex(3),state.frame!!.request.page.page);assertTrue(displayed.isEmpty())
        } finally {presenter.detach()}
    } }
    @Test fun replacedAndStaleFramesReleaseTheirConsumersAndCannotPublishPresentation() = runTest { withReader {
        open();val presenter=ReaderPresenter();val old=state.frame!!
        try {
            presenter.bind(old);val token=presenter.draw(true){}!!
            controller.dispatch(ReaderAction.Page(3));presenter.bind(null);runCurrent()
            assertFalse(presenter.current(token));presenter.bind(old)
            assertNull(presenter.draw(true){fail("Retired source")})
            presenter.bind(state.frame);val current=presenter.draw(true){}!!
            controller.dispatch(ReaderAction.Presented(token));controller.dispatch(ReaderAction.Presented(current));runCurrent()
            assertEquals(listOf(PageIndex(3)),displayed.map{it.second})
        } finally {presenter.detach()}
    } }
    @Test fun retiringControllerCanCompleteAfterScreenConsumerDetaches() = runTest { withReader {
        open();val presenter=ReaderPresenter();presenter.bind(state.frame)
        try {
            val stop=async{controller.retire()};runCurrent();assertFalse(stop.isCompleted)
            presenter.detach();runCurrent();stop.await()
            assertEquals(1,session.pages.single().closes);assertEquals(0,session.closes)
        } finally {presenter.detach()}
    } }
    @Test fun drawFailureDoesNotAcknowledgeAndStillHonorsPendingDetachment() = runTest { withReader {
        open();val presenter=ReaderPresenter();presenter.bind(state.frame)
        try {
            val failure=IllegalStateException("draw defect")
            assertSame(failure,assertThrows(IllegalStateException::class.java){
                presenter.draw(true){presenter.detach();throw failure}
            })
            assertTrue(state.frame!!.availability.value);assertTrue(displayed.isEmpty())
        } finally {presenter.detach()}
    } }
    @Test fun waitingReplacementConsumerIsNotStrandedWhenOldViewDetaches() = runTest { withReader {
        open();val frame=state.frame!!;val old=ReaderPresenter();val replacement=ReaderPresenter()
        old.bind(frame);replacement.bind(frame);assertFalse(replacement.owns(frame))
        val observing=backgroundScope.launch{frame.availability.collect{if(it)replacement.bind(frame)}}
        try {
            runCurrent();old.detach();runCurrent()
            assertTrue(replacement.owns(frame));assertNull(frame.attach())
            val token=replacement.draw(true){}!!;assertTrue(replacement.current(token))
        } finally {observing.cancel();old.detach();replacement.detach()}
    } }
    @Test fun drawingWithoutSuccessfulPostOrEligibleDeliveryCanBeAcknowledgedOnALaterDraw() = runTest { withReader {
        open();val presenter=ReaderPresenter();presenter.bind(state.frame)
        try {
            val discarded=presenter.draw(true){}!!
            // A failed post or temporarily ineligible view never confirms this candidate.
            assertTrue(displayed.isEmpty())
            val redrawn=presenter.draw(true){}!!;assertSame(discarded,redrawn)
            assertTrue(presenter.acknowledge(redrawn));controller.dispatch(ReaderAction.Presented(redrawn));runCurrent()
            assertNull(presenter.draw(true){});assertEquals(1,displayed.size)
        } finally {presenter.detach()}
    } }
    @Test fun delayedDeliveryFromDestroyedSurfaceCannotAcknowledgeAReplacementConsumer() = runTest { withReader {
        open();val presenter=ReaderPresenter();presenter.bind(state.frame)
        try {
            val queued=presenter.draw(true){}!!;presenter.detach();presenter.bind(state.frame)
            assertFalse(presenter.acknowledge(queued));assertTrue(displayed.isEmpty())
            val fresh=presenter.draw(true){}!!;assertTrue(presenter.acknowledge(fresh))
            controller.dispatch(ReaderAction.Presented(fresh));runCurrent();assertEquals(1,displayed.size)
        } finally {presenter.detach()}
    } }
    @Test fun frameExposesOriginalPageGeometryWithoutReadingOrCopyingBitmap() = runTest { withReader {
        open();assertSame(session.pages.single().displayInfo,state.frame!!.displayInfo)
        assertEquals(600,state.frame!!.displayInfo.width);assertEquals(900,state.frame!!.displayInfo.height)
    } }
}
