package com.abdev.partituraspdf.reader

import com.abdev.partituraspdf.core.document.PageIndex
import com.abdev.partituraspdf.core.result.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReaderPresentationTest {
    @Test fun acknowledgementRetryCanUseAFreshConsumerAfterConfigurationDetachment() = runTest { withReader {
        open();val old=attach();acknowledge={Outcome.Failure(AppError.Access.PRIVATE_STORAGE_UNAVAILABLE)}
        controller.dispatch(ReaderAction.Presented(old));runCurrent();old.detached()
        val fresh=attach();controller.dispatch(ReaderAction.Presented(fresh));runCurrent()
        assertEquals(1,displayed.size) // Reattachment alone must not retry an uncertain checkpoint.
        acknowledge={Outcome.Success(Unit)};controller.dispatch(ReaderAction.Retry);runCurrent()
        assertEquals(2,displayed.size);assertEquals(ReaderPhase.VISIBLE,state.phase)
        assertNull(state.failure)
    } }
    @Test fun renderDoesNotAcknowledgeAndOnlyAttachedConsumerCanReportPresentation() = runTest { withReader {
        open(); assertTrue(displayed.isEmpty()); assertNull(state.presentedPage)
        val consumer=attach(); assertNull(state.frame!!.attach())
        assertEquals(state.frame!!.request,consumer.request)
        controller.dispatch(ReaderAction.Presented(consumer)); runCurrent()
        assertEquals(ReaderPhase.VISIBLE,state.phase); assertEquals(PageIndex(2),state.presentedPage)
        assertEquals(listOf(session.info.id to PageIndex(2)),displayed)
        assertEquals(ReaderCheckpoint.Unconfirmed(Outcome.Success(Unit)),state.checkpoint)
        repeat(10){controller.dispatch(ReaderAction.Presented(consumer))}; runCurrent()
        assertEquals(1,displayed.size)
    } }
    @Test fun detachedConsumerCannotAcknowledgeOrReadTheBitmap() = runTest { withReader {
        open(); val consumer=attach(); consumer.detached(); consumer.detached()
        controller.dispatch(ReaderAction.Presented(consumer)); runCurrent()
        assertTrue(displayed.isEmpty()); assertEquals(0,session.pages.single().closes)
        assertEquals("Presentation is detached",assertThrows(IllegalStateException::class.java){consumer.bitmap}.message)
        val replacement=attach()
        controller.dispatch(ReaderAction.Presented(replacement)); runCurrent(); assertEquals(1,displayed.size)
    } }
    @Test fun frameReplacementWaitsForExplicitConsumerDetachmentBeforeNewAllocation() = runTest { withReader {
        open(); val old=state.frame!!; val consumer=attach()
        controller.dispatch(ReaderAction.Page(3)); runCurrent()
        assertNull(state.frame); assertNull(old.attach()); assertEquals(0,session.pages.single().closes)
        assertEquals(1,session.requests.size)
        controller.dispatch(ReaderAction.Presented(consumer)); runCurrent(); assertTrue(displayed.isEmpty())
        consumer.detached(); runCurrent()
        assertEquals(1,session.pages.first().closes); assertEquals(2,session.requests.size)
        val new=attach(); controller.dispatch(ReaderAction.Presented(consumer)); runCurrent()
        assertTrue(displayed.isEmpty())
        controller.dispatch(ReaderAction.Presented(new)); runCurrent()
        assertEquals(listOf(session.info.id to PageIndex(3)),displayed)
    } }
    @Test fun retirementWaitsWithoutTimeoutAndReleasesExactlyOnce() = runTest { withReader {
        open(); val consumer=attach(); val frame=state.frame!!
        val first=async {controller.retire()}; val second=async {controller.retire()}; runCurrent()
        advanceTimeBy(1_000_000); runCurrent()
        assertFalse(first.isCompleted); assertFalse(second.isCompleted); assertEquals(0,session.pages.single().closes)
        assertNull(frame.attach()); assertEquals(ReaderPhase.RETIRED,state.phase)
        consumer.detached(); consumer.detached(); runCurrent()
        assertEquals(Outcome.Success(Unit),first.await()); second.await()
        assertEquals(1,session.pages.single().closes); assertEquals(0,session.closes)
    } }
    @Test fun typedAcknowledgementFailureIsObservableAndExplicitRetryDoesNotRerender() = runTest { withReader {
        open(); val consumer=attach()
        acknowledge={Outcome.Failure(AppError.Access.PRIVATE_STORAGE_UNAVAILABLE)}
        controller.dispatch(ReaderAction.Presented(consumer)); runCurrent()
        assertEquals(ReaderStage.PRESENTATION,state.failure!!.stage)
        assertEquals(ReaderCheckpoint.Unconfirmed(Outcome.Failure(AppError.Access.PRIVATE_STORAGE_UNAVAILABLE)),state.checkpoint)
        assertEquals(PageIndex(2),state.presentedPage); assertEquals(1,displayed.size)
        controller.dispatch(ReaderAction.Presented(consumer)); runCurrent(); assertEquals(1,displayed.size)
        acknowledge={Outcome.Success(Unit)}
        controller.dispatch(ReaderAction.Retry); runCurrent()
        assertEquals(2,displayed.size); assertEquals(1,session.requests.size)
        assertEquals(ReaderPhase.VISIBLE,state.phase)
    } }
    @Test fun canceledAcknowledgementRemainsUnconfirmedAndCanBeExplicitlyRetried() = runTest { withReader {
        open(); val consumer=attach(); acknowledge={throw CancellationException("uncertain schedule")}
        controller.dispatch(ReaderAction.Presented(consumer)); runCurrent()
        assertEquals(ReaderPhase.CANCELED,state.phase); assertEquals(ReaderCheckpoint.Canceled,state.checkpoint)
        acknowledge={Outcome.Success(Unit)}; controller.dispatch(ReaderAction.Retry); runCurrent()
        assertEquals(2,displayed.size); assertEquals(ReaderPhase.VISIBLE,state.phase)
    } }
    @Test fun lastPresentedPageIsPreservedWhileNewPageIsOnlyRequestedOrRendered() = runTest { withReader {
        open(); val consumer=attach(); controller.dispatch(ReaderAction.Presented(consumer));runCurrent()
        controller.dispatch(ReaderAction.Page(4));runCurrent(); consumer.detached();runCurrent()
        assertEquals(PageIndex(4),state.requestedPage); assertEquals(PageIndex(2),state.presentedPage)
        assertEquals(ReaderPhase.RENDERED,state.phase); assertEquals(1,displayed.size)
    } }
    @Test fun acknowledgedFrameDoesNotGainDuplicateCheckpointByReattachingConsumer() = runTest { withReader {
        open(); val one=attach(); controller.dispatch(ReaderAction.Presented(one));runCurrent();one.detached()
        val two=attach();controller.dispatch(ReaderAction.Presented(two));runCurrent()
        assertEquals(1,displayed.size)
    } }
}
