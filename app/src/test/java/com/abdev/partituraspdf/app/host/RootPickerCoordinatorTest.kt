package com.abdev.partituraspdf.app.host

import com.abdev.partituraspdf.core.document.*
import com.abdev.partituraspdf.core.result.*
import com.abdev.partituraspdf.storage.api.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RootPickerCoordinatorTest {
    private val old = FolderRef("test", "old")
    private val fresh = FolderRef("test", "fresh")
    private val denied = Outcome.Failure(AppError.Access.PERMISSION_DENIED)
    private val revoked = Outcome.Failure(AppError.Access.PERMISSION_REVOKED)
    private class Fixture(val scope: TestScope, initial: FolderRef? = null) {
        var navigation = initial?.let { FolderNavigationState(it, listOf(it), 1) }
        var accepts = 0
        val selections = mutableListOf<FolderRef>()
        var accepted: Outcome<FolderRef> = Outcome.Success(FolderRef("test", "fresh"))
        var stored: Outcome<FolderRef?> = Outcome.Success(initial)
        var selectError: Outcome.Failure? = null
        var restoreCalls = 0
        var refreshed = 0
        var raw: Pair<String?, Int>? = null
        val interruptions = mutableListOf<CancellationException>()
        val completions = mutableListOf<Outcome<FolderRef?>>()
        var duringAccept: suspend () -> Unit = {}
        var duringRestore: suspend () -> Unit = {}
        var duringSelect: suspend () -> Unit = {}
        var sequence = 0
        val folders = object : FolderNavigation {
            override suspend fun state() = navigation
            override suspend fun select(root: FolderRef): Outcome<FolderNavigationState> {
                selections += root
                duringSelect()
                selectError?.let { return it }
                return Outcome.Success(FolderNavigationState(root, listOf(root), (navigation?.version ?: 0) + 1).also { navigation = it })
            }
            override suspend fun enter(entry: DocumentEntry, expectedVersion: Long): Outcome<FolderNavigationState> = error("F owns this")
            override suspend fun back(expectedVersion: Long): Outcome<FolderNavigationState> = error("F owns this")
        }
        val picker = RootPickerCoordinator(scope.backgroundScope, folders,
            accept = { uri, flags -> accepts++; raw = uri to flags; duringAccept(); accepted.also { if (it is Outcome.Success) stored = it } },
            restore = { restoreCalls++; duringRestore(); stored },
            completed = completions::add, refreshed = { refreshed++ }, interrupted = interruptions::add, issueId = { "request-${++sequence}" })
        fun request() = scope.backgroundScope.async { picker.choose() }
        suspend fun launch(): String {
            val ack = request(); scope.runCurrent()
            val id = picker.request.value!!
            assertFalse(ack.isCompleted)
            assertTrue(picker.claim(id))
            picker.launched(id, Outcome.Success(Unit)); scope.runCurrent()
            assertEquals(Outcome.Success(Unit), ack.await())
            return id
        }
        fun finish(id: String, ok: Boolean = true, uri: String? = "content://provider/tree/root", flags: Int = 67) {
            picker.result(id, ok, uri, flags); scope.runCurrent()
        }
    }

    @Test fun launchAcknowledgmentDoesNotAuthorizeAndOriginalFlagsReachAcceptanceExactlyOnce() = runTest {
        val f = Fixture(this, old); val id = f.launch()
        assertEquals(0, f.accepts); assertEquals(old, f.navigation!!.root)
        f.finish(id, flags = 0x47)
        assertEquals("content://provider/tree/root" to 0x47, f.raw)
        assertEquals(1, f.accepts); assertEquals(listOf(fresh), f.selections)
        assertEquals(listOf(Outcome.Success(fresh)), f.completions)
        assertEquals(fresh, f.navigation!!.root); assertNull(f.picker.request.value)
        f.finish(id); assertEquals(1, f.accepts)
    }

    @Test fun cancellationIgnoresUriFlagsAndPreservesPreviousNavigationWithoutPersistence() = runTest {
        val f = Fixture(this, old); val before = f.navigation; val id = f.launch()
        f.finish(id, false)
        assertSame(before, f.navigation); assertEquals(0, f.accepts); assertTrue(f.selections.isEmpty())
        assertEquals(listOf(Outcome.Success(null)), f.completions)
    }

    @Test fun missingUriIsForwardedForRealAdapterValidationRatherThanInvented() = runTest {
        val f = Fixture(this); f.accepted = Outcome.Failure(AppError.Validation.INVALID_DOCUMENT)
        f.finish(f.launch(), uri = null, flags = 0)
        assertEquals(null to 0, f.raw); assertEquals(listOf(f.accepted), f.completions)
        assertTrue(f.selections.isEmpty()); assertNull(f.picker.recovery.value)
    }

    @Test fun adapterFailuresRemainDistinctAndNeverMutatePreviousNavigation() = runTest {
        val failures = listOf(AppError.Validation.INVALID_DOCUMENT, AppError.Access.PERMISSION_DENIED,
            AppError.Access.PERMISSION_REVOKED, AppError.Access.PROVIDER_UNAVAILABLE,
            AppError.Access.PROVIDER_IO_FAILURE, AppError.Access.PRIVATE_STORAGE_UNAVAILABLE)
        for (error in failures) {
            val f = Fixture(this, old); val before = f.navigation
            f.accepted = Outcome.Failure(error); f.finish(f.launch())
            assertSame(before, f.navigation); assertEquals(listOf(f.accepted), f.completions)
            assertEquals(1, f.accepts); assertTrue(f.selections.isEmpty())
        }
    }

    @Test fun rapidRepeatedRequestsCannotReplacePendingConsumerOrLaunchTwice() = runTest {
        val f = Fixture(this); val id = f.launch()
        assertFalse(f.picker.claim(id)); assertFalse(f.picker.claim("foreign"))
        assertEquals(Outcome.Failure(AppError.Conflict.STALE_SESSION), f.picker.choose())
        assertEquals(id, f.picker.request.value)
        f.picker.launched("foreign", denied); f.picker.launched(id, denied)
        f.finish(id); assertEquals(1, f.accepts)
    }

    @Test fun callbacksBeforeClaimOrForDifferentRequestAreRejected() = runTest {
        val f = Fixture(this); val ack = f.request(); runCurrent()
        val id = f.picker.request.value!!
        f.picker.launched(id, denied); f.finish(id); f.finish("foreign")
        assertEquals(0, f.accepts); assertFalse(ack.isCompleted)
        assertTrue(f.picker.claim(id)); f.picker.launched(id, Outcome.Success(Unit)); runCurrent()
        f.finish(id); assertEquals(1, f.accepts)
    }

    @Test fun rotationKeepsSameTokenAndOnlyTheOriginalLaunchCanBeClaimed() = runTest {
        val f = Fixture(this); val id = f.launch()
        val recreatedActivityToken = f.picker.request.value!!
        assertEquals(id, recreatedActivityToken); assertFalse(f.picker.claim(recreatedActivityToken))
        f.picker.incomplete(); assertNull(f.picker.recovery.value)
        f.finish(recreatedActivityToken); assertEquals(1, f.completions.size)
    }

    @Test fun earlySynchronousRegistryResultWaitsForLaunchAcknowledgment() = runTest {
        val f = Fixture(this); val ack = f.request(); runCurrent(); val id = f.picker.request.value!!
        assertTrue(f.picker.claim(id)); f.finish(id)
        assertEquals(0, f.accepts); assertFalse(ack.isCompleted)
        f.picker.launched(id, Outcome.Success(Unit)); runCurrent()
        assertEquals(1, f.accepts); assertEquals(1, f.completions.size); assertTrue(ack.isCompleted)
    }

    @Test fun failedLaunchCompletesOnlyLaunchFailureAndDiscardsBufferedResult() = runTest {
        val f = Fixture(this); val ack = f.request(); runCurrent(); val id = f.picker.request.value!!
        assertTrue(f.picker.claim(id)); f.finish(id)
        f.picker.launched(id, denied); runCurrent()
        assertEquals(denied, ack.await()); assertEquals(0, f.accepts); assertTrue(f.completions.isEmpty())
        assertNull(f.picker.request.value); f.picker.launched(id, denied)
    }

    @Test fun oldCallbackCannotAuthorizeANewerRequest() = runTest {
        val f = Fixture(this); val first = f.launch(); f.finish(first, false)
        val second = f.launch(); assertNotEquals(first, second)
        f.finish(first); assertEquals(0, f.accepts); assertEquals(second, f.picker.request.value)
        f.finish(second); assertEquals(1, f.accepts)
    }

    @Test fun concurrentDuplicateCallbackDuringAcceptanceCannotRepeatPersistence() = runTest {
        val f = Fixture(this); val gate = CompletableDeferred<Unit>(); f.duringAccept = { gate.await() }
        val id = f.launch(); f.finish(id); f.finish(id)
        assertEquals(1, f.accepts); assertTrue(f.completions.isEmpty())
        gate.complete(Unit); runCurrent(); assertEquals(1, f.completions.size)
    }

    @Test fun partialRootUpdateReportsFailureWithoutRollbackAndRequiresAuthoritativeReconciliation() = runTest {
        val f = Fixture(this, old); val before = f.navigation; f.selectError = revoked
        f.finish(f.launch())
        assertSame(before, f.navigation); assertEquals(Outcome.Success(fresh), f.stored)
        assertEquals(listOf(revoked), f.completions); assertEquals(revoked.error, f.picker.recovery.value)
        f.selectError = null; f.picker.reconcile(); runCurrent()
        assertEquals(1, f.restoreCalls); assertEquals(1, f.refreshed); assertEquals(fresh, f.navigation!!.root)
        assertNull(f.picker.recovery.value); assertEquals(1, f.accepts) // Never persist again.
    }

    @Test fun explicitReauthorizationCanRecoverWhenStoredGrantCannotBeRestored() = runTest {
        val f = Fixture(this, old); f.selectError = revoked; f.finish(f.launch())
        f.selectError = null
        val id = f.launch(); f.picker.reconcile(); runCurrent()
        assertEquals(0, f.restoreCalls) // No competing restore while Android selection is outstanding.
        f.finish(id, false); assertEquals(revoked.error, f.picker.recovery.value)
        f.finish(f.launch()); assertNull(f.picker.recovery.value)
        assertEquals(2, f.accepts); assertEquals(fresh, f.navigation!!.root)
    }

    @Test fun recoveryIsUserControlledBoundedAndRetainsExactRestoreFailure() = runTest {
        val f = Fixture(this, old); f.selectError = revoked; f.finish(f.launch())
        f.stored = denied; val gate = CompletableDeferred<Unit>(); f.duringRestore = { gate.await() }
        f.picker.reconcile(); f.picker.reconcile(); runCurrent(); assertEquals(1, f.restoreCalls)
        assertEquals(Outcome.Failure(AppError.Conflict.STALE_SESSION), f.picker.choose())
        gate.complete(Unit); runCurrent(); assertEquals(denied.error, f.picker.recovery.value)
        assertEquals(0, f.refreshed); assertEquals(old, f.navigation!!.root)
    }

    @Test fun recoverySelectFailureRemainsBlockedAndDoesNotEraseTrail() = runTest {
        val f = Fixture(this, old); f.selectError = revoked; f.finish(f.launch())
        f.picker.reconcile(); runCurrent()
        assertEquals(revoked.error, f.picker.recovery.value); assertEquals(old, f.navigation!!.root)
        assertEquals(0, f.refreshed)
    }

    @Test fun processDeathWithoutPendingConsumerIsExplicitlyIncompleteAndCanRecoverNoRoot() = runTest {
        val f = Fixture(this); f.picker.incomplete()
        assertEquals(AppError.Conflict.STALE_SESSION, f.picker.recovery.value)
        f.finish("old-process"); assertEquals(0, f.accepts)
        f.picker.reconcile(); runCurrent(); assertNull(f.picker.recovery.value); assertEquals(1, f.refreshed)
    }

    @Test fun missingAuthoritativeRootCannotSilentlyBlessAnOldInMemoryTrail() = runTest {
        val f = Fixture(this, old); f.stored = Outcome.Success(null); f.picker.incomplete()
        f.picker.reconcile(); runCurrent()
        assertEquals(AppError.Conflict.STALE_SESSION, f.picker.recovery.value)
        assertEquals(old, f.navigation!!.root); assertEquals(0, f.refreshed)
    }

    @Test fun noRecoveryMeansNoSpuriousRepositoryReads() = runTest {
        val f = Fixture(this); f.picker.reconcile(); runCurrent()
        assertEquals(0, f.restoreCalls); assertFalse(f.picker.claim("missing"))
        f.finish("missing"); f.picker.launched("missing", denied)
    }

    @Test fun disposalBeforeLaunchReleasesWaiterAndRejectsAllLaterCallbacks() = runTest {
        val f = Fixture(this); val ack = f.request(); runCurrent(); val id = f.picker.request.value!!
        f.picker.close(); runCurrent()
        assertEquals(Outcome.Failure(AppError.Conflict.SESSION_CLOSED), ack.await())
        assertFalse(f.picker.claim(id)); f.picker.launched(id, Outcome.Success(Unit)); f.finish(id)
        assertEquals(0, f.accepts); assertEquals(Outcome.Failure(AppError.Conflict.SESSION_CLOSED), f.picker.choose())
        f.picker.incomplete(); f.picker.reconcile(); assertEquals(0, f.restoreCalls)
    }

    @Test fun disposalDuringNonCooperativeAcceptanceNeverPublishesToRetiredLibrary() = runTest {
        val f = Fixture(this); val gate = CompletableDeferred<Unit>(); f.duringAccept = { gate.await() }
        val id = f.launch(); f.finish(id); f.picker.close(); gate.complete(Unit); runCurrent()
        assertTrue(f.completions.isEmpty()); assertNull(f.picker.request.value)
    }

    @Test fun unexpectedProviderCancellationIsNotConvertedToPermissionFailureOrSuccess() = runTest {
        val f = Fixture(this); val original = CancellationException("provider canceled")
        f.duringAccept = { throw original }
        f.finish(f.launch())
        assertSame(original, f.interruptions.single())
        assertTrue(f.completions.isEmpty()); assertTrue(f.selections.isEmpty())
        f.picker.close() // Production owner closes on this exact interruption.
    }

    @Test fun programmingDefectsReachOwnerUnchangedAndNeverBecomeTypedPermissionErrors() = runTest {
        val f = Fixture(this); val defect = IllegalStateException("bad adapter invariant")
        val failures = mutableListOf<Throwable>()
        val owner = SupervisorJob()
        val scope = CoroutineScope(owner + StandardTestDispatcher(testScheduler) + CoroutineExceptionHandler { _, error -> failures += error })
        val picker = RootPickerCoordinator(scope, f.folders, accept = { _, _ -> throw defect },
            restore = { Outcome.Success(null) }, completed = f.completions::add, refreshed = {}, interrupted = { throw AssertionError(it) })
        val acknowledgment = scope.async { picker.choose() }; runCurrent(); val id = picker.request.value!!
        assertTrue(picker.claim(id)); picker.launched(id, Outcome.Success(Unit)); acknowledgment.await()
        picker.result(id, true, "uri", 1); runCurrent()
        assertSame(defect, failures.single()); assertTrue(f.completions.isEmpty())
        picker.close(); owner.cancelAndJoin()
    }

    @Test fun cancellationDuringNavigatorSynchronizationKeepsOriginalFailureAndNeverReportsSuccess() = runTest {
        val f = Fixture(this); val canceled = CancellationException("navigator interrupted")
        val failures = mutableListOf<CancellationException>()
        val folders = object : FolderNavigation by f.folders {
            override suspend fun select(root: FolderRef): Outcome<FolderNavigationState> = throw canceled
        }
        val picker = RootPickerCoordinator(backgroundScope, folders, accept = { _, _ -> Outcome.Success(fresh) },
            restore = { Outcome.Success(fresh) }, completed = f.completions::add, refreshed = {}, interrupted = failures::add)
        val acknowledgment = backgroundScope.async { picker.choose() }; runCurrent(); val id = picker.request.value!!
        assertTrue(picker.claim(id)); picker.launched(id, Outcome.Success(Unit)); acknowledgment.await()
        picker.result(id, true, "uri", 1); runCurrent()
        assertSame(canceled, failures.single()); assertTrue(f.completions.isEmpty()); picker.close()
    }

    @Test fun disposalDuringNonCooperativeCancellationDoesNotNotifyAnAlreadyClosingOwner() = runTest {
        val f = Fixture(this); val gate = CompletableDeferred<Unit>()
        f.duringAccept = { gate.await(); throw CancellationException("late") }
        val id = f.launch(); f.finish(id); f.picker.close(); gate.complete(Unit); runCurrent()
        assertTrue(f.interruptions.isEmpty()); assertTrue(f.completions.isEmpty())
    }

    @Test fun disposalDuringReconciliationCannotRefreshDisposedController() = runTest {
        val f = Fixture(this); val gate = CompletableDeferred<Unit>(); f.duringRestore = { gate.await() }
        f.picker.incomplete(); f.picker.reconcile(); runCurrent()
        f.picker.close(); gate.complete(Unit); runCurrent(); assertEquals(0, f.refreshed)
    }
    @Test fun uncertainPrivateRootWriteRequiresReadReconciliationRatherThanBlindPersistenceReplay() = runTest {
        val f=Fixture(this,old)
        f.accepted=Outcome.Failure(AppError.Access.PRIVATE_STORAGE_UNAVAILABLE)
        f.duringAccept={f.stored=Outcome.Success(fresh)} // Simulate a committed write with failed acknowledgment.
        f.finish(f.launch())
        assertEquals(AppError.Access.PRIVATE_STORAGE_UNAVAILABLE,f.picker.recovery.value)
        assertEquals(old,f.navigation!!.root); assertEquals(1,f.accepts)
        f.picker.reconcile(); runCurrent()
        assertEquals(fresh,f.navigation!!.root); assertEquals(1,f.restoreCalls); assertEquals(1,f.accepts)
        assertNull(f.picker.recovery.value)
    }
    @Test fun lateFailedNavigatorResultCannotPublishRecoveryAfterPickerClosure() = runTest {
        val f=Fixture(this,old); val gate=CompletableDeferred<Unit>()
        f.duringSelect={gate.await()}; f.selectError=revoked
        f.finish(f.launch()); f.picker.close(); gate.complete(Unit); runCurrent()
        assertNull(f.picker.recovery.value); assertTrue(f.completions.isEmpty())
    }
    @Test fun disposedReconciliationCannotSelectFromALateRestoredRoot() = runTest {
        val f=Fixture(this,old); val gate=CompletableDeferred<Unit>(); f.duringRestore={gate.await()}
        f.picker.incomplete(); f.picker.reconcile(); runCurrent()
        f.picker.close(); gate.complete(Unit); runCurrent()
        assertTrue(f.selections.isEmpty()); assertEquals(0,f.refreshed)
    }
    @Test fun unexpectedReconciliationReadCancellationNotifiesOwnerWithOriginalCause() = runTest {
        val f=Fixture(this,old); val canceled=CancellationException("root reread canceled")
        f.duringRestore={throw canceled}; f.picker.incomplete(); f.picker.reconcile(); runCurrent()
        assertSame(canceled,f.interruptions.single()); assertEquals(0,f.refreshed)
        assertEquals(AppError.Conflict.STALE_SESSION,f.picker.recovery.value); f.picker.close()
    }
    @Test fun unexpectedReconciliationSelectCancellationIsNotSuccessfulRecovery() = runTest {
        val f=Fixture(this,old); val canceled=CancellationException("reconcile navigation canceled")
        f.duringSelect={throw canceled}; f.picker.incomplete(); f.picker.reconcile(); runCurrent()
        assertSame(canceled,f.interruptions.single()); assertEquals(0,f.refreshed)
        assertEquals(AppError.Conflict.STALE_SESSION,f.picker.recovery.value); f.picker.close()
    }
    @Test fun closedReconciliationDoesNotNotifyOwnerAboutLateCancellation() = runTest {
        val f=Fixture(this,old); val gate=CompletableDeferred<Unit>()
        f.duringRestore={gate.await();throw CancellationException("closed root read")}
        f.picker.incomplete(); f.picker.reconcile(); runCurrent(); f.picker.close(); gate.complete(Unit); runCurrent()
        assertTrue(f.interruptions.isEmpty()); assertEquals(0,f.refreshed)
    }

}
