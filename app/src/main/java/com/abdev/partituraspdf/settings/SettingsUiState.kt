package com.abdev.partituraspdf.settings

import com.abdev.partituraspdf.core.foundation.SettingsEffect
import com.abdev.partituraspdf.core.preferences.AppPreferences
import com.abdev.partituraspdf.core.preferences.PageFit
import com.abdev.partituraspdf.core.preferences.ThemeSelection
import com.abdev.partituraspdf.core.result.AppError
import com.abdev.partituraspdf.core.result.Observation

/** Requested effects never substitute for an authoritative Ready value. */
data class SettingsUiState(
    val preferences: Observation<AppPreferences> = Observation.Loading,
    val themeOperation: SettingsOperation = SettingsOperation.Idle,
    val fitOperation: SettingsOperation = SettingsOperation.Idle,
) {
    val selectedTheme: ThemeSelection? get() = (preferences as? Observation.Ready)?.value?.theme
    val selectedFit: PageFit? get() = (preferences as? Observation.Ready)?.value?.reader?.pageFit
}

sealed interface SettingsOperation {
    data object Idle : SettingsOperation
    /** Includes the latest queued intent, even while an older write of this field finishes. */
    data class Pending(val requested: SettingsEffect) : SettingsOperation
    /** Acknowledged or canceled; only observation can resolve the current persisted selection. */
    data class Reconciling(val requested: SettingsEffect, val uncertain: Boolean = false) : SettingsOperation
    /** Retry explicitly resubmits this field-level effect, never an entire preferences snapshot. */
    data class Failed(val requested: SettingsEffect, val error: AppError) : SettingsOperation
}
