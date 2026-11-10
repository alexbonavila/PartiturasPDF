package com.abdev.partituraspdf.pdf.presentation

import com.abdev.partituraspdf.core.result.Outcome
import com.abdev.partituraspdf.pdf.api.*
import com.abdev.partituraspdf.pdf.workflow.*
import com.abdev.partituraspdf.storage.api.*
import com.abdev.partituraspdf.storage.transaction.MRecoveryStatus
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

internal enum class MEditAvailability { UNAPPROVED, AVAILABLE }
internal enum class MEditCancellation { DRAFT, PREPARING, BEFORE_PICKER_CLAIM, AFTER_PICKER_CLAIM }
internal data class MPickerObservation(val operation: TransactionId, val accepted: Boolean, val uri: String?, val flags: Int)
internal enum class MEditPhase { DRAFT, PREPARING, PICKER, PUBLISHING, RESULT }
internal data class MEditState(
    val source: MEditSource? = null, val draft: MEditDraft? = null,
    val availability: MEditAvailability = MEditAvailability.UNAPPROVED,
    val phase: MEditPhase = MEditPhase.DRAFT, val rejection: MPreparation.Rejected? = null,
    val publication: PublicationResult? = null, val recovery: List<MRecoveryStatus> = emptyList(),
    val recovering: Boolean = false, val recoveryRequired: Boolean = true,
    val preparationBusy: Boolean = false, val cancellation: MEditCancellation? = null,
)
private fun PublicationResult?.blocksNewSave(): Boolean = when (this) {
    null, PublicationResult.RejectedBeforePublicEffect -> false
    is PublicationResult.VerifiedNewDocument -> cleanup == SnapshotCleanupStatus.PENDING
    else -> true
}
internal val MEditState.canSave: Boolean get() = draft?.pages?.any { it.included } == true && phase == MEditPhase.DRAFT &&
    availability == MEditAvailability.AVAILABLE && !recovering && !recoveryRequired && rejection?.cleanup != SnapshotCleanupStatus.PENDING &&
    !publication.blocksNewSave() && recovery.all {
        it == MRecoveryStatus.OWNER_ACKNOWLEDGED || it == MRecoveryStatus.VERIFIED_RECORD_OBSERVED
    }


internal data class MDocumentPickerRequest(val handoff: MPickerHandoff, val displayName: String?)

/** Feature integration seam; production always injects the closed authorization boundary.
 * Test implementations may explicitly inject the existing disposable transaction coordinator.
 */
internal interface MEditingBackend {
    val availability: MEditAvailability
    suspend fun resolve(route: MEditRoute): Outcome<SnapshotSource>
    suspend fun prepare(source: SnapshotSource, plan: OrderedPagePlan): MPreparation
    suspend fun destination(request: MPickerHandoff, source: SnapshotSource, uri: String, flags: Int): WriteDestinationCapability?
    suspend fun recover(): List<MRecoveryStatus>
}

/** Retained host-dispatcher owner. No Activity, View, FD or native Reader resources.
 * Existing session/journal own publication. This class only sequences UI and callback handoff.
 */
