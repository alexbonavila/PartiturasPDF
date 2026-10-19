package com.abdev.partituraspdf.settings

import com.abdev.partituraspdf.core.foundation.SettingsEffect
import com.abdev.partituraspdf.core.preferences.*
import com.abdev.partituraspdf.core.result.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsControllerTest {
    private val dark = SettingsEffect.Theme(ThemeSelection.DARK)
    private val light = SettingsEffect.Theme(ThemeSelection.LIGHT)
    private val width = SettingsEffect.Fit(PageFit.WIDTH)
    private val error = AppError.Access.PRIVATE_STORAGE_UNAVAILABLE

    @Test fun initialLoadingNeverFabricatesDefaultsAndActionsBeforeStartDoNothing() = runTest {
        val f = SettingsFixture(backgroundScope, null)
        assertEquals(Observation.Loading, f.state.preferences)
        assertNull(f.state.selectedTheme); assertNull(f.state.selectedFit)
        f.controller.dispatch(SettingsAction.SelectTheme(ThemeSelection.DARK))
        f.controller.dispatch(SettingsAction.RetryObservation)
        runCurrent()
        assertEquals(0, f.collections); assertTrue(f.calls.isEmpty())
        f.controller.start(); runCurrent()
        f.controller.dispatch(SettingsAction.SelectFit(PageFit.WIDTH)); runCurrent()
        assertTrue(f.calls.isEmpty()); assertNull(f.state.selectedFit)
        f.controller.close()
    }

    @Test fun readyDefaultsAndIdempotentStartUseOneContinuousCollector() = runTest {
        val f = SettingsFixture(backgroundScope)
        f.controller.start(); f.controller.start(); runCurrent()
        assertEquals(ThemeSelection.SYSTEM, f.state.selectedTheme)
        assertEquals(PageFit.PAGE, f.state.selectedFit)
        assertEquals(SettingsOperation.Idle, f.state.themeOperation)
        assertEquals(SettingsOperation.Idle, f.state.fitOperation)
        assertEquals(1, f.collections); assertEquals(1, f.collectors)
        f.controller.close(); runCurrent(); assertEquals(0, f.collectors)
    }

    @Test fun failedReadHasNoSelectionAndExplicitRetryStartsOneNewCollector() = runTest {
        val f = SettingsFixture(backgroundScope)
        f.values.tryEmit(Observation.Failed(error))
        f.controller.start(); runCurrent()
        assertEquals(Observation.Failed(error), f.state.preferences)
        assertNull(f.state.selectedTheme); assertNull(f.state.selectedFit)
        assertEquals(0, f.collectors)
        f.controller.dispatch(SettingsAction.SelectTheme(ThemeSelection.DARK))
        f.controller.dispatch(SettingsAction.SelectFit(PageFit.WIDTH))
        f.controller.dispatch(SettingsAction.RetryTheme)
        f.controller.dispatch(SettingsAction.RetryFit)
        runCurrent(); assertTrue(f.calls.isEmpty())
        f.publish(AppPreferences(theme = ThemeSelection.LIGHT))
        repeat(5) { f.controller.dispatch(SettingsAction.RetryObservation) }
        runCurrent()
        assertEquals(ThemeSelection.LIGHT, f.state.selectedTheme)
        assertEquals(2, f.collections); assertEquals(1, f.maximumCollectors)
        f.controller.dispatch(SettingsAction.RetryObservation); runCurrent()
        assertEquals(2, f.collections)
        f.controller.close()
    }

    @Test fun themeAcknowledgementWaitsForObservationWithoutRestartingCollector() = runTest {
        val f = SettingsFixture(backgroundScope)
        f.controller.start(); runCurrent()
        f.controller.dispatch(SettingsAction.SelectTheme(ThemeSelection.DARK)); runCurrent()
        assertEquals(listOf(dark), f.calls)
        assertEquals(SettingsOperation.Pending(dark), f.state.themeOperation)
        assertEquals(ThemeSelection.SYSTEM, f.state.selectedTheme)
        f.acknowledge(); runCurrent()
        assertEquals(SettingsOperation.Reconciling(dark), f.state.themeOperation)
        assertEquals(ThemeSelection.SYSTEM, f.state.selectedTheme)
        repeat(3) { f.controller.dispatch(SettingsAction.SelectTheme(ThemeSelection.DARK)) }
        runCurrent(); assertEquals(1, f.calls.size)
        f.commit(); runCurrent()
        assertEquals(SettingsOperation.Idle, f.state.themeOperation)
        assertEquals(ThemeSelection.DARK, f.state.selectedTheme)
        assertEquals(1, f.collections); assertEquals(0, f.directUpdates)
        f.controller.close()
    }

    @Test fun fitObservationBeforeAcknowledgementResolvesWithoutAnotherEmission() = runTest {
        val f = SettingsFixture(backgroundScope)
        f.controller.start(); runCurrent()
        f.controller.dispatch(SettingsAction.SelectFit(PageFit.WIDTH)); runCurrent()
        f.commit(); runCurrent()
        assertEquals(listOf(width), f.calls)
        assertEquals(PageFit.WIDTH, f.state.selectedFit)
        assertEquals(SettingsOperation.Pending(width), f.state.fitOperation)
        f.acknowledge(); runCurrent()
        assertEquals(SettingsOperation.Idle, f.state.fitOperation)
        assertEquals(1, f.collections)
        f.controller.close()
    }

    @Test fun failedThemePreservesObservedValueAndExplicitRetryTarget() = runTest {
        val f = SettingsFixture(backgroundScope)
        f.controller.start(); runCurrent()
        f.controller.dispatch(SettingsAction.SelectTheme(ThemeSelection.DARK)); runCurrent()
        f.acknowledge(result = Outcome.Failure(error)); runCurrent()
        assertEquals(SettingsOperation.Failed(dark, error), f.state.themeOperation)
        assertEquals(ThemeSelection.SYSTEM, f.state.selectedTheme)
        f.controller.dispatch(SettingsAction.SelectTheme(ThemeSelection.DARK)); runCurrent()
        assertEquals(1, f.calls.size)
        f.controller.dispatch(SettingsAction.RetryTheme); runCurrent()
        assertEquals(listOf(dark, dark), f.calls)
        f.commit(); f.acknowledge(); runCurrent()
        assertEquals(SettingsOperation.Idle, f.state.themeOperation)
        f.controller.close()
    }

    @Test fun failedFitIsIndependentAndRetryPreservesLegacyLanguageAndTheme() = runTest {
        val initial = AppPreferences(theme = ThemeSelection.LIGHT, language = LanguageSelection.CATALAN)
        val f = SettingsFixture(backgroundScope, initial)
        f.controller.start(); runCurrent()
        f.controller.dispatch(SettingsAction.SelectFit(PageFit.WIDTH)); runCurrent()
        f.acknowledge(result = Outcome.Failure(error)); runCurrent()
        assertEquals(SettingsOperation.Failed(width, error), f.state.fitOperation)
        assertEquals(initial, (f.state.preferences as Observation.Ready).value)
        f.controller.dispatch(SettingsAction.RetryFit); runCurrent()
        f.commit(); f.acknowledge(); runCurrent()
        assertEquals(initial.copy(reader = ReaderPreferences(PageFit.WIDTH)), (f.state.preferences as Observation.Ready).value)
        assertEquals(listOf(width, width), f.calls); assertEquals(0, f.directUpdates)
        f.controller.close()
    }

    @Test fun selectingAlreadyObservedValuesOrIdleRetriesNeverWrites() = runTest {
        val f = SettingsFixture(backgroundScope)
        f.controller.start(); runCurrent()
        repeat(4) {
            f.controller.dispatch(SettingsAction.SelectTheme(ThemeSelection.SYSTEM))
            f.controller.dispatch(SettingsAction.SelectFit(PageFit.PAGE))
            f.controller.dispatch(SettingsAction.RetryTheme)
            f.controller.dispatch(SettingsAction.RetryFit)
        }
        runCurrent(); assertTrue(f.calls.isEmpty())
        f.controller.close()
    }

    @Test fun duplicatePendingTapsProduceOneWritePerField() = runTest {
        val f = SettingsFixture(backgroundScope)
        f.controller.start(); runCurrent()
        repeat(100) {
            f.controller.dispatch(SettingsAction.SelectTheme(ThemeSelection.DARK))
            f.controller.dispatch(SettingsAction.SelectFit(PageFit.WIDTH))
        }
        runCurrent(); assertEquals(listOf(dark), f.calls)
        repeat(100) { f.controller.dispatch(SettingsAction.SelectTheme(ThemeSelection.DARK)) }
        runCurrent(); assertEquals(1, f.calls.size)
        f.commit(); f.acknowledge(); runCurrent()
        assertEquals(listOf(dark, width), f.calls)
        f.commit(); f.acknowledge(); runCurrent()
        assertEquals(1, f.maximumWrites)
        f.controller.close()
    }

    @Test fun rapidThemeSelectionsKeepOnlyLatestQueuedIntentAndNeverCancelExecutingWrite() = runTest {
        val f = SettingsFixture(backgroundScope)
        f.controller.start(); runCurrent()
        f.controller.dispatch(SettingsAction.SelectTheme(ThemeSelection.LIGHT)); runCurrent()
        repeat(100) {
            f.controller.dispatch(SettingsAction.SelectTheme(ThemeSelection.SYSTEM))
            f.controller.dispatch(SettingsAction.SelectTheme(ThemeSelection.DARK))
        }
        runCurrent()
        assertEquals(listOf(light), f.calls); assertEquals(1, f.activeWrites)
        assertEquals(SettingsOperation.Pending(dark), f.state.themeOperation)
        f.commit(); f.acknowledge(); runCurrent()
        assertEquals(listOf(light, dark), f.calls)
        assertEquals(ThemeSelection.LIGHT, f.state.selectedTheme)
        f.acknowledge(); runCurrent()
        assertEquals(SettingsOperation.Reconciling(dark), f.state.themeOperation)
        f.commit(); runCurrent()
        assertEquals(ThemeSelection.DARK, f.state.selectedTheme)
        assertEquals(SettingsOperation.Idle, f.state.themeOperation)
        f.controller.close()
    }

    @Test fun returningToExecutingIntentDiscardsQueuedReplacement() = runTest {
        val f = SettingsFixture(backgroundScope)
        f.controller.start(); runCurrent()
        f.controller.dispatch(SettingsAction.SelectTheme(ThemeSelection.LIGHT)); runCurrent()
        f.controller.dispatch(SettingsAction.SelectTheme(ThemeSelection.DARK))
        f.controller.dispatch(SettingsAction.SelectTheme(ThemeSelection.LIGHT))
        f.commit(); f.acknowledge(); runCurrent()
        assertEquals(listOf(light), f.calls)
        assertEquals(SettingsOperation.Idle, f.state.themeOperation)
        f.controller.close()
    }

    @Test fun queuedFitReversalMustWriteEvenWhenOldSnapshotAlreadyEqualsRequestedValue() = runTest {
        val f = SettingsFixture(backgroundScope)
        f.controller.start(); runCurrent()
        f.controller.dispatch(SettingsAction.SelectFit(PageFit.WIDTH)); runCurrent()
        f.controller.dispatch(SettingsAction.SelectFit(PageFit.PAGE))
        f.commit(emit = false); f.acknowledge(); runCurrent()
        assertEquals(listOf(width, SettingsEffect.Fit(PageFit.PAGE)), f.calls)
        f.commit(); f.acknowledge(); runCurrent()
        assertEquals(PageFit.PAGE, f.state.selectedFit)
        assertEquals(SettingsOperation.Idle, f.state.fitOperation)
        f.controller.close()
    }

    @Test fun differentFieldsRemainIndependentAndSerializeActualCommands() = runTest {
        val f = SettingsFixture(backgroundScope, AppPreferences(language = LanguageSelection.SPANISH))
        f.controller.start(); runCurrent()
        f.controller.dispatch(SettingsAction.SelectTheme(ThemeSelection.DARK))
        f.controller.dispatch(SettingsAction.SelectFit(PageFit.WIDTH))
        runCurrent(); assertEquals(listOf(dark), f.calls)
        assertEquals(SettingsOperation.Pending(width), f.state.fitOperation)
        f.commit(); f.acknowledge(); runCurrent()
        assertEquals(listOf(dark, width), f.calls)
        f.commit(); f.acknowledge(); runCurrent()
        assertEquals(1, f.maximumWrites)
        assertEquals(AppPreferences(ThemeSelection.DARK, LanguageSelection.SPANISH, ReaderPreferences(PageFit.WIDTH)),
            (f.state.preferences as Observation.Ready).value)
        f.controller.close()
    }

    @Test fun externalChangesRemainAuthoritativeAndNeverEmitCommands() = runTest {
        val f = SettingsFixture(backgroundScope)
        f.controller.start(); runCurrent()
        val external = AppPreferences(ThemeSelection.LIGHT, LanguageSelection.ENGLISH, ReaderPreferences(PageFit.WIDTH))
        f.publish(external); runCurrent()
        assertEquals(external, (f.state.preferences as Observation.Ready).value)
        assertTrue(f.calls.isEmpty())
        assertEquals(SettingsOperation.Idle, f.state.themeOperation)
        f.controller.close()
    }

    @Test fun conflictingObservationAfterAcknowledgementResolvesToActualValueNotDesiredValue() = runTest {
        val f = SettingsFixture(backgroundScope)
        f.controller.start(); runCurrent()
        f.controller.dispatch(SettingsAction.SelectTheme(ThemeSelection.DARK)); runCurrent()
        f.acknowledge(); runCurrent()
        f.publish(AppPreferences(theme = ThemeSelection.LIGHT)); runCurrent()
        assertEquals(ThemeSelection.LIGHT, f.state.selectedTheme)
        assertEquals(SettingsOperation.Idle, f.state.themeOperation)
        assertEquals(listOf(dark), f.calls)
        f.controller.close()
    }

    @Test fun conflictingObservationBeforeAcknowledgementDoesNotLeaveOperationStuck() = runTest {
        val f = SettingsFixture(backgroundScope)
        f.controller.start(); runCurrent()
        f.controller.dispatch(SettingsAction.SelectFit(PageFit.WIDTH)); runCurrent()
        f.publish(AppPreferences(theme = ThemeSelection.DARK)); runCurrent()
        f.acknowledge(); runCurrent()
        assertEquals(PageFit.PAGE, f.state.selectedFit)
        assertEquals(SettingsOperation.Idle, f.state.fitOperation)
        assertEquals(ThemeSelection.DARK, f.state.selectedTheme)
        f.controller.close()
    }

    @Test fun externalUpdateDoesNotEraseFailedTargetAndRetryAlreadySatisfiedNeedsNoWrite() = runTest {
        val f = SettingsFixture(backgroundScope)
        f.controller.start(); runCurrent()
        f.controller.dispatch(SettingsAction.SelectTheme(ThemeSelection.DARK)); runCurrent()
        f.acknowledge(result = Outcome.Failure(error)); runCurrent()
        f.publish(AppPreferences(theme = ThemeSelection.DARK)); runCurrent()
        assertEquals(SettingsOperation.Failed(dark, error), f.state.themeOperation)
        assertEquals(ThemeSelection.DARK, f.state.selectedTheme)
        f.controller.dispatch(SettingsAction.RetryTheme); runCurrent()
        assertEquals(1, f.calls.size)
        assertEquals(SettingsOperation.Idle, f.state.themeOperation)
        f.controller.close()
    }

    @Test fun queuedIntentSurvivesOlderFailureWithoutInheritingItsError() = runTest {
        val f = SettingsFixture(backgroundScope)
        f.controller.start(); runCurrent()
        f.controller.dispatch(SettingsAction.SelectTheme(ThemeSelection.LIGHT)); runCurrent()
        f.controller.dispatch(SettingsAction.SelectTheme(ThemeSelection.DARK))
        f.acknowledge(result = Outcome.Failure(error)); runCurrent()
        assertEquals(listOf(light, dark), f.calls)
        assertEquals(SettingsOperation.Pending(dark), f.state.themeOperation)
        f.controller.close()
    }

    @Test fun uncertainCancellationAfterPersistenceUsesFreshObservationAndNeverAssumesRollback() = runTest {
        val f = SettingsFixture(backgroundScope)
        val snapshotGate = CompletableDeferred<Unit>()
        f.observer = { flow {
            if (f.collections > 1) snapshotGate.await()
            emit(Observation.Ready(f.persisted))
            awaitCancellation()
        } }
        f.command = { effect ->
            f.commit(effect, emit = false)
            throw CancellationException("uncertain commit")
        }
        f.controller.start(); runCurrent()
        f.controller.dispatch(SettingsAction.SelectTheme(ThemeSelection.DARK)); runCurrent()
        assertEquals(SettingsOperation.Reconciling(dark, uncertain = true), f.state.themeOperation)
        assertEquals(Observation.Loading, f.state.preferences)
        assertNull(f.state.selectedTheme) // Unknown until the fresh read; not a rollback.
        snapshotGate.complete(Unit); runCurrent()
        assertEquals(ThemeSelection.DARK, f.state.selectedTheme)
        assertEquals(SettingsOperation.Idle, f.state.themeOperation)
        assertEquals(2, f.collections); assertEquals(1, f.maximumCollectors)
        assertTrue(backgroundScope.isActive)
        assertEquals(0, f.activeWrites)
        assertTrue(f.commandJobs.single().isCancelled)
        assertEquals(listOf(dark), f.calls) // Cancellation never automatically retries a write.
        f.controller.close()
    }

    @Test fun canceledCommandDoesNotEraseNewerIntentAndNextWriteWaitsForFreshRead() = runTest {
        val f = SettingsFixture(backgroundScope)
        f.controller.start(); runCurrent()
        f.controller.dispatch(SettingsAction.SelectTheme(ThemeSelection.LIGHT)); runCurrent()
        f.controller.dispatch(SettingsAction.SelectTheme(ThemeSelection.DARK))
        f.writes[0].cancel(CancellationException("cancel"))
        runCurrent()
        assertEquals(listOf(light, dark), f.calls)
        assertEquals(SettingsOperation.Pending(dark), f.state.themeOperation)
        assertEquals(2, f.collections); assertEquals(1, f.maximumWrites)
        f.controller.close()
    }

    @Test fun cancellationBeforePersistencePropagatesAndFreshReadConfirmsUnchangedSelection() = runTest {
        val f = SettingsFixture(backgroundScope)
        val cancellation = CancellationException("before commit")
        var completion: Throwable? = null
        f.command = {
            currentCoroutineContext()[Job]!!.invokeOnCompletion { failure -> completion = failure }
            throw cancellation
        }
        f.controller.start(); runCurrent()
        f.controller.dispatch(SettingsAction.SelectFit(PageFit.WIDTH)); runCurrent()
        assertSame(cancellation, completion)
        assertTrue(f.commandJobs.single().isCancelled)
        assertEquals(PageFit.PAGE, f.state.selectedFit)
        assertEquals(SettingsOperation.Idle, f.state.fitOperation)
        assertEquals(2, f.collections); assertEquals(listOf(width), f.calls)
        f.controller.close()
    }

    @Test fun selectionDuringReconciliationMustNotTrustPreWriteSnapshotForQueuedReversal() = runTest {
        val f = SettingsFixture(backgroundScope)
        f.controller.start(); runCurrent()
        f.controller.dispatch(SettingsAction.SelectFit(PageFit.WIDTH)); runCurrent()
        f.commit(emit = false); f.acknowledge(); runCurrent()
        assertEquals(SettingsOperation.Reconciling(width), f.state.fitOperation)
        f.controller.dispatch(SettingsAction.SelectFit(PageFit.PAGE)); runCurrent()
        assertEquals(listOf(width, SettingsEffect.Fit(PageFit.PAGE)), f.calls)
        f.commit(); f.acknowledge(); runCurrent()
        assertEquals(PageFit.PAGE, f.state.selectedFit)
        assertEquals(SettingsOperation.Idle, f.state.fitOperation)
        f.controller.close()
    }

    @Test fun explicitSnapshotRetryCanResolveUnobservedAcknowledgementWithoutRetryingWrite() = runTest {
        val f = SettingsFixture(backgroundScope)
        f.controller.start(); runCurrent()
        f.controller.dispatch(SettingsAction.SelectFit(PageFit.WIDTH)); runCurrent()
        f.commit(emit = false); f.acknowledge(); runCurrent()
        assertEquals(SettingsOperation.Reconciling(width), f.state.fitOperation)
        f.values.tryEmit(Observation.Ready(f.persisted))
        f.controller.dispatch(SettingsAction.RetryObservation); runCurrent()
        assertEquals(PageFit.WIDTH, f.state.selectedFit)
        assertEquals(SettingsOperation.Idle, f.state.fitOperation)
        assertEquals(1, f.calls.size)
        assertEquals(2, f.collections); assertEquals(1, f.maximumCollectors)
        f.controller.close()
    }

    @Test fun failedObservationPausesQueuedWritesUntilExplicitReadRecovery() = runTest {
        val f = SettingsFixture(backgroundScope)
        f.controller.start(); runCurrent()
        f.controller.dispatch(SettingsAction.SelectTheme(ThemeSelection.DARK)); runCurrent()
        f.controller.dispatch(SettingsAction.SelectFit(PageFit.WIDTH))
        f.values.tryEmit(Observation.Failed(error)); runCurrent()
        f.acknowledge(); runCurrent()
        assertEquals(1, f.calls.size)
        assertEquals(Observation.Failed(error), f.state.preferences)
        assertNull(f.state.selectedTheme)
        f.publish(AppPreferences(theme = ThemeSelection.DARK))
        f.controller.dispatch(SettingsAction.RetryObservation); runCurrent()
        assertEquals(listOf(dark, width), f.calls)
        assertEquals(SettingsOperation.Idle, f.state.themeOperation)
        f.controller.close()
    }

    @Test fun queuedValueAlreadySatisfiedByExternalConsumerIsNotWritten() = runTest {
        val f = SettingsFixture(backgroundScope)
        f.controller.start(); runCurrent()
        f.controller.dispatch(SettingsAction.SelectTheme(ThemeSelection.DARK)); runCurrent()
        f.controller.dispatch(SettingsAction.SelectFit(PageFit.WIDTH))
        f.publish(AppPreferences(theme = ThemeSelection.DARK, reader = ReaderPreferences(PageFit.WIDTH)))
        runCurrent(); f.acknowledge(); runCurrent()
        assertEquals(listOf(dark), f.calls)
        assertEquals(SettingsOperation.Idle, f.state.fitOperation)
        f.controller.close()
    }

    @Test fun closeBeforeStartAndActionsAfterCloseAreTerminalAndParentSurvives() = runTest {
        val f = SettingsFixture(backgroundScope)
        f.controller.close(); f.controller.close(); f.controller.start()
        for (action in listOf(SettingsAction.SelectTheme(ThemeSelection.DARK),
            SettingsAction.SelectFit(PageFit.WIDTH), SettingsAction.RetryTheme,
            SettingsAction.RetryFit, SettingsAction.RetryObservation)) f.controller.dispatch(action)
        runCurrent()
        assertEquals(0, f.collections); assertTrue(f.calls.isEmpty())
        assertTrue(backgroundScope.isActive)
    }

    @Test fun closeCancelsObserverAndWriteAndNeverCancelsParent() = runTest {
        val f = SettingsFixture(backgroundScope)
        f.controller.start(); runCurrent()
        f.controller.dispatch(SettingsAction.SelectTheme(ThemeSelection.DARK)); runCurrent()
        f.controller.dispatch(SettingsAction.SelectFit(PageFit.WIDTH))
        val frozen = f.state
        f.controller.close(); f.controller.close(); runCurrent()
        assertEquals(0, f.collectors); assertEquals(0, f.activeWrites)
        f.acknowledge(); f.publish(AppPreferences(theme = ThemeSelection.DARK)); runCurrent()
        f.controller.start(); f.controller.dispatch(SettingsAction.SelectFit(PageFit.WIDTH)); runCurrent()
        assertEquals(frozen, f.state); assertEquals(listOf(dark), f.calls)
        assertTrue(backgroundScope.isActive)
    }

    @Test fun nonCooperativeLateWriteCannotPublishOrRestartObservationAfterClose() = runTest {
        val f = SettingsFixture(backgroundScope)
        val gate = CompletableDeferred<Unit>()
        f.command = { withContext(NonCancellable) { gate.await(); Outcome.Success(Unit) } }
        f.controller.start(); runCurrent()
        f.controller.dispatch(SettingsAction.SelectTheme(ThemeSelection.DARK)); runCurrent()
        val frozen = f.state
        f.controller.close(); runCurrent()
        gate.complete(Unit); runCurrent()
        assertEquals(frozen, f.state); assertEquals(1, f.collections)
        assertEquals(0, f.activeWrites)
    }

    @Test fun obsoleteObserverIsJoinedAndItsLateReadyCannotReplaceNewGeneration() = runTest {
        val f = SettingsFixture(backgroundScope)
        val oldReadGate = CompletableDeferred<Unit>()
        var count = 0
        f.observer = {
            flow {
                if (count++ == 0) {
                    emit(Observation.Failed(error))
                    withContext(NonCancellable) { oldReadGate.await() }
                    emit(Observation.Ready(AppPreferences(theme = ThemeSelection.DARK)))
                } else emit(Observation.Ready(AppPreferences(theme = ThemeSelection.LIGHT)))
            }
        }
        f.controller.start(); runCurrent()
        f.controller.dispatch(SettingsAction.RetryObservation); runCurrent()
        assertEquals(Observation.Loading, f.state.preferences)
        assertEquals(1, f.collections); assertEquals(1, f.collectors)
        oldReadGate.complete(Unit); runCurrent()
        assertEquals(ThemeSelection.LIGHT, f.state.selectedTheme)
        assertEquals(2, f.collections); assertEquals(1, f.maximumCollectors)
        f.controller.close()
    }

    @Test fun newControllerReentryReadsPersistenceRatherThanOldPendingIntent() = runTest {
        val f = SettingsFixture(backgroundScope)
        f.controller.start(); runCurrent()
        f.controller.dispatch(SettingsAction.SelectTheme(ThemeSelection.DARK)); runCurrent()
        f.commit(); f.controller.close(); runCurrent()
        val next = SettingsController(backgroundScope, f.repository, object : com.abdev.partituraspdf.core.foundation.SettingsCommands {
            override suspend fun apply(effect: SettingsEffect): Outcome<Unit> = error("No replay on reentry")
        })
        next.start(); runCurrent()
        assertEquals(ThemeSelection.DARK, next.state.value.selectedTheme)
        assertEquals(SettingsOperation.Idle, next.state.value.themeOperation)
        assertEquals(1, f.calls.size)
        next.close()
    }

    @Test fun canceledParentPreventsStartAndDoesNotCreateCollectors() = runTest {
        val parent = Job()
        val f = SettingsFixture(CoroutineScope(StandardTestDispatcher(testScheduler) + parent))
        parent.cancel()
        f.controller.start(); f.controller.dispatch(SettingsAction.SelectTheme(ThemeSelection.DARK)); runCurrent()
        assertEquals(0, f.collections); assertTrue(f.calls.isEmpty())
        f.controller.close()
    }

    @Test fun programmingCommandFailureReachesOwnerHandlerAndStopsSiblingWork() = runTest {
        val failures = mutableListOf<Throwable>()
        val parent = SupervisorJob()
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + parent + CoroutineExceptionHandler { _, e -> failures.add(e) })
        val f = SettingsFixture(scope)
        val defect = IllegalStateException("command defect")
        f.command = { throw defect }
        f.controller.start(); runCurrent()
        f.controller.dispatch(SettingsAction.SelectFit(PageFit.WIDTH)); runCurrent()
        assertEquals(listOf(defect), failures)
        assertFalse(f.state.fitOperation is SettingsOperation.Failed)
        assertEquals(0, f.collectors); assertTrue(parent.isActive)
        f.controller.dispatch(SettingsAction.SelectTheme(ThemeSelection.DARK)); runCurrent()
        assertEquals(listOf(width), f.calls)
        f.controller.close(); parent.cancel()
    }

    @Test fun programmingObservationFailureIsNotConvertedToTypedReadFailure() = runTest {
        val failures = mutableListOf<Throwable>()
        val parent = SupervisorJob()
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + parent + CoroutineExceptionHandler { _, e -> failures.add(e) })
        val f = SettingsFixture(scope)
        val defect = IllegalArgumentException("bad persisted value")
        f.observer = { flow { throw defect } }
        f.controller.start(); runCurrent()
        assertEquals(listOf(defect), failures)
        assertFalse(f.state.preferences is Observation.Failed)
        assertEquals(0, f.collectors); assertTrue(parent.isActive)
        f.controller.close(); parent.cancel()
    }
    @Test fun closeBeforeExecutorStartsPreventsBothQueuedWrites() = runTest {
        val f = SettingsFixture(backgroundScope)
        f.controller.start(); runCurrent()
        f.controller.dispatch(SettingsAction.SelectTheme(ThemeSelection.DARK))
        f.controller.dispatch(SettingsAction.SelectFit(PageFit.WIDTH))
        val frozen = f.state
        f.controller.close(); runCurrent()
        assertTrue(f.calls.isEmpty())
        assertEquals(0, f.collectors)
        assertEquals(frozen, f.state)
        assertTrue(backgroundScope.isActive)
    }

    @Test fun repeatedRetriesKeepOriginalTargetsAfterConflictingExternalRead() = runTest {
        val f = SettingsFixture(backgroundScope)
        f.command = { Outcome.Failure(error) }
        f.controller.start(); runCurrent()
        f.controller.dispatch(SettingsAction.SelectTheme(ThemeSelection.DARK))
        f.controller.dispatch(SettingsAction.SelectFit(PageFit.WIDTH)); runCurrent()
        assertEquals(listOf(dark, width), f.calls)
        assertEquals(SettingsOperation.Failed(dark, error), f.state.themeOperation)
        assertEquals(SettingsOperation.Failed(width, error), f.state.fitOperation)
        f.publish(AppPreferences(ThemeSelection.LIGHT, LanguageSelection.CATALAN)); runCurrent()
        f.command = null
        repeat(100) {
            f.controller.dispatch(SettingsAction.RetryTheme)
            f.controller.dispatch(SettingsAction.RetryFit)
        }
        runCurrent()
        assertEquals(listOf(dark, width, dark), f.calls)
        f.commit(); f.acknowledge(); runCurrent()
        assertEquals(listOf(dark, width, dark, width), f.calls)
        f.commit(); f.acknowledge(); runCurrent()
        assertEquals(AppPreferences(ThemeSelection.DARK, LanguageSelection.CATALAN, ReaderPreferences(PageFit.WIDTH)),
            (f.state.preferences as Observation.Ready).value)
        assertEquals(1, f.maximumWrites)
        assertEquals(SettingsOperation.Idle, f.state.themeOperation)
        assertEquals(SettingsOperation.Idle, f.state.fitOperation)
        f.controller.close()
    }

    @Test fun closeDuringObserverRecoveryCannotStartAReplacementCollector() = runTest {
        val f = SettingsFixture(backgroundScope)
        val oldReadGate = CompletableDeferred<Unit>()
        f.observer = { flow {
            emit(Observation.Failed(error))
            withContext(NonCancellable) { oldReadGate.await() }
            emit(Observation.Ready(AppPreferences(theme = ThemeSelection.DARK)))
        } }
        f.controller.start(); runCurrent()
        f.controller.dispatch(SettingsAction.RetryObservation); runCurrent()
        assertEquals(Observation.Loading, f.state.preferences)
        assertEquals(1, f.collections)
        val frozen = f.state
        f.controller.close(); oldReadGate.complete(Unit); runCurrent()
        assertEquals(1, f.collections)
        assertEquals(0, f.collectors)
        assertEquals(frozen, f.state)
        assertTrue(backgroundScope.isActive)
    }

    @Test fun canceledSuspendedWritePreservesOtherFieldUntilFreshRead() = runTest {
        val f = SettingsFixture(backgroundScope)
        val freshRead = CompletableDeferred<Unit>()
        f.observer = { flow {
            if (f.collections > 1) freshRead.await()
            emit(Observation.Ready(f.persisted))
            emitAll(f.values)
        } }
        f.controller.start(); runCurrent()
        f.controller.dispatch(SettingsAction.SelectTheme(ThemeSelection.DARK)); runCurrent()
        f.commit(emit = false)
        f.controller.dispatch(SettingsAction.SelectFit(PageFit.WIDTH))
        f.writes[0].cancel(CancellationException("after underlying commit")); runCurrent()
        assertEquals(Observation.Loading, f.state.preferences)
        assertEquals(SettingsOperation.Reconciling(dark, uncertain = true), f.state.themeOperation)
        assertEquals(SettingsOperation.Pending(width), f.state.fitOperation)
        assertEquals(listOf(dark), f.calls)
        assertTrue(f.commandJobs.first().isCancelled)
        f.publish(f.persisted)
        freshRead.complete(Unit); runCurrent()
        assertEquals(ThemeSelection.DARK, f.state.selectedTheme)
        assertEquals(SettingsOperation.Idle, f.state.themeOperation)
        assertEquals(listOf(dark, width), f.calls)
        f.commit(); f.acknowledge(); runCurrent()
        assertEquals(PageFit.WIDTH, f.state.selectedFit)
        assertEquals(1, f.maximumWrites)
        assertEquals(1, f.maximumCollectors)
        f.controller.close()
    }

    @Test fun parentCancellationStopsOwnedWorkAndRejectsSubsequentActions() = runTest {
        val parent = SupervisorJob()
        val f = SettingsFixture(CoroutineScope(StandardTestDispatcher(testScheduler) + parent))
        f.controller.start(); runCurrent()
        f.controller.dispatch(SettingsAction.SelectTheme(ThemeSelection.DARK)); runCurrent()
        val frozen = f.state
        parent.cancel(); runCurrent()
        f.publish(AppPreferences(theme = ThemeSelection.DARK))
        f.acknowledge()
        f.controller.dispatch(SettingsAction.SelectFit(PageFit.WIDTH)); runCurrent()
        assertEquals(0, f.collectors)
        assertEquals(0, f.activeWrites)
        assertEquals(listOf(dark), f.calls)
        assertEquals(frozen, f.state)
        assertTrue(parent.isCancelled)
        f.controller.close()
    }

    @Test fun failedReadDuringUncertainRecoveryRetainsTargetUntilExplicitRetry() = runTest {
        val f = SettingsFixture(backgroundScope)
        f.observer = { flow {
            if (f.collections == 2) emit(Observation.Failed(error))
            else {
                emit(Observation.Ready(f.persisted))
                awaitCancellation()
            }
        } }
        f.command = { effect ->
            f.commit(effect, emit = false)
            throw CancellationException("uncertain")
        }
        f.controller.start(); runCurrent()
        f.controller.dispatch(SettingsAction.SelectTheme(ThemeSelection.DARK)); runCurrent()
        assertEquals(Observation.Failed(error), f.state.preferences)
        assertEquals(SettingsOperation.Reconciling(dark, uncertain = true), f.state.themeOperation)
        assertNull(f.state.selectedTheme)
        runCurrent(); assertEquals(2, f.collections) // No implicit read/write retry loop.
        f.controller.dispatch(SettingsAction.RetryObservation); runCurrent()
        assertEquals(ThemeSelection.DARK, f.state.selectedTheme)
        assertEquals(SettingsOperation.Idle, f.state.themeOperation)
        assertEquals(3, f.collections)
        assertEquals(listOf(dark), f.calls)
        f.controller.close()
    }

    @Test fun externalReplacementAfterDesiredObservationRemainsAuthoritativeAtAcknowledgement() = runTest {
        val f = SettingsFixture(backgroundScope)
        f.controller.start(); runCurrent()
        f.controller.dispatch(SettingsAction.SelectTheme(ThemeSelection.DARK)); runCurrent()
        f.commit(); runCurrent()
        assertEquals(ThemeSelection.DARK, f.state.selectedTheme)
        val external = AppPreferences(ThemeSelection.LIGHT, LanguageSelection.CATALAN)
        f.publish(external); runCurrent()
        f.acknowledge(); runCurrent()
        assertEquals(external, (f.state.preferences as Observation.Ready).value)
        assertEquals(SettingsOperation.Idle, f.state.themeOperation)
        assertEquals(listOf(dark), f.calls)
        assertEquals(1, f.collections)
        f.controller.close()
    }

    @Test fun anotherSelectionReplacesFailedTargetWithoutRetryingOldIntent() = runTest {
        val f = SettingsFixture(backgroundScope)
        f.controller.start(); runCurrent()
        f.controller.dispatch(SettingsAction.SelectTheme(ThemeSelection.DARK)); runCurrent()
        f.acknowledge(result = Outcome.Failure(error)); runCurrent()
        f.controller.dispatch(SettingsAction.SelectTheme(ThemeSelection.LIGHT)); runCurrent()
        assertEquals(listOf(dark, light), f.calls)
        assertEquals(SettingsOperation.Pending(light), f.state.themeOperation)
        f.commit(); f.acknowledge(); runCurrent()
        assertEquals(ThemeSelection.LIGHT, f.state.selectedTheme)
        assertEquals(SettingsOperation.Idle, f.state.themeOperation)
        f.controller.close()
    }

}
