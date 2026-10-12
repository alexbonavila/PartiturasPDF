package com.abdev.partituraspdf.storage.saf

import android.content.ComponentName
import android.content.pm.PackageManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** The manual picker check must live in the debug target APK, not the test APK. */
@RunWith(AndroidJUnit4::class)
class SafGrantAcceptanceRegistrationTest {
    @Test fun manualPickerActivityIsInstalledInDebugTargetOnly(): Unit {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        val component = ComponentName(
            target.packageName,
            "com.abdev.partituraspdf.storage.saf.SafGrantAcceptanceActivity",
        )
        val declared = target.packageManager.getActivityInfo(component, 0)
        assertEquals(target.packageName, declared.packageName)
        assertTrue(declared.exported)
        assertEquals(component.className, declared.name)
    }
}
