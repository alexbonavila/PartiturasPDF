package com.abdev.partituraspdf.reader

import com.abdev.partituraspdf.core.document.PageIndex
import com.abdev.partituraspdf.core.foundation.*
import com.abdev.partituraspdf.core.preferences.PageFit
import com.abdev.partituraspdf.core.result.*
import com.abdev.partituraspdf.pdf.api.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

/**
 * One route-local worker; public intents replace pending work rather than enqueue native calls.
 * I retains this controller/route, quiesces G before route.background()/close(), then resumes G.
 * Retirement must run before cancelling the owner scope. A live presenter must explicitly detach
 * after graphics quiescence; retirement intentionally cannot recycle its buffer on a timeout.
 */
class ReaderController(
    ownerScope: CoroutineScope,
    private val route: ReaderRoute,
    private val renderCeiling: RenderLimits,
    private val heapAvailability: () -> HeapAvailability,
    initialFit: PageFit,
) {
    private val owner = SupervisorJob(ownerScope.coroutineContext[Job])
    private val scope = CoroutineScope(ownerScope.coroutineContext + owner)
    private val lock = Any()
    private val wake = Channel<Unit>(Channel.CONFLATED)
    private val mutableState = MutableStateFlow(ReaderUiState(fit = initialFit))
    val state: StateFlow<ReaderUiState> = mutableState.asStateFlow()
    private var active = true
    private var retired = false
    private var documentGeneration = 0L
    private var renderGeneration = 0L
    private var ready: ReaderReady? = null
    private var needsOpen = false
    private var needsCheck = false
    private var needsRender = false
    private var retiringFrame: ReaderFrame? = null
    private var pendingPresentation: ReaderPresentation? = null
    private var acknowledgedFrame: ReaderFrame? = null
    private var operation: Job? = null
    private var operationKind: Kind? = null
    private var paused = CompletableDeferred<Unit>()
    private val finished = CompletableDeferred<Unit>()

    private enum class Kind { OPEN, CHECK, RENDER, ACK }
    private sealed interface Work {
        data class Release(val frame: ReaderFrame) : Work
        data object Stop : Work
        data class Open(val generation: Long, val intent: ReaderOpenIntent) : Work
        data class Check(val generation: Long) : Work
        data class Render(val generation: Long, val render: Long, val ready: ReaderReady,
            val page: PageIndex, val viewport: ReaderViewport, val viewportGeneration: Long, val fit: PageFit) : Work
        data class Ack(val generation: Long, val presentation: ReaderPresentation) : Work
    }

    private val worker = scope.launch { runWorker() }

    init {
        worker.invokeOnCompletion { cause ->
            // A launch into an already canceled owner never enters the body or its finally.
            // No operation/frame can have been created, but lifecycle waiters still need an end.
            if (!finished.isCompleted) synchronized(lock) {
                retired = true; active = false
                mutableState.value = state.value.copy(phase = ReaderPhase.RETIRED)
                val cancellation = cause ?: CancellationException("Reader worker did not start")
                paused.completeExceptionally(cancellation); finished.completeExceptionally(cancellation)
                wake.close(); owner.cancel()
            }
        }
    }

    fun open(intent: ReaderOpenIntent) = synchronized(lock) {
        if (retired || !owner.isActive) return@synchronized
        val previous = state.value.intent
        if (previous != null && intent.sequence <= previous.sequence) {
            if (intent.sequence == previous.sequence && intent.request != previous.request) {
                fail(ReaderStage.OPEN, AppError.Validation.INVALID_DOCUMENT, ReaderRetry.NONE)
            }
            return@synchronized
        }
        documentGeneration++
        invalidateRender()
        operation?.cancel()
        ready = null; needsCheck = false; needsOpen = true
        mutableState.value = state.value.copy(intent = intent, session = null, requestedPage = null,
            presentedPage = null, phase = if (active) ReaderPhase.OPENING else ReaderPhase.QUIESCED,
            failure = null, checkpoint = ReaderCheckpoint.None, cancellationRetry = ReaderRetry.NONE, checkpointConsumer = null)
        signal()
    }

    fun dispatch(action: ReaderAction) = synchronized(lock) {
        if (retired || !owner.isActive) return@synchronized
        when (action) {
            is ReaderAction.Viewport -> if (action.value != state.value.viewport) {
                invalidateRender()
                mutableState.value = state.value.copy(viewport = action.value,
                    viewportGeneration = state.value.viewportGeneration + 1,
                    failure = if (ready == null) state.value.failure else null)
                scheduleRender()
            }
            is ReaderAction.Fit -> if (action.value != state.value.fit) {
                invalidateRender()
                mutableState.value = state.value.copy(fit = action.value,
                    viewportGeneration = state.value.viewportGeneration + 1,
                    failure = if (ready == null) state.value.failure else null)
                scheduleRender()
            }
            is ReaderAction.Page -> if (active) requestPage(action.index)
            is ReaderAction.Step -> if (active) {
                val index = state.value.requestedPage?.value
                val count = ready?.session?.info?.pageCount
                if (index != null && count != null) {
                    val target = index.toLong() + action.delta
                    if (target in 0 until count.toLong()) requestPage(target.toInt())
                }
            }
            is ReaderAction.Presented -> if (active && validPresentation(action.presentation)) {
                // A new consumer may replace a detached one without automatically retrying a
                // possibly committed checkpoint. Explicit Retry uses the current live consumer.
                mutableState.value = state.value.copy(checkpointConsumer = action.presentation)
                if (acknowledgedFrame !== action.presentation.frame) {
                    pendingPresentation = action.presentation
                    signal()
                }
            }
            ReaderAction.Retry -> if (active) retry()
        }
    }

    /**
     * Stops G work and awaits native completion plus explicit graphical detachment without blocking
     * the UI thread. I must keep the owner scope/UI alive, await this, then inspect background()/
     * close() according to ReaderMemoryPolicy.trim(trim). Unexpected cleanup defects propagate.
     */
    suspend fun quiesce(trim: ReaderTrim): Outcome<Unit> {
        val barrier = synchronized(lock) {
            if (retired) return@synchronized finished
            val response = ReaderMemoryPolicy.trim(trim)
            if (active) { active = false; paused = CompletableDeferred() }
            if (response.cancelPending) { operation?.cancel(); needsRender = false; pendingPresentation = null }
            if (response.releasePage) invalidateRender()
            // I applies closeSession, after this barrier. Resume always queries route.current().
            mutableState.value = state.value.copy(phase = ReaderPhase.QUIESCED)
            signal(); paused
        }
        barrier.await()
        return Outcome.Success(Unit)
    }

    /**
     * I must first finish quiescence and all route mutations/flushes. No concurrent external route
     * open/close is permitted while G is active. current() checks identity before borrowing again;
     * a closed route reopens through the retained intent, never through its obsolete native session.
     */
    fun resume() = synchronized(lock) {
        if (retired || !owner.isActive || active || !paused.isCompleted) return@synchronized
        active = true
        needsCheck = ready != null && !needsOpen
        mutableState.value = state.value.copy(phase = when {
            state.value.intent == null -> ReaderPhase.IDLE
            needsOpen -> ReaderPhase.OPENING
            ready == null -> if (state.value.failure != null) ReaderPhase.ERROR else ReaderPhase.CANCELED
            else -> ReaderPhase.READY
        }, failure = if (ready == null && !needsOpen) state.value.failure else null)
        if (!needsCheck) scheduleRender()
        signal()
    }

    /** Await before owner-scope cancellation; repeated calls observe the same completion/failure. */
    suspend fun retire(): Outcome<Unit> {
        synchronized(lock) {
            if (!retired) {
                retired = true; active = false; documentGeneration++
                invalidateRender(); operation?.cancel()
                mutableState.value = state.value.copy(phase = ReaderPhase.RETIRED)
                signal()
            }
        }
        finished.await()
        return Outcome.Success(Unit)
    }

    private fun signal() { wake.trySend(Unit) }
    private fun requestPage(index: Int) {
        val session = ready?.session
        if (session == null || index < 0 || index >= session.info.pageCount) {
            if (session != null || state.value.failure?.retry != ReaderRetry.OPEN) {
                fail(ReaderStage.PLAN, AppError.Validation.INVALID_PAGE, ReaderRetry.NONE)
            }
        } else if (state.value.requestedPage?.value != index) {
            invalidateRender()
            mutableState.value = state.value.copy(requestedPage = PageIndex(index), failure = null)
            scheduleRender()
        }
    }
    private fun fail(stage: ReaderStage, error: AppError, retry: ReaderRetry) {
        mutableState.value = state.value.copy(phase = ReaderPhase.ERROR, failure = ReaderFailure(stage, error, retry), cancellationRetry = ReaderRetry.NONE)
    }
    private fun scheduleRender() {
        needsRender = ready != null
        if (active && ready != null) mutableState.value = state.value.copy(phase = ReaderPhase.READY)
        signal()
    }
    private fun invalidateRender() {
        renderGeneration++
        if (operationKind == Kind.RENDER || operationKind == Kind.ACK) operation?.cancel()
        if (operationKind == Kind.ACK) mutableState.value = state.value.copy(checkpoint = ReaderCheckpoint.Canceled)
        pendingPresentation = null; acknowledgedFrame = null
        mutableState.value = state.value.copy(checkpointConsumer = null)
        state.value.frame?.let { frame ->
            check(retiringFrame == null)
            retiringFrame = frame
            mutableState.value = state.value.copy(frame = null)
            frame.requestRetirement()
        }
    }
    private fun retry() {
        val retry = readerRetry(state.value)
        when (retry) {
            ReaderRetry.NONE -> Unit
            ReaderRetry.OPEN -> {
                if (state.value.intent == null) return
                documentGeneration++; invalidateRender(); ready = null; needsOpen = true; needsCheck = false
                mutableState.value = state.value.copy(phase = ReaderPhase.OPENING, session = null, failure = null)
                signal()
            }
            ReaderRetry.RENDER -> { invalidateRender(); scheduleRender() }
            ReaderRetry.PRESENTATION -> {
                state.value.checkpointConsumer?.takeIf(::validPresentation)?.let {
                    acknowledgedFrame = null; pendingPresentation = it; signal()
                }
            }
        }
    }

    private fun nextWork(): Work? = synchronized(lock) {
        retiringFrame?.let { return@synchronized Work.Release(it) }
        if (retired) return@synchronized Work.Stop
        if (!active) { paused.complete(Unit); return@synchronized null }
        val intent = state.value.intent ?: return@synchronized null
        if (needsOpen) {
            needsOpen = false
            mutableState.value = state.value.copy(phase = ReaderPhase.OPENING, failure = null)
            return@synchronized Work.Open(documentGeneration, intent)
        }
        if (needsCheck) { needsCheck = false; return@synchronized Work.Check(documentGeneration) }
        pendingPresentation?.let {
            pendingPresentation = null
            if (validPresentation(it) && acknowledgedFrame !== it.frame) {
                acknowledgedFrame = it.frame
                mutableState.value = state.value.copy(phase = ReaderPhase.VISIBLE,
                    presentedPage = it.request.page.page, checkpoint = ReaderCheckpoint.Scheduling, failure = null)
                return@synchronized Work.Ack(documentGeneration, it)
            }
        }
        val session = ready ?: return@synchronized null
        val viewport = state.value.viewport ?: return@synchronized null
        val page = state.value.requestedPage ?: return@synchronized null
        if (!needsRender) return@synchronized null
        needsRender = false
        mutableState.value = state.value.copy(phase = ReaderPhase.RENDERING, failure = null)
        Work.Render(documentGeneration, renderGeneration, session, page, viewport, state.value.viewportGeneration, state.value.fit)
    }

    private fun validPresentation(presentation: ReaderPresentation): Boolean =
        state.value.frame === presentation.frame && presentation.frame.active(presentation) &&
            ready?.session?.info?.validate(presentation.request.page) == Outcome.Success(Unit) &&
            presentation.request.page.page == state.value.requestedPage &&
            presentation.request.viewportGeneration == state.value.viewportGeneration

    private fun current(work: Work): Boolean = active && !retired && when (work) {
        is Work.Open -> documentGeneration == work.generation
        is Work.Check -> documentGeneration == work.generation
        is Work.Render -> documentGeneration == work.generation && renderGeneration == work.render && ready === work.ready
        is Work.Ack -> documentGeneration == work.generation && validPresentation(work.presentation)
        else -> false
    }

    /** Cancellation retains the slot through join, even for a non-cooperative native operation. */
    private suspend fun <T> perform(work: Work, kind: Kind, discard: (T) -> Unit = {}, block: suspend () -> T): T? {
        var acquired: T? = null
        var completed = false
        // The controller's SupervisorJob owns this operation; the worker always awaits/joins it.
        // No extra context-return boundary may drop an acquired lease after await succeeds.
        val task = scope.async(start = CoroutineStart.LAZY) { block().also { acquired = it; completed = true } }
        synchronized(lock) {
            if (current(work)) { operation = task; operationKind = kind; task.start() } else task.cancel()
        }
        return try { task.await() }
        catch (cancelled: CancellationException) {
            withContext(NonCancellable) { task.cancelAndJoin() }
            if (completed) @Suppress("UNCHECKED_CAST") discard(acquired as T)
            currentCoroutineContext().ensureActive()
            synchronized(lock) {
                if (current(work)) {
                    val canceledRetry = when (kind) {
                        Kind.OPEN, Kind.CHECK -> ReaderRetry.OPEN
                        Kind.RENDER -> ReaderRetry.RENDER
                        Kind.ACK -> ReaderRetry.PRESENTATION
                    }
                    mutableState.value = state.value.copy(phase = ReaderPhase.CANCELED, cancellationRetry = canceledRetry,
                        checkpoint = if (kind == Kind.ACK) ReaderCheckpoint.Canceled else state.value.checkpoint)
                }
                // A paused open needs an explicit new attempt on resume, never an old session.
                if (!retired && !active && work is Work.Open && documentGeneration == work.generation) needsOpen = true
            }
            null
        } finally {
            synchronized(lock) { if (operation === task) { operation = null; operationKind = null } }
        }
    }

    private suspend fun execute(work: Work) {
        when (work) {
            is Work.Open -> {
                val result = perform(work, Kind.OPEN) { route.open(work.intent.request) } ?: return
                synchronized(lock) {
                    if (!current(work)) return@synchronized
                    when (result) {
                        is Outcome.Failure -> fail(ReaderStage.OPEN, result.error, readerFailureRetry(result.error, ReaderStage.OPEN))
                        is Outcome.Success -> {
                            val value = result.value
                            if (value.request != work.intent.request || value.page.value >= value.session.info.pageCount) {
                                fail(ReaderStage.OPEN, AppError.Conflict.STALE_SESSION, ReaderRetry.OPEN)
                            } else {
                                ready = value
                                mutableState.value = state.value.copy(session = value.session.info, requestedPage = value.page,
                                    phase = ReaderPhase.READY, failure = null)
                                scheduleRender()
                            }
                        }
                    }
                }
            }
            is Work.Check -> {
                val result = perform(work, Kind.CHECK) { route.current() } // null is a valid closed route.
                synchronized(lock) {
                    if (!current(work) || state.value.phase == ReaderPhase.CANCELED) return@synchronized
                    when {
                        result == null -> {
                            ready = null; needsOpen = true; documentGeneration++
                            mutableState.value = state.value.copy(session = null)
                            signal()
                        }
                        result.request != state.value.intent?.request || result.session.info != ready?.session?.info || result.session !== ready?.session -> {
                            ready = null
                            mutableState.value = state.value.copy(session = null)
                            fail(ReaderStage.OPEN, AppError.Conflict.STALE_SESSION, ReaderRetry.OPEN)
                        }
                        else -> { ready = result; scheduleRender() }
                    }
                }
            }
            is Work.Render -> render(work)
            is Work.Ack -> {
                val result = perform(work, Kind.ACK) {
                    route.displayed(work.presentation.request.page.session, work.presentation.request.page.page)
                } ?: return
                synchronized(lock) {
                    if (current(work)) {
                        mutableState.value = state.value.copy(checkpoint = ReaderCheckpoint.Unconfirmed(result))
                        if (result is Outcome.Failure) {
                            val retry = readerFailureRetry(result.error, ReaderStage.PRESENTATION)
                            if (retry == ReaderRetry.OPEN) {
                                invalidateRender(); ready = null
                                mutableState.value = state.value.copy(session = null)
                            }
                            fail(ReaderStage.PRESENTATION, result.error, retry)
                        }
                    }
                }
            }
            else -> Unit
        }
    }

    private suspend fun render(work: Work.Render) {
        val session = work.ready.session
        val info = session.info
        val page = ReadPageRef(info.id, info.sourceGeneration, work.page, info.revision)
        var stage = ReaderStage.PLAN
        var planned: RenderRequest? = null
        val result = perform(work, Kind.RENDER, discard = { value: Outcome<RenderedPage> ->
            if (value is Outcome.Success) value.value.close()
        }) {
            when (val valid = info.validate(page)) {
                is Outcome.Failure -> valid
                is Outcome.Success -> when (val display = session.displayInfo(page)) {
                    is Outcome.Failure -> display
                    is Outcome.Success -> if (display.value.page != page) Outcome.Failure(AppError.Conflict.STALE_SESSION) else {
                        val id = RenderRequestId(UUID.randomUUID().toString())
                        when (val plan = planReaderRender(id, display.value, work.viewport, work.viewportGeneration,
                            work.fit, renderCeiling, session.renderLimits, heapAvailability())) {
                            is Outcome.Failure -> plan
                            is Outcome.Success -> { planned = plan.value; stage = ReaderStage.RENDER; session.render(plan.value) }
                        }
                    }
                }
            }
        } ?: return
        var retained = false
        try {
            synchronized(lock) {
                if (!current(work)) return@synchronized
                when (result) {
                    is Outcome.Failure -> {
                        val retry = readerFailureRetry(result.error, stage)
                        if (retry != ReaderRetry.RENDER && result.error != AppError.Validation.INVALID_PAGE && result.error != AppError.Validation.INVALID_RENDER_REQUEST) {
                            ready = null
                            mutableState.value = state.value.copy(session = null)
                        }
                        fail(stage, result.error, retry)
                    }
                    is Outcome.Success -> {
                        val lease = result.value
                        val request = lease.request
                        if (request != planned || lease.displayInfo.page != page) {
                            ready = null
                            mutableState.value = state.value.copy(session = null)
                            fail(ReaderStage.RENDER, AppError.Conflict.STALE_SESSION, ReaderRetry.OPEN)
                        } else {
                            mutableState.value = state.value.copy(phase = ReaderPhase.RENDERED, frame = ReaderFrame(lease), failure = null)
                            retained = true
                        }
                    }
                }
            }
        } finally { if (!retained && result is Outcome.Success) result.value.close() }
    }

    private suspend fun runWorker() {
        var failure: Throwable? = null
        try {
            for (ignored in wake) {
                while (true) {
                    when (val work = nextWork() ?: break) {
                        Work.Stop -> return
                        is Work.Release -> {
                            work.frame.awaitReleased()
                            synchronized(lock) { if (retiringFrame === work.frame) retiringFrame = null }
                        }
                        else -> execute(work)
                    }
                }
            }
        } catch (problem: Throwable) { failure = problem; throw problem }
        finally {
            try {
                synchronized(lock) {
                    retired = true; active = false
                    invalidateRender()
                    if (state.value.phase != ReaderPhase.RETIRED) mutableState.value = state.value.copy(phase = ReaderPhase.RETIRED)
                }
                withContext(NonCancellable) { retiringFrame?.awaitReleased() }
            } catch (cleanup: Throwable) {
                if (failure == null) { failure = cleanup; throw cleanup } else if (cleanup !== failure) failure.addSuppressed(cleanup)
            } finally {
                wake.close()
                if (failure == null) { paused.complete(Unit); finished.complete(Unit) }
                else { paused.completeExceptionally(failure); finished.completeExceptionally(failure) }
                owner.cancel()
            }
        }
    }
}
