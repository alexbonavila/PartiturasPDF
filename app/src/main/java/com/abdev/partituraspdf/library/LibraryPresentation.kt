package com.abdev.partituraspdf.library

import com.abdev.partituraspdf.core.document.DocumentListing
import com.abdev.partituraspdf.core.result.AppError
import com.abdev.partituraspdf.core.result.Outcome
import com.abdev.partituraspdf.storage.api.FolderNavigationState
import java.util.Locale

internal fun libraryErrorCategory(error: AppError): LibraryErrorCategory = when (error) {
    AppError.Access.PERMISSION_DENIED -> LibraryErrorCategory.PERMISSION_DENIED
    AppError.Access.PERMISSION_REVOKED -> LibraryErrorCategory.PERMISSION_REVOKED
    AppError.Access.PROVIDER_UNAVAILABLE -> LibraryErrorCategory.PROVIDER_UNAVAILABLE
    AppError.Access.PROVIDER_IO_FAILURE -> LibraryErrorCategory.PROVIDER_IO
    AppError.Access.PRIVATE_STORAGE_UNAVAILABLE -> LibraryErrorCategory.PRIVATE_STORAGE
    AppError.Validation.INVALID_DOCUMENT -> LibraryErrorCategory.INVALID
    AppError.Conflict.STALE_SESSION -> LibraryErrorCategory.STALE
    else -> LibraryErrorCategory.OTHER
}

/** MIME classification only. Opaque storage values are tie breakers, never display names. */
internal fun prepareLibraryListing(
    listing: DocumentListing,
    navigation: FolderNavigationState,
    generation: Long,
): Outcome<LibraryContent.Ready> {
    val rows = mutableListOf<LibraryRow>()
    val identities = mutableSetOf<com.abdev.partituraspdf.core.document.StorageDocumentRef>()
    for (entry in listing.entries) {
        val directory = entry.info.mimeType == "vnd.android.document/directory"
        if (!directory && !entry.info.mimeType.equals("application/pdf", ignoreCase = true)) continue
        if (entry.folder != navigation.current || !identities.add(entry.storage)) {
            return Outcome.Failure(AppError.Validation.INVALID_DOCUMENT)
        }
        val key = LibraryRowKey(entry.storage, navigation.version, generation)
        rows += if (directory) LibraryRow.Directory(key, entry) else LibraryRow.Pdf(key, entry)
    }
    val sorted = rows.sortedWith(
        compareBy<LibraryRow> { it !is LibraryRow.Directory }
            .thenBy { it.entry.info.name == null }
            .thenBy { it.entry.info.name?.lowercase(Locale.ROOT) }
            .thenBy { it.entry.info.name }
            .thenBy { it.entry.storage.provider }
            .thenBy { it.entry.storage.value },
    )
    return Outcome.Success(LibraryContent.Ready(sorted, listing.complete))
}

internal fun sameNavigation(a: FolderNavigationState?, b: FolderNavigationState?): Boolean =
    if (a == null || b == null) a == b
    else a.root == b.root && a.version == b.version && a.breadcrumbs == b.breadcrumbs
