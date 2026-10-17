package com.abdev.partituraspdf.storage.api

import com.abdev.partituraspdf.core.document.*
import com.abdev.partituraspdf.core.result.Outcome
import java.util.Collections

class FolderNavigationState(root: FolderRef, breadcrumbs: List<FolderRef>, val version: Long) {
    val root = root
    val breadcrumbs: List<FolderRef> = Collections.unmodifiableList(breadcrumbs.toList())
    val current: FolderRef get() = breadcrumbs.last()
    init { require(breadcrumbs.isNotEmpty() && breadcrumbs.first() == root && version >= 0) }
}
interface FolderNavigation {
    suspend fun select(root: FolderRef): Outcome<FolderNavigationState>
    suspend fun enter(entry: DocumentEntry, expectedVersion: Long): Outcome<FolderNavigationState>
    suspend fun back(expectedVersion: Long): Outcome<FolderNavigationState>
    suspend fun state(): FolderNavigationState?
}
