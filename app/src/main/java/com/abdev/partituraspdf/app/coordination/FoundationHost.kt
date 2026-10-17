package com.abdev.partituraspdf.app.coordination

import com.abdev.partituraspdf.core.document.*
import com.abdev.partituraspdf.core.foundation.*
import com.abdev.partituraspdf.core.result.*
import com.abdev.partituraspdf.storage.api.FolderNavigation

/** I supplies real picker/navigation callbacks. This coordinator installs no Activity or global state. */
internal class FoundationHost(
    private val roots: SelectedRootRepository,
    private val folders: FolderNavigation,
    private val registration: RegistrationCommands,
    private val chooseRoot: suspend () -> Outcome<Unit>,
    private val open: suspend (DocumentOpenRequest) -> Outcome<Unit>,
) : FeatureHost {
    override suspend fun selectedRoot(): Outcome<FolderRef?> {
        val restored = roots.restore()
        if (restored is Outcome.Success && restored.value != null) {
            val selected = folders.select(restored.value)
            if (selected is Outcome.Failure) return selected
        }
        return restored
    }
    override suspend fun acceptPersistedRoot(root: FolderRef): Outcome<Unit> {
        val stored = roots.select(root)
        if (stored is Outcome.Failure) return stored
        return when (val selected = folders.select(root)) {
            is Outcome.Failure -> selected
            is Outcome.Success -> Outcome.Success(Unit)
        }
    }
    override suspend fun library(event: LibraryEvent): Outcome<Unit> = when (event) {
        LibraryEvent.ChooseRoot -> chooseRoot()
        is LibraryEvent.Open -> open(event.request)
        is LibraryEvent.Enter -> folders.enter(event.entry, event.version).unit()
        is LibraryEvent.Back -> folders.back(event.version).unit()
        is LibraryEvent.Register -> registration.register(event.storage).unit()
    }
    private fun <T> Outcome<T>.unit(): Outcome<Unit> = when (this) {
        is Outcome.Failure -> this
        is Outcome.Success -> Outcome.Success(Unit)
    }
}
