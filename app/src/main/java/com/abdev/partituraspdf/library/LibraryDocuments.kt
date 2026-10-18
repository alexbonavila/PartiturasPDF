package com.abdev.partituraspdf.library

import com.abdev.partituraspdf.core.document.*
import com.abdev.partituraspdf.core.foundation.*
import com.abdev.partituraspdf.core.result.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit

/**
 * Feature orchestration, not persistence or identity issuance. Mutable bookkeeping is actor-owned;
 * children only send results. A listing owns one child scope, four lookup workers and cancellable
 * collectors for its confirmed registrations. No scope per row, polling or unbounded lookup fan-out.
 */
internal class LibraryDocuments(
    private val scope: CoroutineScope,
    private val registry: DocumentRegistry,
    private val metadata: DocumentMetadataRepository,
    private val registration: RegistrationCommands,
    private val host: FeatureHost,
    private val row: (LibraryRowKey) -> LibraryRow.Pdf?,
    private val replace: (LibraryRow.Pdf) -> Unit,
    private val post: (LibraryRowKey, () -> Unit) -> Unit,
    private val fatal: () -> Unit,
) {
    private var listing: CoroutineScope? = null
    private val versions = mutableMapOf<LibraryRowKey, Long>()
    private val metadataVersions = mutableMapOf<LibraryRowKey, Long>()
    private val observers = mutableMapOf<LibraryRowKey, Job>()
    private val favoriteVersions = mutableMapOf<LibraryRowKey, Long>()
    private val lookupSlots = Semaphore(4)
    private val registrations = OperationLocks<StorageDocumentRef>()
    private val favorites = OperationLocks<DocumentRef>()
    private var opening: LibraryRowKey? = null

    fun reset() {
        listing?.cancel(); listing = null
        versions.clear(); metadataVersions.clear(); observers.clear(); favoriteVersions.clear(); opening = null
    }
    fun start(rows: List<LibraryRow>) {
        listing = CoroutineScope(scope.coroutineContext + SupervisorJob(scope.coroutineContext[Job]))
        val pdfs = rows.filterIsInstance<LibraryRow.Pdf>()
        pdfs.forEach { versions[it.key] = 1 }
        val iterator = pdfs.iterator()
        repeat(minOf(4, pdfs.size)) {
            launch {
                while (isActive) {
                    val next = synchronized(iterator) { if (iterator.hasNext()) iterator.next() else null } ?: break
                    val result = lookup(next.key.storage)
                    emit(next.key) { if (versions[next.key] == 1L) associate(next.key, result, LibraryAssociationOperation.LOOKUP) }
                }
            }
        }
    }
    private fun launch(block: suspend CoroutineScope.() -> Unit): Job = checkNotNull(listing).launch(block = block).also { task ->
        task.invokeOnCompletion { if (it != null && it !is CancellationException) fatal() }
    }
    private fun emit(key: LibraryRowKey, update: () -> Unit) = post(key, update)
    private fun version(key: LibraryRowKey): Long = ((versions[key] ?: 0) + 1).also { versions[key] = it }
    private suspend fun lookup(storage: StorageDocumentRef): Outcome<RegisteredDocument> = lookupSlots.withPermit {
        // A fresh generation waits for a canceled-but-still-committing registration before reading.
        registrations.run(storage) { }
        registry.findRegistration(storage)
    }
    private fun registered(key: LibraryRowKey) = row(key)?.association as? LibraryAssociation.Registered
    private fun updateRegistered(key: LibraryRowKey, value: LibraryAssociation.Registered) {
        row(key)?.let { replace(it.copy(association = value)) }
    }
    private fun associate(key: LibraryRowKey, result: Outcome<RegisteredDocument>, operation: LibraryAssociationOperation) {
        val current = row(key) ?: return
        val association = when (result) {
            is Outcome.Success -> if (result.value.storage == current.entry.storage) LibraryAssociation.Registered(result.value)
                else LibraryAssociation.Failed(operation, AppError.Conflict.REGISTRATION_CHANGED)
            is Outcome.Failure -> if (operation == LibraryAssociationOperation.LOOKUP && result.error == AppError.Access.NOT_REGISTERED) LibraryAssociation.Unregistered
                else LibraryAssociation.Failed(operation, result.error)
        }
        replace(current.copy(association = association))
        if (association is LibraryAssociation.Registered) observe(key)
    }
    fun retryLookup(key: LibraryRowKey, registerIfMissing: Boolean = false) {
        val current = row(key) ?: return
        val token = version(key)
        observers.remove(key)?.cancel()
        metadataVersions[key] = (metadataVersions[key] ?: 0) + 1
        replace(current.copy(association = LibraryAssociation.LookingUp))
        launch {
            val result = lookup(key.storage)
            emit(key) {
                if (versions[key] == token) {
                    associate(key, result, LibraryAssociationOperation.LOOKUP)
                    if (registerIfMissing && result is Outcome.Failure && result.error == AppError.Access.NOT_REGISTERED) register(key)
                }
            }
        }
    }
    fun register(key: LibraryRowKey) {
        val current = row(key) ?: return
        if (current.association is LibraryAssociation.Registered || current.association == LibraryAssociation.Registering) return
        val token = version(key)
        replace(current.copy(association = LibraryAssociation.Registering))
        val lifetime = checkNotNull(listing)
        launch {
            try {
                val result = registrations.run(key.storage) { registration.register(key.storage) }
                val reconciled = if (result is Outcome.Failure && result.error == AppError.Conflict.REGISTRATION_CHANGED) {
                    when (val found = lookup(key.storage)) {
                        is Outcome.Success -> found
                        is Outcome.Failure -> if (found.error == AppError.Access.NOT_REGISTERED) result else found
                    }
                } else result
                emit(key) { if (versions[key] == token) associate(key, reconciled, LibraryAssociationOperation.REGISTRATION) }
            } catch (cancelled: CancellationException) {
                if (lifetime.isActive) emit(key) { if (versions[key] == token) retryLookup(key) }
                throw cancelled
            }
        }
    }
    fun retryRegistration(key: LibraryRowKey) {
        val failed = row(key)?.association as? LibraryAssociation.Failed ?: return
        if (failed.operation == LibraryAssociationOperation.REGISTRATION) retryLookup(key, registerIfMissing = true)
    }
    fun retryMetadata(key: LibraryRowKey) {
        val current = registered(key) ?: return
        if (current.metadata is Observation.Failed && current.favorite !is LibraryFavorite.Pending) observe(key)
    }
    private fun observe(key: LibraryRowKey) {
        val current = registered(key) ?: return
        observers.remove(key)?.cancel()
        val token = (metadataVersions[key] ?: 0) + 1
        metadataVersions[key] = token
        updateRegistered(key, current.copy(metadata = Observation.Loading))
        observers[key] = launch {
            metadata.observe(current.value.document).collect { value ->
                emit(key) {
                    val latest = registered(key)
                    if (metadataVersions[key] == token && latest?.value?.document == current.value.document) metadataResult(key, latest, value)
                }
            }
        }
    }
    private fun metadataResult(key: LibraryRowKey, current: LibraryAssociation.Registered, value: Observation<DocumentMetadata>) {
        val reconciliation = current.favorite as? LibraryFavorite.Reconciling
        val favorite = when {
            reconciliation == null || value == Observation.Loading -> current.favorite
            value is Observation.Failed -> LibraryFavorite.Failed(reconciliation.desired, reconciliation.error ?: value.error)
            reconciliation.retryWrite -> LibraryFavorite.Idle
            reconciliation.error != null -> LibraryFavorite.Failed(reconciliation.desired, reconciliation.error)
            else -> LibraryFavorite.Idle
        }
        updateRegistered(key, current.copy(metadata = value, favorite = favorite))
        if (value is Observation.Ready && reconciliation?.retryWrite == true) setFavorite(key, reconciliation.desired)
    }
    fun setFavorite(key: LibraryRowKey, desired: Boolean) {
        val current = registered(key) ?: return
        val observed = current.metadata as? Observation.Ready ?: return
        if (current.favorite is LibraryFavorite.Pending || current.favorite is LibraryFavorite.Reconciling) return
        if (current.favorite is LibraryFavorite.Failed) { reconcileFavorite(key, desired, current.favorite.error, retry = true); return }
        if (observed.value.favorite == desired && !favorites.busy(current.value.document)) return
        val token = (favoriteVersions[key] ?: 0) + 1
        favoriteVersions[key] = token
        updateRegistered(key, current.copy(favorite = LibraryFavorite.Pending(desired)))
        val lifetime = checkNotNull(listing)
        launch {
            try {
                val result = favorites.run(current.value.document) { metadata.setFavorite(current.value.document, desired) }
                emit(key) {
                    if (favoriteVersions[key] == token && registered(key)?.value?.document == current.value.document) {
                        reconcileFavorite(key, desired, (result as? Outcome.Failure)?.error)
                    }
                }
            } catch (cancelled: CancellationException) {
                if (lifetime.isActive) emit(key) { if (favoriteVersions[key] == token) reconcileFavorite(key, desired, null) }
                throw cancelled
            }
        }
    }
    private fun reconcileFavorite(key: LibraryRowKey, desired: Boolean, error: AppError?, retry: Boolean = false) {
        val current = registered(key) ?: return
        updateRegistered(key, current.copy(favorite = LibraryFavorite.Reconciling(desired, error, retry)))
        observe(key)
    }
    fun retryFavorite(key: LibraryRowKey) {
        val failed = registered(key)?.favorite as? LibraryFavorite.Failed ?: return
        reconcileFavorite(key, failed.desired, failed.error, retry = true)
    }
    fun open(key: LibraryRowKey) {
        val current = row(key) ?: return
        if (opening != null || current.opening == LibraryOpening.Pending || current.opening == LibraryOpening.Accepted) return
        val request = when (val association = current.association) {
            is LibraryAssociation.Registered -> DocumentOpenRequest.Registered(association.value.document)
            LibraryAssociation.Unregistered -> DocumentOpenRequest.Transient(current.entry.storage)
            else -> { replace(current.copy(opening = LibraryOpening.Rejected)); return }
        }
        opening = key
        replace(current.copy(opening = LibraryOpening.Pending))
        val lifetime = checkNotNull(listing)
        launch {
            try {
                val result = host.library(LibraryEvent.Open(request))
                emit(key) {
                    opening = null
                    row(key)?.let { replace(it.copy(opening = when (result) {
                        is Outcome.Success -> LibraryOpening.Accepted
                        is Outcome.Failure -> LibraryOpening.Failed(result.error)
                    })) }
                }
            } catch (cancelled: CancellationException) {
                if (lifetime.isActive) emit(key) { opening = null; row(key)?.let { replace(it.copy(opening = LibraryOpening.Rejected)) } }
                throw cancelled
            }
        }
    }
}

/** Per-identity mutation exclusion survives listing replacement; unused locks are not retained. */
private class OperationLocks<K> {
    private class Entry(val mutex: Mutex = Mutex(), var users: Int = 0)
    private val entries = mutableMapOf<K, Entry>()
    fun busy(key: K): Boolean = synchronized(entries) { entries.containsKey(key) }
    suspend fun <T> run(key: K, block: suspend () -> T): T {
        val entry = synchronized(entries) { entries.getOrPut(key) { Entry() }.also { it.users++ } }
        try { return entry.mutex.withLock { block() } }
        finally { synchronized(entries) { if (--entry.users == 0) entries.remove(key) } }
    }
}
