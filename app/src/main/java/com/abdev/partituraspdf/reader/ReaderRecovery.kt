package com.abdev.partituraspdf.reader

import com.abdev.partituraspdf.core.result.AppError

/** Rechecking externally restored access is meaningful; retry cannot fix malformed/protected input. */
internal fun readerFailureRetry(error: AppError, stage: ReaderStage): ReaderRetry = when (error) {
    is AppError.Unsupported, is AppError.Validation -> ReaderRetry.NONE
    AppError.Access.NOT_REGISTERED, AppError.Access.DOCUMENT_DELETED, AppError.Resource.INPUT_LIMIT -> ReaderRetry.NONE
    is AppError.Conflict -> ReaderRetry.OPEN
    else -> when (stage) {
        ReaderStage.OPEN -> ReaderRetry.OPEN
        ReaderStage.PRESENTATION -> if (error == AppError.Access.PRIVATE_STORAGE_UNAVAILABLE) ReaderRetry.PRESENTATION else ReaderRetry.OPEN
        else -> if (error is AppError.Access) ReaderRetry.OPEN else ReaderRetry.RENDER
    }
}

/** UI and controller share eligibility. A detached/stale draw cannot retry an uncertain checkpoint. */
internal fun readerRetry(state: ReaderUiState): ReaderRetry {
    if (state.phase == ReaderPhase.QUIESCED || state.phase == ReaderPhase.RETIRED) return ReaderRetry.NONE
    val retry = state.failure?.retry ?: if (state.phase == ReaderPhase.CANCELED) state.cancellationRetry else ReaderRetry.NONE
    return when (retry) {
        ReaderRetry.OPEN -> if (state.intent != null) retry else ReaderRetry.NONE
        ReaderRetry.RENDER -> if (state.session != null) retry else ReaderRetry.NONE
        ReaderRetry.PRESENTATION -> state.checkpointConsumer?.let {
            if (state.frame === it.frame && it.frame.active(it)) retry else ReaderRetry.NONE
        } ?: ReaderRetry.NONE
        ReaderRetry.NONE -> retry
    }
}
