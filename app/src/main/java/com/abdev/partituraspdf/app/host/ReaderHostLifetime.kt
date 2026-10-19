package com.abdev.partituraspdf.app.host

import com.abdev.partituraspdf.core.foundation.*
import com.abdev.partituraspdf.core.preferences.PageFit
import com.abdev.partituraspdf.core.preferences.AppPreferences
import com.abdev.partituraspdf.core.result.*
import com.abdev.partituraspdf.library.LibraryRowKey
import com.abdev.partituraspdf.pdf.api.*
import com.abdev.partituraspdf.reader.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.abdev.partituraspdf.core.document.PageIndex
import com.abdev.partituraspdf.core.document.DocumentRef

internal data class ReaderOpenPermit(val key: LibraryRowKey?, val revision: Long, val restoredDocument: DocumentRef? = null)
internal data class ReaderHostEntry(val intent: ReaderOpenIntent, val controller: ReaderController,
    internal val route: ReaderRoute, internal val owner: Job)
internal enum class ReaderCleanupStage { QUIESCE, CHECKPOINT, RETIRE, CLOSE }
internal data class ReaderCleanupFailure(val stage: ReaderCleanupStage, val error: AppError)
internal data class ReaderExitReport(val failures: List<ReaderCleanupFailure> = emptyList(), val cause: Throwable? = null) {
    fun failure(): Throwable? = cause ?: if (failures.isEmpty()) null else ReaderExitException(this)
}
internal class ReaderExitException(val report: ReaderExitReport) : IllegalStateException("Reader cleanup was not fully successful")
internal data class ReaderHostState(val entry: ReaderHostEntry? = null, val leaving: Boolean = false,
    val report: ReaderExitReport = ReaderExitReport(), val suspended: Boolean = false,
    val suspendedPage: PageIndex? = null, val recoveryBlocked: Boolean = false)

/** Owner-dispatcher confined. G alone opens the route. Navigation contains no resources.
 * Leaving unmounts ReaderScreen BEFORE awaiting G, even if its lifecycle collector has stopped.
 * The View's existing onRelease/onDetach completes graphical release; this owner never invents it.
 */
