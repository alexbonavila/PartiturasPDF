package com.abdev.partituraspdf.app.host

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.abdev.partituraspdf.R
import com.abdev.partituraspdf.app.navigation.AppDestination
import com.abdev.partituraspdf.library.LibraryScreen
import com.abdev.partituraspdf.settings.SettingsScreen

@Composable
internal fun AppHost(owner: AppHostViewModel) {
    val state by owner.state.collectAsStateWithLifecycle()
    val navigation by owner.navigation.collectAsStateWithLifecycle()
    val revision = navigation.revision // Freeze the rendered route for late UI callbacks.
    BackHandler(enabled = state is AppHostState.Ready && navigation.destination == AppDestination.SETTINGS) {
        owner.back(revision)
    }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        when (val current = state) {
            AppHostState.Loading, AppHostState.Closing -> AppStartupLoading()
            is AppHostState.Failed -> AppStartupFailure(current.retryable, owner::retry)
            is AppHostState.Closed -> AppStartupFailure(false, owner::retry)
            is AppHostState.Ready -> Column(Modifier.fillMaxSize().safeDrawingPadding().testTag("app_shell")) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(stringResource(R.string.app_title), Modifier.weight(1f).padding(vertical = 12.dp)
                        .semantics { heading() }, style = MaterialTheme.typography.titleLarge)
                    if (navigation.destination == AppDestination.LIBRARY) {
                        TextButton(onClick = { owner.showSettings(revision) },
                            modifier = Modifier.heightIn(min = 48.dp).testTag("app_settings")) {
                            Text(stringResource(R.string.app_settings))
                        }
                    } else {
                        TextButton(onClick = { owner.back(revision) },
                            modifier = Modifier.heightIn(min = 48.dp).testTag("app_back")) {
                            Text(stringResource(R.string.app_back))
                        }
                    }
                }
                // This temporary boundary is explicit; unsupported callbacks return typed failures.
                Text(stringResource(R.string.app_integration_boundary), Modifier.padding(horizontal = 16.dp),
                    style = MaterialTheme.typography.bodySmall)
                Box(Modifier.weight(1f)) {
                    when (navigation.destination) {
                        AppDestination.LIBRARY -> {
                            val library by current.features.library.state.collectAsStateWithLifecycle()
                            LibraryScreen(library, { owner.library(it, revision) })
                        }
                        AppDestination.SETTINGS -> {
                            val settings by current.features.settings.state.collectAsStateWithLifecycle()
                            SettingsScreen(settings, { owner.settings(it, revision) })
                        }
                        AppDestination.READER -> Text(stringResource(R.string.app_reader_unavailable))
                    }
                }
            }
        }
    }
}

@Composable
internal fun AppStartupLoading() {
    Column(Modifier.fillMaxSize().padding(24.dp).testTag("app_loading").semantics(mergeDescendants = true) {
        progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate
        liveRegion = LiveRegionMode.Polite
    }) {
        CircularProgressIndicator(Modifier.clearAndSetSemantics { })
        Text(stringResource(R.string.app_loading))
    }
}

@Composable
internal fun AppStartupFailure(retryable: Boolean, onRetry: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp).testTag("app_startup_failure")) {
        Text(stringResource(R.string.app_startup_failure), Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        if (retryable) OutlinedButton(onClick = onRetry, modifier = Modifier.heightIn(min = 48.dp)) {
            Text(stringResource(R.string.app_retry))
        }
    }
}
