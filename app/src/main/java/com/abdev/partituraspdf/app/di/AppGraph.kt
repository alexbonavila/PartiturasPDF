package com.abdev.partituraspdf.app.di

import com.abdev.partituraspdf.core.document.DocumentCatalog
import com.abdev.partituraspdf.core.document.DocumentMetadataRepository
import com.abdev.partituraspdf.core.document.DocumentRegistry
import com.abdev.partituraspdf.core.preferences.PreferencesRepository
import com.abdev.partituraspdf.pdf.api.PdfReadEngine
import com.abdev.partituraspdf.storage.api.DocumentReadAccess
import com.abdev.partituraspdf.storage.saf.SafTreeGrantAccess

/**
 * Composition-host dependencies, not a service locator for features. Pass individual contracts
 * into feature constructors. Injected dependencies are borrowed; this graph never closes them.
 * Reader owners retain responsibility for their sessions, read handles and bitmap leases.
 */
internal class AppGraph(
    /** Inject these immutable values alongside the relevant contracts into future consumers. */
    val runtimeLimits: AppRuntimeLimits,
    val catalog: DocumentCatalog,
    val readAccess: DocumentReadAccess,
    val registry: DocumentRegistry,
    val metadata: DocumentMetadataRepository,
    val preferences: PreferencesRepository,
    val pdfEngine: PdfReadEngine,
    /** Android grant mechanics remain available only to the controlled application host. */
    val treeGrants: SafTreeGrantAccess,
    /** Absent only for existing explicitly injected isolated contract graphs. Production always supplies it. */
    val foundation: FoundationDependencies? = null,
)
