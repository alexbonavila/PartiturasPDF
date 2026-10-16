package com.abdev.partituraspdf.storage.saf

import org.junit.Assert.*
import org.junit.Test

class SafUiReadinessTest {
    @Test fun visibleButDisabledControlIsNotReady() {
        var clock = 0L
        val result = awaitSafUi("select-button", 100, { clock }, { clock += 10 },
            find = { "button" }, ready = { clock >= 30 }, capture = { fail("Unexpected capture") })
        assertEquals("button", result)
        assertEquals(30L, clock)
    }

    @Test fun waitsForActivityAndWindowBeforeAcceptingTheButton() {
        var clock = 0L
        val result = awaitSafUi("resumed focused SAF activity", 100, { clock }, { clock += 10 },
            find = { if (clock >= 20) "button" else null },
            ready = { clock >= 40 }, capture = { fail("Unexpected capture") })
        assertEquals("button", result)
        assertEquals(40L, clock)
    }

    @Test fun missingSelectorCapturesBeforeReportingOriginalFailure() {
        var clock = 0L
        val events = mutableListOf<String>()
        val failure = assertThrows(AssertionError::class.java) {
            awaitSafUi<String>("resource-id=missing", 20, { clock }, { clock += 10 },
                find = { null }, capture = { events += "capture:$it" })
        }
        events += "reported"
        assertEquals(listOf("capture:resource-id=missing", "reported"), events)
        assertEquals("Missing UI: resource-id=missing", failure.message)
        assertEquals(20L, clock)
    }

    @Test fun failedCaptureCannotReplaceTheSelectorFailure() {
        val captureError = IllegalStateException("screenshot unavailable")
        val failure = assertThrows(AssertionError::class.java) {
            awaitSafUi<String>("missing", 0, { 0L }, { fail("Must not poll after deadline") },
                find = { null }, capture = { throw captureError })
        }
        assertEquals("Missing UI: missing", failure.message)
        assertSame(captureError, failure.suppressed.single())
    }

    @Test fun immediateInteractiveControlNeedsNoPollingOrCapture() {
        assertEquals("button", awaitSafUi("button", 0, { 0L }, { fail("Unexpected polling") },
            find = { "button" }, capture = { fail("Unexpected capture") }))
    }

    @Test fun negativeTimeoutIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            awaitSafUi<String>("button", -1, { 0L }, {}, find = { null }, capture = {})
        }
    }
}
