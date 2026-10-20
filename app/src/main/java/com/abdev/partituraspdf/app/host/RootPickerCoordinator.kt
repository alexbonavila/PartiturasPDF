package com.abdev.partituraspdf.app.host

import com.abdev.partituraspdf.core.document.FolderRef
import com.abdev.partituraspdf.core.result.AppError
import com.abdev.partituraspdf.core.result.Outcome
import com.abdev.partituraspdf.storage.api.FolderNavigation
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Owner-dispatcher confined. Holds data, never an Activity/launcher. Each registry key is unique. */
internal class RootPickerCoordinator(
    private val scope: CoroutineScope,
    private val folders: FolderNavigation,
    private val accept: suspend (String?, Int) -> Outcome<FolderRef>,
    private val restore: suspend () -> Outcome<FolderRef?>,
    private val completed: (Outcome<FolderRef?>) -> Unit,
    private val refreshed: () -> Unit,
    private val interrupted: (CancellationException) -> Unit,
    private val issueId: () -> String = { UUID.randomUUID().toString() },
) {
    private data class Pending(val id: String, val acknowledgment: CompletableDeferred<Outcome<Unit>>,
        var launched: Boolean = false, var completing: Boolean = false)
    private var pending: Pending? = null
    private var closed = false
    private var reconciling = false
    private val mutableRequest = MutableStateFlow<String?>(null)
    val request = mutableRequest.asStateFlow()
    private val mutableRecovery = MutableStateFlow<AppError?>(null)
    val recovery = mutableRecovery.asStateFlow()

    suspend fun choose(): Outcome<Unit> {
        if (closed) return failure(AppError.Conflict.SESSION_CLOSED)
        if (pending != null || reconciling) return failure(AppError.Conflict.STALE_SESSION)
        val next = Pending(issueId(), CompletableDeferred())
        pending = next
        mutableRequest.value = next.id
        return next.acknowledgment.await() // Launch acknowledgment only, never authorization.
    }

    /** Claim BEFORE launch; a recreated Activity registers the same key but never launches again. */
    fun claim(id: String): Boolean {
        val next = pending ?: return false
        if (next.id != id || next.launched || closed) return false
        next.launched = true
        return true
    }

    fun launched(id: String, result: Outcome<Unit>) {
        val next = pending ?: return
        if (next.id != id || !next.launched || next.acknowledgment.isCompleted) return
        next.acknowledgment.complete(result)
        if (result is Outcome.Failure) clear()
    }

    /** Raw URI and flags came from Android. Non-OK results are cancellation, regardless of data. */
    fun result(id: String, accepted: Boolean, uri: String?, flags: Int) {
        val next = pending ?: return
        if (closed || next.id != id || !next.launched || next.completing) return
        next.completing = true
        scope.launch {
            if (next.acknowledgment.await() is Outcome.Failure || closed) return@launch
            try {
                val result = if (!accepted) Outcome.Success(null) else when (val stored = accept(uri, flags)) {
                    is Outcome.Failure -> {
                        // A failed root-write acknowledgment does not prove that persistence rolled back.
                        if (!closed && stored.error == AppError.Access.PRIVATE_STORAGE_UNAVAILABLE) mutableRecovery.value = stored.error
                        stored
                    }
                    is Outcome.Success -> {
                        if (closed) return@launch
                        val selected = folders.select(stored.value)
                        if (closed) return@launch
                        when (selected) {
                            is Outcome.Success -> { mutableRecovery.value = null; Outcome.Success(stored.value) }
                            is Outcome.Failure -> {
                                // Persistence changed. Never invent rollback or operate on an old trail.
                                mutableRecovery.value = selected.error
                                selected
                            }
                        }
                    }
                }
                // A legitimate registry result can arrive synchronously during re-registration.
                // Library's actor is still awaiting launch acknowledgment; its queued completion waits.
                if (!closed) { completed(result); clear() }
            } catch (canceled: CancellationException) {
                // Unexpected provider cancellation cannot strand F's pending selection forever.
                // The owner terminates this attempt with the ORIGINAL cancellation, never fake success.
                if (!closed) interrupted(canceled)
                throw canceled
            }
        }
    }

    /** Explicit user-controlled reconciliation. Never replays a possibly committed registration/write. */
    fun reconcile() {
        if (closed || reconciling || pending != null || mutableRecovery.value == null) return
        reconciling = true
        scope.launch {
            try {
                val root = restore()
                if (closed) return@launch
                val outcome = when (root) {
                    is Outcome.Failure -> root
                    is Outcome.Success -> if (root.value == null) {
                        if (folders.state() == null) Outcome.Success(Unit)
                        else failure(AppError.Conflict.STALE_SESSION)
                    } else folders.select(root.value)
                }
                if (!closed) when (outcome) {
                    is Outcome.Failure -> mutableRecovery.value = outcome.error
                    is Outcome.Success -> { mutableRecovery.value = null; refreshed() }
                }
            } catch (canceled: CancellationException) {
                if (!closed) interrupted(canceled)
                throw canceled
            } finally { reconciling = false }
        }
    }

    /** A process-restored Activity has no matching in-memory consumer: never accept its old result. */
    fun incomplete() {
        if (!closed && pending == null) mutableRecovery.value = AppError.Conflict.STALE_SESSION
    }

    fun close() {
        closed = true
        pending?.acknowledgment?.complete(failure(AppError.Conflict.SESSION_CLOSED))
        clear()
    }
    private fun clear() { pending = null; mutableRequest.value = null }
    private fun failure(error: AppError) = Outcome.Failure(error)
}
