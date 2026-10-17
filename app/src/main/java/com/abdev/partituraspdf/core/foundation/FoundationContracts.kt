package com.abdev.partituraspdf.core.foundation

import com.abdev.partituraspdf.core.document.*
import com.abdev.partituraspdf.core.preferences.*
import com.abdev.partituraspdf.core.result.Outcome

/** Android authorization stays in I; this only queries already persisted READ capability. */
fun interface PersistedRootAccess { fun hasRead(root: FolderRef): Outcome<Boolean> }

/** Only authorized selections are durable. Restoration rechecks permission, never deletes metadata. */
interface SelectedRootRepository {
    suspend fun select(root: FolderRef): Outcome<Unit>
    suspend fun restore(): Outcome<FolderRef?>
    suspend fun clear(): Outcome<Unit>
}

sealed interface DocumentOpenRequest {
    data class Registered(val document: DocumentRef) : DocumentOpenRequest
    /** This capability must be reobtained after process death; never serialize it as a permanent grant. */
    data class Transient(val storage: StorageDocumentRef) : DocumentOpenRequest
}

interface RegistrationCommands {
    suspend fun register(storage: StorageDocumentRef): Outcome<RegisteredDocument>
    /** Caller has confirmed relocation; no name/hash inference occurs here. */
    suspend fun relocate(document: DocumentRef, expected: StorageDocumentRef, confirmed: StorageDocumentRef): Outcome<RegisteredDocument>
    suspend fun observe(document: DocumentRef, expected: StorageDocumentRef, revision: DocumentRevision, confirmed: DocumentAvailability): Outcome<Unit>
}

/** Route tokens are issued by the one repository. A newer route invalidates older writers. */
data class PositionWriter(val document: DocumentRef, val epoch: Long)
interface ReaderPositionRepository {
    suspend fun writer(document: DocumentRef): PositionWriter
    suspend fun restore(document: DocumentRef, pageCount: Int): Outcome<PageIndex?>
    /** Sequence increases only for successfully displayed pages, never merely requested renders. */
    suspend fun saveVisible(writer: PositionWriter, sequence: Long, page: PageIndex): Outcome<Unit>
}

/** Immutable navigation descriptions only; no native or Android objects are navigation arguments. */
data class ReaderRouteState(val request: DocumentOpenRequest, val visiblePageHint: PageIndex? = null) {
    val restorableAfterProcessDeath: Boolean get() = request is DocumentOpenRequest.Registered
}

sealed interface LibraryEvent {
    data object ChooseRoot : LibraryEvent
    data class Enter(val entry: DocumentEntry, val version: Long) : LibraryEvent
    data class Back(val version: Long) : LibraryEvent
    data class Open(val request: DocumentOpenRequest) : LibraryEvent
    data class Register(val storage: StorageDocumentRef) : LibraryEvent
}

/** H persists only these active settings; LanguageSelection remains legacy storage compatibility. */
sealed interface SettingsEffect {
    data class Theme(val selection: ThemeSelection) : SettingsEffect
    data class Fit(val selection: PageFit) : SettingsEffect
}
interface SettingsCommands { suspend fun apply(effect: SettingsEffect): Outcome<Unit> }

/** I applies locale/resources and theme; no Activity, picker result or AppGraph leaks into features. */
interface FeatureHost {
    suspend fun selectedRoot(): Outcome<FolderRef?>
    suspend fun acceptPersistedRoot(root: FolderRef): Outcome<Unit>
    suspend fun library(event: LibraryEvent): Outcome<Unit>
}
