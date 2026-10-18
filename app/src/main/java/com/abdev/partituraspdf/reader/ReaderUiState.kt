package com.abdev.partituraspdf.reader

import com.abdev.partituraspdf.core.document.PageIndex
import com.abdev.partituraspdf.core.foundation.DocumentOpenRequest
import com.abdev.partituraspdf.core.preferences.PageFit
import com.abdev.partituraspdf.core.result.AppError
import com.abdev.partituraspdf.core.result.Outcome
import com.abdev.partituraspdf.pdf.api.ReadSessionInfo

/** Sequence is issued by I for user intents, not by recomposition. Older sequences are ignored. */
data class ReaderOpenIntent(val sequence: Long, val request: DocumentOpenRequest) {
    init { require(sequence >= 0) }
}

/** Physical content pixels, excluding reader chrome. Nonpositive layout is rejected before render. */
data class ReaderViewport(val widthPx: Int, val heightPx: Int)

enum class ReaderPhase { IDLE, OPENING, READY, RENDERING, RENDERED, VISIBLE, ERROR, CANCELED, QUIESCED, RETIRED }
enum class ReaderStage { OPEN, PLAN, RENDER, PRESENTATION }
enum class ReaderRetry { NONE, OPEN, RENDER, PRESENTATION }
data class ReaderFailure(val stage: ReaderStage, val error: AppError, val retry: ReaderRetry)

sealed interface ReaderCheckpoint {
    data object None : ReaderCheckpoint
    data object Scheduling : ReaderCheckpoint
    data object Canceled : ReaderCheckpoint
    /** Success acknowledges scheduling only; durable flush remains an I-owned operation. */
    data class Unconfirmed(val result: Outcome<Unit>) : ReaderCheckpoint
}

/** Immutable snapshot. Frame is a transient, controlled borrowing capability, never saved state. */
data class ReaderUiState(
    val phase: ReaderPhase = ReaderPhase.IDLE,
    val intent: ReaderOpenIntent? = null,
    val session: ReadSessionInfo? = null,
    val requestedPage: PageIndex? = null,
    val presentedPage: PageIndex? = null,
    val viewport: ReaderViewport? = null,
    val viewportGeneration: Long = 0,
    val fit: PageFit = PageFit.PAGE,
    val frame: ReaderFrame? = null,
    val failure: ReaderFailure? = null,
    val checkpoint: ReaderCheckpoint = ReaderCheckpoint.None,
)

sealed interface ReaderAction {
    data class Viewport(val value: ReaderViewport) : ReaderAction
    data class Fit(val value: PageFit) : ReaderAction
    data class Page(val index: Int) : ReaderAction
    data object Retry : ReaderAction
    /** Only the active graphical consumer may report a completed presentation. */
    data class Presented(val presentation: ReaderPresentation) : ReaderAction
}
