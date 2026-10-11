package com.abdev.partituraspdf.core.preferences

import com.abdev.partituraspdf.core.result.AppError
import com.abdev.partituraspdf.core.result.Observation
import com.abdev.partituraspdf.core.result.Outcome
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class PreferencesRepositoryTest {
    private class MemoryPreferences : PreferencesRepository {
        private var current = AppPreferences()
        private val state = MutableStateFlow<Observation<AppPreferences>>(Observation.Ready(current))
        var writeError: AppError? = null
        var cancelWrite = false
        override fun observe(): Flow<Observation<AppPreferences>> = flow {
            emit(Observation.Loading)
            emitAll(state)
        }
        override suspend fun update(change: PreferenceChange): Outcome<Unit> {
            if (cancelWrite) throw CancellationException()
            writeError?.let { return Outcome.Failure(it) }
            current = when (change) {
                is PreferenceChange.Theme -> current.copy(theme = change.value)
                is PreferenceChange.Language -> current.copy(language = change.value)
                is PreferenceChange.Fit -> current.copy(reader = current.reader.copy(pageFit = change.value))
            }
            state.value = Observation.Ready(current)
            return Outcome.Success(Unit)
        }
        fun failRead(error: AppError) { state.value = Observation.Failed(error) }
    }

    @Test fun defaultAndPersistenceKeysAreExplicit() {
        val defaults = AppPreferences()
        assertEquals(ThemeSelection.SYSTEM, defaults.theme)
        assertEquals(LanguageSelection.SYSTEM, defaults.language)
        assertEquals(PageFit.PAGE, defaults.reader.pageFit)
        assertEquals(listOf("system", "light", "dark"), ThemeSelection.entries.map { it.key })
        assertEquals(listOf("system", "en", "ca", "es"), LanguageSelection.entries.map { it.key })
        assertEquals(listOf("page", "width"), PageFit.entries.map { it.key })
    }

    @Test fun typedUpdatesPreserveEveryUnrelatedValue() = runBlocking {
        val repository: PreferencesRepository = MemoryPreferences()
        assertEquals(listOf(Observation.Loading, Observation.Ready(AppPreferences())), repository.observe().take(2).toList())
        assertEquals(Outcome.Success(Unit), repository.update(PreferenceChange.Theme(ThemeSelection.DARK)))
        assertEquals(Outcome.Success(Unit), repository.update(PreferenceChange.Language(LanguageSelection.CATALAN)))
        assertEquals(Outcome.Success(Unit), repository.update(PreferenceChange.Fit(PageFit.WIDTH)))
        val expected = AppPreferences(ThemeSelection.DARK, LanguageSelection.CATALAN, ReaderPreferences(PageFit.WIDTH))
        assertEquals(Observation.Ready(expected), repository.observe().take(2).toList()[1])
        repository.update(PreferenceChange.Theme(ThemeSelection.LIGHT))
        assertEquals(Observation.Ready(expected.copy(theme = ThemeSelection.LIGHT)), repository.observe().take(2).toList()[1])
    }

    @Test fun unavailableStoreNeverMasqueradesAsDefaultsAndWriteFailureKeepsValues() = runBlocking {
        val repository = MemoryPreferences()
        repository.writeError = AppError.Access.PRIVATE_STORAGE_UNAVAILABLE
        assertEquals(Outcome.Failure(AppError.Access.PRIVATE_STORAGE_UNAVAILABLE), repository.update(PreferenceChange.Fit(PageFit.WIDTH)))
        assertEquals(Observation.Ready(AppPreferences()), repository.observe().take(2).toList()[1])
        repository.failRead(AppError.Access.PRIVATE_STORAGE_UNAVAILABLE)
        assertEquals(listOf(Observation.Loading, Observation.Failed(AppError.Access.PRIVATE_STORAGE_UNAVAILABLE)), repository.observe().take(2).toList())
    }

    @Test fun cancelledUpdatePropagatesRatherThanEmittingOrdinaryError() {
        val repository = MemoryPreferences().apply { cancelWrite = true }
        assertThrows(CancellationException::class.java) {
            runBlocking { repository.update(PreferenceChange.Theme(ThemeSelection.DARK)) }
        }
    }
}
