package com.abdev.partituraspdf.library

import com.abdev.partituraspdf.core.document.DocumentEntry
import com.abdev.partituraspdf.core.document.DocumentMetadata
import com.abdev.partituraspdf.core.document.RegisteredDocument
import com.abdev.partituraspdf.core.result.AppError
import com.abdev.partituraspdf.core.result.Observation
import com.abdev.partituraspdf.storage.api.FolderNavigationState
import java.util.Collections

data class LibraryUiState(
    val location: LibraryLocation = LibraryLocation.Initializing,
    val rootSelection: LibraryOperation = LibraryOperation.Idle,
    val content: LibraryContent = LibraryContent.NotLoaded,
    val navigation: LibraryOperation = LibraryOperation.Idle,
    /** Rejected stale or non-PDF row command; does not turn a valid listing into a folder failure. */
    val documentError: AppError? = null,
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
    data class Pdf(
        override val key: LibraryRowKey,
        override val entry: DocumentEntry,
        val association: LibraryAssociation = LibraryAssociation.LookingUp,
        val opening: LibraryOpening = LibraryOpening.Idle,
    ) : LibraryRow {
        val canFavorite: Boolean get() = (association as? LibraryAssociation.Registered)?.let {
            it.metadata is Observation.Ready && it.favorite !is LibraryFavorite.Pending && it.favorite !is LibraryFavorite.Reconciling
        } == true
    }
}

enum class LibraryAssociationOperation { LOOKUP, REGISTRATION }
sealed interface LibraryAssociation {
    data object LookingUp : LibraryAssociation
    data object Unregistered : LibraryAssociation
    data object Registering : LibraryAssociation
    data class Failed(val operation: LibraryAssociationOperation, val error: AppError) : LibraryAssociation
    data class Registered(
        val value: RegisteredDocument,
        val metadata: Observation<DocumentMetadata> = Observation.Loading,
        val favorite: LibraryFavorite = LibraryFavorite.Idle,
    ) : LibraryAssociation
}
sealed interface LibraryFavorite {
    data object Idle : LibraryFavorite
    data class Pending(val desired: Boolean) : LibraryFavorite
    /** A fresh collection reconciles uncertainty; retryWrite is an explicit, once-only user retry. */
    data class Reconciling(val desired: Boolean, val error: AppError? = null, val retryWrite: Boolean = false) : LibraryFavorite
    data class Failed(val desired: Boolean, val error: AppError) : LibraryFavorite
}
sealed interface LibraryOpening {
    data object Idle : LibraryOpening
    data object Pending : LibraryOpening
    /** Host acceptance only. Neither proof of access nor of a rendered page. */
    data object Accepted : LibraryOpening
    data object Rejected : LibraryOpening
    data class Failed(val error: AppError) : LibraryOpening
}

enum class LibraryFailureOperation { RESTORE, SELECTION, LISTING, NAVIGATION }
enum class LibraryErrorCategory { PERMISSION_DENIED, PERMISSION_REVOKED, PROVIDER_UNAVAILABLE, PROVIDER_IO, PRIVATE_STORAGE, INVALID, STALE, OTHER }
data class LibraryFailure(val operation: LibraryFailureOperation, val error: AppError) {
    val category: LibraryErrorCategory get() = libraryErrorCategory(error)
}
