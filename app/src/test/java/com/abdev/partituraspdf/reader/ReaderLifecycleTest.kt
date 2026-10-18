package com.abdev.partituraspdf.reader

import com.abdev.partituraspdf.core.document.PageIndex
import com.abdev.partituraspdf.core.foundation.ReaderTrim
import com.abdev.partituraspdf.core.result.*
import com.abdev.partituraspdf.pdf.api.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReaderLifecycleTest {
    @Test fun alreadyCanceledOwnerCannotLeaveRetirementWaitingForAnUnstartedWorker() = runTest {
        val parent=SupervisorJob();parent.cancel()
        val f=ReaderFixture(this,CoroutineScope(coroutineContext+parent))
        val stop=async {runCatching{f.controller.retire()}}
        try {
            runCurrent();assertTrue(stop.isCompleted)
            assertTrue(stop.await().exceptionOrNull() is CancellationException)
            assertEquals(ReaderPhase.RETIRED,f.state.phase);assertTrue(f.opens.isEmpty())
        } finally {stop.cancelAndJoin()}
    }
    @Test fun cleanupDefectCannotLeaveTheWorkerAliveOrRetryLeaseClosure() = runTest {
        val parent=SupervisorJob();val errors=mutableListOf<Throwable>()
        val f=ReaderFixture(this,CoroutineScope(coroutineContext+parent+CoroutineExceptionHandler{_,t->errors+=t}))
        val defect=IllegalStateException("lease cleanup defect");var closes=0
        f.session.render={request->Outcome.Success(object:RenderedPage{
            override val request=request
            override val displayInfo=PageDisplayInfo(request.page,600,900)
            override val bitmap:android.graphics.Bitmap get()=error("Not used")
            override fun close(){closes++;throw defect}
        })}
        try {
            f.open();val stop=async{runCatching{f.controller.retire()}};runCurrent()
            assertEquals(ReaderPhase.RETIRED,f.state.phase);assertTrue(stop.await().isFailure)
            assertEquals(1,errors.size);assertTrue(runCatching{f.controller.retire()}.isFailure)
            assertEquals(1,closes);assertEquals(0,f.session.closes);assertTrue(parent.isActive)
        } finally {parent.cancel();runCurrent()}
    }
    @Test fun resumeAfterFailedOpenPreservesExplicitRecoveryInsteadOfGettingStuckReady() = runTest { withReader {
        opening={Outcome.Failure(AppError.Access.PERMISSION_REVOKED)};open()
        controller.quiesce(ReaderTrim.BACKGROUND);controller.resume();runCurrent()
        assertEquals(ReaderPhase.ERROR,state.phase)
        assertEquals(ReaderFailure(ReaderStage.OPEN,AppError.Access.PERMISSION_REVOKED,ReaderRetry.OPEN),state.failure)
        assertEquals(1,opens.size)
        opening={Outcome.Success(ReaderReady(it,session,PageIndex(0)))}
        controller.dispatch(ReaderAction.Retry);runCurrent();assertEquals(ReaderPhase.RENDERED,state.phase)
    } }
    @Test fun resizeAfterKnownClosedSessionCannotInvokeItAgainOrEraseReopenRecovery() = runTest { withReader {
        session.render={Outcome.Failure(AppError.Conflict.SESSION_CLOSED)};open()
        controller.dispatch(ReaderAction.Viewport(ReaderViewport(700,800)));runCurrent()
        assertEquals(1,session.requests.size);assertNull(state.session)
        assertEquals(ReaderRetry.OPEN,state.failure!!.retry)
        controller.dispatch(ReaderAction.Page(3));runCurrent()
        assertEquals(ReaderRetry.OPEN,state.failure!!.retry)
        session.render={Outcome.Success(TestPage(it).also {p->session.pages+=p})}
        controller.dispatch(ReaderAction.Retry);runCurrent()
        assertEquals(2,opens.size);assertEquals(ReaderPhase.RENDERED,state.phase)
    } }
    @Test fun everyTrimReleasesPageAndHostOwnsFlushAndSessionClosure() = runTest {
        for(trim in ReaderTrim.entries) withReader {
            open();val consumer=attach()
            val quiescence=async {controller.quiesce(trim)};runCurrent()
            assertFalse(quiescence.isCompleted);assertEquals(ReaderPhase.QUIESCED,state.phase)
            assertEquals(0,routeCloses);assertEquals(0,backgrounds);assertEquals(0,session.closes)
            consumer.detached();runCurrent();assertEquals(Outcome.Success(Unit),quiescence.await())
            assertEquals(1,session.pages.single().closes)
            route.background()
            if(trim!=ReaderTrim.LOW_MEMORY) route.close()
            assertEquals(1,backgrounds);assertEquals(if(trim==ReaderTrim.LOW_MEMORY) 0 else 1,routeCloses)
            controller.resume();controller.resume();runCurrent()
            assertEquals(ReaderPhase.RENDERED,state.phase)
            assertEquals(if(trim==ReaderTrim.LOW_MEMORY) 1 else 2,opens.size)
            assertEquals(1,currentCalls);assertEquals(0,session.closes)
        }
    }
    @Test fun quiescenceWaitsForDelayedNativeCancellationAndClosesLateSuccess() = runTest { withReader {
        val gate=gate();session.render={ request ->
            withContext(NonCancellable){gate.await()};Outcome.Success(TestPage(request).also{session.pages+=it})
        }
        open();val quiescence=async{controller.quiesce(ReaderTrim.BACKGROUND)};runCurrent()
        controller.resume();runCurrent();assertFalse(quiescence.isCompleted);assertEquals(1,session.requests.size)
        gate.complete(Unit);runCurrent();quiescence.await()
        assertNull(state.frame);assertEquals(1,session.pages.single().closes)
        assertEquals(0,routeCloses);assertEquals(0,session.closes)
    } }
    @Test fun cooperativePendingRenderIsCanceledWithoutPublishingOrCheckpointing() = runTest { withReader {
        var canceled=false;session.render={try{awaitCancellation()}finally{canceled=true}}
        open();val q=async{controller.quiesce(ReaderTrim.LOW_MEMORY)};runCurrent();q.await()
        assertTrue(canceled);assertNull(state.frame);assertTrue(displayed.isEmpty())
        assertEquals(ReaderPhase.QUIESCED,state.phase)
    } }
    @Test fun pausedOpenRetriesOnResumeOnlyAndDoesNotBorrowItsLateSession() = runTest { withReader {
        val gate=gate();opening={withContext(NonCancellable){gate.await()};Outcome.Success(ReaderReady(it,session,PageIndex(1)))}
        open();val q=async{controller.quiesce(ReaderTrim.BACKGROUND)};runCurrent()
        gate.complete(Unit);runCurrent();q.await();assertNull(state.session)
        route.close();controller.resume();runCurrent()
        assertEquals(2,opens.size);assertEquals(1,session.requests.size);assertEquals(0,session.closes)
    } }
    @Test fun newIntentWhilePausedIsDeferredAndSupersedesOldResume() = runTest { withReader {
        open();controller.quiesce(ReaderTrim.LOW_MEMORY)
        controller.open(ReaderOpenIntent(2,transientRequest));controller.dispatch(ReaderAction.Page(7));runCurrent()
        assertEquals(1,opens.size);assertNull(state.session);assertEquals(ReaderPhase.QUIESCED,state.phase)
        controller.resume();runCurrent();assertEquals(listOf(registeredRequest,transientRequest),opens)
        assertEquals(PageIndex(2),state.requestedPage);assertEquals(0,currentCalls)
    } }
    @Test fun externalRouteReplacementCannotRestoreAnUnrelatedOrDifferentSession() = runTest {
        for(wrongRequest in listOf(false,true)) withReader {
            open();controller.quiesce(ReaderTrim.LOW_MEMORY)
            active=ReaderReady(if(wrongRequest)transientRequest else registeredRequest,TestSession("other"),PageIndex(0))
            controller.resume();runCurrent()
            assertEquals(ReaderPhase.ERROR,state.phase);assertEquals(AppError.Conflict.STALE_SESSION,state.failure!!.error)
            assertNull(state.session);assertNull(state.frame);assertEquals(1,session.requests.size)
        }
    }
    @Test fun closedRouteIsReopenedRatherThanReusingOldNativeSession() = runTest { withReader {
        open();controller.quiesce(ReaderTrim.LOW_MEMORY);active=null
        val newSession=TestSession("fresh",count=3)
        opening={Outcome.Success(ReaderReady(it,newSession,PageIndex(0)))}
        controller.resume();runCurrent()
        assertEquals(newSession.info,state.session);assertEquals(1,newSession.requests.size)
        assertEquals(1,session.requests.size);assertEquals(0,session.closes)
    } }
    @Test fun idleLifecycleAndRepeatedRetirementNeverCreateRouteOperations() = runTest { withReader {
        controller.dispatch(ReaderAction.Retry);controller.resume();runCurrent()
        controller.quiesce(ReaderTrim.CRITICAL);controller.quiesce(ReaderTrim.LOW_MEMORY)
        controller.resume();runCurrent();assertEquals(ReaderPhase.IDLE,state.phase)
        assertTrue(opens.isEmpty());assertEquals(0,currentCalls)
        controller.retire();controller.retire();controller.quiesce(ReaderTrim.BACKGROUND)
        controller.open(ReaderOpenIntent(1,registeredRequest));controller.dispatch(ReaderAction.Viewport(ReaderViewport(300,400)))
        runCurrent();assertEquals(ReaderPhase.RETIRED,state.phase);assertTrue(opens.isEmpty())
    } }
    @Test fun unexpectedProgrammingDefectsPropagateAndRetireOwnedWork() = runTest {
        for(mode in 0..3) {
            val parent=SupervisorJob();val errors=mutableListOf<Throwable>()
            val f=ReaderFixture(this,CoroutineScope(coroutineContext+parent+CoroutineExceptionHandler{_,t->errors+=t}))
            val defect=IllegalStateException("broken dependency")
            when(mode){
                0->f.opening={throw defect}
                1->f.session.inspect={throw defect}
                2->f.session.render={throw defect}
                3->f.acknowledge={throw defect}
            }
            f.open()
            if(mode==3){val p=f.attach();f.controller.dispatch(ReaderAction.Presented(p));runCurrent();p.detached()}
            runCurrent()
            // Coroutine stack-trace recovery can copy the exception and retain it as the cause.
            assertEquals(1,errors.size)
            assertTrue(generateSequence(errors.single()){it.cause}.any{it===defect})
            assertEquals(ReaderPhase.RETIRED,f.state.phase)
            assertNull(f.state.failure);assertTrue(parent.isActive);assertEquals(0,f.session.closes)
            parent.cancel()
        }
    }
    @Test fun ownerCancellationDoesNotLeakPublishedUnattachedLease() = runTest {
        val parent=SupervisorJob();val f=ReaderFixture(this,CoroutineScope(coroutineContext+parent))
        f.open();parent.cancel();runCurrent()
        assertEquals(ReaderPhase.RETIRED,f.state.phase);assertEquals(1,f.session.pages.single().closes)
        assertEquals(0,f.session.closes)
    }
    @Test fun parentCancellationWaitsForNonCooperativeNativeOutputAndDisposesIt() = runTest {
        val parent=SupervisorJob();val f=ReaderFixture(this,CoroutineScope(coroutineContext+parent))
        val gate=f.gate()
        f.session.render={request->withContext(NonCancellable){gate.await()};Outcome.Success(TestPage(request).also{f.session.pages+=it})}
        f.open();parent.cancel();runCurrent();assertNull(f.state.frame)
        gate.complete(Unit);runCurrent()
        assertEquals(1,f.session.pages.single().closes);assertEquals(ReaderPhase.RETIRED,f.state.phase)
        assertEquals(0,f.session.closes)
    }
    @Test fun selfCanceledOpenRemainsRetryableAcrossPauseWithoutInventingSuccess() = runTest { withReader {
        opening={throw CancellationException("canceled")};open()
        controller.quiesce(ReaderTrim.LOW_MEMORY);controller.resume();runCurrent()
        assertEquals(ReaderPhase.CANCELED,state.phase);assertNull(state.failure)
        opening={Outcome.Success(ReaderReady(it,session,PageIndex(0)))}
        controller.dispatch(ReaderAction.Retry);runCurrent();assertEquals(ReaderPhase.RENDERED,state.phase)
    } }
    @Test fun cancellationDuringResumeCheckIsNotMisreadAsAClosedRouteAndReopened() = runTest { withReader {
        open();controller.quiesce(ReaderTrim.LOW_MEMORY)
        current={throw CancellationException("check canceled")};controller.resume();runCurrent()
        assertEquals(ReaderPhase.CANCELED,state.phase);assertEquals(1,opens.size)
        current={active};controller.dispatch(ReaderAction.Retry);runCurrent()
        assertEquals(2,opens.size);assertEquals(ReaderPhase.RENDERED,state.phase)
    } }
}
