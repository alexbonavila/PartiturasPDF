package com.abdev.partituraspdf.pdf.workflow

import com.abdev.partituraspdf.storage.api.*
import com.abdev.partituraspdf.storage.transaction.*
import com.abdev.partituraspdf.pdf.api.*
import com.abdev.partituraspdf.pdf.editing.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.*
import java.util.UUID

class MTransactionLifecycleTest {
    @get:Rule val temporary = TemporaryFolder()
    private fun rig(scope: CoroutineScope) = MTestRig(File(temporary.root.canonicalFile, UUID.randomUUID().toString()), scope)
    private suspend fun MTestRig.ready() = coordinator().prepare(source, plan) as MPreparation.Ready
    @Test fun engineCleanupDebtSurvivesOtherwiseCompletePreparationCleanup() = runTest {
        val r = rig(backgroundScope)
        val engine = object : PdfModificationEngine {
            override suspend fun transform(source: WorkingSnapshot, plan: OrderedPagePlan, transaction: TransactionId,
                                           store: PrivateSnapshotStore) =
                EngineResult.Rejected(PdfOperationFailure.TransformationFailure, SnapshotCleanupStatus.PENDING)
        }
        val coordinator = MTransactionCoordinator(r.gate, r.capture, r.store, r.journal, engine, r.validator)
        val result = coordinator.prepare(r.source, r.plan) as MPreparation.Rejected
        assertEquals(PdfOperationFailure.TransformationFailure, result.reason)
        assertEquals(SnapshotCleanupStatus.PENDING, result.cleanup)
        (r.gate.tryAcquire() as WriteAdmissionGate.Admission.Acquired).close()
    }
    @Test fun verifiedCleanupIsDurableAndAdmissionReopens() = runTest {
        val r = rig(backgroundScope); val ready = r.ready(); val destination = r.destination(ready.request.operation)
        val result = ready.session.complete(ready.request.token, WriteDestinationCapability(destination)) as PublicationResult.VerifiedNewDocument
        assertEquals(SnapshotCleanupStatus.COMPLETE, result.cleanup)
        assertEquals(MState.CLEANED, r.journal.read(ready.request.operation).last().state)
        assertEquals(1, destination.writes); assertArrayEquals(r.bytes, destination.stored)
        (r.gate.tryAcquire() as WriteAdmissionGate.Admission.Acquired).close()
    }
    @Test fun verifiedWithPendingCleanupStillReleasesAdmissionAndRetainsCharge() = runTest {
        val r = rig(backgroundScope); val ready = r.ready(); val destination = r.destination(ready.request.operation)
        r.fault = { if (it == "delete") throw IOException("Retained") }
        val result = ready.session.complete(ready.request.token, WriteDestinationCapability(destination)) as PublicationResult.VerifiedNewDocument
        assertEquals(SnapshotCleanupStatus.PENDING, result.cleanup)
        assertEquals(MState.CLEANUP_AUTHORIZED, r.journal.read(ready.request.operation).last().state)
        assertTrue(r.budget.used() > 0)
        (r.gate.tryAcquire() as WriteAdmissionGate.Admission.Acquired).close()
    }
    @Test fun expectedPreparationRejectionReportsActualCleanupDebt() = runTest {
        val r = rig(backgroundScope)
        val refused = object : PdfOperationValidator {
            override suspend fun validate(source: WorkingSnapshot, output: WorkingSnapshot, plan: OrderedPagePlan): ValidationResult {
                r.fault = { if (it == "delete") throw IOException("Retained") }
                return ValidationResult.Refused(PdfValidationReport.Rejected)
            }
        }
        val result = r.coordinator(refused).prepare(r.source, r.plan) as MPreparation.Rejected
        assertEquals(PdfOperationFailure.ValidationInconclusive, result.reason)
        assertEquals(SnapshotCleanupStatus.PENDING, result.cleanup)
        (r.gate.tryAcquire() as WriteAdmissionGate.Admission.Acquired).close()
    }
    @Test fun unexpectedPreparationCleanupIsVisibleWithoutReplacingReason() = runTest {
        val r = rig(backgroundScope); val defect = IllegalStateException("Cleanup defect")
        val refused = object : PdfOperationValidator {
            override suspend fun validate(source: WorkingSnapshot, output: WorkingSnapshot, plan: OrderedPagePlan): ValidationResult {
                r.fault = { if (it == "delete") throw defect }
                return ValidationResult.Refused(PdfValidationReport.Unsupported)
            }
        }
        val result = r.coordinator(refused).prepare(r.source, r.plan) as MPreparation.Rejected
        assertEquals(PdfOperationFailure.ValidationInconclusive, result.reason)
        assertEquals(SnapshotCleanupStatus.PENDING, result.cleanup)
        assertNotNull(result.cleanupFault)
    }
    @Test fun missingCallbackNeedsExplicitDurableOwnerAcknowledgement() = runTest {
        val r = rig(backgroundScope); val ready = r.ready()
        assertSame(PublicationResult.UncertainPublication, ready.session.complete(ready.request.token, null))
        assertSame(WriteAdmissionGate.Admission.Busy, r.gate.tryAcquire())
        assertEquals(MOwnerResolution.ACKNOWLEDGED, ready.session.acknowledgeUnresolved()); assertEquals(MOwnerResolution.ALREADY_RESOLVED, ready.session.acknowledgeUnresolved())
        assertEquals(MState.OWNER_RESOLVED, r.journal.read(ready.request.operation).last().state)
        assertTrue(r.budget.used() > 0)
        (r.gate.tryAcquire() as WriteAdmissionGate.Admission.Acquired).close()
        assertSame(PublicationResult.NeedsOwnerResolution, ready.session.complete(ready.request.token, null))
    }
    @Test fun staleAndDuplicateTokensNeverWriteTwice() = runTest {
        val r = rig(backgroundScope); val ready = r.ready(); val port = r.destination(ready.request.operation)
        assertSame(PublicationResult.NeedsOwnerResolution, ready.session.complete("wrong", WriteDestinationCapability(port)))
        ready.session.complete(ready.request.token, WriteDestinationCapability(port))
        assertSame(PublicationResult.NeedsOwnerResolution, ready.session.complete(ready.request.token, WriteDestinationCapability(port)))
        assertEquals(1, port.writes)
    }
    @Test fun resolutionWaitsForActiveWriterAndCannotEnableReplay() = runTest {
        val r = rig(backgroundScope); val ready = r.ready(); val port = r.destination(ready.request.operation)
        port.blockWrite = CompletableDeferred()
        val write = async { ready.session.complete(ready.request.token, WriteDestinationCapability(port)) }
        port.entered.await()
        val resolution = async(start = CoroutineStart.UNDISPATCHED) { ready.session.acknowledgeUnresolved() }
        assertFalse(resolution.isCompleted); assertSame(WriteAdmissionGate.Admission.Busy, r.gate.tryAcquire())
        port.blockWrite!!.complete(Unit); write.await(); assertEquals(MOwnerResolution.ALREADY_RESOLVED, resolution.await())
        assertEquals(1, port.writes)
    }
    @Test fun cancellationKeepsExactIdentityAndRequiresResolutionAfterWriterStops() = runTest {
        val r = rig(backgroundScope); val ready = r.ready(); val port = r.destination(ready.request.operation)
        val cancellation = CancellationException("Owner cancellation")
        port.blockWrite = CompletableDeferred()
        var cancellationObserved = false
        val job = launch {
            try { ready.session.complete(ready.request.token, WriteDestinationCapability(port)); fail("Must cancel") }
            catch (caught: CancellationException) {
                // Job.cancel may deliver a recovered/wrapped exception, unlike direct throw.
                assertEquals(cancellation.message, caught.message)
                cancellationObserved = true
                throw caught
            }
        }
        port.entered.await(); job.cancel(cancellation); job.join()
        assertTrue(cancellationObserved); assertTrue(job.isCancelled)
        assertNull(ready.session.verifiedPublication())
        assertTrue(r.budget.used() > 0)
        assertEquals(2, boundedFiles(r.root).count { it.name.endsWith(".sealed") })
        assertSame(WriteAdmissionGate.Admission.Busy, r.gate.tryAcquire())
        assertEquals(MOwnerResolution.ACKNOWLEDGED, ready.session.acknowledgeUnresolved()); assertEquals(1, port.writes)
    }
    @Test fun publicFaultsNeverReturnVerifiedOrRetryAndCanBeExplicitlyResolved() = runTest {
        for (fault in listOf("denied", "revalidate", "open", "partial", "close", "read", "short", "corrupt")) {
            val r = rig(backgroundScope); val ready = r.ready(); val port = r.destination(ready.request.operation).apply { fail = fault }
            val result = ready.session.complete(ready.request.token, WriteDestinationCapability(port))
            assertFalse(fault, result is PublicationResult.VerifiedNewDocument)
            assertTrue(port.writes <= 1); assertTrue(r.budget.used() > 0)
            assertEquals(MOwnerResolution.ACKNOWLEDGED, ready.session.acknowledgeUnresolved())
        }
    }
    @Test fun failedFinalAckStaysUnverifiedAndExplicitResolutionDisclosesUnconfirmedRecord() = runTest {
        val r = rig(backgroundScope); val ready = r.ready(); val port = r.destination(ready.request.operation)
        r.fault = { point -> if (point == "fsync" && File(r.root.directory,
            "tx-${ready.request.operation.value}/publication/000000000012.tmp").exists()) throw IOException("Final ack failed") }
        val result = ready.session.complete(ready.request.token, WriteDestinationCapability(port))
        assertSame(PublicationResult.UnverifiedDestination, result)
        assertEquals(1, port.writes); assertEquals(1, port.reads)
        assertSame(WriteAdmissionGate.Admission.Busy, r.gate.tryAcquire())
        assertEquals(MOwnerResolution.ACKNOWLEDGED_RECORD_UNCONFIRMED, ready.session.acknowledgeUnresolved())
        assertTrue(r.budget.used() > 0)
        (r.gate.tryAcquire() as WriteAdmissionGate.Admission.Acquired).close()
        assertSame(PublicationResult.NeedsOwnerResolution, ready.session.complete(ready.request.token, WriteDestinationCapability(port)))
    }
    @Test fun simultaneousDuplicateCallbacksHaveExactlyOneWrite() = runTest {
        val r = rig(backgroundScope); val ready = r.ready(); val port = r.destination(ready.request.operation)
        port.blockWrite = CompletableDeferred()
        val first = async { ready.session.complete(ready.request.token, WriteDestinationCapability(port)) }
        port.entered.await()
        val duplicate = async(start = CoroutineStart.UNDISPATCHED) { ready.session.complete(ready.request.token, WriteDestinationCapability(port)) }
        assertFalse(duplicate.isCompleted)
        port.blockWrite!!.complete(Unit)
        assertTrue(first.await() is PublicationResult.VerifiedNewDocument)
        assertSame(PublicationResult.NeedsOwnerResolution, duplicate.await()); assertEquals(1, port.writes)
    }
    @Test fun productionEngineGateRejectsWithoutPickerAndReturnsAdmission() = runTest {
        val r = rig(backgroundScope)
        val coordinator = MTransactionCoordinator(r.gate, r.capture, r.store, r.journal)
        val result = coordinator.prepare(r.source, r.plan) as MPreparation.Rejected
        assertEquals(PdfOperationFailure.UnsupportedDocument, result.reason)
        assertEquals(SnapshotCleanupStatus.COMPLETE, result.cleanup)
        (r.gate.tryAcquire() as WriteAdmissionGate.Admission.Acquired).close()
    }

