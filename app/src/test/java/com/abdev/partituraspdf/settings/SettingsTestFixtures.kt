package com.abdev.partituraspdf.settings

import com.abdev.partituraspdf.core.foundation.SettingsCommands
import com.abdev.partituraspdf.core.foundation.SettingsEffect
import com.abdev.partituraspdf.core.preferences.*
import com.abdev.partituraspdf.core.result.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.transformWhile

internal class SettingsFixture(
    scope: CoroutineScope,
    initial: AppPreferences? = AppPreferences(),
) {
    var persisted = initial ?: AppPreferences()
    val values = MutableSharedFlow<Observation<AppPreferences>>(replay = 1, extraBufferCapacity = 32)
    var collections = 0
    var collectors = 0
    var maximumCollectors = 0
    var directUpdates = 0
    var observer: (() -> Flow<Observation<AppPreferences>>)? = null
    val repository = object : PreferencesRepository {
        override fun observe(): Flow<Observation<AppPreferences>> = flow {
            collections++
            collectors++
            maximumCollectors = maxOf(maximumCollectors, collectors)
            try {
                emit(Observation.Loading)
                val source = observer?.invoke() ?: values.transformWhile {
                    emit(it)
                    it !is Observation.Failed
                }
                source.collect { emit(it) }
            } finally {
                collectors--
            }
        }
        override suspend fun update(change: PreferenceChange): Outcome<Unit> {
            directUpdates++
            error("H must write through SettingsCommands")
        }
    }
    val calls = mutableListOf<SettingsEffect>()
    val commandJobs = mutableListOf<Job>()
    val writes = mutableListOf<CompletableDeferred<Outcome<Unit>>>()
    var command: (suspend (SettingsEffect) -> Outcome<Unit>)? = null
    var activeWrites = 0
    var maximumWrites = 0
    val controller = SettingsController(scope, repository, object : SettingsCommands {
        override suspend fun apply(effect: SettingsEffect): Outcome<Unit> {
            calls.add(effect)
            commandJobs.add(checkNotNull(currentCoroutineContext()[Job]))
            activeWrites++
            maximumWrites = maxOf(maximumWrites, activeWrites)
            try {
                command?.let { return it(effect) }
                return CompletableDeferred<Outcome<Unit>>().also { writes.add(it) }.await()
            } finally {
                activeWrites--
            }
        }
    })
    val state get() = controller.state.value
    init { if (initial != null) publish(initial) }

    fun publish(value: AppPreferences) {
        persisted = value
        check(values.tryEmit(Observation.Ready(value)))
    }
    fun commit(effect: SettingsEffect = calls.last(), emit: Boolean = true) {
        persisted = when (effect) {
            is SettingsEffect.Theme -> persisted.copy(theme = effect.selection)
            is SettingsEffect.Fit -> persisted.copy(reader = persisted.reader.copy(pageFit = effect.selection))
        }
        if (emit) publish(persisted)
    }
    fun acknowledge(index: Int = writes.lastIndex, result: Outcome<Unit> = Outcome.Success(Unit)) {
        writes[index].complete(result)
    }
}
