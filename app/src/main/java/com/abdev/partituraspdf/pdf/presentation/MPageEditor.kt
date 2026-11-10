package com.abdev.partituraspdf.pdf.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.abdev.partituraspdf.R
import com.abdev.partituraspdf.pdf.api.PdfOperationFailure
import com.abdev.partituraspdf.storage.api.*
import com.abdev.partituraspdf.storage.transaction.MRecoveryStatus

@Composable
internal fun MEditOutcome(state: MEditState) {
    val message = when {
        state.publication is PublicationResult.VerifiedNewDocument ->
            if (state.publication.cleanup == SnapshotCleanupStatus.PENDING) R.string.m_saved_cleanup else R.string.m_saved
        state.publication == PublicationResult.RejectedBeforePublicEffect -> R.string.m_canceled_before
        state.publication != null -> R.string.m_uncertain
        state.rejection != null -> when (state.rejection.reason) {
            PdfOperationFailure.InvalidPlan -> R.string.m_invalid_plan
            PdfOperationFailure.ProtectedDocument -> R.string.m_protected
            PdfOperationFailure.UnsupportedDocument -> R.string.m_unsupported
            PdfOperationFailure.Closing -> R.string.m_busy
            else -> R.string.m_rejected
        }
        state.preparationBusy -> R.string.m_busy
        state.phase == MEditPhase.PREPARING || state.phase == MEditPhase.PUBLISHING -> R.string.m_busy
        state.phase == MEditPhase.PICKER -> R.string.m_picker_wait
        else -> null
    }
    if (message != null) Text(stringResource(message), Modifier.testTag("m_outcome").semantics { liveRegion = LiveRegionMode.Polite })
    if (state.cancellation == MEditCancellation.BEFORE_PICKER_CLAIM)
        Text(stringResource(R.string.m_picker_not_launched))
    if (state.cancellation == MEditCancellation.AFTER_PICKER_CLAIM)
        Text(stringResource(R.string.m_picker_may_exist))
    if (state.rejection?.cleanup == SnapshotCleanupStatus.PENDING)
        Text(stringResource(R.string.m_cleanup_pending), Modifier.semantics { liveRegion = LiveRegionMode.Polite })
    if (state.recovering) Text(stringResource(R.string.m_recovering))
    if (state.recovery.isNotEmpty()) Text(stringResource(R.string.m_recovery_review), Modifier.testTag("m_recovery").semantics { liveRegion = LiveRegionMode.Polite })
}

/** Overlay only: existing Reader remains mounted underneath with its sole native route. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun MPageEditor(state: MEditState, onChange: (MEditAction) -> Unit, onSave: () -> Unit, onClose: () -> Unit,
                         modifier: Modifier = Modifier) {
    val draft = state.draft ?: return
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier.widthIn(max = 840.dp).fillMaxWidth(0.95f).fillMaxHeight(0.9f).testTag("m_editor"), shape = MaterialTheme.shapes.large) {
            Column(Modifier.padding(16.dp)) {
                LazyColumn(Modifier.weight(1f).fillMaxWidth().testTag("m_pages")) {
                    item(key = "header") {
                        Column {
                            Text(stringResource(R.string.m_editor_title), Modifier.semantics { heading() }, style = MaterialTheme.typography.titleLarge)
                            Text(stringResource(R.string.m_separate_copy))
                            if (state.availability == MEditAvailability.UNAPPROVED)
                                Text(stringResource(R.string.m_unavailable), Modifier.testTag("m_unavailable").semantics { liveRegion = LiveRegionMode.Polite })
                            MEditOutcome(state)
                        }
                    }
                    items(draft.pages, key = { "page:${it.index}" }) { page ->
                        val number = page.index + 1
                        val include = stringResource(R.string.m_include_page, number)
                        val up = stringResource(R.string.m_move_up, number)
                        val down = stringResource(R.string.m_move_down, number)
                        val rotate = stringResource(R.string.m_rotate_page, number)
                        val editable = state.phase == MEditPhase.DRAFT
                        Column(Modifier.fillMaxWidth().padding(vertical = 6.dp).testTag("m_page_${page.index}")) {
                            Text(stringResource(R.string.m_page_rotation, number, page.turns * 90))
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Checkbox(page.included, { onChange(MEditAction.Include(page.index, it)) },
                                    Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp).semantics { contentDescription = include }, enabled = editable)
                                TextButton({ onChange(MEditAction.Move(page.index, -1)) }, enabled = editable && draft.pages.first() != page,
                                    modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = up }) { Text(stringResource(R.string.m_up)) }
                                TextButton({ onChange(MEditAction.Move(page.index, 1)) }, enabled = editable && draft.pages.last() != page,
                                    modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = down }) { Text(stringResource(R.string.m_down)) }
                                TextButton({ onChange(MEditAction.Rotate(page.index)) }, enabled = editable,
                                    modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = rotate }) { Text(stringResource(R.string.m_rotate)) }
                            }
                        }
                    }
                }
                if (draft.pages.none { it.included }) Text(stringResource(R.string.m_empty_selection), Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onSave, enabled = state.canSave, modifier = Modifier.heightIn(min = 48.dp).testTag("m_save_as")) { Text(stringResource(R.string.m_save_as)) }
                    OutlinedButton(onClose, modifier = Modifier.heightIn(min = 48.dp).testTag("m_close")) { Text(stringResource(R.string.m_return_reader)) }
                }
            }
        }
    }
}
