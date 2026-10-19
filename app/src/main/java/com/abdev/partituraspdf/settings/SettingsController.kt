package com.abdev.partituraspdf.settings

import com.abdev.partituraspdf.core.foundation.SettingsCommands
import com.abdev.partituraspdf.core.foundation.SettingsEffect
import com.abdev.partituraspdf.core.preferences.AppPreferences
import com.abdev.partituraspdf.core.preferences.PreferencesRepository
import com.abdev.partituraspdf.core.result.Observation
import com.abdev.partituraspdf.core.result.Outcome
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * I owns/retains this route-local controller and supplies a scope with an exception handler.
 * No Android state, persistence construction, global theme effects or language commands.
 *
 * One continuous observer and one writer. Mailboxes contain at most one latest intent per field.
 * An acknowledgement resolves only when a Ready was received after that write started (in either
 * order). A differing Ready resolves to the externally observed value, never to the desired value:
 * the repository has no revisions with which to attribute observations to individual commands.
 * Without a fresh Ready we remain Reconciling; RetryObservation can explicitly request a snapshot.
 * Ordinary successful writes never restart observation. Uncertain cancellation does, after the
 * canceled command has finished. Restarts join the old collector and reject obsolete generations.
 */
class SettingsController(
    ownerScope: CoroutineScope,
    private val preferences: PreferencesRepository,
    private val commands: SettingsCommands,
) {
    private val owner = SupervisorJob(ownerScope.coroutineContext[Job])
    private val scope = CoroutineScope(ownerScope.coroutineContext + owner)
    private val lock = Any()
    private val changes = Channel<Unit>(Channel.CONFLATED)
    private val refreshes = Channel<Unit>(Channel.CONFLATED)
    private val mutableState = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = mutableState.asStateFlow()
    private var started = false
    private var closed = false
    private var generation = 0L
    private var readyVersion = 0L
    private var order = 0L
    private val queued = mutableMapOf<Field, Intent>()
    private val reconciling = mutableMapOf<Field, Write>()
    private var active: Write? = null

    private enum class Field { THEME, FIT }
    private data class Intent(val effect: SettingsEffect, val order: Long, val mustWrite: Boolean)
    private data class Write(val intent: Intent, val readVersion: Long)
    private fun field(effect: SettingsEffect) = when (effect) {
        is SettingsEffect.Theme -> Field.THEME
        is SettingsEffect.Fit -> Field.FIT
    }
    private fun live() = started && !closed && owner.isActive
    private fun ready() = (state.value.preferences as? Observation.Ready)?.value
    private fun matches(effect: SettingsEffect, value: AppPreferences) = when (effect) {
        is SettingsEffect.Theme -> effect.selection == value.theme
        is SettingsEffect.Fit -> effect.selection == value.reader.pageFit
    }
    private fun operation(field: Field) = when (field) {
        Field.THEME -> state.value.themeOperation
        Field.FIT -> state.value.fitOperation
    }
    private fun operation(field: Field, value: SettingsOperation) {
        mutableState.value = when (field) {
            Field.THEME -> state.value.copy(themeOperation = value)
            Field.FIT -> state.value.copy(fitOperation = value)
        }
    }

    fun start() = synchronized(lock) {
        if (started || closed || !owner.isActive) return@synchronized
        started = true
        guarded { observe() }
        guarded { write() }
        refresh()
    }

    /** Actions before start/after close are ignored; selection/retry writes require Ready. */
    fun dispatch(action: SettingsAction) = synchronized(lock) {
        if (!live()) return@synchronized
        when (action) {
            is SettingsAction.SelectTheme -> select(SettingsEffect.Theme(action.selection))
            is SettingsAction.SelectFit -> select(SettingsEffect.Fit(action.selection))
            SettingsAction.RetryObservation -> if (state.value.preferences is Observation.Failed ||
                reconciling.isNotEmpty()) refresh()
            SettingsAction.RetryTheme -> retry(Field.THEME)
            SettingsAction.RetryFit -> retry(Field.FIT)
        }
    }

    /** Terminal and idempotent. Never cancels the supplied parent; no late result may publish. */
    fun close() = synchronized(lock) {
        if (closed) return@synchronized
        closed = true
        queued.clear()
        reconciling.clear()
        changes.cancel()
        refreshes.cancel()
        owner.cancel()
    }

    private fun guarded(block: suspend () -> Unit) {
        scope.launch { block() }.invokeOnCompletion { failure ->
            // Defects still reach the inherited coroutine exception handler.
            if (failure != null && failure !is CancellationException) close()
        }
    }

    private fun refresh() {
        generation++
        mutableState.value = state.value.copy(preferences = Observation.Loading)
        refreshes.trySend(Unit)
    }

    private suspend fun observe() {
        var collector: Job? = null
        for (ignored in refreshes) {
            collector?.cancelAndJoin()
            synchronized(lock) {
                if (live()) {
                    val token = generation
                    collector = scope.launch {
                        preferences.observe().collect { value -> observed(token, value) }
                    }.also { task ->
                        task.invokeOnCompletion { failure ->
                            if (failure != null && failure !is CancellationException) close()
                        }
                    }
                }
            }
        }
    }

    private fun observed(token: Long, value: Observation<AppPreferences>) = synchronized(lock) {
        if (!live() || token != generation) return@synchronized
        mutableState.value = state.value.copy(preferences = value)
        if (value is Observation.Ready) {
            readyVersion++
            for (field in Field.entries) {
                val waiting = reconciling[field]
                if (waiting != null && readyVersion > waiting.readVersion) {
                    reconciling.remove(field)
                    operation(field, SettingsOperation.Idle)
                }
            }
            changes.trySend(Unit)
        }
    }

    private fun retry(field: Field) {
        val failed = operation(field) as? SettingsOperation.Failed ?: return
        select(failed.requested, retry = true)
    }

    private fun select(effect: SettingsEffect, retry: Boolean = false) {
        val current = ready() ?: return
        val field = field(effect)
        val executing = active?.takeIf { field(it.intent.effect) == field }
        if (executing?.intent?.effect == effect) {
            queued.remove(field) // Returning to the in-flight intent supersedes a queued replacement.
            operation(field, SettingsOperation.Pending(effect))
            return
        }
        if (queued[field]?.effect == effect) return
        if (!retry && (operation(field) as? SettingsOperation.Failed)?.requested == effect) return
        if (reconciling[field]?.intent?.effect == effect) return
        // An older write may already have committed without an observed emission. Do not drop
        // a queued reversal merely because it equals the pre-write snapshot.
        val mustWrite = executing != null || reconciling.containsKey(field) || queued[field]?.mustWrite == true
        reconciling.remove(field)
        if (!mustWrite && matches(effect, current)) {
            queued.remove(field)
            operation(field, SettingsOperation.Idle)
        } else {
            queued[field] = Intent(effect, ++order, mustWrite)
            operation(field, SettingsOperation.Pending(effect))
            changes.trySend(Unit)
        }
    }

    private fun next(): Write? {
        if (!live()) return null
        val current = ready() ?: return null
        while (queued.isNotEmpty()) {
            val intent = queued.values.minBy { it.order }
            val field = field(intent.effect)
            queued.remove(field)
            if (!intent.mustWrite && matches(intent.effect, current)) {
                operation(field, SettingsOperation.Idle)
            } else {
                return Write(intent, readyVersion).also { active = it }
            }
        }
        return null
    }

    private suspend fun write() {
        for (ignored in changes) {
            while (true) {
                val pending = synchronized(lock) { next() } ?: break
                var result: Outcome<Unit>? = null
                // A separately canceled command remains a canceled child, not a fake Failure.
                val task = scope.launch(start = CoroutineStart.LAZY) {
                    result = commands.apply(pending.intent.effect)
                }
                task.invokeOnCompletion { failure -> finished(pending, result, failure) }
                task.start()
                task.join()
            }
        }
    }

    private fun finished(write: Write, result: Outcome<Unit>?, failure: Throwable?) = synchronized(lock) {
        if (!live()) return@synchronized
        if (failure != null && failure !is CancellationException) {
            close()
            return@synchronized
        }
        active = null
        val field = field(write.intent.effect)
        if (!queued.containsKey(field)) {
            when {
                failure is CancellationException -> {
                    reconciling[field] = write.copy(readVersion = readyVersion)
                    operation(field, SettingsOperation.Reconciling(write.intent.effect, uncertain = true))
                }
                result is Outcome.Failure -> operation(field, SettingsOperation.Failed(write.intent.effect, result.error))
                else -> {
                    if (ready() != null && readyVersion > write.readVersion) operation(field, SettingsOperation.Idle)
                    else {
                        reconciling[field] = write
                        operation(field, SettingsOperation.Reconciling(write.intent.effect))
                    }
                }
            }
        }
        if (failure is CancellationException) refresh()
        changes.trySend(Unit)
    }
}
