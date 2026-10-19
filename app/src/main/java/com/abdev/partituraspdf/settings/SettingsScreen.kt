package com.abdev.partituraspdf.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import com.abdev.partituraspdf.R
import com.abdev.partituraspdf.core.foundation.SettingsEffect
import com.abdev.partituraspdf.core.preferences.PageFit
import com.abdev.partituraspdf.core.preferences.ThemeSelection
import com.abdev.partituraspdf.core.result.AppError
import com.abdev.partituraspdf.core.result.Observation

/** Presentation only. I supplies state/callbacks and owns collection, lifecycle and host effects. */
@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onAction: (SettingsAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val ready = state.preferences is Observation.Ready
    Surface(modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize().safeDrawingPadding(), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier.widthIn(max = 640.dp).fillMaxWidth().verticalScroll(rememberScrollState())
                    .testTag("settings_content").padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.semantics { heading() })
                when (val observation = state.preferences) {
                    Observation.Loading -> SettingsProgress(stringResource(R.string.settings_loading))
                    is Observation.Failed -> SettingsError(
                        R.string.settings_read_error, observation.error, R.string.settings_retry_read,
                        enabled = true, tag = "settings_retry_read",
                    ) { onAction(SettingsAction.RetryObservation) }
                    is Observation.Ready -> Unit
                }
                SettingsGroup(R.string.settings_theme_title, "settings_theme") {
                    val enabled = ready && state.themeOperation !is SettingsOperation.Pending &&
                        state.themeOperation !is SettingsOperation.Reconciling
                    // A failed target is resubmitted only by its explicit Retry action, matching
                    // the controller contract; other selections remain available.
                    ThemeSelection.entries.forEach { selection ->
                        SettingsChoice(
                            themeLabel(selection), state.selectedTheme == selection,
                            enabled && (state.themeOperation as? SettingsOperation.Failed)?.requested != SettingsEffect.Theme(selection),
                            "settings_theme_" + selection.key,
                        ) { onAction(SettingsAction.SelectTheme(selection)) }
                    }
                    SettingsOperationStatus(
                        state.themeOperation, ready, R.string.settings_theme_write_error,
                        R.string.settings_retry_theme, "settings_theme",
                        onRetry = { onAction(SettingsAction.RetryTheme) },
                        onRefresh = { onAction(SettingsAction.RetryObservation) },
                    )
                }
                SettingsGroup(R.string.settings_fit_title, "settings_fit") {
                    val enabled = ready && state.fitOperation !is SettingsOperation.Pending &&
                        state.fitOperation !is SettingsOperation.Reconciling
                    PageFit.entries.forEach { selection ->
                        SettingsChoice(
                            fitLabel(selection), state.selectedFit == selection,
                            enabled && (state.fitOperation as? SettingsOperation.Failed)?.requested != SettingsEffect.Fit(selection),
                            "settings_fit_" + selection.key,
                            description = if (selection == PageFit.PAGE) R.string.settings_fit_page_description
                                else R.string.settings_fit_width_description,
                        ) { onAction(SettingsAction.SelectFit(selection)) }
                    }
                    SettingsOperationStatus(
                        state.fitOperation, ready, R.string.settings_fit_write_error,
                        R.string.settings_retry_fit, "settings_fit",
                        onRetry = { onAction(SettingsAction.RetryFit) },
                        onRefresh = { onAction(SettingsAction.RetryObservation) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsGroup(title: Int, tag: String, content: @Composable ColumnScope.() -> Unit) {
    val label = stringResource(title)
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(
            Modifier.fillMaxWidth().padding(16.dp).selectableGroup().testTag(tag)
                .semantics { contentDescription = label },
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(label, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
            content()
        }
    }
}

@Composable
private fun SettingsChoice(
    label: Int,
    selected: Boolean,
    enabled: Boolean,
    tag: String,
    description: Int? = null,
    onSelect: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().testTag(tag).heightIn(min = 56.dp)
            .selectable(selected, enabled = enabled, role = Role.RadioButton,
                onClick = { if (!selected) onSelect() })
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // The whole row is the sole selectable/focusable target.
        RadioButton(selected, onClick = null, enabled = enabled)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(label), style = MaterialTheme.typography.bodyLarge)
            description?.let { Text(stringResource(it), style = MaterialTheme.typography.bodyMedium) }
        }
    }
}

@Composable
private fun SettingsOperationStatus(
    operation: SettingsOperation,
    ready: Boolean,
    errorTitle: Int,
    retryLabel: Int,
    tag: String,
    onRetry: () -> Unit,
    onRefresh: () -> Unit,
) {
    when (operation) {
        SettingsOperation.Idle -> Unit
        is SettingsOperation.Pending -> SettingsProgress(
            stringResource(R.string.settings_pending, requestedLabel(operation.requested)), tag + "_status",
        )
        is SettingsOperation.Reconciling -> {
            SettingsProgress(
                stringResource(
                    if (operation.uncertain) R.string.settings_reconciling_uncertain else R.string.settings_reconciling,
                    requestedLabel(operation.requested),
                ), tag + "_status",
            )
            // Rechecking is also permitted during Loading after uncertain cancellation. It is an
            // explicit recovery request, not a write retry, and must not be disabled by this state.
            SettingsRetry(R.string.settings_verify_again, true, tag + "_verify", onRefresh)
        }
        is SettingsOperation.Failed -> {
            Text(stringResource(R.string.settings_requested, requestedLabel(operation.requested)))
            SettingsError(errorTitle, operation.error, retryLabel, ready, tag + "_retry", onRetry)
        }
    }
}

@Composable
private fun SettingsProgress(message: String, tag: String = "settings_loading") {
    Row(
        Modifier.fillMaxWidth().testTag(tag).semantics(mergeDescendants = true) {
            progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate
            liveRegion = LiveRegionMode.Polite
        },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CircularProgressIndicator(Modifier.size(24.dp).clearAndSetSemantics { }, strokeWidth = 2.dp)
        Text(message, Modifier.weight(1f))
    }
}

@Composable
private fun SettingsError(title: Int, error: AppError, retryLabel: Int, enabled: Boolean, tag: String, onRetry: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }) {
            Text(stringResource(title), color = MaterialTheme.colorScheme.error)
            Text(stringResource(if (error == AppError.Access.PRIVATE_STORAGE_UNAVAILABLE)
                R.string.settings_error_storage else R.string.settings_error_other))
        }
        SettingsRetry(retryLabel, enabled, tag, onRetry)
    }
}

@Composable
private fun SettingsRetry(label: Int, enabled: Boolean, tag: String, onClick: () -> Unit) {
    OutlinedButton(onClick, Modifier.heightIn(min = 48.dp).testTag(tag), enabled = enabled) {
        Text(stringResource(label))
    }
}

private fun themeLabel(selection: ThemeSelection): Int = when (selection) {
    ThemeSelection.SYSTEM -> R.string.settings_theme_system
    ThemeSelection.LIGHT -> R.string.settings_theme_light
    ThemeSelection.DARK -> R.string.settings_theme_dark
}

private fun fitLabel(selection: PageFit): Int = when (selection) {
    PageFit.PAGE -> R.string.settings_fit_page
    PageFit.WIDTH -> R.string.settings_fit_width
}

@Composable
private fun requestedLabel(effect: SettingsEffect): String = stringResource(when (effect) {
    is SettingsEffect.Theme -> themeLabel(effect.selection)
    is SettingsEffect.Fit -> fitLabel(effect.selection)
})
