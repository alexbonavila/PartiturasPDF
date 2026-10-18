package com.abdev.partituraspdf.reader

import com.abdev.partituraspdf.core.document.PageIndex
import com.abdev.partituraspdf.core.result.*
import com.abdev.partituraspdf.pdf.api.ReaderReady
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReaderOpeningTest {
    @Test fun registeredAndTransientOpenOnlyThroughRouteAndRespectRestoredPage() = runTest {
        for (request in listOf(registeredRequest,transientRequest)) withReader {
            assertEquals(ReaderPhase.IDLE,state.phase)
            open(request,viewport = false)
            assertEquals(listOf(request),opens); assertEquals(ReaderPhase.READY,state.phase)
            assertEquals(PageIndex(2),state.requestedPage); assertNull(state.presentedPage)
            assertEquals(session.info,state.session); assertTrue(session.requests.isEmpty())
            controller.dispatch(ReaderAction.Viewport(ReaderViewport(600,900))); runCurrent()
            assertEquals(ReaderPhase.RENDERED,state.phase)
            assertEquals(PageIndex(2),state.frame!!.request.page.page); assertTrue(displayed.isEmpty())
        }
    }
    @Test fun repeatedOrOlderIntentIsIdempotentAndReusedSequenceCannotOpenAnotherDocument() = runTest { withReader {
        open()
        repeat(20) { controller.open(ReaderOpenIntent(1,registeredRequest)) }
        controller.open(ReaderOpenIntent(0,transientRequest)); runCurrent()
        assertEquals(1,opens.size)
        controller.open(ReaderOpenIntent(1,transientRequest)); runCurrent()
        assertEquals(AppError.Validation.INVALID_DOCUMENT,state.failure!!.error); assertEquals(1,opens.size)
        assertThrows(IllegalArgumentException::class.java) { ReaderOpenIntent(-1, registeredRequest) }
    } }
    @Test fun expectedOpenFailuresArePreservedAndRetryIsExplicit() = runTest {
        val errors: List<AppError> = AppError.Access.entries.filter { it != AppError.Access.NOT_REGISTERED && it != AppError.Access.DOCUMENT_DELETED }
        for (error in errors) withReader {
            opening = { Outcome.Failure(error) }; open()
            assertEquals(ReaderFailure(ReaderStage.OPEN,error,ReaderRetry.OPEN),state.failure)
            controller.open(ReaderOpenIntent(1,registeredRequest)); runCurrent(); assertEquals(1,opens.size)
            opening = { Outcome.Success(ReaderReady(it,session,PageIndex(0))) }
            controller.dispatch(ReaderAction.Retry); runCurrent()
            assertEquals(2,opens.size); assertEquals(PageIndex(0),state.requestedPage)
            assertEquals(ReaderPhase.RENDERED,state.phase)
        }
    }
    @Test fun viewportChangesDuringOpeningDoNotDuplicateOrCancelTheOpen() = runTest { withReader {
        val gate = gate(); opening = { gate.await(); Outcome.Success(ReaderReady(it,session,PageIndex(1))) }
        open(); assertEquals(ReaderPhase.OPENING,state.phase)
        controller.dispatch(ReaderAction.Viewport(ReaderViewport(800,600))); runCurrent()
        gate.complete(Unit); runCurrent()
        assertEquals(1,opens.size); assertEquals(1,session.requests.size)
        assertEquals(800,state.viewport!!.widthPx)
    } }
    @Test fun replacementOpenIsSerializedAndCannotPublishLateOldSession() = runTest { withReader {
        val gate = gate(); var activeOpens = 0; var maximum = 0
        opening = { request ->
            activeOpens++; maximum=maxOf(maximum,activeOpens)
            try {
                if (request==registeredRequest) withContext(NonCancellable) { gate.await() }
                Outcome.Success(ReaderReady(request,session,PageIndex(if(request==registeredRequest) 1 else 4)))
            } finally { activeOpens-- }
        }
        open(); controller.open(ReaderOpenIntent(2,transientRequest)); runCurrent()
        assertEquals(1,opens.size); assertNull(state.session)
        gate.complete(Unit); runCurrent()
        assertEquals(listOf(registeredRequest,transientRequest),opens); assertEquals(1,maximum)
        assertEquals(transientRequest,state.intent!!.request); assertEquals(PageIndex(4),state.requestedPage)
        assertEquals(listOf(4),session.requests.map { it.page.page.value }); assertEquals(0,session.closes)
    } }
    @Test fun inconsistentReadyCannotBecomeAnAcceptedSession() = runTest {
        for (wrongRequest in listOf(false,true)) withReader {
            opening = { Outcome.Success(ReaderReady(if(wrongRequest) transientRequest else it,session,PageIndex(if(wrongRequest) 0 else 99))) }
            open(); assertEquals(AppError.Conflict.STALE_SESSION,state.failure!!.error)
            assertNull(state.session); assertTrue(session.requests.isEmpty()); assertEquals(0,session.closes)
        }
    }
    @Test fun canceledOpeningIsNotAnOperationalFailureOrSuccessAndCanBeRetried() = runTest { withReader {
        opening = { throw CancellationException("canceled route") }; open()
        assertEquals(ReaderPhase.CANCELED,state.phase); assertNull(state.failure)
        opening = { Outcome.Success(ReaderReady(it,session,PageIndex(0))) }
        controller.dispatch(ReaderAction.Retry); runCurrent(); assertEquals(ReaderPhase.RENDERED,state.phase)
    } }
    @Test fun retirementRejectsLateOpenAndAllSubsequentActions() = runTest { withReader {
        val gate=gate(); opening={ withContext(NonCancellable){gate.await()}; Outcome.Success(ReaderReady(it,session,PageIndex(0))) }
        open(); val retirement=async { controller.retire() }; runCurrent(); assertFalse(retirement.isCompleted)
        val before=state
        controller.open(ReaderOpenIntent(2,transientRequest)); controller.resume()
        controller.dispatch(ReaderAction.Page(1)); controller.dispatch(ReaderAction.Retry)
        gate.complete(Unit); runCurrent(); retirement.await()
        assertSame(before,state); assertEquals(ReaderPhase.RETIRED,state.phase)
        assertEquals(1,opens.size); assertTrue(session.requests.isEmpty()); assertEquals(0,routeCloses); assertEquals(0,session.closes)
    } }
}
