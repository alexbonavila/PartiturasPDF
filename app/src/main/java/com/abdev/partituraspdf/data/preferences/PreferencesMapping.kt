package com.abdev.partituraspdf.data.preferences

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.abdev.partituraspdf.core.preferences.*

/** Initial format: stable string key names and domain enum keys, never ordinals or labels. */
internal object PreferencesMapping {
    val theme = stringPreferencesKey("theme_selection")
    val language = stringPreferencesKey("language_selection")
    val pageFit = stringPreferencesKey("reader_page_fit")

    fun read(values: Preferences): AppPreferences = AppPreferences(
        decode(values[theme], ThemeSelection.SYSTEM, ThemeSelection.entries) { it.key },
        decode(values[language], LanguageSelection.SYSTEM, LanguageSelection.entries) { it.key },
        ReaderPreferences(decode(values[pageFit], PageFit.PAGE, PageFit.entries) { it.key }),
    )

    fun apply(values: MutablePreferences, change: PreferenceChange) {
        // Validate all owned fields before writing. A field update is not a corruption migration.
        read(values)
        when (change) {
            is PreferenceChange.Theme -> values[theme] = change.value.key
            is PreferenceChange.Language -> values[language] = change.value.key
            is PreferenceChange.Fit -> values[pageFit] = change.value.key
        }
    }

    private fun <T> decode(value: String?, default: T, entries: List<T>, key: (T) -> String): T {
        if (value == null) return default
        return checkNotNull(entries.singleOrNull { key(it) == value }) { "Unsupported persisted preference value" }
    }
}
