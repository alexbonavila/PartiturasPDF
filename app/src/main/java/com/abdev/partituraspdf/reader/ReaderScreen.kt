package com.abdev.partituraspdf.reader

import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.abdev.partituraspdf.R
import com.abdev.partituraspdf.core.foundation.DocumentOpenRequest
import com.abdev.partituraspdf.core.preferences.PageFit
import com.abdev.partituraspdf.core.result.AppError
import com.abdev.partituraspdf.core.result.Outcome

/** No route opening, native rendering or persistence here. I supplies state, callbacks and lifetime. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReaderScreen(state: ReaderUiState, onAction: (ReaderAction) -> Unit, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val action by rememberUpdatedState(onAction)
    val latest by rememberUpdatedState(state)
    var viewport by remember { mutableStateOf(ReaderViewport(0, 0)) }
    var transform by remember(state.intent) { mutableStateOf(ReaderTransform()) }
    var options by remember { mutableStateOf(false) }
    val available = state.phase !in setOf(ReaderPhase.IDLE, ReaderPhase.OPENING, ReaderPhase.QUIESCED, ReaderPhase.RETIRED) && state.session != null
    val frame = state.frame.takeIf { available }
    val attachable = frame?.availability?.collectAsState()?.value == true
    val metadata = frame?.displayInfo
    val currentMetadata by rememberUpdatedState(metadata)
    val geometry = metadata?.let { readerImageLayout(it.width, it.height, viewport, state.fit, transform) }
    val color = MaterialTheme.colorScheme.surfaceVariant.toArgb()
    val slop = LocalViewConfiguration.current.touchSlop
    val edgeMinimum = with(LocalDensity.current) { 48.dp.toPx() }

    LaunchedEffect(viewport) {
        if (viewport.widthPx > 0 && viewport.heightPx > 0 && viewport != latest.viewport) action(ReaderAction.Viewport(viewport))
    }
    LaunchedEffect(state.intent, state.requestedPage) { transform = transform.copy(panX = 0f, panY = 0f) }
    LaunchedEffect(available) { if (!available) options = false }
    SideEffect { if (geometry != null && geometry.transform != transform) transform = geometry.transform }

    fun page(delta: Int) {
        action(ReaderAction.Step(delta))
    }
    fun pan(dx: Float, dy: Float, zoom: Float = 1f) {
        if (!dx.isFinite() || !dy.isFinite() || !zoom.isFinite() || zoom <= 0) return
        val proposed = transform.copy(zoom = (transform.zoom * zoom).coerceIn(1f, 4f), panX = transform.panX + dx, panY = transform.panY + dy)
        transform = currentMetadata?.let { readerImageLayout(it.width, it.height, viewport, latest.fit, proposed)?.transform } ?: proposed
    }

    Surface(modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding()) {
            val chromeHeight = minOf(128.dp, maxHeight * .3f)
            Column(Modifier.fillMaxSize()) {
                // Fixed allocation avoids render -> status height -> viewport -> render feedback.
                // At large font size every control/status remains reachable by scrolling chrome.
                Column(Modifier.fillMaxWidth().height(chromeHeight).verticalScroll(rememberScrollState()).padding(horizontal = 8.dp)) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        ReaderButton(R.string.reader_back, true, onBack)
                        ReaderButton(R.string.reader_previous, available && (state.requestedPage?.value ?: 0) > 0) { page(-1) }
                        ReaderButton(R.string.reader_next, available && (state.requestedPage?.value ?: Int.MAX_VALUE) < (state.session?.pageCount ?: 0) - 1) { page(1) }
                        Box {
                            ReaderButton(R.string.reader_options, available) { options = true }
                            DropdownMenu(options && available, { options = false }) {
                                DropdownMenuItem(text = { Text(stringResource(R.string.reader_fit_page)) }, modifier = Modifier.semantics { selected = state.fit == PageFit.PAGE }, onClick = { options = false; action(ReaderAction.Fit(PageFit.PAGE)) })
                                DropdownMenuItem(text = { Text(stringResource(R.string.reader_fit_width)) }, modifier = Modifier.semantics { selected = state.fit == PageFit.WIDTH }, onClick = { options = false; action(ReaderAction.Fit(PageFit.WIDTH)) })
                                DropdownMenuItem(text = { Text(stringResource(R.string.reader_zoom_in)) }, enabled = transform.zoom < 4f,
                                    onClick = { pan(0f, 0f, 1.25f) })
                                DropdownMenuItem(text = { Text(stringResource(R.string.reader_zoom_out)) }, enabled = transform.zoom > 1f,
                                    onClick = { pan(0f, 0f, .8f) })
                                DropdownMenuItem(text = { Text(stringResource(R.string.reader_zoom_reset)) }, onClick = { transform = ReaderTransform() })
                            }
                        }
                    }
                    val current = state.presentedPage?.takeIf { it == metadata?.page?.page && state.phase != ReaderPhase.RENDERED }
                    Text(if (current == null) stringResource(R.string.reader_page_unpresented)
                        else stringResource(R.string.reader_page_counter, current.value + 1, state.session!!.pageCount),
                        Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                    ReaderStatus(state, onAction)
                }

                val displayed = state.presentedPage?.takeIf { it == metadata?.page?.page && state.phase != ReaderPhase.RENDERED }
                val pageDescription = displayed?.let { stringResource(R.string.reader_page_description, it.value + 1, state.session!!.pageCount) }
                    ?: stringResource(R.string.reader_no_visible_page)
                val previousLabel = stringResource(R.string.reader_previous)
                val nextLabel = stringResource(R.string.reader_next)
                val zoomInLabel = stringResource(R.string.reader_zoom_in)
                val zoomOutLabel = stringResource(R.string.reader_zoom_out)
                val resetLabel = stringResource(R.string.reader_zoom_reset)
                val zoomDescription = stringResource(R.string.reader_zoom_state, (transform.zoom * 100).toInt())
                // One gesture detector arbitrates taps, pinch, drag and WIDTH vertical scrolling.
                Box(Modifier.weight(1f).fillMaxWidth().testTag("reader_page")
                    .onSizeChanged { if (it.width > 0 && it.height > 0) viewport = ReaderViewport(it.width, it.height) }
                    .semantics {
                        contentDescription = pageDescription; stateDescription = zoomDescription
                        if (available) {
                            customActions = buildList {
                                if ((state.requestedPage?.value ?: 0) > 0) add(CustomAccessibilityAction(previousLabel) { page(-1); true })
                                if ((state.requestedPage?.value ?: Int.MAX_VALUE) < (state.session?.pageCount ?: 0) - 1)
                                    add(CustomAccessibilityAction(nextLabel) { page(1); true })
                                if (transform.zoom < 4f) add(CustomAccessibilityAction(zoomInLabel) { pan(0f, 0f, 1.25f); true })
                                if (transform.zoom > 1f) add(CustomAccessibilityAction(zoomOutLabel) { pan(0f, 0f, .8f); true })
                                add(CustomAccessibilityAction(resetLabel) { transform = ReaderTransform(); true })
                            }
                            if ((geometry?.verticalRange ?: 0f) > 0f) {
                                verticalScrollAxisRange = ScrollAxisRange({ -transform.panY }, { geometry?.verticalRange ?: 0f })
                                scrollBy { x, y -> pan(-x, -y); true }
                            }
                        }
                    }
                    .onKeyEvent { event ->
                        if (event.type != KeyEventType.KeyDown || !available || options || event.isCtrlPressed || event.isAltPressed || event.isMetaPressed) false else when (event.key) {
                            Key.DirectionLeft -> { page(-1); true }
                            Key.DirectionRight -> { page(1); true }
                            Key.DirectionUp -> { pan(0f, viewport.heightPx * .1f); true }
                            Key.DirectionDown -> { pan(0f, -viewport.heightPx * .1f); true }
                            Key.Plus, Key.Equals -> { pan(0f, 0f, 1.25f); true }
                            Key.Minus -> { pan(0f, 0f, .8f); true }
                            Key.Zero -> { transform = ReaderTransform(); true }
                            else -> false
                        }
                    }.focusable()
                    .pointerInput(state.intent, state.requestedPage, viewport, available, slop, edgeMinimum) {
                        if (!available) return@pointerInput
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val gate = ReaderGestureGate(slop)
                            gate.update(1, 0f, 0f, 1f, down.isConsumed)
                            var event = awaitPointerEvent()
                            while (true) {
                                val delta = event.calculatePan()
                                val zoom = event.calculateZoom()
                                if (gate.update(event.changes.count { it.pressed || it.previousPressed }, delta.x, delta.y, zoom,
                                        event.changes.any { it.isConsumed })) {
                                    pan(delta.x, delta.y, zoom)
                                    event.changes.forEach { it.consume() }
                                }
                                if (event.changes.none { it.pressed }) break
                                event = awaitPointerEvent()
                            }
                            if (gate.isTap()) readerEdgeStep(down.position.x, viewport.widthPx, edgeMinimum)?.let { action(ReaderAction.Step(it)) }
                        }
                    }) {
                    AndroidView(factory = { ReaderPageSurface(it) }, modifier = Modifier.fillMaxSize(),
                        onReset = null, onRelease = { it.release() },
                        update = { surface -> surface.update(frame, geometry, color, attachable) { action(ReaderAction.Presented(it)) } })
                }
            }
        }
    }
}

@Composable
private fun ReaderButton(label: Int, enabled: Boolean, onClick: () -> Unit) {
    OutlinedButton(onClick, Modifier.heightIn(min = 48.dp), enabled = enabled,
        colors = ButtonDefaults.outlinedButtonColors(contentColor = LocalContentColor.current)) { Text(stringResource(label)) }
}

@Composable
private fun ReaderStatus(state: ReaderUiState, onAction: (ReaderAction) -> Unit) {
    val label = when (state.phase) {
        ReaderPhase.IDLE -> R.string.reader_idle
        ReaderPhase.OPENING -> R.string.reader_opening
        ReaderPhase.READY -> R.string.reader_waiting_viewport
        ReaderPhase.RENDERING -> R.string.reader_rendering
        ReaderPhase.RENDERED -> R.string.reader_awaiting_draw
        ReaderPhase.VISIBLE -> null
        ReaderPhase.ERROR -> null
        ReaderPhase.CANCELED -> R.string.reader_canceled
        ReaderPhase.QUIESCED -> R.string.reader_paused
        ReaderPhase.RETIRED -> R.string.reader_retired
    }
    if (label != null) Text(stringResource(label), Modifier.semantics { liveRegion = LiveRegionMode.Polite })
    state.failure?.let { failure ->
        val error = when (failure.error) {
            AppError.Access.PERMISSION_DENIED -> R.string.reader_error_permission
            AppError.Access.PERMISSION_REVOKED -> R.string.reader_error_revoked
            AppError.Access.PROVIDER_UNAVAILABLE -> R.string.reader_error_provider
            AppError.Access.PROVIDER_IO_FAILURE -> R.string.reader_error_io
            AppError.Access.PRIVATE_STORAGE_UNAVAILABLE -> R.string.reader_error_storage
            AppError.Access.NOT_REGISTERED -> R.string.reader_error_registration
            AppError.Access.DOCUMENT_DELETED -> R.string.reader_error_deleted
            AppError.Validation.INVALID_DOCUMENT -> R.string.reader_error_invalid
            AppError.Validation.INVALID_PAGE -> R.string.reader_error_page
            AppError.Validation.INVALID_RENDER_REQUEST -> R.string.reader_error_render_request
            AppError.Conflict.SESSION_CLOSED -> R.string.reader_error_closed
            is AppError.Conflict -> R.string.reader_error_stale
            AppError.Resource.INPUT_LIMIT -> R.string.reader_error_input_limit
            is AppError.Resource -> R.string.reader_error_resource
            AppError.Unsupported.PASSWORD_PROTECTED -> R.string.reader_error_password
            is AppError.Unsupported -> R.string.reader_error_unsupported
        }
        Text(stringResource(error), Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        val recovery = when (readerRetry(state)) {
            ReaderRetry.OPEN -> R.string.reader_recovery_open
            ReaderRetry.RENDER -> R.string.reader_recovery_render
            ReaderRetry.PRESENTATION -> R.string.reader_recovery_checkpoint
            ReaderRetry.NONE -> R.string.reader_recovery_back
        }
        Text(stringResource(recovery))
    }
    val checkpoint = when {
        state.checkpoint == ReaderCheckpoint.None -> null
        state.intent?.request is DocumentOpenRequest.Transient -> R.string.reader_checkpoint_transient
        state.checkpoint == ReaderCheckpoint.Scheduling -> R.string.reader_checkpoint_scheduling
        state.checkpoint == ReaderCheckpoint.Canceled -> R.string.reader_checkpoint_canceled
        (state.checkpoint as? ReaderCheckpoint.Unconfirmed)?.result is Outcome.Failure -> R.string.reader_checkpoint_failure
        else -> R.string.reader_checkpoint_unconfirmed
    }
    if (checkpoint != null) Text(stringResource(checkpoint))
    if (readerRetry(state) != ReaderRetry.NONE) ReaderButton(R.string.reader_retry, true) { onAction(ReaderAction.Retry) }
}
