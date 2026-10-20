package com.abdev.partituraspdf.app.host

import com.abdev.partituraspdf.library.LibraryController
import com.abdev.partituraspdf.settings.SettingsController
import com.abdev.partituraspdf.storage.api.FolderNavigation
import kotlinx.coroutines.CoroutineScope

internal data class AppHostFeatures(
    val folders: FolderNavigation,
    val library: LibraryController,
    val settings: SettingsController,
    val picker: RootPickerCoordinator? = null,
    val reader: ReaderHostLifetime? = null,
)

/** An attempt exists before blocking initialization, retaining ownership even on cancellation. */
internal interface AppHostSession : AutoCloseable {
    fun load(scope: CoroutineScope): AppHostFeatures
    suspend fun retireReader(): ReaderExitReport? = null
    fun stop()
    override fun close()
}

internal sealed interface AppHostState {
    data object Loading : AppHostState
    data class Ready(val features: AppHostFeatures) : AppHostState
    /** Original cause remains inspectable; UI never exposes paths or exception messages. */
    data class Failed(val cause: Throwable, val retryable: Boolean) : AppHostState
    data object Closing : AppHostState
    data class Closed(val failure: Throwable?) : AppHostState
}
