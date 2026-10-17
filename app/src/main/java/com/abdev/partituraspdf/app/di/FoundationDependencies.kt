package com.abdev.partituraspdf.app.di

import android.app.Application
import com.abdev.partituraspdf.app.coordination.*
import com.abdev.partituraspdf.core.document.*
import com.abdev.partituraspdf.core.foundation.*
import com.abdev.partituraspdf.core.preferences.PreferencesRepository
import com.abdev.partituraspdf.data.foundation.*
import com.abdev.partituraspdf.pdf.api.*
import com.abdev.partituraspdf.storage.api.*
import com.abdev.partituraspdf.storage.saf.*
import kotlinx.coroutines.CoroutineScope

/** Application-owned adapters, no background jobs. I creates route-local navigation and lifetimes. */
internal class FoundationDependencies(
    application: Application,
    private val registry: DocumentRegistry,
    preferences: PreferencesRepository,
    private val access: DocumentReadAccess,
    private val engine: PdfReadEngine,
    private val limits: AppRuntimeLimits,
    private val catalog: DocumentCatalog,
) {
    private val grants = SafPersistedRootAccess(application.contentResolver)
    val roots: SelectedRootRepository = PrivateSelectedRootRepository(application, grants)
    val positions: ReaderPositionRepository = SharedPreferencesReaderPositions(application)
    val registration: RegistrationCommands = RegistrationCoordinator(registry)
    val settings: SettingsCommands = RepositorySettingsCommands(preferences)
    val rootPicker = SafRootSelection(SafTreeGrantAccess(application.contentResolver), roots)
    fun folders(): FolderNavigation = SafFolderNavigation(catalog, grants)
    fun reader(scope: CoroutineScope): ReaderRoute = ReaderRouteOwner(scope, registry, access, engine, positions, limits.read)
    fun host(folders: FolderNavigation, chooseRoot: suspend () -> com.abdev.partituraspdf.core.result.Outcome<Unit>,
             open: suspend (DocumentOpenRequest) -> com.abdev.partituraspdf.core.result.Outcome<Unit>): FeatureHost =
        FoundationHost(roots, folders, registration, chooseRoot, open)
}
