package com.abdev.partituraspdf.library

import com.abdev.partituraspdf.core.document.DocumentEntry
import com.abdev.partituraspdf.core.result.AppError
import com.abdev.partituraspdf.storage.api.FolderNavigationState
import java.util.Collections

data class LibraryUiState(
    val location: LibraryLocation = LibraryLocation.Initializing,
    val rootSelection: LibraryOperation = LibraryOperation.Idle,
    val content: LibraryContent = LibraryContent.NotLoaded,
    val navigation: LibraryOperation = LibraryOperation.Idle,
)

sealed interface LibraryLocation {
    data object Initializing : LibraryLocation
    data object NoRoot : LibraryLocation
    data class Available(val snapshot: FolderNavigationState) : LibraryLocation {
        val canGoBack: Boolean get() = snapshot.breadcrumbs.size > 1
    }
    data class Failed(val failure: LibraryFailure) : LibraryLocation
}

sealed interface LibraryOperation {
    data object Idle : LibraryOperation
    data object Pending : LibraryOperation
    /** Only an explicit picker cancellation, never a swallowed CancellationException. */
    data object Canceled : LibraryOperation
    data class Failed(val failure: LibraryFailure) : LibraryOperation
}

sealed interface LibraryContent {
    data object NotLoaded : LibraryContent
    data class Loading(val generation: Long) : LibraryContent
    /** Complete and incomplete empty results deliberately have different meanings. */
    class Ready(rows: List<LibraryRow>, val complete: Boolean) : LibraryContent {
        val rows: List<LibraryRow> = Collections.unmodifiableList(rows.toList())
    }
    data class Failed(val failure: LibraryFailure) : LibraryContent
}

sealed interface LibraryRow {
    val key: LibraryRowKey
    val entry: DocumentEntry
    data class Directory(override val key: LibraryRowKey, override val entry: DocumentEntry) : LibraryRow
    data class Pdf(override val key: LibraryRowKey, override val entry: DocumentEntry) : LibraryRow
}

enum class LibraryFailureOperation { RESTORE, SELECTION, LISTING, NAVIGATION }
enum class LibraryErrorCategory { PERMISSION_DENIED, PERMISSION_REVOKED, PROVIDER_UNAVAILABLE, PROVIDER_IO, PRIVATE_STORAGE, INVALID, STALE, OTHER }
data class LibraryFailure(val operation: LibraryFailureOperation, val error: AppError) {
    val category: LibraryErrorCategory get() = libraryErrorCategory(error)
}
