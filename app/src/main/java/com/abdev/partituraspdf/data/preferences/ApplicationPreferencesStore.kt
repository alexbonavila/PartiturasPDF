package com.abdev.partituraspdf.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore

// Exactly one private delegate owns this process-lifetime store and its library-managed IO scope.
// Do not open this file with another delegate/factory. Tests inject separate stores instead.
private val Context.applicationPreferences by preferencesDataStore(name = "app_preferences")

/** Future composition-root wiring reuses this instance; no startup or Activity wiring in C04. */
internal fun applicationPreferencesStore(context: Context): DataStore<Preferences> =
    context.applicationContext.applicationPreferences
