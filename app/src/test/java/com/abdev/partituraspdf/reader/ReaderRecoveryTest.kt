package com.abdev.partituraspdf.reader

import com.abdev.partituraspdf.core.document.PageIndex
import com.abdev.partituraspdf.core.result.*
import com.abdev.partituraspdf.pdf.api.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReaderRecoveryTest {
    @Test fun terminalOpenErrorsRequireHostReviewOrANewIntentInsteadOfADeadRetryButton() = runTest {
        val terminal:List<AppError> = AppError.Unsupported.entries + AppError.Validation.entries +
            listOf(AppError.Access.NOT_REGISTERED,AppError.Access.DOCUMENT_DELETED,AppError.Resource.INPUT_LIMIT)
        for(error in terminal) withReader {
            opening={Outcome.Failure(error)};open()
            assertEquals(ReaderFailure(ReaderStage.OPEN,error,ReaderRetry.NONE),state.failure)
            assertEquals(ReaderRetry.NONE,readerRetry(state))
            controller.dispatch(ReaderAction.Retry);runCurrent();assertEquals(1,opens.size)
            opening={Outcome.Success(ReaderReady(it,session,PageIndex(0)))}
            controller.open(ReaderOpenIntent(2,transientRequest));runCurrent()
            assertEquals(ReaderPhase.RENDERED,state.phase);assertNull(state.failure)
        }
    }
    @Test fun recoveryMatrixKeepsTypedStagesAndNeverPromisesToRepairUnsupportedInput() {
        val terminal:List<AppError> = AppError.Unsupported.entries + AppError.Validation.entries +
            listOf(AppError.Access.NOT_REGISTERED,AppError.Access.DOCUMENT_DELETED,AppError.Resource.INPUT_LIMIT)
        for(stage in ReaderStage.entries) for(error in terminal) assertEquals(ReaderRetry.NONE,readerFailureRetry(error,stage))
        for(error in AppError.Conflict.entries) for(stage in ReaderStage.entries) assertEquals(ReaderRetry.OPEN,readerFailureRetry(error,stage))
        val access=AppError.Access.entries.filter{it !in listOf(AppError.Access.NOT_REGISTERED,AppError.Access.DOCUMENT_DELETED)}
        for(error in access){
            assertEquals(ReaderRetry.OPEN,readerFailureRetry(error,ReaderStage.OPEN))
            assertEquals(ReaderRetry.OPEN,readerFailureRetry(error,ReaderStage.RENDER))
            assertEquals(if(error==AppError.Access.PRIVATE_STORAGE_UNAVAILABLE)ReaderRetry.PRESENTATION else ReaderRetry.OPEN,
                readerFailureRetry(error,ReaderStage.PRESENTATION))
        }
        for(error in listOf(AppError.Resource.RENDER_LIMIT,AppError.Resource.INSUFFICIENT_STORAGE)){
            assertEquals(ReaderRetry.OPEN,readerFailureRetry(error,ReaderStage.OPEN))
            for(stage in listOf(ReaderStage.PLAN,ReaderStage.RENDER)) assertEquals(ReaderRetry.RENDER,readerFailureRetry(error,stage))
            assertEquals(ReaderRetry.OPEN,readerFailureRetry(error,ReaderStage.PRESENTATION))
        }
    }
    @Test fun malformedOrUnsupportedRenderCannotBeSilentlyRetriedByResize() = runTest {
        for(error in listOf(AppError.Validation.INVALID_DOCUMENT,AppError.Unsupported.DOCUMENT_FORMAT,AppError.Unsupported.PASSWORD_PROTECTED)) withReader {
            session.inspect={Outcome.Failure(error)};open();assertNull(state.frame);assertNull(state.session)
            controller.dispatch(ReaderAction.Viewport(ReaderViewport(800,600)));controller.dispatch(ReaderAction.Retry);runCurrent()
            assertEquals(1,session.inspections.size);assertEquals(error,state.failure!!.error)
            assertEquals(ReaderRetry.NONE,readerRetry(state));assertEquals(ReaderPhase.ERROR,state.phase)
        }
    }
    @Test fun detachedCheckpointCannotRetryUntilANewConsumerActuallyReportsDrawing() = runTest { withReader {
        open();val old=attach();acknowledge={Outcome.Failure(AppError.Access.PRIVATE_STORAGE_UNAVAILABLE)}
        controller.dispatch(ReaderAction.Presented(old));runCurrent()
        assertEquals(ReaderRetry.PRESENTATION,readerRetry(state));assertEquals(PageIndex(2),state.presentedPage)
        old.detached();assertEquals(ReaderRetry.NONE,readerRetry(state))
        val fresh=attach();controller.dispatch(ReaderAction.Retry);runCurrent();assertEquals(1,displayed.size)
        assertEquals(ReaderRetry.NONE,readerRetry(state))
        controller.dispatch(ReaderAction.Presented(fresh));runCurrent()
        assertEquals(ReaderRetry.PRESENTATION,readerRetry(state));assertEquals(1,displayed.size)
        acknowledge={Outcome.Success(Unit)};controller.dispatch(ReaderAction.Retry);runCurrent()
        assertEquals(2,displayed.size);assertEquals(ReaderCheckpoint.Unconfirmed(Outcome.Success(Unit)),state.checkpoint)
        assertEquals(ReaderRetry.NONE,readerRetry(state));assertEquals(1,session.requests.size)
    } }
    @Test fun checkpointFailureAfterRouteClosureInvalidatesBorrowedSessionAndRequiresReopening() = runTest {
        for(error in AppError.Conflict.entries) withReader {
            open();val old=attach();acknowledge={Outcome.Failure(error)}
            controller.dispatch(ReaderAction.Presented(old));runCurrent()
            assertNull(state.frame);assertNull(state.session);assertEquals(ReaderRetry.OPEN,readerRetry(state))
            assertEquals(error,state.failure!!.error);assertEquals(ReaderStage.PRESENTATION,state.failure!!.stage)
            old.detached();runCurrent()
            val fresh=TestSession("replacement");opening={Outcome.Success(ReaderReady(it,fresh,PageIndex(0)))}
            controller.dispatch(ReaderAction.Retry);runCurrent()
            assertEquals(2,opens.size);assertEquals(1,fresh.requests.size);assertEquals(1,session.requests.size)
            assertEquals(0,session.closes);assertEquals(0,routeCloses)
        }
    }
    @Test fun replacingFailedCheckpointNeverRetriesTheOldPage() = runTest { withReader {
        open();val old=attach();acknowledge={Outcome.Failure(AppError.Access.PRIVATE_STORAGE_UNAVAILABLE)}
        controller.dispatch(ReaderAction.Presented(old));runCurrent();controller.dispatch(ReaderAction.Page(5))
        controller.dispatch(ReaderAction.Retry);controller.dispatch(ReaderAction.Presented(old));old.detached();runCurrent()
        assertEquals(listOf(PageIndex(2)),displayed.map{it.second});assertEquals(ReaderRetry.NONE,readerRetry(state))
        val fresh=attach();acknowledge={Outcome.Success(Unit)};controller.dispatch(ReaderAction.Presented(fresh));runCurrent()
        assertEquals(listOf(PageIndex(2),PageIndex(5)),displayed.map{it.second});assertNull(state.failure)
    } }
    @Test fun transientAndRegisteredSchedulingSuccessRemainUnconfirmedNotDurable() = runTest {
        for(request in listOf(registeredRequest,transientRequest)) withReader {
            open(request);val consumer=attach();controller.dispatch(ReaderAction.Presented(consumer));runCurrent()
            assertEquals(ReaderCheckpoint.Unconfirmed(Outcome.Success(Unit)),state.checkpoint)
            assertEquals(ReaderPhase.VISIBLE,state.phase);assertEquals(request,state.intent!!.request)
            assertEquals(0,backgrounds);assertEquals(0,routeCloses)
        }
    }
    @Test fun canceledCheckpointRetainsOnlyCurrentDrawRecoveryAndPauseDisablesIt() = runTest { withReader {
        open();val consumer=attach();acknowledge={throw CancellationException("schedule canceled")}
        controller.dispatch(ReaderAction.Presented(consumer));runCurrent()
        assertEquals(ReaderRetry.PRESENTATION,readerRetry(state));assertNull(state.failure)
        consumer.detached();assertEquals(ReaderRetry.NONE,readerRetry(state))
        controller.quiesce(com.abdev.partituraspdf.core.foundation.ReaderTrim.LOW_MEMORY)
        assertEquals(ReaderRetry.NONE,readerRetry(state));controller.retire();assertEquals(ReaderRetry.NONE,readerRetry(state))
    } }
    @Test fun uiRecoveryRequiresItsActualIntentSessionOrDrawCapability() {
        val error=AppError.Resource.RENDER_LIMIT
        fun snapshot(retry:ReaderRetry)=ReaderUiState(phase=ReaderPhase.ERROR,failure=ReaderFailure(ReaderStage.RENDER,error,retry))
        for(retry in ReaderRetry.entries) assertEquals(ReaderRetry.NONE,readerRetry(snapshot(retry)))
        assertEquals(ReaderRetry.NONE,readerRetry(ReaderUiState(phase=ReaderPhase.CANCELED)))
        assertEquals(ReaderRetry.NONE,readerRetry(snapshot(ReaderRetry.OPEN).copy(phase=ReaderPhase.RETIRED)))
    }
}
