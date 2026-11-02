package com.abdev.partituraspdf.reader

import com.abdev.partituraspdf.core.document.*
import com.abdev.partituraspdf.core.foundation.ReaderTrim
import com.abdev.partituraspdf.core.result.*
import com.abdev.partituraspdf.pdf.api.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ReaderGuardedNavigationTest {
    @Test fun sameIntentReplacementRejectsCapturedSessionThenAcceptsCurrentSession() = runTest { withReader {
        open()
        val intent=state.intent!!;val captured=state.session!!
        controller.quiesce(ReaderTrim.LOW_MEMORY);active=null
        val replacement=TestSession("replacement",3)
        opening={Outcome.Success(ReaderReady(it,replacement,PageIndex(0)))}
        controller.resume();runCurrent()
        assertEquals(intent,state.intent);assertEquals(replacement.info,state.session)
        val before=state;val oldRenders=session.requests.size;val newRenders=replacement.requests.size
        controller.dispatch(ReaderAction.PageIfCurrent(1,intent,captured));runCurrent()
        assertSame(before,state);assertEquals(oldRenders,session.requests.size);assertEquals(newRenders,replacement.requests.size)
        controller.dispatch(ReaderAction.PageIfCurrent(1,intent,replacement.info));runCurrent()
        assertEquals(PageIndex(1),state.requestedPage);assertEquals(newRenders+1,replacement.requests.size)
        assertTrue(displayed.isEmpty());assertEquals(2,opens.size)
        assertEquals(0,replacement.closes)
    } }
    @Test fun wrongExpectedFieldsAndInvalidIndicesHaveNoSideEffects() = runTest { withReader {
        open();val before=state;val intent=state.intent!!;val info=state.session!!
        val revision=DocumentRevision.VerifiedSnapshot(DocumentRef(DocumentId("test-document")),SnapshotEvidence("a".repeat(64),1))
        val invalid=listOf(ReaderAction.PageIfCurrent(3,intent.copy(sequence=0),info),
            ReaderAction.PageIfCurrent(3,intent.copy(request=transientRequest),info),
            ReaderAction.PageIfCurrent(3,intent,info.copy(id=ReadSessionId("different"))),
            ReaderAction.PageIfCurrent(3,intent,info.copy(sourceGeneration=8)),
            ReaderAction.PageIfCurrent(3,intent,info.copy(pageCount=7)),
            ReaderAction.PageIfCurrent(3,intent,info.copy(revision=revision)),
            ReaderAction.PageIfCurrent(-1,intent,info),ReaderAction.PageIfCurrent(info.pageCount,intent,info),
            ReaderAction.PageIfCurrent(Int.MAX_VALUE,intent,info))
        for (action in invalid) {
            controller.dispatch(action);runCurrent()
            assertSame(before,state);assertEquals(1,session.requests.size);assertEquals(1,opens.size)
            assertTrue(displayed.isEmpty());assertEquals(0,session.pages.single().closes)
        }
    } }
    @Test fun authorityIsCheckedEvenWhenPublishedMetadataStillMatchesExpectation() = runTest { withReader {
        var authoritative=session.info
        val adapter=object:PdfReadSession by session { override val info:ReadSessionInfo get()=authoritative }
        opening={Outcome.Success(ReaderReady(it,adapter,PageIndex(2)))}
        open();val intent=state.intent!!;val captured=state.session!!;val before=state
        authoritative=authoritative.copy(sourceGeneration=8)
        controller.dispatch(ReaderAction.PageIfCurrent(3,intent,captured));runCurrent()
        assertSame(before,state);assertEquals(1,session.requests.size);assertTrue(displayed.isEmpty())
    } }
    @Test fun missingQuiescedRetiredAndErrorStatesCannotAcceptGuardedRequests() = runTest { withReader {
        val synthetic=ReaderAction.PageIfCurrent(0,ReaderOpenIntent(1,registeredRequest),session.info)
        val idle=state;controller.dispatch(synthetic);runCurrent();assertSame(idle,state);assertTrue(opens.isEmpty())
        open();val action=ReaderAction.PageIfCurrent(3,state.intent!!,state.session!!)
        controller.dispatch(ReaderAction.Page(-1));runCurrent() // Existing unguarded invalid-page behavior unchanged.
        val failed=state;assertEquals(ReaderPhase.ERROR,failed.phase)
        controller.dispatch(action);runCurrent();assertSame(failed,state)
        controller.quiesce(ReaderTrim.BACKGROUND)
        val paused=state;controller.dispatch(action);runCurrent();assertSame(paused,state)
        controller.retire();val retired=state
        controller.dispatch(action);runCurrent();assertSame(retired,state)
        assertEquals(1,session.requests.size);assertTrue(displayed.isEmpty())
    } }
    @Test fun validGuardedJumpSchedulesOnceAndWaitsForGraphicalAcknowledgment() = runTest { withReader {
        open();val intent=state.intent!!;val info=state.session!!
        controller.dispatch(ReaderAction.PageIfCurrent(3,intent,info));runCurrent()
        assertEquals(2,session.requests.size);assertEquals(PageIndex(3),state.requestedPage)
        assertNull(state.presentedPage);assertTrue(displayed.isEmpty());assertEquals(1,opens.size)
        val before=state
        repeat(3) {controller.dispatch(ReaderAction.PageIfCurrent(3,intent,info))};runCurrent()
        assertSame(before,state);assertEquals(2,session.requests.size);assertTrue(displayed.isEmpty())
        val presenter=ReaderPresenter()
        try {
            presenter.bind(state.frame);val presentation=presenter.draw(true){}!!
            assertTrue(presenter.acknowledge(presentation))
            controller.dispatch(ReaderAction.Presented(presentation));runCurrent()
            assertEquals(PageIndex(3),state.presentedPage)
            assertEquals(listOf(info.id to PageIndex(3)),displayed)
        } finally {presenter.detach()}
        controller.dispatch(ReaderAction.Step(1));controller.dispatch(ReaderAction.Step(1));runCurrent()
        assertEquals(PageIndex(5),state.requestedPage);assertEquals(1,opens.size)
    } }
}
