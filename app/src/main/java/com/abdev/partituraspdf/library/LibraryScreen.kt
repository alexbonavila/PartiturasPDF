package com.abdev.partituraspdf.library

import android.text.format.Formatter
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import com.abdev.partituraspdf.R
import com.abdev.partituraspdf.core.result.AppError
import com.abdev.partituraspdf.core.result.Observation

/** Rendering only. I owns state collection, route lifetime, picker and reader navigation. */
@Composable
fun LibraryScreen(state: LibraryUiState, onAction: (LibraryAction) -> Unit, modifier: Modifier = Modifier) {
    val busy = state.rootSelection == LibraryOperation.Pending || state.navigation == LibraryOperation.Pending
    val available = state.location as? LibraryLocation.Available
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize().safeDrawingPadding(), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                Modifier.widthIn(max = 960.dp).fillMaxSize().testTag("library_list"),
                contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item("title") { Text(stringResource(R.string.library_title), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() }) }
                item("root_controls") {
                    LibraryControls {
                        LibraryButton(if (available == null) R.string.library_select_folder else R.string.library_change_folder,
                            enabled = !busy, description = stringResource(R.string.library_select_folder_description)) { onAction(LibraryAction.ChooseRoot) }
                        if (available != null) {
                            LibraryButton(R.string.library_back, enabled = !busy && available.canGoBack) { onAction(LibraryAction.Back(available.snapshot.version)) }
                            LibraryButton(R.string.library_refresh, enabled = !busy) { onAction(LibraryAction.Refresh) }
                        }
                    }
                }
                item("root_status") {
                    when (val selection = state.rootSelection) {
                        LibraryOperation.Pending -> LibraryLoading(stringResource(R.string.library_selection_pending))
                        LibraryOperation.Canceled -> Text(stringResource(R.string.library_selection_canceled))
                        is LibraryOperation.Failed -> LibraryError(R.string.library_selection_error, selection.failure.error, R.string.library_retry, !busy) { onAction(LibraryAction.Retry) }
                        LibraryOperation.Idle -> Unit
                    }
                }
                item("location_status") {
                    when (val location = state.location) {
                        LibraryLocation.Initializing -> LibraryLoading(stringResource(R.string.library_initializing))
                        LibraryLocation.NoRoot -> Text(stringResource(R.string.library_no_root))
                        is LibraryLocation.Failed -> LibraryError(R.string.library_restore_error, location.failure.error, R.string.library_retry, !busy) { onAction(LibraryAction.Refresh) }
                        is LibraryLocation.Available -> Text(stringResource(R.string.library_location), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                    }
                }
                // FolderRef has no display-name field. Levels represent the real trail without URI parsing,
                // invented ancestor names or unsupported direct breadcrumb navigation.
                if (available != null) items(available.snapshot.breadcrumbs.size, key = { "breadcrumb-$it" }) { level ->
                    Text(if (level == 0) stringResource(R.string.library_root_segment) else stringResource(R.string.library_nested_segment, level),
                        Modifier.padding(start = (minOf(level, 4) * 8).dp))
                }
                item("navigation_status") {
                    when (val navigation = state.navigation) {
                        LibraryOperation.Pending -> LibraryLoading(stringResource(R.string.library_navigation_pending))
                        is LibraryOperation.Failed -> LibraryError(R.string.library_navigation_error, navigation.failure.error, R.string.library_refresh, !busy) { onAction(LibraryAction.Refresh) }
                        else -> Unit
                    }
                    state.documentError?.let { LibraryError(R.string.library_action_error, it, R.string.library_refresh, !busy) { onAction(LibraryAction.Refresh) } }
                }
                when (val content = state.content) {
                    LibraryContent.NotLoaded -> if (available != null && !busy) item("not_loaded") { Text(stringResource(R.string.library_not_loaded)) }
                    is LibraryContent.Loading -> item("loading") { LibraryLoading(stringResource(R.string.library_loading)) }
                    is LibraryContent.Failed -> item("listing_error") { LibraryError(R.string.library_listing_error, content.failure.error, R.string.library_refresh, !busy) { onAction(LibraryAction.Refresh) } }
                    is LibraryContent.Ready -> {
                        if (!content.complete) item("partial") { Text(stringResource(R.string.library_partial), Modifier.semantics { liveRegion = LiveRegionMode.Polite }) }
                        if (content.complete && content.rows.isEmpty()) item("empty") { Text(stringResource(R.string.library_empty)) }
                        itemsIndexed(content.rows, key = { index, row -> "row-${row.key.loadGeneration}-${row.key.navigationVersion}-$index" }) { _, row ->
                            when (row) {
                                is LibraryRow.Directory -> LibraryFolderRow(row, !busy, onAction)
                                is LibraryRow.Pdf -> LibraryPdfRow(row, !busy, onAction)
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LibraryControls(content: @Composable FlowRowScope.() -> Unit) {
    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp), content = content)
}

@Composable
private fun LibraryButton(label: Int, enabled: Boolean = true, description: String? = null, onClick: () -> Unit) {
    OutlinedButton(onClick, Modifier.heightIn(min = 48.dp).then(if (description == null) Modifier else Modifier.semantics { contentDescription = description }),
        enabled = enabled, colors = ButtonDefaults.outlinedButtonColors(contentColor = LocalContentColor.current)) {
        Text(stringResource(label))
    }
}

@Composable
private fun LibraryLoading(message: String) {
    Row(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {
        progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate
        liveRegion = LiveRegionMode.Polite
    }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        CircularProgressIndicator(Modifier.size(24.dp).clearAndSetSemantics { }, color = LocalContentColor.current)
        Text(message, Modifier.weight(1f))
    }
}

@Composable
private fun LibraryError(context: Int, error: AppError, retry: Int, enabled: Boolean, onRetry: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.errorContainer, shape = MaterialTheme.shapes.medium) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(context), style = MaterialTheme.typography.titleSmall)
            Text(libraryErrorText(error), Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            LibraryButton(retry, enabled) { onRetry() }
        }
    }
}