internal class ReaderHostLifetime(
    private val scope: CoroutineScope,
    private val routeFactory: (CoroutineScope) -> ReaderRoute,
    private val renderCeiling: RenderLimits,
    private val heap: () -> HeapAvailability,
    private val fit: () -> Outcome<PageFit>,
) {
    private val mutableState = MutableStateFlow(ReaderHostState())
    val state = mutableState.asStateFlow()
    private var permit: ReaderOpenPermit? = null
    private var sequence = 0L
    private var terminal = false
    private var resumeRequested = false
    private var foreground = true
    private var pendingTrim: ReaderTrim? = null
    private var suspendedTrim: ReaderTrim? = null
    private var lifecycle: Job? = null
    private var sessionClosed = false
    private var closeUncertain = false
    private var lastVisible: PageIndex? = null
    private var exit: Deferred<ReaderExitReport>? = null
    private var allowed: (ReaderOpenPermit, DocumentOpenRequest) -> Boolean = { _, _ -> false }
    private var entered: (ReaderOpenPermit) -> Unit = {}
    private var returned: (ReaderExitReport) -> Unit = {}
    private var lifecycleFailure: (ReaderExitReport) -> Unit = {}

    fun connect(allowed: (ReaderOpenPermit, DocumentOpenRequest) -> Boolean,
        entered: (ReaderOpenPermit) -> Unit, returned: (ReaderExitReport) -> Unit,
        lifecycleFailure: (ReaderExitReport) -> Unit = {}) {
        this.allowed = allowed; this.entered = entered; this.returned = returned; this.lifecycleFailure = lifecycleFailure
    }
    fun arm(key: LibraryRowKey, revision: Long) {
        if (!terminal && state.value.entry == null) permit = ReaderOpenPermit(key, revision)
    }
    /** Separate registered entry permit; the host validates its one-shot restoration generation. */
    fun restore(document: DocumentRef, revision: Long): Outcome<Unit> {
        if (terminal || state.value.entry != null) return Outcome.Failure(AppError.Conflict.STALE_SESSION)
        permit = ReaderOpenPermit(null, revision, document)
        return accept(DocumentOpenRequest.Registered(document))
    }
    fun accept(request: DocumentOpenRequest): Outcome<Unit> {
        val ticket = permit ?: return Outcome.Failure(AppError.Conflict.STALE_SESSION)
        permit = null
        if (terminal || !foreground || state.value.entry != null || !allowed(ticket, request))
            return Outcome.Failure(AppError.Conflict.STALE_SESSION)
        val initialFit = when (val preference = fit()) {
            is Outcome.Failure -> return preference
            is Outcome.Success -> preference.value
        }
        val owner = SupervisorJob(scope.coroutineContext[Job])
        val readerScope = CoroutineScope(scope.coroutineContext + owner)
        val route: ReaderRoute
        try {
            val delegate = routeFactory(readerScope)
            // In-process transient recovery hint only. G still owns every open and presentation.
            route = object : ReaderRoute by delegate {
                override suspend fun open(request: DocumentOpenRequest): Outcome<ReaderReady> = when (val opened = delegate.open(request)) {
                    is Outcome.Failure -> opened
                    is Outcome.Success -> if (request is DocumentOpenRequest.Transient && lastVisible != null)
                        Outcome.Success(opened.value.copy(page = PageIndex(lastVisible!!.value.coerceAtMost(opened.value.session.info.pageCount - 1))))
                        else opened
                }
            }
        } catch (failure: Throwable) { owner.cancel(); throw failure }
        val controller = ReaderController(readerScope, route, renderCeiling, heap, initialFit)
        val intent = ReaderOpenIntent(++sequence, request)
        exit = null; sessionClosed = false; closeUncertain = false; lastVisible = null; suspendedTrim = null
        mutableState.value = ReaderHostState(ReaderHostEntry(intent, controller, route, owner))
        entered(ticket)
        controller.open(intent)
        if (!foreground) trim(intent.sequence, ReaderTrim.BACKGROUND)
        return Outcome.Success(Unit) // Ownership/intent only, not native or graphical success.
    }
    fun dispatch(sequence: Long, action: ReaderAction) {
        val current = state.value
        if (!terminal && !current.leaving && (!current.suspended || action is ReaderAction.Viewport) && current.entry?.intent?.sequence == sequence)
            current.entry.controller.dispatch(action)
    }
    /** Events are generation-bound; no old Activity/lifetime callback may trim a replacement. */
    fun trim(sequence: Long, trim: ReaderTrim) {
        val current = state.value
        if (terminal || current.leaving || current.entry?.intent?.sequence != sequence) return
        if (trim == ReaderTrim.BACKGROUND) foreground = false
        if (suspendedTrim == trim && lifecycle?.isActive != true) return
        resumeRequested = false
        pendingTrim = stronger(pendingTrim, trim)
        lastVisible = current.entry.controller.state.value.presentedPage ?: lastVisible
        mutableState.value = current.copy(suspended = true,
            suspendedPage = current.entry.controller.state.value.requestedPage)
        drain(current.entry)
    }
    fun foreground(active: Boolean) {
        foreground = active
        resumeRequested = active
        val entry = state.value.entry ?: return
        if (!active) trim(entry.intent.sequence, ReaderTrim.BACKGROUND)
        else if (!terminal && !state.value.leaving && state.value.suspended) drain(entry)
    }
    private fun stronger(old: ReaderTrim?, next: ReaderTrim): ReaderTrim = when {
        old == ReaderTrim.CRITICAL || next == ReaderTrim.CRITICAL -> ReaderTrim.CRITICAL
        old == ReaderTrim.BACKGROUND || next == ReaderTrim.BACKGROUND -> ReaderTrim.BACKGROUND
        else -> ReaderTrim.LOW_MEMORY
    }
    private fun drain(entry: ReaderHostEntry) {
        if (lifecycle?.isActive == true) return
        val task = scope.launch(start = CoroutineStart.LAZY) {
            while (!terminal && !state.value.leaving && state.value.entry === entry) {
                val trim = pendingTrim
                if (trim != null) {
                    pendingTrim = null
                    if (suspendedTrim != null && stronger(suspendedTrim, trim) == suspendedTrim) continue
                    val report = pause(entry, trim)
                    suspendedTrim = stronger(suspendedTrim, trim)
                    mutableState.value = state.value.copy(report = merge(state.value.report, report),
                        recoveryBlocked = closeUncertain || report.cause != null)
                    if (report.failure() != null) lifecycleFailure(report)
                    continue
                }
                if (foreground && resumeRequested && !state.value.recoveryBlocked) {
                    entry.controller.resume() // G checks current()/reopens, never a competing host open.
                    sessionClosed = false; suspendedTrim = null; resumeRequested = false
                    mutableState.value = state.value.copy(suspended = false, suspendedPage = null)
                }
                break
            }
        }
        lifecycle = task
        task.start()
    }
    private suspend fun pause(entry: ReaderHostEntry, trim: ReaderTrim): ReaderExitReport = withContext(NonCancellable) {
        val failures = mutableListOf<ReaderCleanupFailure>()
        var cause: Throwable? = null
        suspend fun step(stage: ReaderCleanupStage, action: suspend () -> Outcome<Unit>) {
            try { val result = action(); if (result is Outcome.Failure) failures += ReaderCleanupFailure(stage, result.error) }
            catch (failure: Throwable) { if (cause == null) cause = failure else if (cause !== failure) cause!!.addSuppressed(failure) }
        }
        step(ReaderCleanupStage.QUIESCE) { entry.controller.quiesce(trim) }
        if (ReaderMemoryPolicy.trim(trim).closeSession && !sessionClosed) {
            step(ReaderCleanupStage.CHECKPOINT) { entry.route.background() }
            step(ReaderCleanupStage.CLOSE) { entry.route.close() }
            sessionClosed = true
            closeUncertain = cause != null || failures.any { it.stage == ReaderCleanupStage.CLOSE }
        }
        if (cause != null && failures.isNotEmpty()) cause!!.addSuppressed(ReaderExitException(ReaderExitReport(failures.toList())))
        ReaderExitReport(failures.toList(), cause)
    }
    private fun merge(old: ReaderExitReport, next: ReaderExitReport): ReaderExitReport {
        val cause = old.cause ?: next.cause
        if (old.cause != null && next.cause != null && old.cause !== next.cause) old.cause.addSuppressed(next.cause)
        return ReaderExitReport(old.failures + next.failures, cause)
    }

    fun stopAccepting() { terminal = true; permit = null }

    fun leave(): Deferred<ReaderExitReport> {
        exit?.let { return it }
        val entry = state.value.entry ?: return CompletableDeferred(state.value.report)
        permit = null
        mutableState.value = state.value.copy(leaving = true)
        // This job belongs to the retained host scope, not the Reader scope it must eventually cancel.
        return scope.async(start = CoroutineStart.LAZY) {
            lifecycle?.join() // Never cancel a mandatory in-flight trim/flush/close.
            pendingTrim = null
            val report = merge(state.value.report, cleanup(entry))
            mutableState.value = ReaderHostState(report = report)
            if (!terminal) returned(report)
            report
        }.also { exit = it; it.start() }
    }
    suspend fun terminate(): ReaderExitReport { stopAccepting(); return leave().await() }

    private suspend fun cleanup(entry: ReaderHostEntry): ReaderExitReport = withContext(NonCancellable) {
        val failures = mutableListOf<ReaderCleanupFailure>()
        var original: Throwable? = null
        suspend fun step(stage: ReaderCleanupStage, action: suspend () -> Outcome<Unit>) {
            try {
                val outcome = action()
                if (outcome is Outcome.Failure) failures += ReaderCleanupFailure(stage, outcome.error)
            } catch (failure: Throwable) {
                if (original == null) original = failure else if (original !== failure) original!!.addSuppressed(failure)
            }
        }
        step(ReaderCleanupStage.QUIESCE) { entry.controller.quiesce(ReaderTrim.BACKGROUND) }
        if (!sessionClosed) step(ReaderCleanupStage.CHECKPOINT) { entry.route.background() }
        step(ReaderCleanupStage.RETIRE) { entry.controller.retire() }
        if (!sessionClosed) step(ReaderCleanupStage.CLOSE) { entry.route.close() }
        entry.owner.cancelAndJoin()
        val report = ReaderExitReport(failures.toList(), original)
        if (original != null && failures.isNotEmpty()) original!!.addSuppressed(ReaderExitException(ReaderExitReport(failures.toList())))
        report
    }
}

/** One confirmed snapshot from H's existing repository observation, never a default for Loading. */
internal fun confirmedReaderFit(preferences: Observation<AppPreferences>): Outcome<PageFit> = when (preferences) {
    Observation.Loading -> Outcome.Failure(AppError.Conflict.STALE_SESSION)
    is Observation.Failed -> Outcome.Failure(preferences.error)
    is Observation.Ready -> Outcome.Success(preferences.value.reader.pageFit)
}
