package com.abdev.partituraspdf.storage.saf

import android.net.Uri
import android.provider.DocumentsContract
import com.abdev.partituraspdf.core.document.*
import com.abdev.partituraspdf.core.foundation.PersistedRootAccess
import com.abdev.partituraspdf.core.result.*
import com.abdev.partituraspdf.storage.api.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class SafFolderNavigation(private val catalog: DocumentCatalog, private val grants: PersistedRootAccess) : FolderNavigation {
    private val lock = Mutex()
    private var current: FolderNavigationState? = null
    private var version = 0L
    override suspend fun state(): FolderNavigationState? = lock.withLock { current }
    private fun grant(root: FolderRef): Outcome<Unit> = when (val result = grants.hasRead(root)) {
        is Outcome.Failure -> result
        is Outcome.Success -> if (result.value) Outcome.Success(Unit) else Outcome.Failure(AppError.Access.PERMISSION_REVOKED)
    }
    override suspend fun select(root: FolderRef): Outcome<FolderNavigationState> = lock.withLock {
        val uri = SafUriCodec.decodeFolder(root) ?: return@withLock Outcome.Failure(AppError.Validation.INVALID_DOCUMENT)
        // A selected root is an authentic tree, never a caller-selected nested document URI.
        if (uri.pathSegments.size != 2) return@withLock Outcome.Failure(AppError.Validation.INVALID_DOCUMENT)
        val allowed = grant(root)
        if (allowed is Outcome.Failure) return@withLock allowed
        Outcome.Success(FolderNavigationState(root, listOf(root), ++version).also { current = it })
    }
    override suspend fun enter(entry: DocumentEntry, expectedVersion: Long): Outcome<FolderNavigationState> {
        val before = state() ?: return Outcome.Failure(AppError.Validation.INVALID_DOCUMENT)
        if (before.version != expectedVersion) return Outcome.Failure(AppError.Conflict.STALE_SESSION)
        val allowed = grant(before.root)
        if (allowed is Outcome.Failure) return allowed
        if (entry.folder != before.current || entry.info.mimeType != DocumentsContract.Document.MIME_TYPE_DIR) return invalid()
        val observed = catalog.list(before.current)
        if (observed is Outcome.Failure) return observed
        val confirmed = (observed as Outcome.Success).value.entries.filter { it.storage == entry.storage }
        if (confirmed.size != 1 || confirmed.single().folder != before.current || confirmed.single().info.mimeType != DocumentsContract.Document.MIME_TYPE_DIR) return invalid()
        val root = SafUriCodec.decodeFolder(before.root) ?: return invalid()
        val child = SafUriCodec.decodeDocument(entry.storage) ?: return invalid()
        if (child.pathSegments.size != 4 || child.authority != root.authority || child.query != null || child.fragment != null ||
            DocumentsContract.getTreeDocumentId(child) != DocumentsContract.getTreeDocumentId(root)) return invalid()
        val next = FolderRef(entry.storage.provider, child.toString())
        if (before.breadcrumbs.size >= 64 || next in before.breadcrumbs || DocumentsContract.getDocumentId(child) == DocumentsContract.getTreeDocumentId(root)) return invalid()
        return lock.withLock {
            if (current?.version != expectedVersion) return@withLock Outcome.Failure(AppError.Conflict.STALE_SESSION)
            val rechecked = grant(before.root)
            if (rechecked is Outcome.Failure) return@withLock rechecked
            Outcome.Success(FolderNavigationState(before.root, before.breadcrumbs + next, ++version).also { current = it })
        }
    }
    override suspend fun back(expectedVersion: Long): Outcome<FolderNavigationState> = lock.withLock {
        val before = current ?: return@withLock invalid()
        if (before.version != expectedVersion) return@withLock Outcome.Failure(AppError.Conflict.STALE_SESSION)
        val allowed = grant(before.root)
        if (allowed is Outcome.Failure) return@withLock allowed
        Outcome.Success(if (before.breadcrumbs.size == 1) before else
            FolderNavigationState(before.root, before.breadcrumbs.dropLast(1), ++version).also { current = it })
    }
    private fun invalid() = Outcome.Failure(AppError.Validation.INVALID_DOCUMENT)
}
