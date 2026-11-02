package com.abdev.partituraspdf.reader

import androidx.compose.foundation.interaction.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import com.abdev.partituraspdf.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReaderPageSeek(state: ReaderUiState, onAction: (ReaderAction) -> Unit) {
    val current by rememberUpdatedState(state)
    val dispatch by rememberUpdatedState(onAction)
    val context = readerNavigationContext(state)
    val eligible = readerSeekTarget(state)
    var pending by remember(context) { mutableStateOf<Pair<ReaderSeekTarget, Int>?>(null) }
    val interactions = remember { MutableInteractionSource() }
    LaunchedEffect(eligible) { if (eligible == null) pending = null }
    LaunchedEffect(interactions) {
        interactions.interactions.collect {
            if (it is DragInteraction.Cancel || it is PressInteraction.Cancel) pending = null
        }
    }
    val count = state.session?.pageCount ?: 1
    val presented = eligible?.presentedNumber?.minus(1)
    val index = pending?.second ?: presented ?: 0
    val label = stringResource(R.string.reader_seek_label)
    val description = pending?.let { stringResource(R.string.reader_seek_preview, it.second + 1, count) }
        ?: presented?.let { stringResource(R.string.reader_page_counter, it + 1, count) }
        ?: stringResource(R.string.reader_page_unpresented)
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp).testTag("reader_seek")) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(R.string.reader_seek_endpoint, 1)); Text(description, Modifier.weight(1f).padding(horizontal = 8.dp).testTag("reader_seek_position")); Text(stringResource(R.string.reader_seek_endpoint, count))
        }
        key(context) { Slider(
            value = readerSeekPosition(index, count), valueRange = 0f..1f, steps = 0,
            enabled = eligible != null && count > 1,
            interactionSource = interactions,
            // Material3's default measured thumb is 44dp; padding alone does not enlarge
            // the inner slider semantics/gesture layout. Keep the actual layout at 48dp.
            thumb = { SliderDefaults.Thumb(interactionSource = interactions,
                enabled = eligible != null && count > 1, modifier = Modifier.height(48.dp)) },
            onValueChange = { position ->
                val live = readerSeekTarget(current)
                val captured = pending?.first ?: live
                val selected = readerSeekIndex(position, current.session?.pageCount ?: 0)
                if (live != null && captured != null && selected != null && readerNavigationContextIsCurrent(captured.context, current))
                    pending = captured to selected
            },
            onValueChangeFinished = {
                val selection = pending
                pending = null
                if (selection != null) readerSeekAction(selection.first, selection.second, current)?.let { dispatch(it) }
            },
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("reader_seek_slider").semantics {
                contentDescription = label
                stateDescription = description
            },
        ) }
    }
}