internal class MEditCoordinator(private val scope: CoroutineScope, private val backend: MEditingBackend,
                                private val io: CoroutineDispatcher = Dispatchers.IO) {
    private data class Pending(val ready: MPreparation.Ready, val source: SnapshotSource,
                               val route: MEditRoute, var claimed: Boolean = false, var consuming: Boolean = false,
                               var outcome: PublicationResult? = null,
                               val launch: CompletableDeferred<Unit> = CompletableDeferred())
    private val mutableState = MutableStateFlow(MEditState(availability = backend.availability))
    val state = mutableState.asStateFlow()
    private val mutableRequest = MutableStateFlow<MDocumentPickerRequest?>(null)
    val request = mutableRequest.asStateFlow()
    private var current: () -> MEditRoute? = { null }
    private var pending: Pending? = null
    private var work: Job? = null
    private var draining: Job? = null
    private var recovery: Job? = null
    @Volatile private var closed = false
    /** Private defect evidence, not user-facing/logged exception messages. */
    var diagnostic: Throwable? = null
        private set
    /** Raw callback observation, in memory only; never a permission or publication capability. */
    var observedResult: MPickerObservation? = null
        private set
    /** Saved only as a Boolean; never serialize live authority or returned provider identities. */
    val requiresRecoveryOnRestore: Boolean get() = pending != null || work?.isActive == true ||
        draining?.isActive == true || state.value.publication.blocksNewSave() ||
        state.value.rejection?.cleanup == SnapshotCleanupStatus.PENDING

    fun connect(source: () -> MEditRoute?) { current = source }
    fun refreshRecovery() {
        // Explicit entry refreshes completed scans. Concurrent entry is coalesced; no polling.
        // Never inspect a journal while this owner is preparing/publishing/draining it.
        if (closed || recovery?.isActive == true || work?.isActive == true || draining?.isActive == true) return
        mutableState.value = state.value.copy(recovering = true, recoveryRequired = true)
        recovery = scope.launch(start = CoroutineStart.LAZY) {
            try {
                val statuses = withContext(io) { backend.recover() }
                if (!closed) mutableState.value = state.value.copy(recovery = statuses.toList(), recovering = false, recoveryRequired = false)
            } catch (cancel: CancellationException) { throw cancel }
            catch (failure: Throwable) {
                diagnostic = failure
                if (!closed) mutableState.value = state.value.copy(recovery = listOf(MRecoveryStatus.BLOCKED))
                throw failure
            }
            finally { if (!closed) mutableState.value = state.value.copy(recovering = false) }
        }.also { it.start() }
    }
    fun open(source: MEditSource) {
        if (closed || source.route != current() || work?.isActive == true || draining?.isActive == true || pending != null) return
        if (state.value.source?.route == source.route && state.value.draft != null && state.value.phase == MEditPhase.DRAFT) return
        val draft = MEditDraft.initial(source.pageCount) ?: run {
            rejected(MPreparation.Rejected(PdfOperationFailure.InvalidPlan)); return
        }
        val before = state.value
        mutableState.value = before.copy(source = source, draft = draft, phase = MEditPhase.DRAFT, availability = backend.availability,
            rejection = before.rejection?.takeIf { it.cleanup == SnapshotCleanupStatus.PENDING },
            publication = before.publication.takeIf { it.blocksNewSave() }, preparationBusy = false, cancellation = null)
    }
    fun change(route: MEditRoute, action: MEditAction) {
        val state = state.value
        if (!closed && state.source?.route == route && current() == route && state.phase == MEditPhase.DRAFT)
            mutableState.value = state.copy(draft = state.draft?.change(action),
                rejection = state.rejection?.takeIf { it.cleanup == SnapshotCleanupStatus.PENDING }, preparationBusy = false)
    }
    fun routeChanged() { if (state.value.source?.route?.let { it != current() } == true) dismiss() }
    fun save(route: MEditRoute) {
        val before = state.value
        if (closed || before.source?.route != route || current() != route || before.phase != MEditPhase.DRAFT ||
            work?.isActive == true || draining?.isActive == true || pending != null) return
        if (before.publication.blocksNewSave()) return
        if (before.rejection?.cleanup == SnapshotCleanupStatus.PENDING) return
        if (backend.availability != MEditAvailability.AVAILABLE) return // No resolve, capture or picker.
        val plan = before.draft?.freeze() ?: return
        if (before.recovering || before.recoveryRequired || before.recovery.any { it != MRecoveryStatus.OWNER_ACKNOWLEDGED && it != MRecoveryStatus.VERIFIED_RECORD_OBSERVED }) return
        mutableState.value = before.copy(phase = MEditPhase.PREPARING, rejection = null, publication = null, recoveryRequired = true)
        work = scope.launch(start = CoroutineStart.LAZY) {
            var unhanded: MPreparation.Ready? = null
            var orphanSource: SnapshotSource? = null
            var primary: Throwable? = null
            try {
                val source = when (val resolved = withContext(io) { backend.resolve(route) }) {
                    is Outcome.Failure -> { rejected(MPreparation.Rejected(PdfOperationFailure.InvalidInput)); return@launch }
                    is Outcome.Success -> resolved.value
                }
                if (closed || current() != route) return@launch
                orphanSource = source
                // Capture inside IO: cancellation of dispatcher return must not lose a Ready owner.
                val prepared = withContext(io) { backend.prepare(source, plan).also { unhanded = it as? MPreparation.Ready } }
                when (prepared) {
                    MPreparation.Busy -> mutableState.value = state.value.copy(phase = MEditPhase.DRAFT, preparationBusy = true)
                    is MPreparation.Rejected -> rejected(prepared)
                    is MPreparation.Ready -> {
                        val stillSource = withContext(io) { backend.resolve(route) }
                        if (closed || current() != route || stillSource != Outcome.Success(source)) return@launch
                        pending = Pending(prepared, source, route)
                        unhanded = null // Owner transfer without suspension.
                        mutableState.value = state.value.copy(phase = MEditPhase.PICKER)
                        mutableRequest.value = MDocumentPickerRequest(prepared.request, before.source?.displayName)
                    }
                }
            } catch (failure: Throwable) {
                primary = failure; diagnostic = failure
                throw failure
            } finally {
                try {
                    val orphan = unhanded
                    if (orphan != null) {
                        // Keep the live session/admission owner even when dispatcher return or
                        // source revalidation prevented the Ready from reaching the Activity.
                        val retained = Pending(orphan, source = checkNotNull(orphanSource), route = route)
                        pending = retained
                        val outcome = settle(retained)
                        if (!closed) mutableState.value = state.value.copy(phase = MEditPhase.RESULT, publication = outcome)
                    } else if (!closed && state.value.phase == MEditPhase.PREPARING) {
                        mutableState.value = state.value.copy(phase = MEditPhase.RESULT, publication = PublicationResult.RejectedBeforePublicEffect)
                    }
                    if (primary?.suppressed?.any { it is com.abdev.partituraspdf.storage.transaction.SnapshotCleanupPendingException } == true && !closed)
                        mutableState.value = state.value.copy(rejection = MPreparation.Rejected(PdfOperationFailure.Closing, SnapshotCleanupStatus.PENDING))
                } catch (cleanup: Throwable) {
                    diagnostic = primary ?: cleanup
                    if (!closed && pending != null) mutableState.value = state.value.copy(phase = MEditPhase.RESULT,
                        publication = state.value.publication ?: PublicationResult.NeedsOwnerResolution)
                    if (primary != null) { if (primary !== cleanup) primary.addSuppressed(cleanup) } else throw cleanup
                } finally { if (work === currentCoroutineContext()[Job]) work = null }
            }
        }.also { it.start() }
    }
    private fun rejected(result: MPreparation.Rejected) {
        if (!closed) mutableState.value = state.value.copy(phase = MEditPhase.DRAFT, rejection = result)
        diagnostic = result.cleanupFault
    }
    /** Claim before launcher.launch; recreation re-registers but cannot claim twice. */
    fun claim(request: MPickerHandoff): Boolean {
        val p = pending ?: return false
        if (closed || p.ready.request !== request || current() != p.route || p.claimed ||
            backend.availability != MEditAvailability.AVAILABLE) return false
        p.claimed = true
        return true
    }
    fun launched(request: MPickerHandoff, success: Boolean) {
        val p = pending ?: return
        if (p.ready.request !== request || !p.claimed) return
        p.launch.complete(Unit)
        if (!success) dismiss()
    }
    fun result(operation: TransactionId, token: String, accepted: Boolean, uri: String?, flags: Int) {
        val p = pending ?: return
        if (closed || p.ready.request.operation != operation || p.ready.request.token != token ||
            !p.claimed || p.consuming || current() != p.route) return
        p.consuming = true
        observedResult = MPickerObservation(operation, accepted, uri, flags)
        mutableRequest.value = null
        mutableState.value = state.value.copy(phase = MEditPhase.PUBLISHING)
        work = scope.launch(start = CoroutineStart.LAZY) {
            var outcome: PublicationResult? = null
            var primary: Throwable? = null
            try {
                p.launch.await()
                val cap = if (accepted && !uri.isNullOrBlank() && flags and 3 == 3) withContext(io) {
                    if (backend.resolve(p.route) == Outcome.Success(p.source))
                        backend.destination(p.ready.request, p.source, uri, flags)
                    else null
                } else null
                if (closed || current() != p.route) return@launch
                outcome = withContext(io) { p.ready.session.complete(token, cap).also { p.outcome = it } }
            } catch (failure: CancellationException) { primary = failure; diagnostic = failure; throw failure }
            catch (failure: java.io.IOException) { primary = failure; diagnostic = failure }
            catch (failure: SecurityException) { primary = failure; diagnostic = failure }
            catch (failure: Throwable) { primary = failure; diagnostic = failure; throw failure }
            finally {
                try {
                    if (outcome == null) outcome = settle(p)
                    withContext(NonCancellable + io) { p.ready.session.cleanupFailure() }?.let { cleanup ->
                        if (primary != null) { if (primary !== cleanup) primary.addSuppressed(cleanup); diagnostic = primary }
                        else diagnostic = cleanup
                    }
                    val final = outcome
                    if (!closed) mutableState.value = state.value.copy(phase = MEditPhase.RESULT, publication = final)
                    pending = if (final is PublicationResult.VerifiedNewDocument) null else p
                } catch (cleanup: Throwable) {
                    diagnostic = primary ?: cleanup
                    if (!closed) mutableState.value = state.value.copy(phase = MEditPhase.RESULT,
                        publication = outcome ?: state.value.publication ?: PublicationResult.NeedsOwnerResolution)
                    if (primary != null) { if (primary !== cleanup) primary.addSuppressed(cleanup) } else throw cleanup
                } finally { if (work === currentCoroutineContext()[Job]) work = null }
            }
        }.also { it.start() }
    }
    private suspend fun settle(p: Pending): PublicationResult = withContext(NonCancellable + io) {
        (p.ready.session.verifiedPublication() ?: p.outcome ?: p.ready.session.complete(p.ready.request.token, null))
            .also { p.outcome = it }
    }
    /** Process-restored state has no live session; never reconstruct authority from saved IDs. */
    fun incomplete() {
        if (!closed && pending == null) mutableState.value = state.value.copy(phase = MEditPhase.RESULT,
            publication = PublicationResult.NeedsOwnerResolution)
    }
    /** Returns true when Back belonged to the editor, preserving the existing Reader underneath. */
    fun dismiss(): Boolean {
        if (state.value.draft == null) return false
        drain()
        return true
    }
    private fun drain() {
        val stage = when {
            pending?.claimed == true -> MEditCancellation.AFTER_PICKER_CLAIM
            pending != null -> MEditCancellation.BEFORE_PICKER_CLAIM
            work?.isActive == true -> MEditCancellation.PREPARING
            else -> MEditCancellation.DRAFT
        }
        mutableState.value = state.value.copy(draft = null, cancellation = state.value.cancellation ?: stage)
        mutableRequest.value = null
        if (draining?.isActive == true) return
        val previous = work
        val retained = pending
        draining = scope.launch(start = CoroutineStart.LAZY) {
            try {
                previous?.cancelAndJoin()
                // Preparation may have installed an unhanded Ready in its cancellation finally.
                val owned = pending ?: retained
                if (owned != null) {
                    val outcome = settle(owned)
                    if (outcome is PublicationResult.VerifiedNewDocument) pending = null
                    if (!closed) mutableState.value = state.value.copy(phase = MEditPhase.RESULT, publication = outcome)
                    // No automatic acknowledgement, cleanup, rollback or admission release of uncertainty.
                }
            } catch (failure: Throwable) {
                diagnostic = failure
                if (!closed) mutableState.value = state.value.copy(phase = MEditPhase.RESULT,
                    publication = state.value.publication ?: PublicationResult.NeedsOwnerResolution)
                throw failure
            }
        }.also { it.start() }
    }
    fun stopAccepting() { closed = true; mutableRequest.value = null }
    suspend fun terminate(): Unit = withContext(NonCancellable) {
        stopAccepting()
        recovery?.cancelAndJoin()
        drain()
        draining?.join()
        work?.cancelAndJoin()
    }
}
