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
class ReaderPageJumpTest {
    private fun ReaderFixture.acknowledge(presenter: ReaderPresenter) {
        presenter.bind(state.frame)
        val consumer = presenter.draw(true) {}!!
        assertTrue(presenter.acknowledge(consumer))
        controller.dispatch(ReaderAction.Presented(consumer))
        test.runCurrent()
    }

    @Test fun renderedRequestedPageIsNotAnEligiblePresentedPage() = runTest { withReader {
        assertNull(readerPageJumpTarget(state))
        open();assertEquals(ReaderPhase.RENDERED, state.phase)
        assertNull(readerPageJumpTarget(state))
        val presenter = ReaderPresenter()
        try {
            acknowledge(presenter)
            val target = readerPageJumpTarget(state)!!
            assertEquals(3, target.presentedNumber)
            assertEquals(readerNavigationContext(state), target.context)
            assertEquals(target, target.copy());assertEquals(target.hashCode(), target.copy().hashCode())
            assertNotEquals(target, target.copy(presentedNumber = 1))
            assertTrue(target.toString().contains("ReaderPageJumpTarget"))
        } finally { presenter.detach() }
    } }

    @Test fun unavailableInconsistentOrDetachedPresentationCannotOpenDialog() = runTest { withReader {
        open();val presenter = ReaderPresenter()
        try {
            acknowledge(presenter)
            val visible = state
            for (phase in ReaderPhase.entries.filter { it != ReaderPhase.VISIBLE })
                assertNull(readerPageJumpTarget(visible.copy(phase = phase)))
            assertNull(readerPageJumpTarget(visible.copy(intent = null)))
            assertNull(readerPageJumpTarget(visible.copy(session = null)))
            assertNull(readerPageJumpTarget(visible.copy(frame = null)))
            assertNull(readerPageJumpTarget(visible.copy(presentedPage = null)))
            assertNull(readerPageJumpTarget(visible.copy(presentedPage = PageIndex(0))))
            assertNull(readerPageJumpTarget(visible.copy(session = session.info.copy(id = ReadSessionId("replacement")))))
            assertNull(readerPageJumpTarget(visible.copy(checkpointConsumer = null)))
            assertNull(readerPageJumpTarget(visible.copy(failure = ReaderFailure(ReaderStage.RENDER, AppError.Access.PROVIDER_UNAVAILABLE, ReaderRetry.OPEN))))
            val otherFrame = ReaderFrame(TestPage(session.requests.single()))
            val otherConsumer = otherFrame.attach()!!
            try { assertNull(readerPageJumpTarget(visible.copy(checkpointConsumer = otherConsumer))) }
            finally { otherConsumer.detached();otherFrame.requestRetirement() }
            presenter.detach()
            assertNull(readerPageJumpTarget(visible))
        } finally { presenter.detach() }
    } }

    @Test fun inputValidationNeverNavigatesAndMapsFirstAndLastExactly() = runTest { withReader {
        open();val presenter = ReaderPresenter()
        try {
            acknowledge(presenter)
            val target = readerPageJumpTarget(state)!!;val before = state
            for (input in listOf("", " ", "-1", "+1", "1.5", "2147483648", "999999999999999", "١", "１２", "0", "9"))
                assertNull(input, readerPageJumpAction(target, input, state))
            assertEquals(ReaderAction.PageIfCurrent(0, target.context.intent, target.context.session), readerPageJumpAction(target, " 1 ", state))
            assertEquals(7, readerPageJumpAction(target, "8", state)!!.index)
            assertSame(before, state);assertEquals(1, session.requests.size);assertEquals(1, opens.size)
        } finally { presenter.detach() }
    } }

    @Test fun contextAndFailureChangesCannotRetargetCapturedDialog() = runTest { withReader {
        open();val presenter = ReaderPresenter()
        try {
            acknowledge(presenter)
            val target = readerPageJumpTarget(state)!!
            val failure = ReaderFailure(ReaderStage.RENDER, AppError.Access.PROVIDER_UNAVAILABLE, ReaderRetry.OPEN)
            val invalid = listOf(state.copy(intent = state.intent!!.copy(sequence = 2)),
                state.copy(session = session.info.copy(id = ReadSessionId("new"))),
                state.copy(session = session.info.copy(sourceGeneration = 8)),
                state.copy(session = session.info.copy(pageCount = 4)), state.copy(phase = ReaderPhase.QUIESCED),
                state.copy(phase = ReaderPhase.ERROR), state.copy(failure = failure))
            invalid.forEach { assertNull(readerPageJumpAction(target, "1", it)) }
            assertNotNull(readerPageJumpAction(target, "1", state.copy(viewportGeneration = 99, fit = PageFit.WIDTH)))
        } finally { presenter.detach() }
    } }

    @Test fun validJumpDoesNotCheckpointUntilNewGraphicalAcknowledgment() = runTest { withReader {
        open();val presenter = ReaderPresenter()
        try {
            acknowledge(presenter)
            val target = readerPageJumpTarget(state)!!
            val originalAcks = displayed.toList()
            val action = readerPageJumpAction(target, "8", state)!!
            presenter.detach()
            controller.dispatch(action);runCurrent()
            assertEquals(PageIndex(7), state.requestedPage)
            assertEquals(originalAcks, displayed)
            assertEquals(ReaderPhase.RENDERED, state.phase)
            assertNull(readerPageJumpTarget(state))
            acknowledge(presenter)
            assertEquals(PageIndex(7), state.presentedPage)
            assertEquals(originalAcks + (session.info.id to PageIndex(7)), displayed)
        } finally { presenter.detach() }
    } }

    @Test fun actionBuiltBeforeReplacementIsRejectedByAtomicControllerGuard() = runTest { withReader {
        open();val presenter = ReaderPresenter()
        try {
            acknowledge(presenter)
            val target = readerPageJumpTarget(state)!!
            val action = readerPageJumpAction(target, "8", state)!!
            presenter.detach()
            controller.open(ReaderOpenIntent(2, transientRequest));runCurrent()
            val before = state;val renders = session.requests.size
            controller.dispatch(action);runCurrent()
            assertSame(before, state);assertEquals(renders, session.requests.size)
            assertNull(readerPageJumpAction(target, "8", state))
        } finally { presenter.detach() }
    } }
}
