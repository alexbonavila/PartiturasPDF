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
}
