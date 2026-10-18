package com.abdev.partituraspdf.library

import com.abdev.partituraspdf.core.document.DocumentCatalog
import com.abdev.partituraspdf.core.document.DocumentListing
import com.abdev.partituraspdf.core.document.DocumentRegistry
import com.abdev.partituraspdf.core.document.DocumentMetadataRepository
import com.abdev.partituraspdf.core.document.FolderRef
import com.abdev.partituraspdf.core.foundation.FeatureHost
import com.abdev.partituraspdf.core.foundation.LibraryEvent
import com.abdev.partituraspdf.core.foundation.RegistrationCommands
import com.abdev.partituraspdf.core.result.AppError
import com.abdev.partituraspdf.core.result.Outcome
import com.abdev.partituraspdf.storage.api.FolderNavigation
import com.abdev.partituraspdf.storage.api.FolderNavigationState
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * I retains this owner and the same navigator as its host across configuration changes.
 * Commands are serialized; provider loads are separate children and may finish out of order.
 * I sends one picker completion per pending request, after synchronizing the shared navigator.
 */
class LibraryController(
    ownerScope: CoroutineScope,
    private val catalog: DocumentCatalog,
    private val folders: FolderNavigation,
    private val host: FeatureHost,
    registry: DocumentRegistry,
    metadata: DocumentMetadataRepository,
    registration: RegistrationCommands,
) {
    private val owner = SupervisorJob(ownerScope.coroutineContext[Job])
    private val scope = CoroutineScope(ownerScope.coroutineContext + owner)
    private val started = AtomicBoolean()
    private val lifecycle = Any()
    private var closed = false
    private val messages = Channel<Message>(Channel.UNLIMITED)
    private val mutableState = MutableStateFlow(LibraryUiState())
    val state: StateFlow<LibraryUiState> = mutableState.asStateFlow()
    private var generation = 0L
    private var load: Job? = null
    private val documents = LibraryDocuments(scope, registry, metadata, registration, host, ::pdf, ::replacePdf,
        { key, update -> messages.trySend(Message.Document(key, update)); Unit }, { owner.cancel() })

    private sealed interface Message {
        data class Action(val action: LibraryAction) : Message
        data class Selection(val result: Outcome<FolderRef?>) : Message
        data class Loaded(val generation: Long, val navigation: FolderNavigationState, val result: Outcome<DocumentListing>) : Message
        data class Document(val key: LibraryRowKey, val update: () -> Unit) : Message
    }

    fun start() {
        if (!started.compareAndSet(false, true)) return
        synchronized(lifecycle) {
            if (closed || !owner.isActive) return
            scope.launch {
                try {
                    initialize()
                    for (message in messages) {
                        when (message) {
                            is Message.Action -> action(message.action)
                            is Message.Selection -> selection(message.result)
                            is Message.Loaded -> loaded(message)
                            is Message.Document -> documentResult(message)
                        }
                    }
                } finally {
                    // Unexpected failures propagate; stop sibling loads rather than leaving an orphan.
                    messages.cancel()
                    documents.reset()
                    owner.cancel()
                }
            }
        }
    }

    fun dispatch(action: LibraryAction) = send(Message.Action(action))
    /** I reports cancellation as Success(null), after synchronizing the same navigator on acceptance. */
    fun onRootSelectionFinished(result: Outcome<FolderRef?>) = send(Message.Selection(result))
    private fun send(message: Message) {
        if (started.get() && owner.isActive) messages.trySend(message)
    }

    /** Cancels our children and discards queued work. Late/non-cooperative providers cannot publish. */
    fun close() {
        synchronized(lifecycle) {
            closed = true
            messages.cancel()
            owner.cancel()
        }
    }

    private fun publish(state: LibraryUiState) {
        synchronized(lifecycle) { if (!closed && owner.isActive) mutableState.value = state }
    }
    private fun invalidate() { generation++; load?.cancel(); load = null; documents.reset() }
    private fun failure(operation: LibraryFailureOperation, error: AppError) = LibraryFailure(operation, error)
    private fun current() = (state.value.location as? LibraryLocation.Available)?.snapshot
    private fun pendingSelection() = state.value.rootSelection == LibraryOperation.Pending

    private suspend fun initialize() {
        val retained = folders.state()
        if (retained != null) { beginLoad(retained); return }
        publish(state.value.copy(location = LibraryLocation.Initializing, content = LibraryContent.NotLoaded))
        when (val restored = host.selectedRoot()) {
            is Outcome.Failure -> publish(state.value.copy(location = LibraryLocation.Failed(failure(LibraryFailureOperation.RESTORE, restored.error))))
            is Outcome.Success -> {
                val navigation = folders.state()
                if (restored.value == null && navigation == null) noRoot()
                else if (navigation != null && restored.value == navigation.root) beginLoad(navigation)
                else publish(state.value.copy(location = LibraryLocation.Failed(failure(LibraryFailureOperation.RESTORE, AppError.Conflict.STALE_SESSION))))
            }
        }
    }
    private fun noRoot() {
        invalidate()
        publish(state.value.copy(location = LibraryLocation.NoRoot, content = LibraryContent.NotLoaded))
    }
    private fun beginLoad(navigation: FolderNavigationState) {
        invalidate()
        val token = generation
        publish(state.value.copy(location = LibraryLocation.Available(navigation), content = LibraryContent.Loading(token), documentError = null))
        load = scope.launch {
            val result = catalog.list(navigation.current)
            messages.trySend(Message.Loaded(token, navigation, result))
        }.also { task ->
            task.invokeOnCompletion { failure ->
                // Expected failures are Outcome values. A defect escapes to the owner's handler
                // and terminates this controller; ordinary supersession must not terminate it.
                if (failure != null && failure !is CancellationException) owner.cancel()
            }
        }
    }
    private suspend fun loaded(message: Message.Loaded) {
        if (message.generation != generation || pendingSelection() || !sameNavigation(current(), message.navigation)) return
        val actual = folders.state()
        if (!sameNavigation(actual, message.navigation)) { navigationFailure(AppError.Conflict.STALE_SESSION, actual); return }
        val prepared = when (val result = message.result) {
            is Outcome.Failure -> result
            is Outcome.Success -> prepareLibraryListing(result.value, message.navigation, message.generation)
        }
        publish(state.value.copy(content = when (prepared) {
            is Outcome.Success -> prepared.value
            is Outcome.Failure -> LibraryContent.Failed(failure(LibraryFailureOperation.LISTING, prepared.error))
        }))
        if (prepared is Outcome.Success) documents.start(prepared.value.rows)
    }

    private suspend fun action(action: LibraryAction) {
        if (pendingSelection()) return
        when (action) {
            LibraryAction.Refresh -> refresh()
            LibraryAction.Retry -> retry()
            is LibraryAction.Enter -> enter(action.key)
            is LibraryAction.Back -> navigateBack(action.navigationVersion)
            LibraryAction.ChooseRoot -> chooseRoot()
            is LibraryAction.Document -> documentAction(action)
        }
    }
    private fun pdf(key: LibraryRowKey): LibraryRow.Pdf? =
        (state.value.content as? LibraryContent.Ready)?.rows?.singleOrNull { it.key == key } as? LibraryRow.Pdf

    private fun replacePdf(row: LibraryRow.Pdf) {
        val ready = state.value.content as? LibraryContent.Ready ?: return
        publish(state.value.copy(content = LibraryContent.Ready(ready.rows.map { if (it.key == row.key) row else it }, ready.complete)))
    }
    private suspend fun documentResult(message: Message.Document) {
        if (message.key.loadGeneration != generation || pendingSelection() || pdf(message.key) == null) return
        val actual = folders.state()
        if (!sameNavigation(current(), actual)) { navigationFailure(AppError.Conflict.STALE_SESSION, actual); return }
        message.update()
    }
    private suspend fun documentAction(action: LibraryAction.Document) {
        val row = pdf(action.key)
        val error = when {
            !sameNavigation(current(), folders.state()) || action.key.loadGeneration != generation -> AppError.Conflict.STALE_SESSION
            row == null -> if ((state.value.content as? LibraryContent.Ready)?.rows?.any { it.key == action.key } == true) AppError.Validation.INVALID_DOCUMENT else AppError.Conflict.STALE_SESSION
            else -> null
        }
        if (error != null) { publish(state.value.copy(documentError = error)); return }
        publish(state.value.copy(documentError = null))
        when (action) {
            is LibraryAction.Register -> if (row!!.association is LibraryAssociation.Failed && row.association.operation == LibraryAssociationOperation.REGISTRATION) documents.retryRegistration(action.key) else documents.register(action.key)
            is LibraryAction.SetFavorite -> documents.setFavorite(action.key, action.favorite)
            is LibraryAction.Open -> documents.open(action.key)
            is LibraryAction.RetryLookup -> if (row!!.association is LibraryAssociation.Failed) documents.retryLookup(action.key)
            is LibraryAction.RetryRegistration -> documents.retryRegistration(action.key)
            is LibraryAction.RetryMetadata -> documents.retryMetadata(action.key)
            is LibraryAction.RetryFavorite -> documents.retryFavorite(action.key)
            is LibraryAction.RetryOpen -> if (row!!.opening is LibraryOpening.Failed || row.opening == LibraryOpening.Rejected) documents.open(action.key)
        }
    }
    private suspend fun refresh() {
        publish(state.value.copy(navigation = LibraryOperation.Idle))
        val actual = folders.state()
        if (actual != null) beginLoad(actual) else initialize()
    }
    private suspend fun retry() {
        // Navigation retries refresh authoritative state; never blindly replay a possibly committed move.
        when {
            state.value.rootSelection is LibraryOperation.Failed -> chooseRoot()
            state.value.location is LibraryLocation.Failed -> initialize()
            state.value.navigation is LibraryOperation.Failed || state.value.content is LibraryContent.Failed -> refresh()
        }
    }
    private suspend fun chooseRoot() {
        invalidate()
        publish(state.value.copy(rootSelection = LibraryOperation.Pending, content = LibraryContent.NotLoaded, navigation = LibraryOperation.Idle))
        when (val launched = host.library(LibraryEvent.ChooseRoot)) {
            is Outcome.Success -> Unit // Launch acknowledgement is not authorization or a completed result.
            is Outcome.Failure -> publish(state.value.copy(rootSelection = LibraryOperation.Failed(failure(LibraryFailureOperation.SELECTION, launched.error))))
        }
    }
    private suspend fun selection(result: Outcome<FolderRef?>) {
        if (!pendingSelection()) return // Includes late completion after cancellation, failed launch or close.
        when (result) {
            is Outcome.Failure -> publish(state.value.copy(rootSelection = LibraryOperation.Failed(failure(LibraryFailureOperation.SELECTION, result.error))))
            is Outcome.Success -> {
                val actual = folders.state()
                if (result.value != null && (actual == null || actual.root != result.value)) {
                    publish(state.value.copy(
                        location = actual?.let(LibraryLocation::Available) ?: LibraryLocation.NoRoot,
                        rootSelection = LibraryOperation.Failed(failure(LibraryFailureOperation.SELECTION, AppError.Conflict.STALE_SESSION)),
                    ))
                    return
                }
                publish(state.value.copy(rootSelection = if (result.value == null) LibraryOperation.Canceled else LibraryOperation.Idle))
                if (actual == null) noRoot() else beginLoad(actual)
            }
        }
    }
    private suspend fun enter(key: LibraryRowKey) {
        val before = current()
        val row = (state.value.content as? LibraryContent.Ready)?.rows?.singleOrNull { it.key == key }
        if (before == null || row == null || key.navigationVersion != before.version) {
            navigationFailure(AppError.Conflict.STALE_SESSION, folders.state()); return
        }
        if (row !is LibraryRow.Directory) { navigationFailure(AppError.Validation.INVALID_DOCUMENT, folders.state()); return }
        navigate(before, LibraryEvent.Enter(row.entry, before.version)) { after ->
            after.root == before.root && after.version > before.version &&
                after.breadcrumbs.size == before.breadcrumbs.size + 1 &&
                after.breadcrumbs.dropLast(1) == before.breadcrumbs && after.current !in before.breadcrumbs
        }
    }
    private suspend fun navigateBack(version: Long) {
        val before = current()
        if (before == null || before.version != version) { navigationFailure(AppError.Conflict.STALE_SESSION, folders.state()); return }
        navigate(before, LibraryEvent.Back(version)) { after ->
            if (before.breadcrumbs.size == 1) sameNavigation(before, after)
            else after.root == before.root && after.version > before.version && after.breadcrumbs == before.breadcrumbs.dropLast(1)
        }
    }
    private suspend fun navigate(before: FolderNavigationState, event: LibraryEvent, valid: (FolderNavigationState) -> Boolean) {
        val actual = folders.state()
        if (!sameNavigation(before, actual)) { navigationFailure(AppError.Conflict.STALE_SESSION, actual); return }
        invalidate()
        publish(state.value.copy(content = LibraryContent.NotLoaded, navigation = LibraryOperation.Pending))
        val result = host.library(event)
        val after = folders.state()
        when {
            result is Outcome.Failure -> navigationFailure(result.error, after)
            after == null || !valid(after) -> navigationFailure(AppError.Conflict.STALE_SESSION, after)
            else -> { publish(state.value.copy(navigation = LibraryOperation.Idle)); beginLoad(after) }
        }
    }
    private fun navigationFailure(error: AppError, actual: FolderNavigationState?) {
        publish(state.value.copy(navigation = LibraryOperation.Failed(failure(LibraryFailureOperation.NAVIGATION, error))))
        if (actual == null) noRoot() else beginLoad(actual)
    }
}
