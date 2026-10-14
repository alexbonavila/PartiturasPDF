package com.abdev.partituraspdf.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.abdev.partituraspdf.core.preferences.AppPreferences
import com.abdev.partituraspdf.core.preferences.PreferenceChange
import com.abdev.partituraspdf.core.preferences.PreferencesRepository
import com.abdev.partituraspdf.core.result.Observation
import com.abdev.partituraspdf.core.result.Outcome
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import java.io.IOException

/** The caller owns the injected store. This adapter creates no scopes or background jobs. */
internal class DataStorePreferencesRepository(private val store: DataStore<Preferences>) : PreferencesRepository {
    override fun observe(): Flow<Observation<AppPreferences>> = flow {
        emit(Observation.Loading)
        store.data.collect { emit(Observation.Ready(PreferencesMapping.read(it))) }
    }.catch { failure ->
        // Operational failure terminates this collection. A new collector starts Loading again.
        emit(Observation.Failed(preferencesStorageFailure(failure)))
    }

    override suspend fun update(change: PreferenceChange): Outcome<Unit> {
        currentCoroutineContext().ensureActive()
        return try {
            store.edit { PreferencesMapping.apply(it, change) }
            Outcome.Success(Unit)
        } catch (failure: IOException) {
            Outcome.Failure(preferencesStorageFailure(failure))
        }
    }
}