@Composable
private fun libraryErrorText(error: AppError): String = stringResource(when (error) {
    AppError.Access.PERMISSION_DENIED -> R.string.library_error_permission_denied
    AppError.Access.PERMISSION_REVOKED -> R.string.library_error_permission_revoked
    AppError.Access.PROVIDER_UNAVAILABLE -> R.string.library_error_provider_unavailable
    AppError.Access.PROVIDER_IO_FAILURE -> R.string.library_error_provider_io
    AppError.Access.PRIVATE_STORAGE_UNAVAILABLE -> R.string.library_error_private_storage
    AppError.Access.NOT_REGISTERED -> R.string.library_error_not_registered
    AppError.Access.DOCUMENT_DELETED -> R.string.library_error_deleted
    AppError.Conflict.STALE_SESSION, AppError.Conflict.SESSION_CLOSED -> R.string.library_error_stale
    AppError.Conflict.REGISTRATION_CHANGED, AppError.Conflict.REVISION_CHANGED -> R.string.library_error_conflict
    is AppError.Validation -> R.string.library_error_invalid
    is AppError.Resource -> R.string.library_error_resource
    is AppError.Unsupported -> R.string.library_error_unsupported
})

@Composable
private fun LibraryFolderRow(row: LibraryRow.Directory, enabled: Boolean, onAction: (LibraryAction) -> Unit) {
    val name = row.entry.info.name?.takeIf { it.isNotBlank() } ?: stringResource(R.string.library_unknown_folder)
    val enterLabel = stringResource(R.string.library_enter_folder)
    OutlinedCard(Modifier.fillMaxWidth().heightIn(min = 48.dp)
        .clickable(enabled = enabled, role = Role.Button, onClickLabel = enterLabel) { onAction(LibraryAction.Enter(row.key)) }
        .semantics(mergeDescendants = true) { }) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(name, style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.library_folder_type))
        }
    }
}

