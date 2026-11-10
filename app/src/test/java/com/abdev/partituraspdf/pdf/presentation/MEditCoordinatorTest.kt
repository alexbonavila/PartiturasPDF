package com.abdev.partituraspdf.pdf.presentation

import com.abdev.partituraspdf.app.di.MProductionWriteAuthorization
import com.abdev.partituraspdf.core.document.*
import com.abdev.partituraspdf.core.foundation.DocumentOpenRequest
import com.abdev.partituraspdf.core.result.Outcome
import com.abdev.partituraspdf.pdf.api.*
import com.abdev.partituraspdf.pdf.workflow.*
import com.abdev.partituraspdf.storage.api.*
import com.abdev.partituraspdf.storage.transaction.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Privileged byte-only MTestRig seam. No PDF fidelity, Android grants or production approval. */
@OptIn(ExperimentalCoroutinesApi::class)
class MEditCoordinatorTest {
    @get:Rule val temporary = TemporaryFolder()
    private inner class Fixture(scope: TestScope) {
        val rig = MTestRig(temporary.newFolder(), scope.backgroundScope)
        val route = MEditRoute(4, 8, DocumentOpenRequest.Transient(rig.source.storage))
        var active: MEditRoute? = route
        var prepares = 0; var resolutions = 0; var bindings = 0
        var onResolve: () -> Unit = {}
        var availability = MEditAvailability.AVAILABLE
        var hold: CompletableDeferred<Unit>? = null
        var reject: MPreparation? = null
        var deny = false
        var configure: (MTestRig.Destination) -> Unit = {}
        var recovered: List<MRecoveryStatus> = emptyList()
        var lastFlags = 0; var lastUri: String? = null
        var destination: MTestRig.Destination? = null
        val backend = object : MEditingBackend {
            override val availability get() = this@Fixture.availability
            override suspend fun resolve(route: MEditRoute): Outcome<SnapshotSource> {
                resolutions++;onResolve()
                return Outcome.Success(SnapshotSource(rig.source.storage,
                    (route.request as? DocumentOpenRequest.Registered)?.document))
            }
            override suspend fun prepare(source: SnapshotSource, plan: OrderedPagePlan): MPreparation {
                prepares++;hold?.await()
                return reject ?: rig.coordinator().prepare(source, plan)
            }
            override suspend fun destination(request: MPickerHandoff, source: SnapshotSource, uri: String, flags: Int): WriteDestinationCapability? {
                bindings++;lastFlags=flags;lastUri=uri
                if (deny) return null
                return rig.destination(request.operation).also { configure(it);destination=it }.let { WriteDestinationCapability(it) }
            }
            override suspend fun recover() = recovered
        }
        val feature = MEditCoordinator(scope, backend, StandardTestDispatcher(scope.testScheduler)).also { it.connect { active } }
        fun open(route: MEditRoute = this.route) { feature.open(MEditSource(route, 3, "Observed.pdf")) }
    }
    @Test fun draftReordersRotatesExtractsAndFreezesWithoutDuplicatesOrEmptyPlans() {
        val draft=MEditDraft.initial(3)!!
        val changed=draft.change(MEditAction.Move(2,-1)).change(MEditAction.Rotate(2))
            .change(MEditAction.Include(0,false))
        assertEquals(listOf(PageSelection(2,1),PageSelection(1,0)),changed.freeze()!!.selections)
        assertEquals(listOf(0,1,2),draft.pages.map { it.index })
        var rotated=changed
        repeat(3) { rotated=rotated.change(MEditAction.Rotate(2)) }
        assertEquals(0,rotated.pages.single { it.index==2 }.turns)
        val frozen=changed.freeze()!!
        assertThrows(UnsupportedOperationException::class.java) { (frozen.selections as MutableList<*>).clear() }
        assertThrows(IllegalArgumentException::class.java) { OrderedPagePlan(listOf(PageSelection(0,0),PageSelection(0,1))) }
        assertThrows(IllegalArgumentException::class.java) { PageSelection(0,4) }
        assertNull(MEditDraft.initial(0));assertNull(MEditDraft.initial(10_001))
        val single=MEditDraft.initial(1)!!.change(MEditAction.Include(0,false))
        assertNull(single.freeze())
        assertSame(draft,draft.change(MEditAction.Move(0,-1)))
        assertSame(draft,draft.change(MEditAction.Move(1,20)))
        assertSame(draft,draft.change(MEditAction.Rotate(99)))
    }
    @Test fun productionGateRefusesBeforeAnySourceResolutionPreparationOrPicker() = runTest {
        val f=Fixture(this);f.availability=MProductionWriteAuthorization.availability
        f.open();f.feature.save(f.route);runCurrent()
        assertEquals(MEditAvailability.UNAPPROVED,MProductionWriteAuthorization.availability)
        assertEquals(0,f.prepares);assertEquals(0,f.resolutions);assertNull(f.feature.request.value)
        assertNotNull(f.feature.state.value.draft);assertFalse(f.feature.state.value.canSave)
        f.feature.terminate()
    }
    @Test fun staleRouteAndLateDraftActionsCannotChangeNewSource() = runTest {
        val f=Fixture(this);f.open()
        f.active=f.route.copy(intent=9);f.feature.change(f.route,MEditAction.Rotate(0));f.feature.save(f.route)
        assertEquals(0,f.feature.state.value.draft!!.pages[0].turns);assertEquals(0,f.prepares)
        f.feature.routeChanged();runCurrent();assertNull(f.feature.state.value.draft)
        f.feature.open(MEditSource(f.active!!,2));f.feature.change(f.route,MEditAction.Include(0,false))
        assertEquals(2,f.feature.state.value.draft!!.freeze()!!.selections.size)
        f.feature.terminate()
    }
    @Test fun concurrentSaveAndRepeatedClaimsRemainOneShot() = runTest {
        val f=Fixture(this);f.hold=CompletableDeferred();f.open()
        f.feature.save(f.route);f.feature.save(f.route);runCurrent();assertEquals(1,f.prepares)
        f.hold!!.complete(Unit);runCurrent()
        val request=f.feature.request.value!!.handoff
        assertTrue(f.feature.claim(request));assertFalse(f.feature.claim(request))
        f.feature.launched(request,true)
        f.feature.result(request.operation,"wrong",true,"candidate",3);runCurrent();assertEquals(0,f.bindings)
        f.feature.result(request.operation,request.token,true,"candidate",3)
        f.feature.result(request.operation,request.token,true,"candidate",3);runCurrent()
        assertEquals(1,f.bindings);assertEquals(1,f.destination!!.writes)
        assertTrue(f.feature.state.value.publication is PublicationResult.VerifiedNewDocument)
        f.feature.terminate()
    }
    @Test fun missingUriCancellationAndMissingPermissionsRetainUncertaintyWithoutWrites() = runTest {
        for((accepted,uri,flags) in listOf(Triple(false,"ignored",3),Triple(true,null,3),Triple(true,"candidate",1))) {
            val f=Fixture(this);f.open();f.feature.save(f.route);runCurrent()
            val request=f.feature.request.value!!.handoff
            assertTrue(f.feature.claim(request));f.feature.launched(request,true)
            f.feature.result(request.operation,request.token,accepted,uri,flags);runCurrent()
            assertEquals(0,f.bindings);assertSame(PublicationResult.UncertainPublication,f.feature.state.value.publication)
            assertTrue(f.rig.budget.used()>0);assertFalse(f.feature.state.value.canSave)
            f.feature.terminate()
        }
    }
    @Test fun unsupportedProvenanceNeverOpensAWriter() = runTest {
        val f=Fixture(this);f.deny=true;f.open();f.feature.save(f.route);runCurrent()
        val request=f.feature.request.value!!.handoff;f.feature.claim(request);f.feature.launched(request,true)
        f.feature.result(request.operation,request.token,true,"opaque-candidate",67);runCurrent()
        assertEquals(67,f.lastFlags);assertEquals("opaque-candidate",f.lastUri)
        assertNull(f.destination);assertSame(PublicationResult.UncertainPublication,f.feature.state.value.publication)
        f.feature.terminate()
    }
    @Test fun typedPreparationFailureAndCleanupDebtAreNotConvertedToSuccess() = runTest {
        val f=Fixture(this)
        val original=MPreparation.Rejected(PdfOperationFailure.ProtectedDocument,SnapshotCleanupStatus.PENDING)
        f.reject=original;f.open();f.feature.save(f.route);runCurrent()
        assertSame(original,f.feature.state.value.rejection);assertNull(f.feature.request.value)
        assertFalse(f.feature.state.value.canSave)
        f.feature.change(f.route,MEditAction.Rotate(0))
        assertSame(original,f.feature.state.value.rejection);assertFalse(f.feature.state.value.canSave)
        f.feature.terminate()
    }
    @Test fun cancelDuringPreparationJoinsAndNeverLaunches() = runTest {
        val f=Fixture(this);f.hold=CompletableDeferred();f.open();f.feature.save(f.route);runCurrent()
        assertTrue(f.feature.dismiss());runCurrent()
        assertNull(f.feature.request.value);assertNull(f.feature.state.value.draft)
        assertSame(PublicationResult.RejectedBeforePublicEffect,f.feature.state.value.publication)
        f.feature.terminate()
    }
    @Test fun launchFailureAndRetirementRejectLateCallbacks() = runTest {
        val f=Fixture(this);f.open();f.feature.save(f.route);runCurrent()
        val request=f.feature.request.value!!.handoff;f.feature.claim(request)
        f.feature.launched(request,false);runCurrent()
        assertNull(f.feature.request.value);assertNull(f.feature.state.value.draft)
        f.feature.terminate();f.feature.result(request.operation,request.token,true,"candidate",3);runCurrent()
        assertEquals(0,f.bindings)
    }
    @Test fun processRestoredMarkerCannotRestoreAuthorityAndBlocksFreshSave() = runTest {
        val f=Fixture(this);f.feature.incomplete();f.open();f.feature.save(f.route);runCurrent()
        assertSame(PublicationResult.NeedsOwnerResolution,f.feature.state.value.publication)
        assertEquals(0,f.prepares);assertNull(f.feature.request.value);f.feature.terminate()
    }
    @Test fun recoveryNeverInventsReceiptAndUncertainRecordsBlockAdmission() = runTest {
        for(status in MRecoveryStatus.entries) {
            val f=Fixture(this);f.recovered=listOf(status);f.feature.refreshRecovery();runCurrent();f.open()
            assertEquals(listOf(status),f.feature.state.value.recovery);assertNull(f.feature.state.value.publication)
            f.feature.save(f.route);runCurrent()
            val allowed=status in setOf(MRecoveryStatus.OWNER_ACKNOWLEDGED,MRecoveryStatus.VERIFIED_RECORD_OBSERVED)
            assertEquals(if(allowed)1 else 0,f.prepares)
            f.feature.terminate()
        }
    }
    @Test fun registeredAndTransientPlansPreserveOriginalRequestsAndDoNotRebindOutput() = runTest {
        for(registered in listOf(false,true)) {
            val f=Fixture(this)
            val original=if(registered)f.route.copy(request=DocumentOpenRequest.Registered(DocumentRef(DocumentId("original"))))else f.route
            f.active=original;f.open(original);f.feature.save(original);runCurrent()
            val request=f.feature.request.value!!.handoff;f.feature.claim(request);f.feature.launched(request,true)
            f.feature.result(request.operation,request.token,true,"new-document",3);runCurrent()
            assertEquals(original,f.active);assertEquals(original,f.feature.state.value.source!!.route)
            assertEquals(1,f.destination!!.writes);f.feature.terminate()
        }
    }
    @Test fun readbackMismatchAndSourceAliasCannotBecomeVerified() = runTest {
        for(alias in listOf(false,true)) {
            val f=Fixture(this)
            f.configure={ if(alias)it.target=f.rig.source.storage else it.fail="corrupt" }
            f.open();f.feature.save(f.route);runCurrent()
            val request=f.feature.request.value!!.handoff;f.feature.claim(request);f.feature.launched(request,true)
            f.feature.result(request.operation,request.token,true,"candidate",3);runCurrent()
            assertFalse(f.feature.state.value.publication is PublicationResult.VerifiedNewDocument)
            assertEquals(if(alias)0 else 1,f.destination!!.writes)
            f.feature.terminate()
        }
    }
    @Test fun verifiedPublicationWithCleanupDebtStaysVerifiedAndAccounted() = runTest {
        val f=Fixture(this);f.open();f.feature.save(f.route);runCurrent()
        f.rig.fault={ if(it=="delete")throw java.io.IOException("Injected private cleanup fault") }
        val request=f.feature.request.value!!.handoff;f.feature.claim(request);f.feature.launched(request,true)
        f.feature.result(request.operation,request.token,true,"candidate",3);runCurrent()
        val result=f.feature.state.value.publication as PublicationResult.VerifiedNewDocument
        assertEquals(SnapshotCleanupStatus.PENDING,result.cleanup)
        assertEquals(1,f.destination!!.writes);assertTrue(f.rig.budget.used()>0)
        f.feature.terminate()
    }
    @Test fun cancellationDuringPublicationNeverReportsRollbackOrAllowsRetry() = runTest {
        val f=Fixture(this);f.configure={ it.blockWrite=CompletableDeferred() }
        f.open();f.feature.save(f.route);runCurrent()
        val request=f.feature.request.value!!.handoff;f.feature.claim(request);f.feature.launched(request,true)
        f.feature.result(request.operation,request.token,true,"candidate",3);runCurrent()
        assertTrue(f.destination!!.entered.isCompleted)
        f.feature.dismiss();runCurrent()
        assertFalse(f.feature.state.value.publication is PublicationResult.VerifiedNewDocument)
        assertNotSame(PublicationResult.RejectedBeforePublicEffect,f.feature.state.value.publication)
        f.feature.result(request.operation,request.token,true,"candidate",3);runCurrent()
        assertEquals(1,f.destination!!.writes);assertTrue(f.rig.budget.used()>0)
        f.feature.terminate()
    }
    @Test fun routeReplacementAfterReadyRetainsEvidenceWithoutExposingPicker() = runTest {
        val f=Fixture(this)
        f.onResolve={ if(f.resolutions==2)f.active=f.route.copy(intent=99) }
        f.open();f.feature.save(f.route);runCurrent()
        assertNull(f.feature.request.value);assertEquals(0,f.bindings)
        assertSame(PublicationResult.UncertainPublication,f.feature.state.value.publication)
        assertTrue(f.rig.budget.used()>0)
        f.feature.terminate()
    }
    @Test fun safeNamesAreBoundedDisplayHintsNotUriOrPathAuthority() {
        assertEquals("Score-edited.pdf",suggestedPdfName("Score.pdf","fallback.pdf","-edited"))
        assertEquals("fallback.pdf",suggestedPdfName("../\\/","fallback.pdf","-edited"))
        assertFalse(suggestedPdfName("a/../../name.pdf","fallback.pdf","-edited").contains('/'))
        assertTrue(suggestedPdfName("a".repeat(1000),"fallback.pdf","-edited").length<=83)
    }
}
