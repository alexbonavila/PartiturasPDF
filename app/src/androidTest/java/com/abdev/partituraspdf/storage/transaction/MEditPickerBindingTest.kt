package com.abdev.partituraspdf.storage.transaction

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.contract.ActivityResultContract
import androidx.core.app.ActivityOptionsCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.abdev.partituraspdf.app.host.bindMDocumentPicker
import com.abdev.partituraspdf.core.foundation.DocumentOpenRequest
import com.abdev.partituraspdf.core.result.Outcome
import com.abdev.partituraspdf.pdf.api.OrderedPagePlan
import com.abdev.partituraspdf.pdf.presentation.*
import com.abdev.partituraspdf.pdf.workflow.MPickerHandoff
import com.abdev.partituraspdf.storage.api.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Real AndroidX registry and disposable provider, injected test adapter. No real picker/grant proof. */
@RunWith(AndroidJUnit4::class)
class MEditPickerBindingTest {
    private class Registry : ActivityResultRegistry() {
        var launches=0;var code=0;var intent:Intent?=null
        val started=CompletableDeferred<Unit>()
        override fun <I,O> onLaunch(requestCode:Int,contract:ActivityResultContract<I,O>,input:I,options:ActivityOptionsCompat?) {
            launches++;code=requestCode;intent=input as Intent;started.complete(Unit)
        }
    }
    private class Fixture(val scope:CoroutineScope) {
        val rig=MPdfboxIntegrationRig() // Test-only qualified adapter, never the unaccepted production engine.
        var registry=Registry();var binding:Job?=null
        var enabled=true;var deny=false;var binds=0
        var observed:Pair<String,Int>?=null
        var target:MPdfboxIntegrationRig.Destination?=null
        val route=MEditRoute(2,3,DocumentOpenRequest.Transient(rig.source.storage))
        var current:MEditRoute?=route
        val backend=object:MEditingBackend {
            override val availability get()=if(enabled)MEditAvailability.AVAILABLE else MEditAvailability.UNAPPROVED
            override suspend fun resolve(route:MEditRoute)=Outcome.Success(rig.source)
            override suspend fun prepare(source:SnapshotSource,plan:OrderedPagePlan)=rig.coordinator().prepare(source,plan)
            override suspend fun destination(request:MPickerHandoff,source:SnapshotSource,uri:String,flags:Int):WriteDestinationCapability? {
                binds++;observed=uri to flags
                if(deny || uri==source.storage.value)return null
                return rig.Destination(request.operation).also{target=it}.capability()
            }
            override suspend fun recover()=emptyList<MRecoveryStatus>()
        }
        val feature=MEditCoordinator(scope,backend).also { it.connect { current } }
        fun bind() { binding=scope.launch(start=CoroutineStart.UNDISPATCHED) { bindMDocumentPicker(registry,feature) { "synthetic.pdf" } } }
        suspend fun prepare() {
            feature.refreshRecovery();feature.state.first { !it.recovering && !it.recoveryRequired }
            feature.open(MEditSource(route,2));feature.save(route)
            feature.state.first { it.phase==MEditPhase.PICKER }
            registry.started.await()
        }
        fun result(accepted:Boolean=true,uri:String?="content://disposable/new",flags:Int=67) {
            assertTrue(registry.dispatchResult(registry.code,if(accepted)Activity.RESULT_OK else Activity.RESULT_CANCELED,
                uri?.let { Intent().setData(Uri.parse(it)).addFlags(flags) }))
        }
    }
    private fun exercise(body:suspend Fixture.()->Unit) = runBlocking {
        withContext(Dispatchers.Main.immediate) {
            val job=SupervisorJob();var fixture:Fixture?=null;var primary:Throwable?=null
            try {
                val constructed=Fixture(CoroutineScope(job+Dispatchers.Main.immediate))
                fixture=constructed
                withTimeout(20_000) { constructed.body() }
            } catch(failure:Throwable) { primary=failure;throw failure }
            finally { withContext(NonCancellable) {
                var cleanup:Throwable?=null
                suspend fun release(action:suspend()->Unit) {
                    try { action() } catch(failure:Throwable) {
                        if(cleanup==null)cleanup=failure else cleanup!!.addSuppressed(failure)
                    }
                }
                release { fixture?.feature?.terminate() }
                release { fixture?.binding?.cancelAndJoin() }
                release { job.cancelAndJoin() }
                release { fixture?.rig?.close() }
                cleanup?.let { if(primary!=null)primary.addSuppressed(it)else throw it }
            } }
        }
    }
    @Test fun realRegistryCreatesPdfIntentAndForwardsExactFlagsToOneWrite() { exercise {
        bind();prepare()
        assertEquals(Intent.ACTION_CREATE_DOCUMENT,registry.intent!!.action)
        assertEquals("application/pdf",registry.intent!!.type)
        assertTrue(registry.intent!!.categories.contains(Intent.CATEGORY_OPENABLE))
        assertEquals("synthetic.pdf",registry.intent!!.getStringExtra(Intent.EXTRA_TITLE))
        result();feature.state.first { it.phase==MEditPhase.RESULT }
        assertEquals("content://disposable/new" to 67,observed)
        assertEquals(1,binds);assertEquals(1,target!!.writes)
        assertTrue(feature.state.value.publication is PublicationResult.VerifiedNewDocument)
        rig.unchanged()
    } }
    @Test fun registryRecreationReconnectsPendingCallbackWithoutRelaunch() { exercise {
        bind();prepare();val oldCode=registry.code
        binding!!.cancelAndJoin()
        val saved=Bundle();registry.onSaveInstanceState(saved)
        registry=Registry().also { it.onRestoreInstanceState(saved) }
        assertTrue(registry.dispatchResult(oldCode,Activity.RESULT_OK,Intent().setData(Uri.parse("content://disposable/new")).addFlags(3)))
        bind();feature.state.first { it.phase==MEditPhase.RESULT }
        assertEquals(0,registry.launches);assertEquals(1,binds);assertEquals(1,target!!.writes)
    } }
    @Test fun missingUriAndCanceledResultNeverBindOrWrite() { exercise {
        bind();prepare();result(false,"content://ignored/uri",67)
        feature.state.first { it.phase==MEditPhase.RESULT }
        assertEquals(0,binds);assertNull(target)
        assertSame(PublicationResult.UncertainPublication,feature.state.value.publication)
    } }
    @Test fun permissionAndAliasOrUnsupportedProvenanceStayUnverified() { exercise {
        deny=true;bind();prepare();result()
        feature.state.first { it.phase==MEditPhase.RESULT }
        assertEquals(1,binds);assertNull(target)
        assertSame(PublicationResult.UncertainPublication,feature.state.value.publication)
    } }
    @Test fun successfulResultWithoutUriRemainsUncertainWithoutBinding() { exercise {
        bind();prepare();result(uri=null)
        feature.state.first { it.phase==MEditPhase.RESULT }
        assertEquals(0,binds);assertNull(target)
        assertEquals(null,feature.observedResult!!.uri)
        assertSame(PublicationResult.UncertainPublication,feature.state.value.publication)
    } }
    @Test fun readOnlyResultFlagsCannotReachDestinationBinding() { exercise {
        bind();prepare();result(flags=1)
        feature.state.first { it.phase==MEditPhase.RESULT }
        assertEquals(1,feature.observedResult!!.flags);assertEquals(0,binds);assertNull(target)
    } }
    @Test fun processRestoredOwnerDoesNotLaunchOrAcceptOldCallback() { exercise {
        feature.incomplete();bind();feature.open(MEditSource(route,2));feature.save(route)
        feature.result(TransactionId(java.util.UUID.randomUUID().toString()),"stale",true,"content://disposable/new",3)
        assertEquals(0,registry.launches);assertEquals(0,binds)
        assertSame(PublicationResult.NeedsOwnerResolution,feature.state.value.publication)
    } }
    @Test fun closedAuthorizationStillAllowsDraftButNeverLaunchesPicker() { exercise {
        enabled=false;bind();feature.open(MEditSource(route,2));feature.save(route)
        assertNotNull(feature.state.value.draft);assertEquals(0,registry.launches);assertNull(feature.request.value)
    } }
    @Test fun changedReaderRejectsCallbackAndRetainsUncertainOriginalOperation() { exercise {
        bind();prepare();val handoff=feature.request.value!!.handoff
        current=route.copy(intent=route.intent+1)
        feature.result(handoff.operation,handoff.token,true,"content://disposable/new",3)
        assertEquals(0,binds);assertNull(target)
        feature.routeChanged();feature.state.first { it.phase==MEditPhase.RESULT }
        assertSame(PublicationResult.UncertainPublication,feature.state.value.publication)
        assertTrue(feature.requiresRecoveryOnRestore)
        assertEquals(1,registry.launches);rig.unchanged()
    } }
    @Test fun retiringPickerOwnerJoinsWithoutWriteOrAcceptingLateResult() { exercise {
        bind();prepare();val handoff=feature.request.value!!.handoff
        feature.terminate()
        feature.result(handoff.operation,handoff.token,true,"content://disposable/late",3)
        assertEquals(0,binds);assertNull(target);assertNull(feature.request.value)
        assertTrue(feature.requiresRecoveryOnRestore)
        assertSame(com.abdev.partituraspdf.pdf.workflow.WriteAdmissionGate.Admission.Busy,rig.gate.tryAcquire())
        assertEquals(1,registry.launches);rig.unchanged()
    } }
}
