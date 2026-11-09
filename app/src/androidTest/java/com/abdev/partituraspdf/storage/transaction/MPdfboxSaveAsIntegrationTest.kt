package com.abdev.partituraspdf.storage.transaction

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.abdev.partituraspdf.pdf.api.*
import com.abdev.partituraspdf.pdf.editing.*
import com.abdev.partituraspdf.pdf.engine.spike.SpikeSemantics
import com.abdev.partituraspdf.pdf.workflow.*
import com.abdev.partituraspdf.storage.api.*
import com.abdev.partituraspdf.storage.transaction.testprovider.MNewDocumentProvider
import com.tom_roush.pdfbox.pdmodel.PDDocument
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class MPdfboxSaveAsIntegrationTest {
    private suspend fun sandbox(fixture: String = "02", body: suspend (MPdfboxIntegrationRig) -> Unit) {
        val rig = MPdfboxIntegrationRig(fixture)
        var primary: Throwable? = null
        try { body(rig) } catch (t: Throwable) { primary = t; throw t }
        finally { withContext(NonCancellable) {
            try { rig.close() } catch (cleanup: Throwable) { if (primary != null) primary.addSuppressed(cleanup) else throw cleanup }
        } }
    }
    @Test fun realReorderRotationPublishesOnceWithDurableReadbackAndCleanup() { runBlocking {
        sandbox { rig ->
            val ready = rig.ready()
            assertTrue(rig.journal.read(ready.request.operation).any { it.state == MState.PREPARED_FOR_PICKER })
            val sealed = rig.journal.read(ready.request.operation).last().output!!
            val destination = rig.Destination(ready.request.operation)
            assertTrue(rig.coordinator().prepare(rig.source, rig.plan) is MPreparation.Busy)
            val result = ready.session.complete(ready.request.token, destination.capability()) as PublicationResult.VerifiedNewDocument
            assertEquals(destination.destination, result.destination)
            assertEquals(sealed, result.evidence); assertEquals(sealed, destination.evidence())
            assertNotEquals(rig.fixture.getString("sha256"), result.evidence.sha256)
            assertEquals(SnapshotCleanupStatus.COMPLETE, result.cleanup)
            val records = rig.journal.read(ready.request.operation)
            assertTrue(records.any { it.state == MState.VERIFIED_COMMITTED })
            assertEquals(MState.CLEANED, records.last().state)
            assertEquals(1, destination.writes); assertEquals(1, destination.reads)
            // Open actual provider output independently after byte verification, not a producer buffer.
            android.os.ParcelFileDescriptor.AutoCloseInputStream(rig.provider.openDocument(destination.id,"r",null)).use { input ->
                PDDocument.load(input).use { pdf ->
                    assertEquals(2,pdf.numberOfPages)
                    assertEquals(90,pdf.getPage(0).rotation); assertEquals(270,pdf.getPage(1).rotation)
                    val pages = SpikeSemantics.snapshot(pdf, listOf(0,0))
                    assertTrue(pages[0].text.contains("PAGE-ID 002")); assertTrue(pages[1].text.contains("PAGE-ID 001"))
                }
            }
            assertSame(PublicationResult.NeedsOwnerResolution, ready.session.complete(ready.request.token,destination.capability()))
            assertEquals(1,destination.writes)
            val admission=rig.gate.tryAcquire() as WriteAdmissionGate.Admission.Acquired; admission.close()
            assertFalse(java.io.File(rig.root.directory,"tx-${ready.request.operation.value}/source.sealed").exists())
            assertFalse(java.io.File(rig.root.directory,"tx-${ready.request.operation.value}/output.sealed").exists())
            val recovery = MReadOnlyReconciler(MStorageBudget(rig.root),rig.journal,object:KnownDestinationReader {
                override suspend fun readIfAuthorized(opaque:String,consume:suspend(java.io.InputStream)->Unit):Boolean {
                    destination.readBack(consume);return true
                }
            })
            assertEquals(MRecoveryStatus.VERIFIED_RECORD_OBSERVED,recovery.inspect(ready.request.operation))
            rig.unchanged()
        }
    } }
    @Test fun workingCopyAnnotationsAndZeroTurnNativePixelsAreValidated() { runBlocking {
        sandbox("12") { rig ->
            val plan=OrderedPagePlan(listOf(PageSelection(1,0),PageSelection(0,0)))
            val ready=rig.coordinator().prepare(rig.source,plan) as MPreparation.Ready
            val destination=rig.Destination(ready.request.operation)
            assertTrue(ready.session.complete(ready.request.token,destination.capability()) is PublicationResult.VerifiedNewDocument)
            assertEquals(1,destination.writes)
        }
    } }
    @Test fun realValidatorRejectsWrongPlanAndMalformedSealedOutput() { runBlocking {
        sandbox { rig ->
            val id=TransactionId(java.util.UUID.randomUUID().toString())
            val captured=TransactionSnapshotCapturer(rig.access,rig.store).captureForTransaction(rig.source,SnapshotCaptureLimits(64L*1024*1024),id)
                as SnapshotCaptureResult.Captured
            val source=captured.snapshot
            var output:WorkingSnapshot?=null
            try {
                output=(rig.adapters.transform(source,rig.plan,id,rig.store) as EngineResult.Produced).output
                val wrong=OrderedPagePlan(listOf(PageSelection(0,0),PageSelection(1,0)))
                assertTrue(rig.adapters.validate(source,output,wrong) is ValidationResult.Refused)
            } finally {
                output?.let { it.requestClose(); assertSame(SnapshotReleaseResult.Released,it.awaitReleased()) }
                source.requestClose();assertSame(SnapshotReleaseResult.Released,source.awaitReleased())
            }
        }
        sandbox { rig ->
            val id=TransactionId(java.util.UUID.randomUUID().toString())
            val source=(TransactionSnapshotCapturer(rig.access,rig.store).captureForTransaction(rig.source,SnapshotCaptureLimits(64L*1024*1024),id) as SnapshotCaptureResult.Captured).snapshot
            val output=(rig.store.stage(id) { it.write(byteArrayOf(1,2,3)) } as SnapshotCaptureResult.Captured).snapshot
            try { assertTrue(rig.adapters.validate(source,output,rig.plan) is ValidationResult.Refused) }
            finally { for(snapshot in listOf(output,source)) { snapshot.requestClose();assertSame(SnapshotReleaseResult.Released,snapshot.awaitReleased()) } }
        }
    } }
    @Test fun refusedProviderCreateAndCancellationBeforeLaunchCannotPublish() { runBlocking {
        sandbox { rig ->
            val ready=rig.ready();rig.provider.fault=MNewDocumentProvider.Fault.CREATE_DENIED
            assertThrows(java.io.FileNotFoundException::class.java) { rig.Destination(ready.request.operation) }
            assertNull(ready.session.verifiedPublication())
            ready.session.acknowledgeUnresolved()
        }
        sandbox { rig ->
            val entered=CompletableDeferred<Unit>()
            val engine=object:PdfModificationEngine {
                override suspend fun transform(source:WorkingSnapshot,plan:OrderedPagePlan,transaction:TransactionId,store:PrivateSnapshotStore):EngineResult {
                    entered.complete(Unit);awaitCancellation()
                }
            }
            val task=launch(Dispatchers.IO) { rig.coordinator(engine=engine).prepare(rig.source,rig.plan) }
            withTimeout(20_000) { entered.await() }; task.cancelAndJoin()
            assertTrue(task.isCancelled)
            val admission=rig.gate.tryAcquire() as WriteAdmissionGate.Admission.Acquired;admission.close()
        }
    } }
    @Test fun invalidIndexProtectedCorruptAndUnknownBytesNeverLaunch() { runBlocking {
        sandbox { rig ->
            val bad=OrderedPagePlan(listOf(PageSelection(2,0)))
            assertEquals(PdfOperationFailure.InvalidPlan,(rig.coordinator().prepare(rig.source,bad) as MPreparation.Rejected).reason)
            val originalBytes=rig.original.readBytes()
            try {
                rig.original.appendBytes(byteArrayOf(7))
                assertEquals(PdfOperationFailure.UnsupportedDocument,(rig.coordinator().prepare(rig.source,rig.plan) as MPreparation.Rejected).reason)
            } finally { rig.original.writeBytes(originalBytes) } // Test-owned input copy only.
        }
        for ((id,reason) in listOf("14" to PdfOperationFailure.ProtectedDocument,"15" to PdfOperationFailure.InvalidInput)) sandbox(id) { rig ->
            assertEquals(reason,(rig.coordinator().prepare(rig.source,rig.plan) as MPreparation.Rejected).reason)
        }
    } }
    @Test fun sealValidationAndPreparedCheckpointFailuresCannotIssueHandoff() { runBlocking {
        sandbox { rig ->
            rig.failSeal=true
            assertTrue(rig.coordinator().prepare(rig.source,rig.plan) is MPreparation.Rejected)
        }
        sandbox { rig ->
            val refusal=object:PdfOperationValidator {
                override suspend fun validate(source:WorkingSnapshot,output:WorkingSnapshot,plan:OrderedPagePlan)=ValidationResult.Refused(PdfValidationReport.Rejected)
            }
            assertTrue(rig.coordinator(refusal).prepare(rig.source,rig.plan) is MPreparation.Rejected)
        }
        sandbox { rig ->
            rig.failCheckpoint="000000000005.tmp"
            assertTrue(rig.coordinator().prepare(rig.source,rig.plan) is MPreparation.Rejected)
        }
    } }
    @Test fun actualProviderWriteRefusalCorruptReadbackAndReliableErrorsNeverVerify() { runBlocking {
        for (fault in listOf(MNewDocumentProvider.Fault.WRITE_DENIED,MNewDocumentProvider.Fault.CORRUPT_READBACK,
                            MNewDocumentProvider.Fault.PARTIAL_WRITE,MNewDocumentProvider.Fault.CLOSE_ERROR)) sandbox { rig ->
            val ready=rig.ready();val destination=rig.Destination(ready.request.operation)
            rig.provider.fault=fault
            val result=ready.session.complete(ready.request.token,destination.capability())
            assertFalse(result is PublicationResult.VerifiedNewDocument)
            assertEquals(1,destination.writes)
            assertSame(PublicationResult.NeedsOwnerResolution,ready.session.complete(ready.request.token,destination.capability()))
            assertEquals(1,destination.writes)
            ready.session.acknowledgeUnresolved()
        }
    } }
    @Test fun injectedShortReadCloseFailureAndPrewriteRefusalStayUnverified() { runBlocking {
        for (kind in listOf("short","close","denied")) sandbox { rig ->
            val ready=rig.ready();val destination=rig.Destination(ready.request.operation)
            destination.shortRead=kind=="short"; destination.closeFailure=kind=="close";destination.denied=kind=="denied"
            assertFalse(ready.session.complete(ready.request.token,destination.capability()) is PublicationResult.VerifiedNewDocument)
            assertEquals(if(kind=="denied")0 else 1,destination.writes)
            ready.session.acknowledgeUnresolved()
        }
    } }
    @Test fun staleDuplicateAndMissingCallbacksNeverReplayPublicWrite() { runBlocking {
        sandbox { rig ->
            val ready=rig.ready();val destination=rig.Destination(ready.request.operation)
            assertSame(PublicationResult.NeedsOwnerResolution,ready.session.complete("wrong",destination.capability()))
            assertEquals(0,destination.writes)
            assertSame(PublicationResult.UncertainPublication,ready.session.complete(ready.request.token,null))
            assertSame(PublicationResult.NeedsOwnerResolution,ready.session.complete(ready.request.token,destination.capability()))
            assertEquals(0,destination.writes);ready.session.acknowledgeUnresolved()
        }
    } }
    @Test fun cancellationAtWriterBarriersRetainsUncertaintyAndStopsBeforeResolution() { runBlocking {
        for (afterCopy in listOf(false,true)) sandbox { rig ->
            val ready=rig.ready();val destination=rig.Destination(ready.request.operation)
            val barrier=CompletableDeferred<Unit>()
            if(afterCopy)destination.waitAfterCopy=barrier else destination.waitBeforeWrite=barrier
            val job=launch(Dispatchers.IO) { ready.session.complete(ready.request.token,destination.capability()) }
            withTimeout(20_000) { if(afterCopy)destination.copied.await() else destination.entered.await() }
            job.cancelAndJoin();assertTrue(job.isCancelled)
            assertNull(ready.session.verifiedPublication());assertEquals(1,destination.writes)
            assertTrue(rig.gate.tryAcquire() is WriteAdmissionGate.Admission.Busy)
            ready.session.acknowledgeUnresolved()
            assertSame(PublicationResult.NeedsOwnerResolution,ready.session.complete(ready.request.token,destination.capability()))
            assertEquals(1,destination.writes)
        }
    } }
    @Test fun finalCheckpointFaultAndExceptionalPrivateCleanupKeepIndependentOutcomes() { runBlocking {
        sandbox { rig ->
            val ready=rig.ready();val destination=rig.Destination(ready.request.operation)
            rig.failCheckpoint="000000000012.tmp" // VERIFIED_COMMITTED after READBACK_OBSERVED.
            assertFalse(ready.session.complete(ready.request.token,destination.capability()) is PublicationResult.VerifiedNewDocument)
            assertEquals(1,destination.writes);ready.session.acknowledgeUnresolved()
        }
        sandbox { rig ->
            val ready=rig.ready();val destination=rig.Destination(ready.request.operation)
            val defect=IllegalStateException("Injected private cleanup defect");rig.cleanupFault=defect
            val result=ready.session.complete(ready.request.token,destination.capability()) as PublicationResult.VerifiedNewDocument
            assertEquals(SnapshotCleanupStatus.PENDING,result.cleanup)
            assertNotNull(ready.session.cleanupFailure());assertEquals(result.evidence,destination.evidence())
            assertTrue(rig.budget.used() >= result.evidence.byteCount)
            val admission=rig.gate.tryAcquire() as WriteAdmissionGate.Admission.Acquired;admission.close()
            assertEquals(1,destination.writes)
        }
    } }
}
