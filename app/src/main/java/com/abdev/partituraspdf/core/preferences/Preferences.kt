package com.abdev.partituraspdf.core.preferences

import com.abdev.partituraspdf.core.result.Observation
import com.abdev.partituraspdf.core.result.Outcome
import kotlinx.coroutines.flow.Flow

/** Stable keys for future persistence; never serialize enum ordinals. */
enum class ThemeSelection(val key: String) { SYSTEM("system"), LIGHT("light"), DARK("dark") }
enum class LanguageSelection(val key: String) { SYSTEM("system"), ENGLISH("en"), CATALAN("ca"), SPANISH("es") }
enum class PageFit(val key: String) { PAGE("page"), WIDTH("width") }

data class ReaderPreferences(val pageFit: PageFit = PageFit.PAGE)
data class AppPreferences(
    val theme: ThemeSelection = ThemeSelection.SYSTEM,
    val language: LanguageSelection = LanguageSelection.SYSTEM,
    val reader: ReaderPreferences = ReaderPreferences(),
)

/** Each operation changes only the selected field, including fields nested under reader. */
sealed interface PreferenceChange {
    data class Theme(val value: ThemeSelection) : PreferenceChange
    data class Language(val value: LanguageSelection) : PreferenceChange
    data class Fit(val value: PageFit) : PreferenceChange
}

/**
 * Implemented by data.preferences. Follow [Observation]; defaults are emitted only for a
 * successfully read absent store, never to mask an I/O error. update acknowledges durable,
 * atomic field changes. Cancellation propagates; an interrupted update may have committed,
 * so observe to reconcile. No locale application or theme/UI side effect belongs here.
 */
interface PreferencesRepository {
    fun observe(): Flow<Observation<AppPreferences>>
    suspend fun update(change: PreferenceChange): Outcome<Unit>
}
