package com.abdev.partituraspdf.storage.saf

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.os.SystemClock
import android.provider.DocumentsContract
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import com.abdev.partituraspdf.core.document.FolderRef
import com.abdev.partituraspdf.core.result.AppError
import com.abdev.partituraspdf.core.result.Outcome
import com.abdev.partituraspdf.storage.api.ReadAccessLimits
import java.io.File
import java.security.MessageDigest
import java.util.regex.Pattern
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Opt-in only: not in the ordinary androidTest source set or CI APK. */
@RunWith(AndroidJUnit4::class)
class SafLocalE2eTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val args = InstrumentationRegistry.getArguments()
    private val device = UiDevice.getInstance(instrumentation)
    private val run = requireNotNull(args.getString("localE2eRun")).also {
        require(it.matches(Regex("[a-f0-9]{32}")))
    }
    private val directory = "PartiturasE2E-$run"
    private val tree = DocumentsContract.buildTreeDocumentUri(
        "com.android.externalstorage.documents", "primary:Download/$directory",
    )
    private val document = DocumentsContract.buildDocumentUriUsingTree(tree,
        "primary:Download/$directory/fixture.pdf")
    private val folder = FolderRef(SafUriCodec.PROVIDER_ID, tree.toString())
    private val grants = SafTreeGrantAccess(context.contentResolver)
    private val prefs = context.getSharedPreferences("saf-grant-acceptance-debug", Context.MODE_PRIVATE)
    private val stateFile = File(context.filesDir, "local-e2e-$run.json")

    private fun guard() {
        assertEquals("Only disposable API 26 emulators are supported", 26, Build.VERSION.SDK_INT)
        assertTrue("Refusing a non-emulator build", Build.FINGERPRINT.startsWith("google/sdk_gphone") ||
            Build.FINGERPRINT.startsWith("google/sdk_google") || Build.HARDWARE in setOf("ranchu", "goldfish"))
    }

    private fun currentGrants(): Set<String> = context.contentResolver.persistedUriPermissions.map {
        "${it.uri}|${it.isReadPermission}|${it.isWritePermission}"
    }.toSet()

    private fun state(): JSONObject = JSONObject(stateFile.readText()).also {
        assertEquals(run, it.getString("run"))
        assertEquals(tree.toString(), it.getString("tree"))
    }

    private fun assertOtherGrantsUnchanged() {
        val baseline = state().getJSONArray("baseline")
        val expected = (0 until baseline.length()).map { baseline.getString(it) }.toSet()
        assertEquals("Unrelated persisted grants changed", expected,
            currentGrants().filterNot { it.startsWith("$tree|") }.toSet())
    }

    @Test fun acquire() {
        guard()
        assertFalse("Run state already exists", stateFile.exists())
        assertFalse(context.contentResolver.persistedUriPermissions.any { it.uri == tree })
        assertEquals("Pre-existing broader authorization would invalidate the grant-loss test; preserve it and use a fresh owned AVD",
            PackageManager.PERMISSION_DENIED, readPermission())
        stateFile.writeText(JSONObject().put("run", run).put("tree", tree.toString())
            .put("baseline", JSONArray(currentGrants().toList()))
            .put("previous", prefs.getString("selected-tree-uri", null) ?: JSONObject.NULL).toString())
        pickFolder()
        verifyAccess()
        assertOtherGrantsUnchanged()
        if (args.getString("localE2eFailure") == "after-grant") {
            fail("Deliberate local E2E diagnostic failure after authentic grant acquisition")
        }
    }

    @Test fun restartAndRelease() {
        guard()
        state()
        // This is a separate instrumentation invocation after a host force-stop.
        verifyAccess()
        assertOtherGrantsUnchanged()
        assertEquals(Outcome.Success(Unit), grants.releasePersistedReadGrant(folder))
        assertEquals(Outcome.Success(false), grants.hasPersistedReadGrant(folder))
    }

    @Test fun lossAndRestore() {
        guard()
        state()
        assertEquals(Outcome.Success(false), grants.hasPersistedReadGrant(folder))
        assertEquals(Outcome.Failure(AppError.Access.PERMISSION_REVOKED),
            grants.requirePreviouslyPersistedReadGrant(folder))
        assertEquals("OS URI authorization must actually be denied", PackageManager.PERMISSION_DENIED, readPermission())
        // A missing known grant establishes revocation, never document deletion.
        val denied = assertThrows(SecurityException::class.java) {
            context.contentResolver.openInputStream(document)?.use { it.read() }
        }
        runBlocking {
            assertEquals(Outcome.Failure(AppError.Access.PERMISSION_DENIED),
                SafDocumentCatalog(context.contentResolver).list(folder))
        }
        instrumentation.sendStatus(0, Bundle().apply {
            putString("localE2eEvidence", "Known grant absent; OS URI READ denied; actual read exception=" +
                denied.javaClass.name + "; production listing=PERMISSION_DENIED; host verifies original PDF hash")
        })
        pickFolder()
        verifyAccess()
        assertOtherGrantsUnchanged()
    }

    @Test fun restoredAfterRestart() {
        guard()
        state()
        verifyAccess()
        assertOtherGrantsUnchanged()
    }

    @Test fun cleanupOwnedGrant() {
        guard()
        // Called only AFTER host diagnostics, including on a functional failure.
        // No state means acquisition never started: do not touch any grant/preferences.
        if (!stateFile.exists()) return
        val saved = state()
        assertOtherGrantsUnchanged()
        if (context.contentResolver.persistedUriPermissions.any { it.uri == tree && it.isReadPermission }) {
            assertEquals(Outcome.Success(Unit), grants.releasePersistedReadGrant(folder))
        }
        assertEquals(Outcome.Success(false), grants.hasPersistedReadGrant(folder))
        assertOtherGrantsUnchanged()
        val editor = prefs.edit()
        if (saved.isNull("previous")) editor.remove("selected-tree-uri")
        else editor.putString("selected-tree-uri", saved.getString("previous"))
        assertTrue(editor.commit())
        assertTrue(stateFile.delete())
    }

    private fun readPermission(): Int = context.checkUriPermission(document,
        Process.myPid(), Process.myUid(), Intent.FLAG_GRANT_READ_URI_PERMISSION)

    private fun verifyAccess() {
        assertEquals(PackageManager.PERMISSION_GRANTED, readPermission())
        assertEquals(tree.toString(), prefs.getString("selected-tree-uri", null))
        val persisted = context.contentResolver.persistedUriPermissions.single { it.uri == tree }
        assertTrue(persisted.isReadPermission)
        assertFalse("Production must persist READ only", persisted.isWritePermission)
        assertEquals(Outcome.Success(Unit), grants.requirePreviouslyPersistedReadGrant(folder))
        runBlocking {
            val listing = SafDocumentCatalog(context.contentResolver).list(folder)
            assertTrue("Real provider listing: $listing", listing is Outcome.Success)
            val entry = (listing as Outcome.Success).value.entries.single()
            val opened = SafDocumentReadAccess(context.contentResolver, context)
                .openSeekable(entry.storage, ReadAccessLimits(1024 * 1024))
            assertTrue("Real seekable read: $opened", opened is Outcome.Success)
            (opened as Outcome.Success).value.use { assertTrue(it.seekable) }
            val uri = Uri.parse(entry.storage.value)
            val digest = context.contentResolver.openInputStream(uri)!!.use { input ->
                val hash = MessageDigest.getInstance("SHA-256")
                val buffer = ByteArray(8192)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    hash.update(buffer, 0, count)
                }
                hash.digest().joinToString("") { "%02x".format(it) }
            }
            assertEquals("Canonical PDF must remain unchanged", args.getString("localE2eSha256"), digest)
        }
    }

    private fun captureMissingUi(selector: String) {
        val started = SystemClock.elapsedRealtime()
        // This runs BEFORE the assertion escapes to AndroidJUnitRunner, not after process teardown.
        val event = File(context.filesDir, "local-e2e-$run-ui-failures/${UUID.randomUUID().toString().replace("-", "")}")
        check(event.mkdirs()) { "Cannot create private failure evidence directory" }
        val errors = JSONArray()
        try {
            check(device.takeScreenshot(File(event, "screenshot.png"))) { "takeScreenshot returned false" }
        } catch (error: Exception) {
            errors.put("screenshot: $error")
        }
        try {
            device.dumpWindowHierarchy(File(event, "hierarchy.xml"))
        } catch (error: Exception) {
            errors.put("hierarchy: $error")
        }
        val visiblePackage = try {
            device.currentPackageName
        } catch (error: Exception) {
            errors.put("package: $error")
            null
        }
        File(event, "metadata.json").writeText(JSONObject()
            .put("run", run).put("selector", selector).put("stage", args.getString("class"))
            .put("captureStartedElapsedRealtimeMs", started)
            .put("elapsedRealtimeMs", SystemClock.elapsedRealtime()).put("wallTimeMs", System.currentTimeMillis())
            .put("package", visiblePackage).put("errors", errors).toString())
    }

    private fun failUi(failure: AssertionError): Nothing {
        try {
            captureMissingUi(failure.message ?: "UI assertion failed")
        } catch (captureError: Exception) {
            failure.addSuppressed(captureError)
        }
        throw failure
    }

    private fun requireUi(selector: androidx.test.uiautomator.BySelector): UiObject2 = awaitSafUi(
        selector.toString(), WAIT, SystemClock::elapsedRealtime, { SystemClock.sleep(100) },
        find = { device.findObject(selector) }, capture = ::captureMissingUi,
    )

    private fun requireInteractiveActivity(): UiObject2 = awaitSafUi(
        "resumed/focused SafGrantAcceptanceActivity and enabled saf_accept_select_button", WAIT,
        SystemClock::elapsedRealtime, { SystemClock.sleep(100) },
        find = { device.findObject(By.res(context.packageName, "saf_accept_select_button")) },
        ready = { button ->
            var activityReady = false
            instrumentation.runOnMainSync {
                activityReady = ActivityLifecycleMonitorRegistry.getInstance()
                    .getActivitiesInStage(Stage.RESUMED).any { activity ->
                        activity is SafGrantAcceptanceActivity && activity.hasWindowFocus() &&
                            activity.window.decorView.isShown
                    }
            }
            activityReady && button.isEnabled && button.isClickable && !button.visibleBounds.isEmpty
        },
        capture = ::captureMissingUi,
    )

    private fun pickFolder() {
        device.wakeUp()
        context.startActivity(Intent(context, SafGrantAcceptanceActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        requireInteractiveActivity().click()
        try {
            assertTrue("System DocumentsUI must appear", device.wait(
                Until.hasObject(By.pkg(PICKER)), WAIT))
        } catch (failure: AssertionError) {
            failUi(failure)
        }
        // API 26 DocumentsUI exposes its roots through the standard navigation drawer.
        if (!device.hasObject(By.res(PICKER, "roots_list"))) {
            requireUi(By.desc("Show roots")).click()
        }
        // Show internal storage is a persistent DocumentsUI preference; use its menu if needed.
        fun storageRoot(): UiObject2? {
            val roots = requireUi(By.res(PICKER, "roots_list"))
            return roots.wait(Until.findObject(By.text(
                Pattern.compile("Internal storage|" + Pattern.quote(Build.MODEL)))), 2000)
        }
        var root = storageRoot()
        if (root == null) {
            device.pressBack()
            requireUi(By.desc("More options")).click()
            requireUi(By.text("Show internal storage")).click()
            requireUi(By.desc("Show roots")).click()
            root = storageRoot() ?: failUi(AssertionError("Internal storage root is unavailable"))
        }
        root.click()
        // Navigate by hierarchy/text, not coordinates; fresh AVD has a bounded directory list.
        requireUi(By.res("android:id/title").text("Download")).click()
        requireUi(By.res("android:id/title").text(directory)).click()
        requireUi(By.res("android:id/button1").pkg(PICKER)).click()
        // API 26 SELECT returns the grant; wait for the real activity-result callback.
        val deadline = SystemClock.elapsedRealtime() + WAIT
        while (SystemClock.elapsedRealtime() < deadline) {
            if (prefs.getString("selected-tree-uri", null) == tree.toString() &&
                grants.hasPersistedReadGrant(folder) == Outcome.Success(true)) return
            SystemClock.sleep(100)
        }
        failUi(AssertionError("Real picker result did not persist the expected tree READ grant"))
    }

    private companion object {
        const val PICKER = "com.android.documentsui"
        const val WAIT = 15000L
    }
}
