package com.abdev.partituraspdf.app.integration

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.contract.ActivityResultContract
import androidx.core.app.ActivityOptionsCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.abdev.partituraspdf.app.host.RootPickerCoordinator
import com.abdev.partituraspdf.app.host.bindRootPicker
import com.abdev.partituraspdf.core.document.*
import com.abdev.partituraspdf.core.result.*
import com.abdev.partituraspdf.storage.api.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Actual AndroidX registry/Intent mapping, simulated results. NOT authentic Android URI grants. */
@RunWith(AndroidJUnit4::class)
class RootPickerBindingTest {
    private class Registry : ActivityResultRegistry() {
        var launches = 0
        var requestCode = 0
        var intent: Intent? = null
        var launchFailure: RuntimeException? = null
        override fun <I, O> onLaunch(requestCode: Int, contract: ActivityResultContract<I, O>, input: I,
            options: ActivityOptionsCompat?) {
            launches++; this.requestCode = requestCode
            intent = input as Intent
            launchFailure?.let { throw it }
        }
    }
    private class Fixture(val scope: CoroutineScope) {
        var registry = Registry()
        var binding: Job? = null
        val completed = CompletableDeferred<Outcome<FolderRef?>>()
        var accepts = 0
        var raw: Pair<String?, Int>? = null
        val root = FolderRef("saf", "content://test.documents/tree/root")
        val picker = RootPickerCoordinator(scope, object : FolderNavigation {
            override suspend fun state(): FolderNavigationState? = null
            override suspend fun select(root: FolderRef) = Outcome.Success(FolderNavigationState(root, listOf(root), 1))
            override suspend fun enter(entry: DocumentEntry, expectedVersion: Long): Outcome<FolderNavigationState> = error("Unused")
            override suspend fun back(expectedVersion: Long): Outcome<FolderNavigationState> = error("Unused")
        }, accept = { uri, flags -> accepts++; raw = uri to flags; Outcome.Success(root) },
            restore = { Outcome.Success(null) }, completed = { completed.complete(it) }, refreshed = {}, interrupted = { throw AssertionError(it) })
        fun bind() { binding = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            bindRootPicker(registry, picker) { Intent(Intent.ACTION_OPEN_DOCUMENT_TREE) }
        } }
        suspend fun choose() {
            val acknowledgment = scope.async { picker.choose() }
            assertEquals(Outcome.Success(Unit), acknowledgment.await())
            assertEquals(Intent.ACTION_OPEN_DOCUMENT_TREE, registry.intent!!.action)
        }
        fun result(code: Int = Activity.RESULT_OK, data: Intent? = Intent().setData(Uri.parse(root.value)).addFlags(67)) {
            assertTrue(registry.dispatchResult(registry.requestCode, code, data))
        }
    }
    private fun exercise(block: suspend Fixture.() -> Unit) {
        runBlocking {
            withContext(Dispatchers.Main.immediate) {
                val owner = SupervisorJob()
                val fixture = Fixture(CoroutineScope(owner + Dispatchers.Main.immediate))
                try { withTimeout(10_000) { fixture.block() } }
                finally { fixture.picker.close(); owner.cancelAndJoin() }
            }
        }
    }
    @Test fun actualRegistryForwardsOriginalIntentUriAndFlagsWithoutAuthorizationOnLaunch() {
        exercise {
            bind(); choose(); assertEquals(0, accepts); assertFalse(completed.isCompleted)
            result(); assertEquals(Outcome.Success(root), completed.await())
            assertEquals(root.value to 67, raw); assertEquals(1, accepts); assertEquals(1, registry.launches)
        }
    }
    @Test fun canceledRegistryResultCannotPersistEvenWhenItContainsAUri() {
        exercise {
            bind(); choose(); result(Activity.RESULT_CANCELED)
            assertEquals(Outcome.Success(null), completed.await()); assertEquals(0, accepts)
        }
    }
    @Test fun missingSuccessfulIntentKeepsNullUriAndZeroFlagsForAdapterValidation() {
        exercise {
            bind(); choose(); result(data = null); completed.await()
            assertEquals(null to 0, raw)
        }
    }
    @Test fun pendingRegistryResultSurvivesUnregisterAndRecreatedRegistryWithoutRelaunch() {
        exercise {
            bind(); choose(); val id = picker.request.value; val request = registry.requestCode
            binding!!.cancelAndJoin()
            val state = Bundle(); registry.onSaveInstanceState(state)
            registry = Registry().also { it.onRestoreInstanceState(state) }
            // Android can return before the new Activity connects to its retained consumer.
            assertTrue(registry.dispatchResult(request, Activity.RESULT_OK, Intent().setData(Uri.parse(root.value)).addFlags(1)))
            bind(); assertEquals(Outcome.Success(root), completed.await())
            assertEquals(0, registry.launches); assertNotNull(id); assertEquals(1, accepts)
        }
    }
    @Test fun unavailablePickerProducesTypedLaunchFailureWithoutAnyCompletionOrGrant() {
        exercise {
            registry.launchFailure = android.content.ActivityNotFoundException()
            bind(); val acknowledgment = scope.async { picker.choose() }
            assertEquals(Outcome.Failure(AppError.Access.PROVIDER_UNAVAILABLE), acknowledgment.await())
            assertNull(picker.request.value); assertEquals(0, accepts); assertFalse(completed.isCompleted)
        }
    }
    @Test fun deniedPickerLaunchRemainsPermissionFailure() {
        exercise {
            registry.launchFailure = SecurityException()
            bind(); val acknowledgment = scope.async { picker.choose() }
            assertEquals(Outcome.Failure(AppError.Access.PERMISSION_DENIED), acknowledgment.await())
            assertEquals(0, accepts)
        }
    }
}
