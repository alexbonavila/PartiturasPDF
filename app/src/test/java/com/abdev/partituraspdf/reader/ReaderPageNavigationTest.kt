package com.abdev.partituraspdf.reader

import com.abdev.partituraspdf.core.document.*
import com.abdev.partituraspdf.core.foundation.DocumentOpenRequest
import com.abdev.partituraspdf.pdf.api.*
import org.junit.Assert.*
import org.junit.Test

class ReaderPageNavigationTest {
    private fun state() = ReaderUiState(phase = ReaderPhase.VISIBLE,
        intent = ReaderOpenIntent(1, DocumentOpenRequest.Transient(StorageDocumentRef("test", "one"))),
        session = ReadSessionInfo(ReadSessionId("session"), 7, 100))
    @Test fun validAsciiNumbersConvertWithoutOverflow() {
        for (count in listOf(1,2,10,50,100,Int.MAX_VALUE)) {
            assertEquals(ReaderPageInput.Valid(0),validateReaderPageNumber(" 1 ",count))
            assertEquals(ReaderPageInput.Valid(count-1),validateReaderPageNumber(count.toString(),count))
        }
        assertEquals(ReaderPageInput.Valid(11),validateReaderPageNumber("0012",100))
        assertEquals(ReaderPageInput.Valid(11),validateReaderPageNumber("\t12\n",100))
    }
    @Test fun invalidSyntaxAndOverflowAreNotOutOfRange() {
        for (text in listOf("", "  ", "-1", "+1", "1.0", "1e2", "1 2", "a", "١", "１２", "2147483648", "999999999999999999999999"))
            assertEquals(text,ReaderPageInput.InvalidNumber,validateReaderPageNumber(text,100))
    }
    @Test fun zeroOutsideCountAndUnavailableCountsAreOutOfRange() {
        for (count in listOf(null,0,-1,Int.MIN_VALUE)) assertEquals(ReaderPageInput.OutOfRange,validateReaderPageNumber("1",count))
        for (text in listOf("0","000","101")) assertEquals(ReaderPageInput.OutOfRange,validateReaderPageNumber(text,100))
    }
    @Test fun onlyActiveKnownIntentAndSessionHaveNavigationContext() {
        val state=state()
        for (phase in ReaderPhase.entries) {
            val candidate=state.copy(phase=phase)
            assertEquals(phase in listOf(ReaderPhase.READY,ReaderPhase.RENDERING,ReaderPhase.RENDERED,ReaderPhase.VISIBLE),readerNavigationContext(candidate)!=null)
        }
        assertNull(readerNavigationContext(state.copy(intent=null)))
        assertNull(readerNavigationContext(state.copy(session=null)))
    }
    @Test fun staleIntentSessionGenerationAndCountAreRejectedButViewportIsNotIdentity() {
        val state=state();val context=readerNavigationContext(state)!!
        assertTrue(readerNavigationContextIsCurrent(context,state))
        assertTrue(readerNavigationContextIsCurrent(context,state.copy(viewportGeneration=99,viewport=ReaderViewport(400,900))))
        val session=state.session!!;val intent=state.intent!!
        val replacements=listOf(state.copy(intent=intent.copy(sequence=2)),
            state.copy(intent=intent.copy(request=DocumentOpenRequest.Transient(StorageDocumentRef("test","other")))),
            state.copy(session=session.copy(id=ReadSessionId("replacement"))),
            state.copy(session=session.copy(sourceGeneration=8)),state.copy(session=session.copy(pageCount=50)),
            state.copy(session=null),state.copy(intent=null),state.copy(phase=ReaderPhase.QUIESCED),state.copy(phase=ReaderPhase.RETIRED))
        replacements.forEach { assertFalse(readerNavigationContextIsCurrent(context,it)) }
        assertEquals(context,readerNavigationContext(state)) // Immutable original context remains usable only for original state.
    }
}
