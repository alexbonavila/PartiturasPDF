package com.abdev.partituraspdf.settings

import com.abdev.partituraspdf.core.preferences.PageFit
import com.abdev.partituraspdf.core.preferences.ThemeSelection

sealed interface SettingsAction {
    data class SelectTheme(val selection: ThemeSelection) : SettingsAction
    data class SelectFit(val selection: PageFit) : SettingsAction
    data object RetryObservation : SettingsAction
    data object RetryTheme : SettingsAction
    data object RetryFit : SettingsAction
}
