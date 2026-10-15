package com.abdev.partituraspdf.app.di

import android.app.Application
import com.abdev.partituraspdf.data.metadata.MetadataDatabase
import com.abdev.partituraspdf.data.metadata.RoomDocumentMetadataRepository
import com.abdev.partituraspdf.data.metadata.RoomDocumentRegistry
import com.abdev.partituraspdf.data.preferences.DataStorePreferencesRepository
import com.abdev.partituraspdf.data.preferences.applicationPreferencesStore
import com.abdev.partituraspdf.pdf.android.AndroidPdfReadEngine
import com.abdev.partituraspdf.pdf.api.RenderLimits
import com.abdev.partituraspdf.storage.saf.SafDocumentCatalog
import com.abdev.partituraspdf.storage.saf.SafDocumentReadAccess
import com.abdev.partituraspdf.storage.saf.SafTreeGrantAccess
import java.io.Closeable

/**
 * One owner per application lifetime, explicitly retained by the future composition host.
 * Activity recreation must reuse this owner, never close/recreate it. No global provider,
 * Application.onTerminate hook, startup wiring or production limit defaults are introduced.
 *
 * close() is for an explicitly quiescent test/host shutdown, off the main thread. Stop all
 * repository work first. It closes only owned Room resources, not the process-wide DataStore
 * delegate or caller-owned PDF sessions/bitmaps. Process death needs no termination callback.
 */
internal class AppGraphFactory internal constructor(private val createResources: () -> Resources) : Closeable {
    /** Application-only input prevents retaining an Activity through a Context constructor. */
    constructor(application: Application, renderLimits: RenderLimits) : this({
        val resolver = application.contentResolver
        val database = MetadataDatabase.open(application)
        try {
            Resources(
                AppGraph(
                    catalog = SafDocumentCatalog(resolver),
                    readAccess = SafDocumentReadAccess(resolver, application),
                    registry = RoomDocumentRegistry(database),
                    metadata = RoomDocumentMetadataRepository(database),
                    preferences = DataStorePreferencesRepository(applicationPreferencesStore(application)),
                    pdfEngine = AndroidPdfReadEngine(renderLimits),
                    treeGrants = SafTreeGrantAccess(resolver),
                ),
                release = database::close,
            )
        } catch (failure: Throwable) {
            try {
                database.close()
            } catch (cleanup: Throwable) {
                if (cleanup !== failure) failure.addSuppressed(cleanup)
            }
            throw failure
        }
    })

    /** Isolated tests provide their own contracts and explicit resource owner, with no Android IO. */
    internal class Resources(val graph: AppGraph, val release: () -> Unit)

    private var resources: Resources? = null
    private var closed = false

    @Synchronized
    fun get(): AppGraph {
        check(!closed) { "Application graph owner is closed" }
        val current = resources ?: createResources().also { resources = it }
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
