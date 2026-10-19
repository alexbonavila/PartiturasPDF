package com.abdev.partituraspdf.app.host

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import com.abdev.partituraspdf.reader.ReaderScreen
import com.abdev.partituraspdf.reader.ReaderPhase
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.abdev.partituraspdf.R
import com.abdev.partituraspdf.app.navigation.AppDestination
import com.abdev.partituraspdf.core.result.AppError
import com.abdev.partituraspdf.library.LibraryAction
import com.abdev.partituraspdf.library.LibraryOperation
import com.abdev.partituraspdf.library.LibraryScreen
import com.abdev.partituraspdf.settings.SettingsScreen

@Composable
internal fun AppHost(owner: AppHostViewModel) {
    val state by owner.state.collectAsStateWithLifecycle()
    val navigation by owner.navigation.collectAsStateWithLifecycle()
    val revision = navigation.revision // Freeze the rendered route for late UI callbacks.
    BackHandler(enabled = state is AppHostState.Ready && navigation.destination != AppDestination.LIBRARY) {
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
                            val exitReport = current.features.reader?.state?.collectAsState()?.value?.report
                            val picker = current.features.picker
                            val recovery = picker?.recovery?.collectAsStateWithLifecycle()?.value
                            if (recovery != null) Column(Modifier.padding(24.dp).testTag("app_root_recovery")) {
                                Text(stringResource(R.string.library_restore_error), Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                                Text(stringResource(when (recovery) {
                                    AppError.Access.PERMISSION_REVOKED -> R.string.library_error_permission_revoked
                                    AppError.Access.PERMISSION_DENIED -> R.string.library_error_permission_denied
                                    AppError.Access.PRIVATE_STORAGE_UNAVAILABLE -> R.string.library_error_private_storage
                                    AppError.Access.PROVIDER_UNAVAILABLE -> R.string.library_error_provider_unavailable
                                    AppError.Access.PROVIDER_IO_FAILURE -> R.string.library_error_provider_io
                                    is AppError.Validation -> R.string.library_error_invalid
                                    else -> R.string.library_error_stale
                                }))
                                val pending = library.rootSelection == LibraryOperation.Pending
                                if (pending) Text(stringResource(R.string.library_selection_pending))
                                OutlinedButton(onClick = { picker.reconcile() }, modifier = Modifier.heightIn(min = 48.dp), enabled = !pending) {
                                    Text(stringResource(R.string.library_retry))
                                }
                                OutlinedButton(onClick = { owner.library(LibraryAction.ChooseRoot, revision) },
                                    modifier = Modifier.heightIn(min = 48.dp), enabled = !pending) {
                                    Text(stringResource(R.string.library_select_folder))
                                }
                            } else Column {
                                exitReport?.failures?.forEach { failure ->
                                    Text(stringResource(if (failure.stage == ReaderCleanupStage.CHECKPOINT)
                                        R.string.reader_checkpoint_failure else R.string.library_action_error),
                                        Modifier.padding(8.dp).semantics { liveRegion = LiveRegionMode.Polite })
                                }
                                LibraryScreen(library, { owner.library(it, revision) })
                            }
                        }
                        AppDestination.SETTINGS -> {
                            val settings by current.features.settings.state.collectAsStateWithLifecycle()
                            SettingsScreen(settings, { owner.settings(it, revision) })
                        }
                        AppDestination.READER -> {
                            val lifetime = current.features.reader
                            if (lifetime != null) {
                                // Retirement state stays delivered even while lifecycle-aware PDF-state collection pauses.
                                val reading by lifetime.state.collectAsState()
                                val entry = reading.entry
                                if (reading.leaving || entry == null) AppStartupLoading()
                                else {
                                    // Keep the mounted screen and local zoom while temporary suspension removes its frame.
                                    // This delivery must outlive STOPPED, independently of ordinary lifecycle collectors.
                                    val pdf by entry.controller.state.collectAsState()
                                    Column {
                                        if (reading.suspended) {
                                            reading.report.failures.forEach { failure -> Text(stringResource(if (failure.stage == ReaderCleanupStage.CHECKPOINT)
                                                R.string.reader_checkpoint_failure else R.string.library_action_error)) }
                                            if (reading.recoveryBlocked) Text(stringResource(R.string.library_action_error))
                                            else TextButton(onClick = { owner.resumeReader(revision) }) { Text(stringResource(R.string.reader_retry)) }
                                        }
                                        ReaderScreen(if (reading.suspended) pdf.copy(frame = null, phase = ReaderPhase.QUIESCED,
                                            requestedPage = reading.suspendedPage) else pdf,
                                            { owner.reader(it, entry.intent.sequence, revision) }, { owner.back(revision) })
                                    }
                                }
                            }
                        }
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
