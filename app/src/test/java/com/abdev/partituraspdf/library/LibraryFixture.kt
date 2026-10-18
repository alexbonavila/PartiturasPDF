package com.abdev.partituraspdf.library

import com.abdev.partituraspdf.core.document.*
import com.abdev.partituraspdf.core.foundation.*
import com.abdev.partituraspdf.core.result.*
import com.abdev.partituraspdf.storage.api.*
import kotlinx.coroutines.CoroutineScope

internal val root = FolderRef("test", "root")
internal fun trail(vararg folders: FolderRef, version: Long = 1) = FolderNavigationState(folders.first(), folders.toList(), version)
internal fun entry(name: String? = "folder", id: String = "child", folder: FolderRef = root, mime: String? = "vnd.android.document/directory") =
    DocumentEntry(StorageDocumentRef("test", id), DocumentInfo(name, mime), folder)

/** Contract doubles only; no simulated Android grants or claim of real SAF behavior. */
internal class LibraryFixture(scope: CoroutineScope, retained: FolderNavigationState? = null) : AutoCloseable {
    var navigation = retained
    var readNavigation: suspend () -> FolderNavigationState? = { navigation }
    var restores = 0
    var restore: suspend () -> Outcome<FolderRef?> = {
        navigation = trail(root)
        Outcome.Success(root)
    }
    val events = mutableListOf<LibraryEvent>()
    var event: suspend (LibraryEvent) -> Outcome<Unit> = { command ->
        val before = navigation
        when (command) {
            is LibraryEvent.Enter -> navigation = FolderNavigationState(before!!.root, before.breadcrumbs + FolderRef("test", command.entry.storage.value), before.version + 1)
            is LibraryEvent.Back -> if (before!!.breadcrumbs.size > 1) navigation = FolderNavigationState(before.root, before.breadcrumbs.dropLast(1), before.version + 1)
            else -> Unit
        }
        Outcome.Success(Unit)
    }
    val calls = mutableListOf<FolderRef>()
    var list: suspend (FolderRef) -> Outcome<DocumentListing> = { Outcome.Success(DocumentListing(listOf(entry(folder = it)), true)) }
    val catalog = object : DocumentCatalog {
        override suspend fun list(folder: FolderRef): Outcome<DocumentListing> { calls += folder; return this@LibraryFixture.list(folder) }
        override suspend fun inspect(document: StorageDocumentRef): Outcome<DocumentInfo> = error("Not part of browsing")
    }
    val navigator = object : FolderNavigation {
        override suspend fun state() = readNavigation()
        override suspend fun select(root: FolderRef): Outcome<FolderNavigationState> = error("Host owns root selection")
        override suspend fun enter(entry: DocumentEntry, expectedVersion: Long): Outcome<FolderNavigationState> = error("Use host event")
        override suspend fun back(expectedVersion: Long): Outcome<FolderNavigationState> = error("Use host event")
    }
    val host = object : FeatureHost {
        override suspend fun selectedRoot(): Outcome<FolderRef?> { restores++; return restore() }
        override suspend fun acceptPersistedRoot(root: FolderRef): Outcome<Unit> = error("Only I accepts picker results")
        override suspend fun library(event: LibraryEvent): Outcome<Unit> { events += event; return this@LibraryFixture.event(event) }
    }
    val documents = LibraryDocumentFixture()
    val controller = LibraryController(scope, catalog, navigator, host, documents.registry, documents.metadata, documents.commands)
    val state get() = controller.state.value
    val ready get() = state.content as LibraryContent.Ready
    val available get() = state.location as LibraryLocation.Available
    fun enter() = controller.dispatch(LibraryAction.Enter(ready.rows.first().key))
    override fun close() = controller.close()
}
