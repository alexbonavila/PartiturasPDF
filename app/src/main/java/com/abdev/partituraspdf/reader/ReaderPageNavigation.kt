package com.abdev.partituraspdf.reader

import com.abdev.partituraspdf.pdf.api.ReadSessionInfo

internal sealed interface ReaderPageInput {
    data object InvalidNumber : ReaderPageInput
    data object OutOfRange : ReaderPageInput
    data class Valid(val index: Int) : ReaderPageInput
}

/** ASCII decimal only; neither validation nor success dispatches a page or checkpoint. */
internal fun validateReaderPageNumber(input: String, pageCount: Int?): ReaderPageInput {
    val text = input.trim()
    if (text.isEmpty() || text.any { it !in '0'..'9' }) return ReaderPageInput.InvalidNumber
    val page = text.toIntOrNull() ?: return ReaderPageInput.InvalidNumber
    if (pageCount == null || pageCount <= 0 || page < 1 || page > pageCount) return ReaderPageInput.OutOfRange
    return ReaderPageInput.Valid(page - 1)
}

/** Ephemeral context, not saved state or evidence of exact PDF bytes. */
internal data class ReaderNavigationContext(val intent: ReaderOpenIntent, val session: ReadSessionInfo)

internal fun readerNavigationContext(state: ReaderUiState): ReaderNavigationContext? {
    if (state.phase !in setOf(ReaderPhase.READY, ReaderPhase.RENDERING, ReaderPhase.RENDERED, ReaderPhase.VISIBLE)) return null
    val intent = state.intent ?: return null
    val session = state.session ?: return null
    return ReaderNavigationContext(intent, session)
}

/**
 * Recheck immediately before UI dispatch. This is NOT atomic controller authorization:
 * ReaderAction.Page has no expected-session token. Viewport changes are not session changes.
 */
internal fun readerNavigationContextIsCurrent(captured: ReaderNavigationContext, latest: ReaderUiState): Boolean =
    readerNavigationContext(latest) == captured