    @Test fun primaryValidatorExceptionSurvivesAllCleanupDefects() = runTest {
        val r = rig(backgroundScope); val primary = IllegalArgumentException("Validation defect")
        val cleanup = IllegalStateException("Cleanup defect")
        val validator = object : PdfOperationValidator {
            override suspend fun validate(source: WorkingSnapshot, output: WorkingSnapshot, plan: OrderedPagePlan): ValidationResult {
                r.fault = { if (it == "delete") throw cleanup }; throw primary
            }
        }
        try { r.coordinator(validator).prepare(r.source, r.plan); fail("Must throw original") }
        catch (caught: IllegalArgumentException) {
            assertSame(primary, caught)
            assertTrue(caught.suppressed.any { it === cleanup || it.cause === cleanup })
        }
        (r.gate.tryAcquire() as WriteAdmissionGate.Admission.Acquired).close()
        assertTrue(r.budget.used() > 0)
    }
    @Test fun mismatchedOperationSourceAliasAndRevocationBeforeOpenNeverWrite() = runTest {
        for (mode in listOf("operation", "alias", "revoked")) {
            val r = rig(backgroundScope); val ready = r.ready()
            val id = if (mode == "operation") TransactionId(UUID.randomUUID().toString()) else ready.request.operation
            val port = r.destination(id)
            if (mode == "alias") port.target = r.source.storage
            if (mode == "revoked") port.denyAtValidation = 2
            assertFalse(ready.session.complete(ready.request.token, WriteDestinationCapability(port)) is PublicationResult.VerifiedNewDocument)
            assertEquals(0, port.writes)
            assertEquals(MOwnerResolution.ACKNOWLEDGED, ready.session.acknowledgeUnresolved())
        }
    }

    @Test fun exceptionalCleanupCannotEraseDurablyVerifiedPublication() = runTest {
        val r = rig(backgroundScope); val ready = r.ready(); val port = r.destination(ready.request.operation)
        val defect = IllegalStateException("Unexpected private cleanup")
        r.fault = { if (it == "delete") throw defect }
        val result = ready.session.complete(ready.request.token, WriteDestinationCapability(port)) as PublicationResult.VerifiedNewDocument
        assertEquals(port.destination, result.destination)
        assertEquals(SnapshotCleanupStatus.PENDING, result.cleanup)
        assertEquals(result, ready.session.verifiedPublication())
        val diagnostic = ready.session.cleanupFailure()
        assertTrue(diagnostic === defect || diagnostic?.cause === defect)
        assertEquals(MState.CLEANUP_AUTHORIZED, r.journal.read(ready.request.operation).last().state)
        assertTrue(r.budget.used() > 0)
        assertEquals(2, boundedFiles(r.root).count { it.name.endsWith(".sealed") })
        assertEquals(1, port.writes); assertArrayEquals(r.bytes, port.stored)
        (r.gate.tryAcquire() as WriteAdmissionGate.Admission.Acquired).close()
    }

}
