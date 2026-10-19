package com.abdev.partituraspdf.app.host

import androidx.lifecycle.ViewModel
import com.abdev.partituraspdf.app.navigation.AppNavigation
import com.abdev.partituraspdf.library.LibraryAction
import com.abdev.partituraspdf.library.LibraryAssociation
import com.abdev.partituraspdf.library.LibraryContent
import com.abdev.partituraspdf.library.LibraryRow
import com.abdev.partituraspdf.core.foundation.DocumentOpenRequest
import com.abdev.partituraspdf.reader.ReaderAction
import com.abdev.partituraspdf.app.navigation.AppDestination
import com.abdev.partituraspdf.settings.SettingsAction
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Retained launcher owner. Intentionally does not use viewModelScope: its automatic cancellation
 * must not interrupt owned cleanup. One bounded shutdown job completes before this scope cancels.
 * No Activity/View callbacks are retained. A future Reader must join this same shutdown protocol.
 */
internal class AppHostViewModel(
    private val createSession: () -> AppHostSession,
    private val dispatcher: CoroutineDispatcher,
    private val io: CoroutineDispatcher,
    private val recoverable: (Throwable) -> Boolean,
    private val reportFailure: (Throwable) -> Unit,
) : ViewModel() {
    val lifetimeId: String = java.util.UUID.randomUUID().toString()
    private val lock = Any()
    private val owner = SupervisorJob()
    private val handler = CoroutineExceptionHandler { _, failure -> dispose(failure) }
    private val scope = CoroutineScope(owner + dispatcher + handler)
    private val mutableState = MutableStateFlow<AppHostState>(AppHostState.Loading)
    val state = mutableState.asStateFlow()
    private val mutableNavigation = MutableStateFlow(AppNavigation())
    val navigation = mutableNavigation.asStateFlow()
    private var startup: Job? = null
    private var live: Attempt? = null
    private var closing = false
    private var terminalFailure: Throwable? = null
    private val disposed = CompletableDeferred<Throwable?>()
    private data class Attempt(val session: AppHostSession?, val children: Job)
    private data class Release(val failure: Throwable?, val clean: Boolean)

    init { start() }

    /** Idempotent, also across Activity recreation. Failure retry is always explicit. */
    fun start() = synchronized(lock) {
        if (closing || startup != null) return@synchronized
        launchStartup()
    }

    fun retry() = synchronized(lock) {
        val failed = state.value as? AppHostState.Failed ?: return@synchronized
        if (!closing && failed.retryable && startup?.isActive != true) launchStartup()
    }

    private fun launchStartup() {
        mutableState.value = AppHostState.Loading
        startup = scope.launch(start = CoroutineStart.LAZY) { initialize() }.also { it.start() }
    }

    private suspend fun initialize() {
        val children = SupervisorJob(owner)
        var session: AppHostSession? = null
        var transferred = false
        var problem: Throwable? = null
        try {
            session = createSession()
            // Capture inside IO so coroutine stack-trace recovery cannot replace the original cause.
            val loaded = withContext(io) { runCatching { session.load(CoroutineScope(scope.coroutineContext + children)) } }
            val features = loaded.getOrThrow()
            synchronized(lock) {
                if (closing) throw CancellationException("Host disposal")
                features.reader?.connect(
                    allowed = { permit, request -> synchronized(lock) {
                        if (closing || state.value !is AppHostState.Ready || navigation.value.destination != AppDestination.LIBRARY ||
                            navigation.value.revision != permit.revision) false
                        else {
                            val row = (features.library.state.value.content as? LibraryContent.Ready)?.rows
                                ?.singleOrNull { it.key == permit.key } as? LibraryRow.Pdf
                            when (val association = row?.association) {
                                is LibraryAssociation.Registered -> request == DocumentOpenRequest.Registered(association.value.document)
                                LibraryAssociation.Unregistered -> request == DocumentOpenRequest.Transient(row.entry.storage)
                                else -> false
                            }
                        }
                    } },
                    entered = { permit -> synchronized(lock) { mutableNavigation.value = navigation.value.reader(permit.revision) } },
                    returned = { report -> synchronized(lock) {
                        if (!closing) {
                            if (report.cause != null) dispose(report.failure())
                            else {
                                report.failure()?.let(reportFailure)
                                mutableNavigation.value = navigation.value.readerFinished(navigation.value.revision)
                                features.library.dispatch(LibraryAction.Refresh)
                            }
                        }
                    } },
                )
                features.library.start()
                if (closing) throw CancellationException("Host disposal")
                features.settings.start()
                if (closing) throw CancellationException("Host disposal")
                live = Attempt(session, children)
                transferred = true
                mutableState.value = AppHostState.Ready(features)
            }
        } catch (failure: Throwable) {
            problem = failure // Preserve cancellation/defects; never translate into provider errors.
        }
        if (transferred) return
        // All initialization failures reach this one epilogue. A finally block would duplicate
        // the state machine at every coroutine exit and obscure its single ownership transfer.
        val released = release(Attempt(session, children), problem)
        val failure = checkNotNull(released.failure)
        val cancellation = failure is CancellationException
        val observable = !cancellation || failure.suppressed.isNotEmpty()
        synchronized(lock) {
            if (closing) {
                if (observable) terminalFailure = combine(terminalFailure, failure)
            } else {
                mutableState.value = AppHostState.Failed(failure,
                    released.clean && (cancellation || recoverable(failure)))
            }
        }
        if (observable) reportFailure(failure)
        if (failure is CancellationException) throw failure
    }

    /** Always join graph users before close; independent of caller or ViewModel cancellation. */
    private suspend fun release(attempt: Attempt, original: Throwable?): Release = withContext(NonCancellable) {
        var failure = original
        var clean = true
        try {
            attempt.session?.retireReader()?.failure()?.let { cleanup -> clean = false; failure = combine(failure, cleanup) }
        } catch (cleanup: Throwable) { clean = false; failure = combine(failure, cleanup) }
        try { attempt.session?.stop() } catch (cleanup: Throwable) { clean = false; failure = combine(failure, cleanup) }
        attempt.children.cancelAndJoin()
        val closed = withContext(io) { runCatching { attempt.session?.close() } }
        closed.exceptionOrNull()?.let { cleanup -> clean = false; failure = combine(failure, cleanup) }
        Release(failure, clean)
    }

    fun showSettings(expectedRevision: Long) = synchronized(lock) {
        if (!closing && state.value is AppHostState.Ready)
            mutableNavigation.value = navigation.value.settings(expectedRevision)
    }

    fun back(expectedRevision: Long) = synchronized(lock) {
        if (!closing && state.value is AppHostState.Ready && navigation.value.revision == expectedRevision) {
            if (navigation.value.destination == AppDestination.READER)
                (state.value as AppHostState.Ready).features.reader?.leave()
            else mutableNavigation.value = navigation.value.back(expectedRevision)
        }
    }

    fun library(action: LibraryAction, expectedRevision: Long) = synchronized(lock) {
        if (!closing && navigation.value.revision == expectedRevision &&
            navigation.value.destination == com.abdev.partituraspdf.app.navigation.AppDestination.LIBRARY)
            (state.value as? AppHostState.Ready)?.features?.let { features ->
                if (features.picker?.recovery?.value == null || action == LibraryAction.ChooseRoot) {
                    when (action) {
                        is LibraryAction.Open -> features.reader?.arm(action.key, expectedRevision)
                        is LibraryAction.RetryOpen -> features.reader?.arm(action.key, expectedRevision)
                        else -> Unit
                    }
                    features.library.dispatch(action)
                }
            }
    }

    fun reader(action: ReaderAction, sequence: Long, expectedRevision: Long) = synchronized(lock) {
        if (!closing && navigation.value.destination == AppDestination.READER && navigation.value.revision == expectedRevision)
            (state.value as? AppHostState.Ready)?.features?.reader?.dispatch(sequence, action)
    }

    fun settings(action: SettingsAction, expectedRevision: Long) = synchronized(lock) {
        if (!closing && navigation.value.revision == expectedRevision &&
            navigation.value.destination == com.abdev.partituraspdf.app.navigation.AppDestination.SETTINGS)
            (state.value as? AppHostState.Ready)?.features?.settings?.dispatch(action)
    }

    /** Returned evidence is also exposed as Closed; production failures are logged by the factory. */
    fun dispose(cause: Throwable? = null): Deferred<Throwable?> = synchronized(lock) {
        if (closing) {
            if (!disposed.isCompleted) terminalFailure = combine(terminalFailure, cause)
            return@synchronized disposed
        }
        closing = true
        (state.value as? AppHostState.Ready)?.features?.let { it.picker?.close(); it.reader?.stopAccepting() }
        terminalFailure = combine(terminalFailure, cause)
        mutableState.value = AppHostState.Closing
        val boot = startup
        boot?.cancel()
        scope.launch {
            boot?.join() // Includes partial-startup cleanup, even if blocking load finishes late.
            val attempt = synchronized(lock) { live.also { live = null } }
            val initial = synchronized(lock) { terminalFailure }
            var failure = initial
            if (attempt != null) failure = release(attempt, failure).failure
            synchronized(lock) {
                if (terminalFailure !== initial) failure = combine(terminalFailure, failure)
            }
            failure?.let(reportFailure)
            mutableState.value = AppHostState.Closed(failure)
            disposed.complete(failure)
            owner.cancel()
        }
        disposed
    }

    override fun onCleared() { dispose() }

    private fun combine(primary: Throwable?, next: Throwable?): Throwable? {
        if (primary == null) return next
        if (next != null && next !== primary) primary.addSuppressed(next)
        return primary
    }
}
