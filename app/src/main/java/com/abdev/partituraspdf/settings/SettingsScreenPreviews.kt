package com.abdev.partituraspdf.settings

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import com.abdev.partituraspdf.core.foundation.SettingsEffect
import com.abdev.partituraspdf.core.preferences.*
import com.abdev.partituraspdf.core.result.*
import com.abdev.partituraspdf.ui.theme.PartiturasPDFTheme

/** Tooling samples only: immutable states, no controller, collector or persistence. */
class SettingsPreviewStates : PreviewParameterProvider<SettingsUiState> {
    override val values: Sequence<SettingsUiState> get() {
        val ready = SettingsUiState(Observation.Ready(AppPreferences()))
        val storage = AppError.Access.PRIVATE_STORAGE_UNAVAILABLE
        return sequenceOf(
            SettingsUiState(),
            ready,
            ready.copy(preferences = Observation.Ready(AppPreferences(
                theme = ThemeSelection.DARK, reader = ReaderPreferences(PageFit.WIDTH),
            ))),
            ready.copy(themeOperation = SettingsOperation.Pending(SettingsEffect.Theme(ThemeSelection.LIGHT))),
            ready.copy(fitOperation = SettingsOperation.Reconciling(SettingsEffect.Fit(PageFit.WIDTH))),
            SettingsUiState(Observation.Failed(storage)),
            ready.copy(themeOperation = SettingsOperation.Failed(SettingsEffect.Theme(ThemeSelection.DARK), storage)),
            ready.copy(fitOperation = SettingsOperation.Failed(SettingsEffect.Fit(PageFit.WIDTH), storage)),
        )
    }
}

@Preview(name = "Settings states light", showBackground = true, widthDp = 360, heightDp = 800)
@Preview(name = "Settings states dark", showBackground = true, widthDp = 360, heightDp = 800,
    uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun SettingsStatesPreview(@PreviewParameter(SettingsPreviewStates::class) state: SettingsUiState) {
    PartiturasPDFTheme { SettingsScreen(state, onAction = {}) }
}

@Preview(name = "Narrow 200 percent text", showBackground = true, widthDp = 320, heightDp = 640, fontScale = 2f)
@Composable
private fun SettingsLargeTextPreview() {
    PartiturasPDFTheme {
        SettingsScreen(SettingsUiState(
            preferences = Observation.Ready(AppPreferences()),
            themeOperation = SettingsOperation.Reconciling(SettingsEffect.Theme(ThemeSelection.DARK), uncertain = true),
            fitOperation = SettingsOperation.Failed(SettingsEffect.Fit(PageFit.WIDTH), AppError.Access.PRIVATE_STORAGE_UNAVAILABLE),
        ), onAction = {})
    }
}

@Preview(name = "Tablet landscape", showBackground = true, widthDp = 1024, heightDp = 600)
@Composable
private fun SettingsTabletPreview() {
    PartiturasPDFTheme {
        SettingsScreen(SettingsUiState(Observation.Ready(AppPreferences(
            theme = ThemeSelection.LIGHT, reader = ReaderPreferences(PageFit.WIDTH),
        ))), onAction = {})
    }
}
