package com.abdev.partituraspdf.app.host

import com.abdev.partituraspdf.app.navigation.AppDestination
import com.abdev.partituraspdf.core.preferences.AppPreferences
import com.abdev.partituraspdf.core.preferences.ThemeSelection
import com.abdev.partituraspdf.core.result.Observation
import com.abdev.partituraspdf.reader.ReaderPhase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*

/** Loading/Failed use a visual SYSTEM fallback, never a confirmed persisted value. */
internal fun appDarkTheme(preferences: Observation<AppPreferences>, systemDark: Boolean): Boolean =
    when ((preferences as? Observation.Ready)?.value?.theme) {
        ThemeSelection.LIGHT -> false
        ThemeSelection.DARK -> true
        else -> systemDark
    }

internal fun AppHostViewModel.readerVisible(): Boolean {
    val ready = state.value as? AppHostState.Ready ?: return false
    if (navigation.value.destination != AppDestination.READER) return false
    val reading = ready.features.reader?.state?.value ?: return false
    if (reading.leaving || reading.suspended) return false
    val pdf = reading.entry?.controller?.state?.value ?: return false
    val consumer = pdf.checkpointConsumer ?: return false
    return pdf.phase == ReaderPhase.VISIBLE && pdf.frame?.active(consumer) == true
}

/** Wakeups only: the Activity always re-reads current snapshots before touching its Window. */
@OptIn(ExperimentalCoroutinesApi::class)
internal fun AppHostViewModel.readerVisibility(): Flow<Unit> = state.flatMapLatest { host ->
    val reading = (host as? AppHostState.Ready)?.features?.reader
    if (reading == null) flowOf(Unit)
    else combine(navigation, reading.state) { _, lifetime -> lifetime.entry }.flatMapLatest { entry ->
        entry?.controller?.state?.flatMapLatest { pdf -> pdf.frame?.availability?.map { Unit } ?: flowOf(Unit) } ?: flowOf(Unit)
    }
}
internal fun keepReaderScreenOn(visible: Boolean, resumed: Boolean, focused: Boolean): Boolean = visible && resumed && focused

