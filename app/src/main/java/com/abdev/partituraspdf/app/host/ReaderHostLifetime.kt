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

internal data class ReaderOpenPermit(val key: LibraryRowKey, val revision: Long)
internal data class ReaderHostEntry(val intent: ReaderOpenIntent, val controller: ReaderController,
    internal val route: ReaderRoute, internal val owner: Job)
internal enum class ReaderCleanupStage { QUIESCE, CHECKPOINT, RETIRE, CLOSE }
internal data class ReaderCleanupFailure(val stage: ReaderCleanupStage, val error: AppError)
internal data class ReaderExitReport(val failures: List<ReaderCleanupFailure> = emptyList(), val cause: Throwable? = null) {
    fun failure(): Throwable? = cause ?: if (failures.isEmpty()) null else ReaderExitException(this)
}
internal class ReaderExitException(val report: ReaderExitReport) : IllegalStateException("Reader cleanup was not fully successful")
internal data class ReaderHostState(val entry: ReaderHostEntry? = null, val leaving: Boolean = false,
    val report: ReaderExitReport = ReaderExitReport())

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
    private var exit: Deferred<ReaderExitReport>? = null
    private var allowed: (ReaderOpenPermit, DocumentOpenRequest) -> Boolean = { _, _ -> false }
    private var entered: (ReaderOpenPermit) -> Unit = {}
    private var returned: (ReaderExitReport) -> Unit = {}

    fun connect(allowed: (ReaderOpenPermit, DocumentOpenRequest) -> Boolean,
        entered: (ReaderOpenPermit) -> Unit, returned: (ReaderExitReport) -> Unit) {
        this.allowed = allowed; this.entered = entered; this.returned = returned
    }
    fun arm(key: LibraryRowKey, revision: Long) {
        if (!terminal && state.value.entry == null) permit = ReaderOpenPermit(key, revision)
    }
    fun accept(request: DocumentOpenRequest): Outcome<Unit> {
        val ticket = permit ?: return Outcome.Failure(AppError.Conflict.STALE_SESSION)
        permit = null
        if (terminal || state.value.entry != null || !allowed(ticket, request))
            return Outcome.Failure(AppError.Conflict.STALE_SESSION)
        val initialFit = when (val preference = fit()) {
            is Outcome.Failure -> return preference
            is Outcome.Success -> preference.value
        }
        val owner = SupervisorJob(scope.coroutineContext[Job])
        val readerScope = CoroutineScope(scope.coroutineContext + owner)
        val route: ReaderRoute
        try { route = routeFactory(readerScope) } catch (failure: Throwable) { owner.cancel(); throw failure }
        val controller = ReaderController(readerScope, route, renderCeiling, heap, initialFit)
        val intent = ReaderOpenIntent(++sequence, request)
        exit = null
        mutableState.value = ReaderHostState(ReaderHostEntry(intent, controller, route, owner))
        entered(ticket)
        controller.open(intent)
        return Outcome.Success(Unit) // Ownership/intent only, not native or graphical success.
    }
    fun dispatch(sequence: Long, action: ReaderAction) {
        val current = state.value
        if (!terminal && !current.leaving && current.entry?.intent?.sequence == sequence)
            current.entry.controller.dispatch(action)
    }
    fun stopAccepting() { terminal = true; permit = null }

    fun leave(): Deferred<ReaderExitReport> {
        exit?.let { return it }
        val entry = state.value.entry ?: return CompletableDeferred(state.value.report)
        permit = null
        mutableState.value = state.value.copy(leaving = true)
        // This job belongs to the retained host scope, not the Reader scope it must eventually cancel.
        return scope.async(start = CoroutineStart.LAZY) {
            val report = cleanup(entry)
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
        step(ReaderCleanupStage.CHECKPOINT) { entry.route.background() }
        step(ReaderCleanupStage.RETIRE) { entry.controller.retire() }
        step(ReaderCleanupStage.CLOSE) { entry.route.close() }
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
