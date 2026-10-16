package com.abdev.partituraspdf.app.di

import android.app.Application
import com.abdev.partituraspdf.data.metadata.MetadataDatabase
import com.abdev.partituraspdf.data.metadata.RoomDocumentMetadataRepository
import com.abdev.partituraspdf.data.metadata.RoomDocumentRegistry
import com.abdev.partituraspdf.data.preferences.DataStorePreferencesRepository
import com.abdev.partituraspdf.data.preferences.applicationPreferencesStore
import com.abdev.partituraspdf.pdf.android.AndroidPdfReadEngine
import com.abdev.partituraspdf.storage.saf.SafDocumentCatalog
import com.abdev.partituraspdf.storage.saf.SafDocumentReadAccess
import com.abdev.partituraspdf.storage.saf.SafTreeGrantAccess
import java.io.Closeable

/**
 * One owner per application lifetime, explicitly retained by the future composition host.
 * Activity recreation must reuse this owner, never close/recreate it. No global provider,
 * Application.onTerminate hook or startup wiring is introduced. Limits are explicit and fixed
 * for this owner; selecting provisional values does not create or mutate a global configuration.
 *
 * close() is for an explicitly quiescent test/host shutdown, off the main thread. Stop all
 * repository work first. It closes only owned Room resources, not the process-wide DataStore
 * delegate or caller-owned PDF sessions/bitmaps. Process death needs no termination callback.
 */
internal class AppGraphFactory internal constructor(
    private val runtimeLimits: AppRuntimeLimits,
    private val createResources: (AppRuntimeLimits) -> Resources,
) : Closeable {
    /** Application-only input prevents retaining an Activity through a Context constructor. */
    constructor(application: Application, runtimeLimits: AppRuntimeLimits) : this(runtimeLimits, { limits ->
        val resolver = application.contentResolver
        val database = MetadataDatabase.open(application)
        constructOwnedResources(database::close) {
            AppGraph(
                runtimeLimits = limits,
                catalog = SafDocumentCatalog(resolver),
                readAccess = SafDocumentReadAccess(resolver, application),
                registry = RoomDocumentRegistry(database),
                metadata = RoomDocumentMetadataRepository(database),
                preferences = DataStorePreferencesRepository(applicationPreferencesStore(application)),
                pdfEngine = AndroidPdfReadEngine(limits.render),
                treeGrants = SafTreeGrantAccess(resolver),
            )
        }
    })

    internal companion object {
        /** Takes ownership now; successful assembly transfers release to the returned resources. */
        internal fun constructOwnedResources(release: () -> Unit, assemble: () -> AppGraph): Resources {
            try {
                return Resources(assemble(), release)
            } catch (failure: Throwable) {
                try {
                    release()
                } catch (cleanup: Throwable) {
                    if (cleanup !== failure) failure.addSuppressed(cleanup)
                }
                throw failure
            }
        }
    }

    /** Isolated tests provide their own contracts and explicit resource owner, with no Android IO. */
    internal class Resources(val graph: AppGraph, val release: () -> Unit)

    private var resources: Resources? = null
    private var closed = false

    @Synchronized
    fun get(): AppGraph {
        check(!closed) { "Application graph owner is closed" }
        val current = resources ?: createResources(runtimeLimits).also { resources = it }
        return current.graph
    }

    @Synchronized
    override fun close() {
        if (closed) return
        closed = true
        val owned = resources
        resources = null
        owned?.release?.invoke()
    }
}
