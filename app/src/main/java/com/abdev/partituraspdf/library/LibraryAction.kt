package com.abdev.partituraspdf.library

import com.abdev.partituraspdf.core.document.StorageDocumentRef

/** Identifies an observed row, never a reconstructed provider entry or permanent document ID. */
data class LibraryRowKey(
    val storage: StorageDocumentRef,
    val navigationVersion: Long,
    val loadGeneration: Long,
)

sealed interface LibraryAction {
    data object ChooseRoot : LibraryAction
    data object Refresh : LibraryAction
    data object Retry : LibraryAction
    data class Back(val navigationVersion: Long) : LibraryAction
    data class Enter(val key: LibraryRowKey) : LibraryAction
    sealed interface Document : LibraryAction { val key: LibraryRowKey }
    data class Register(override val key: LibraryRowKey) : Document
    data class SetFavorite(override val key: LibraryRowKey, val favorite: Boolean) : Document
    data class Open(override val key: LibraryRowKey) : Document
    data class RetryLookup(override val key: LibraryRowKey) : Document
    data class RetryRegistration(override val key: LibraryRowKey) : Document
    data class RetryMetadata(override val key: LibraryRowKey) : Document
    data class RetryFavorite(override val key: LibraryRowKey) : Document
    data class RetryOpen(override val key: LibraryRowKey) : Document
}