@Composable
private fun LibraryPdfRow(row: LibraryRow.Pdf, enabled: Boolean, onAction: (LibraryAction) -> Unit) {
    val name = row.entry.info.name?.takeIf { it.isNotBlank() } ?: stringResource(R.string.library_unknown_pdf)
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            Text(stringResource(R.string.library_pdf_type))
            row.entry.info.byteSize?.let { Text(stringResource(R.string.library_size, Formatter.formatShortFileSize(LocalContext.current, it))) }
            when (val association = row.association) {
                LibraryAssociation.LookingUp -> LibraryLoading(stringResource(R.string.library_lookup_pending))
                LibraryAssociation.Registering -> LibraryLoading(stringResource(R.string.library_registration_pending))
                LibraryAssociation.Unregistered -> {
                    Text(stringResource(R.string.library_unregistered))
                    LibraryButton(R.string.library_register, enabled, stringResource(R.string.library_register_description, name)) { onAction(LibraryAction.Register(row.key)) }
                }
                is LibraryAssociation.Failed -> {
                    val lookup = association.operation == LibraryAssociationOperation.LOOKUP
                    LibraryError(if (lookup) R.string.library_lookup_error else R.string.library_registration_error, association.error,
                        if (lookup) R.string.library_retry_lookup else R.string.library_retry_registration, enabled) {
                        onAction(if (lookup) LibraryAction.RetryLookup(row.key) else LibraryAction.RetryRegistration(row.key))
                    }
                }
                is LibraryAssociation.Registered -> {
                    Text(stringResource(R.string.library_registered))
                    LibraryFavoriteControls(row, association, name, enabled, onAction)
                }
            }
            LibraryOpenControls(row, name, enabled, onAction)
        }
    }
}

@Composable
private fun LibraryFavoriteControls(row: LibraryRow.Pdf, registered: LibraryAssociation.Registered, name: String, enabled: Boolean, onAction: (LibraryAction) -> Unit) {
    when (val metadata = registered.metadata) {
        Observation.Loading -> if (registered.favorite !is LibraryFavorite.Reconciling) LibraryLoading(stringResource(R.string.library_metadata_loading))
        is Observation.Failed -> LibraryError(R.string.library_metadata_error, metadata.error, R.string.library_retry_metadata,
            enabled && registered.favorite !is LibraryFavorite.Pending) { onAction(LibraryAction.RetryMetadata(row.key)) }
        is Observation.Ready -> if (registered.favorite !is LibraryFavorite.Reconciling) {
            val favorite = metadata.value.favorite
            val description = stringResource(if (favorite) R.string.library_favorite_remove_description else R.string.library_favorite_add_description, name)
            val status = stringResource(if (favorite) R.string.library_favorite_on else R.string.library_favorite_off)
            OutlinedButton({ onAction(LibraryAction.SetFavorite(row.key, !favorite)) },
                Modifier.heightIn(min = 48.dp).semantics { contentDescription = description; selected = favorite; stateDescription = status },
                enabled = enabled && row.canFavorite, colors = ButtonDefaults.outlinedButtonColors(contentColor = LocalContentColor.current)) {
                Text(stringResource(if (favorite) R.string.library_favorite_remove else R.string.library_favorite_add))
            }
        }
    }
    when (val favorite = registered.favorite) {
        LibraryFavorite.Idle -> Unit
        is LibraryFavorite.Pending -> LibraryLoading(stringResource(R.string.library_favorite_pending))
        is LibraryFavorite.Reconciling -> LibraryLoading(stringResource(R.string.library_favorite_reconciling))
        is LibraryFavorite.Failed -> LibraryError(R.string.library_favorite_error, favorite.error, R.string.library_retry_favorite, enabled) { onAction(LibraryAction.RetryFavorite(row.key)) }
    }
}

@Composable
private fun LibraryOpenControls(row: LibraryRow.Pdf, name: String, enabled: Boolean, onAction: (LibraryAction) -> Unit) {
    val confirmed = row.association is LibraryAssociation.Registered || row.association == LibraryAssociation.Unregistered
    when (val opening = row.opening) {
        LibraryOpening.Idle -> LibraryButton(R.string.library_open, enabled && confirmed, stringResource(R.string.library_open_description, name)) { onAction(LibraryAction.Open(row.key)) }
        LibraryOpening.Pending -> LibraryLoading(stringResource(R.string.library_open_pending))
        LibraryOpening.Accepted -> Text(stringResource(R.string.library_open_accepted), Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        LibraryOpening.Rejected -> {
            Text(stringResource(R.string.library_open_rejected))
            LibraryButton(R.string.library_retry_open, enabled && confirmed) { onAction(LibraryAction.RetryOpen(row.key)) }
        }
        is LibraryOpening.Failed -> LibraryError(R.string.library_open_error, opening.error, R.string.library_retry_open, enabled && confirmed) { onAction(LibraryAction.RetryOpen(row.key)) }
    }
}
