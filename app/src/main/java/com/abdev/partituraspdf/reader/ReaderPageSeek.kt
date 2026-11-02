package com.abdev.partituraspdf.reader

import com.abdev.partituraspdf.core.result.Outcome
import kotlin.math.roundToLong

/** Ephemeral seek data only: no frame/bitmap lease and no saved document capability. */
internal data class ReaderSeekTarget(val context: ReaderNavigationContext, val presentedNumber: Int)

internal fun readerSeekTarget(state: ReaderUiState): ReaderSeekTarget? {
    val context = readerNavigationContext(state) ?: return null
    if (state.phase != ReaderPhase.VISIBLE || state.failure != null) return null
    val frame = state.frame ?: return null
    val page = state.presentedPage ?: return null
    if (page != frame.displayInfo.page.page || context.session.validate(frame.displayInfo.page) !is Outcome.Success) return null
    val consumer = state.checkpointConsumer ?: return null
    if (consumer.frame !== frame || !frame.active(consumer)) return null
    return ReaderSeekTarget(context, page.value + 1)
}


/** Continuous normalized position: constant space even for very large page counts. */
internal fun readerSeekIndex(position: Float, count: Int): Int? {
    if (!position.isFinite() || position < 0f || position > 1f || count < 1) return null
    return (position.toDouble() * (count - 1).toDouble()).roundToLong().toInt()
}

internal fun readerSeekPosition(index: Int, count: Int): Float =
    if (count <= 1) 0f else (index.toDouble() / (count - 1).toDouble()).toFloat().coerceIn(0f, 1f)

internal fun readerSeekAction(target: ReaderSeekTarget, index: Int, latest: ReaderUiState): ReaderAction.PageIfCurrent? {
    if (!readerNavigationContextIsCurrent(target.context, latest) || latest.failure != null) return null
    if (readerSeekTarget(latest) == null) return null
    if (index !in 0 until target.context.session.pageCount) return null
    if (latest.presentedPage?.value == index || latest.requestedPage?.value == index) return null
    return ReaderAction.PageIfCurrent(index, target.context.intent, target.context.session)
}
