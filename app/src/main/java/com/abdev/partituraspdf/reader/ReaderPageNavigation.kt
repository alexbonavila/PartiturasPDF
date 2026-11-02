package com.abdev.partituraspdf.reader

import com.abdev.partituraspdf.pdf.api.ReadSessionInfo

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
 * PageIfCurrent performs definitive atomic authorization. Viewport changes are not session changes.
 */
internal fun readerNavigationContextIsCurrent(captured: ReaderNavigationContext, latest: ReaderUiState): Boolean =
    readerNavigationContext(latest) == captured
