package com.abdev.partituraspdf.reader

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.abdev.partituraspdf.R
import com.abdev.partituraspdf.core.result.Outcome

/** Ephemeral modal data only: no frame/bitmap lease and no saved document capability. */
internal data class ReaderPageJumpTarget(val context: ReaderNavigationContext, val presentedNumber: Int)

internal fun readerPageJumpTarget(state: ReaderUiState): ReaderPageJumpTarget? {
    val context = readerNavigationContext(state) ?: return null
    if (state.phase != ReaderPhase.VISIBLE || state.failure != null) return null
    val frame = state.frame ?: return null
    val page = state.presentedPage ?: return null
    if (page != frame.displayInfo.page.page || context.session.validate(frame.displayInfo.page) !is Outcome.Success) return null
    val consumer = state.checkpointConsumer ?: return null
    if (consumer.frame !== frame || !frame.active(consumer)) return null
    return ReaderPageJumpTarget(context, page.value + 1)
}

/** UI recheck only. The controller still performs the definitive atomic authorization. */
internal fun readerPageJumpAction(target: ReaderPageJumpTarget, input: String, latest: ReaderUiState): ReaderAction.PageIfCurrent? {
    if (!readerNavigationContextIsCurrent(target.context, latest) || latest.failure != null) return null
    val valid = validateReaderPageNumber(input, latest.session?.pageCount) as? ReaderPageInput.Valid ?: return null
    return ReaderAction.PageIfCurrent(valid.index, target.context.intent, target.context.session)
}

@Composable
internal fun ReaderPageJumpDialog(target: ReaderPageJumpTarget, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var input by remember(target) {
        val initial = target.presentedNumber.toString()
        mutableStateOf(TextFieldValue(initial, TextRange(0, initial.length)))
    }
    val focus = remember { FocusRequester() }
    val validation = validateReaderPageNumber(input.text, target.context.session.pageCount)
    val error = when (validation) {
        ReaderPageInput.InvalidNumber -> stringResource(R.string.reader_jump_invalid_number)
        ReaderPageInput.OutOfRange -> stringResource(R.string.reader_jump_out_of_range, target.context.session.pageCount)
        is ReaderPageInput.Valid -> null
    }
    val confirm = { if (validation is ReaderPageInput.Valid) onConfirm(input.text) }
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("reader_jump_dialog").onPreviewKeyEvent {
            if (it.key == Key.Escape) {
                if (it.type == KeyEventType.KeyUp) onDismiss()
                true
            } else false
        },
        title = { Text(stringResource(R.string.reader_jump_title)) },
        text = {
            // LocalView here belongs to the separate Compose Dialog composition, not Activity.
            val view = LocalView.current
            val window = checkNotNull((view.parent as? DialogWindowProvider)?.window)
            DisposableEffect(window) {
                val policy = ReaderDialogImePolicy(window)
                onDispose { policy.close() }
            }
            // DisposableEffect installs the policy before this focus request is launched.
            LaunchedEffect(window, target) { focus.requestFocus() }
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.reader_jump_total, target.context.session.pageCount))
                OutlinedTextField(
                    value = input, onValueChange = { input = it }, singleLine = true,
                    label = { Text(stringResource(R.string.reader_jump_page_input)) },
                    isError = error != null,
                    supportingText = error?.let { message -> { Text(message, Modifier.semantics { liveRegion = LiveRegionMode.Polite }) } },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { confirm() }),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus).testTag("reader_jump_input"),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { confirm() }, enabled = validation is ReaderPageInput.Valid, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.reader_jump_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.reader_jump_cancel)) }
        },
    )
}
