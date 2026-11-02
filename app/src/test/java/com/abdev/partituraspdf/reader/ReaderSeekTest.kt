package com.abdev.partituraspdf.reader

import com.abdev.partituraspdf.core.document.PageIndex
import com.abdev.partituraspdf.core.preferences.PageFit
import com.abdev.partituraspdf.core.result.AppError
import com.abdev.partituraspdf.pdf.api.ReadSessionId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

/** Synthetic presenter acknowledgments, not Android display/runtime evidence. */
@OptIn(ExperimentalCoroutinesApi::class)
class ReaderSeekTest {
    private fun ReaderFixture.acknowledge(presenter: ReaderPresenter) {
        presenter.bind(state.frame)
        val consumer = presenter.draw(true) {}!!
        assertTrue(presenter.acknowledge(consumer))
        controller.dispatch(ReaderAction.Presented(consumer))
        test.runCurrent()
    }

    @Test fun renderedRequestedPageIsNotAnEligiblePresentedPage() = runTest { withReader {
        assertNull(readerSeekTarget(state))
        open();assertEquals(ReaderPhase.RENDERED, state.phase)
        assertNull(readerSeekTarget(state))
        val presenter = ReaderPresenter()
        try {
            acknowledge(presenter)
            val target = readerSeekTarget(state)!!
            assertEquals(3, target.presentedNumber)
            assertEquals(readerNavigationContext(state), target.context)
            assertEquals(target, target.copy());assertEquals(target.hashCode(), target.copy().hashCode())
            assertNotEquals(target, target.copy(presentedNumber = 1))
            assertTrue(target.toString().contains("ReaderSeekTarget"))
        } finally { presenter.detach() }
    } }

    @Test fun unavailableInconsistentOrDetachedPresentationCannotStartSeek() = runTest { withReader {
        open();val presenter = ReaderPresenter()
        try {
            acknowledge(presenter)
            val visible = state
            for (phase in ReaderPhase.entries.filter { it != ReaderPhase.VISIBLE })
                assertNull(readerSeekTarget(visible.copy(phase = phase)))
            assertNull(readerSeekTarget(visible.copy(intent = null)))
            assertNull(readerSeekTarget(visible.copy(session = null)))
            assertNull(readerSeekTarget(visible.copy(frame = null)))
            assertNull(readerSeekTarget(visible.copy(presentedPage = null)))
            assertNull(readerSeekTarget(visible.copy(presentedPage = PageIndex(0))))
            assertNull(readerSeekTarget(visible.copy(session = session.info.copy(id = ReadSessionId("replacement")))))
            assertNull(readerSeekTarget(visible.copy(checkpointConsumer = null)))
            assertNull(readerSeekTarget(visible.copy(failure = ReaderFailure(ReaderStage.RENDER, AppError.Access.PROVIDER_UNAVAILABLE, ReaderRetry.OPEN))))
            val otherFrame = ReaderFrame(TestPage(session.requests.single()))
            val otherConsumer = otherFrame.attach()!!
            try { assertNull(readerSeekTarget(visible.copy(checkpointConsumer = otherConsumer))) }
            finally { otherConsumer.detached();otherFrame.requestRetirement() }
            presenter.detach()
            assertNull(readerSeekTarget(visible))
        } finally { presenter.detach() }
    } }

    @Test fun selectionValidationNeverNavigatesAndMapsFirstAndLastExactly() = runTest { withReader {
        open();val presenter = ReaderPresenter()
        try {
            acknowledge(presenter)
            val target = readerSeekTarget(state)!!;val before = state
            for (input in listOf(-1,8,Int.MAX_VALUE))
                assertNull(readerSeekAction(target, input, state))
            assertNull(readerSeekAction(target, 2, state))
            assertNull(readerSeekAction(target, 0, state.copy(phase=ReaderPhase.RENDERING)))
            assertNull(readerSeekAction(target, 0, state.copy(requestedPage=PageIndex(0))))
            assertEquals(ReaderAction.PageIfCurrent(0, target.context.intent, target.context.session), readerSeekAction(target, 0, state))
            assertEquals(7, readerSeekAction(target, 7, state)!!.index)
            assertSame(before, state);assertEquals(1, session.requests.size);assertEquals(1, opens.size)
        } finally { presenter.detach() }
    } }

