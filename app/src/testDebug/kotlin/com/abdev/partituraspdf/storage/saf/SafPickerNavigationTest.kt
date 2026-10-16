package com.abdev.partituraspdf.storage.saf

import org.junit.Assert.*
import org.junit.Test

class SafPickerNavigationTest {
    @Test fun primaryRootUsesDeviceNameEvenWhenItDiffersFromBuildModel() {
        assertEquals("Owner's emulator", primarySafRootTitle("Owner's emulator") { fail("Unexpected fallback"); "" })
    }

    @Test fun absentDeviceNameUsesLocalizedProviderTitle() {
        assertEquals("Almacenamiento interno", primarySafRootTitle(null) { "Almacenamiento interno" })
    }

    @Test fun emptyDeviceNameUsesLocalizedProviderTitle() {
        assertEquals("Emmagatzematge intern", primarySafRootTitle("") { "Emmagatzematge intern" })
    }

    private class Picker {
        var foreground = true
        var root: String? = "device-name-root"
        var configuredRoot: String? = "device-name-root"
        var closeOnDrawer = false
        var closeOnMenu = false
        var menuFailure = false
        val events = mutableListOf<String>()
        fun select() = selectSafPrimaryRoot(
            foreground = { events += "foreground"; foreground },
            showRoots = { events += "drawer"; if (closeOnDrawer) foreground = false },
            findRoot = { events += "lookup"; root },
            showInternalStorage = {
                events += "menu"
                if (menuFailure) throw AssertionError("Missing UI: overflow")
                root = configuredRoot
                if (closeOnMenu) foreground = false
            },
            selectRoot = { events += "select:$it" },
            capture = { events += "capture:$it" },
        )
    }

    @Test fun visibleRootIsSelectedWithoutConfigurationOrBack() {
        val picker = Picker()
        picker.select()
        assertEquals(listOf("foreground", "drawer", "foreground", "lookup", "foreground",
            "select:device-name-root"), picker.events)
    }

    @Test fun missingRootIsCapturedBeforeMenuAndDrawerIsReopenedAfterConfiguration() {
        val picker = Picker().apply { root = null }
        picker.select()
        assertEquals(listOf("foreground", "drawer", "foreground", "lookup",
            "capture:checkpoint: primary storage root missing before configuration", "foreground",
            "menu", "foreground", "drawer", "foreground", "lookup", "foreground",
            "select:device-name-root"), picker.events)
    }

    @Test fun closedPickerNeverReceivesDrawerInteraction() {
        val picker = Picker().apply { foreground = false }
        assertThrows(AssertionError::class.java) { picker.select() }
        assertEquals(listOf("foreground",
            "capture:DocumentsUI left the foreground during root discovery"), picker.events)
    }

    @Test fun pickerClosureDuringDrawerNavigationCapturesAndStopsBeforeLookup() {
        val picker = Picker().apply { closeOnDrawer = true }
        assertThrows(AssertionError::class.java) { picker.select() }
        assertFalse(picker.events.contains("lookup"))
        assertFalse(picker.events.contains("menu"))
        assertTrue(picker.events.last().startsWith("capture:"))
    }

    @Test fun pickerClosureDuringConfigurationStopsBeforeReopeningDrawer() {
        val picker = Picker().apply { root = null; closeOnMenu = true }
        assertThrows(AssertionError::class.java) { picker.select() }
        assertEquals(1, picker.events.count { it == "drawer" })
        assertFalse(picker.events.any { it.startsWith("select:") })
        assertTrue(picker.events.last().startsWith("capture:"))
    }

    @Test fun stillMissingRootIsCapturedWithoutSelectingAnAlternativeProvider() {
        val picker = Picker().apply { root = null; configuredRoot = null }
        val failure = assertThrows(AssertionError::class.java) { picker.select() }
        assertEquals("Primary storage root unavailable after configuration", failure.message)
        assertEquals(2, picker.events.count { it.startsWith("capture:") })
        assertFalse(picker.events.any { it.startsWith("select:") })
    }

    @Test fun unavailableMenuCannotFallThroughToFolderSelection() {
        val picker = Picker().apply { root = null; menuFailure = true }
        val failure = assertThrows(AssertionError::class.java) { picker.select() }
        assertEquals("Missing UI: overflow", failure.message)
        assertFalse(picker.events.any { it.startsWith("select:") })
        assertEquals(1, picker.events.count { it == "drawer" })
    }

    @Test fun unavailableCheckpointCaptureStopsBeforeChangingTheMissingRootScreen() {
        var navigated = false
        assertThrows(IllegalStateException::class.java) {
            selectSafPrimaryRoot<String>({ true }, {}, { null }, { navigated = true }, {},
                { throw IllegalStateException("evidence storage unavailable") })
        }
        assertFalse(navigated)
    }

    @Test fun closureCaptureErrorDoesNotReplaceTheOriginalAssertion() {
        val captureError = IllegalStateException("capture unavailable")
        val failure = assertThrows(AssertionError::class.java) {
            selectSafPrimaryRoot<String>({ false }, {}, { null }, {}, {}, { throw captureError })
        }
        assertEquals("DocumentsUI left the foreground during root discovery", failure.message)
        assertSame(captureError, failure.suppressed.single())
    }

    @Test fun missingRootCaptureErrorPreservesTheOriginalAssertion() {
        var captures = 0
        val failure = assertThrows(AssertionError::class.java) {
            selectSafPrimaryRoot<String>({ true }, {}, { null }, {}, {}, {
                if (++captures == 2) throw IllegalStateException("capture unavailable")
            })
        }
        assertEquals("Primary storage root unavailable after configuration", failure.message)
        assertEquals("capture unavailable", failure.suppressed.single().message)
    }
}