    @Test fun contextAndFailureChangesCannotRetargetCapturedSeek() = runTest { withReader {
        open();val presenter = ReaderPresenter()
        try {
            acknowledge(presenter)
            val target = readerSeekTarget(state)!!
            val failure = ReaderFailure(ReaderStage.RENDER, AppError.Access.PROVIDER_UNAVAILABLE, ReaderRetry.OPEN)
            val invalid = listOf(state.copy(intent = state.intent!!.copy(sequence = 2)),
                state.copy(session = session.info.copy(id = ReadSessionId("new"))),
                state.copy(session = session.info.copy(sourceGeneration = 8)),
                state.copy(session = session.info.copy(pageCount = 4)), state.copy(phase = ReaderPhase.QUIESCED),
                state.copy(phase = ReaderPhase.ERROR), state.copy(failure = failure))
            invalid.forEach { assertNull(readerSeekAction(target, 0, it)) }
            assertNotNull(readerSeekAction(target, 0, state.copy(viewportGeneration = 99, fit = PageFit.WIDTH)))
        } finally { presenter.detach() }
    } }

    @Test fun validJumpDoesNotCheckpointUntilNewGraphicalAcknowledgment() = runTest { withReader {
        open();val presenter = ReaderPresenter()
        try {
            acknowledge(presenter)
            val target = readerSeekTarget(state)!!
            val originalAcks = displayed.toList()
            val action = readerSeekAction(target, 7, state)!!
            presenter.detach()
            controller.dispatch(action);runCurrent()
            assertEquals(PageIndex(7), state.requestedPage)
            assertEquals(originalAcks, displayed)
            assertEquals(ReaderPhase.RENDERED, state.phase)
            assertNull(readerSeekTarget(state))
            acknowledge(presenter)
            assertEquals(PageIndex(7), state.presentedPage)
            assertEquals(originalAcks + (session.info.id to PageIndex(7)), displayed)
        } finally { presenter.detach() }
    } }

    @Test fun actionBuiltBeforeReplacementIsRejectedByAtomicControllerGuard() = runTest { withReader {
        open();val presenter = ReaderPresenter()
        try {
            acknowledge(presenter)
            val target = readerSeekTarget(state)!!
            val action = readerSeekAction(target, 7, state)!!
            presenter.detach()
            controller.open(ReaderOpenIntent(2, transientRequest));runCurrent()
            val before = state;val renders = session.requests.size
            controller.dispatch(action);runCurrent()
            assertSame(before, state);assertEquals(renders, session.requests.size)
            assertNull(readerSeekAction(target, 7, state))
        } finally { presenter.detach() }
    } }
    @Test fun normalizedMappingIsBoundedForAllSupportedCounts() {
        for(count in listOf(1,2,8,50,100,10000,Int.MAX_VALUE)) {
            assertEquals(0,readerSeekIndex(0f,count))
            assertEquals(count-1,readerSeekIndex(1f,count))
            for(position in listOf(Float.MIN_VALUE,.1f,.5f,.9f,Math.nextDown(1f))) {
                val index=readerSeekIndex(position,count)!!
                assertTrue(index in 0 until count)
                assertTrue(readerSeekPosition(index,count) in 0f..1f)
            }
            assertEquals(0f,readerSeekPosition(0,count),0f)
            if(count>1)assertEquals(1f,readerSeekPosition(count-1,count),0f)
        }
        assertEquals(4,readerSeekIndex(.5f,8))
        for(position in listOf(Float.NaN,Float.POSITIVE_INFINITY,Float.NEGATIVE_INFINITY,-.01f,1.01f))
            assertNull(readerSeekIndex(position,8))
        assertNull(readerSeekIndex(.5f,0));assertNull(readerSeekIndex(.5f,-1))
    }

}
