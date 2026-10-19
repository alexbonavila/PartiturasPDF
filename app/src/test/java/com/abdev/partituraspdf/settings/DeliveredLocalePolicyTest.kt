package com.abdev.partituraspdf.settings

import com.abdev.partituraspdf.core.foundation.DeliveredLanguages
import com.abdev.partituraspdf.core.foundation.DeviceLocalePolicy
import org.junit.Assert.assertEquals
import org.junit.Test

/** Verifies the existing policy with complete delivery; does not install a host override. */
class DeliveredLocalePolicyTest {
    private val delivered = DeliveredLanguages(english = true, catalan = true)

    @Test fun deliveredRegionalLanguagesUseTheirOwnBundles() {
        for ((tag, expected) in listOf("es-ES" to "es", "ca-ES" to "ca", "en-GB" to "en", "EN_us" to "en")) {
            assertEquals(tag, expected, DeviceLocalePolicy.resolve(listOf(tag), delivered))
        }
    }

    @Test fun unsupportedPrimaryLanguageFallsBackToSpanishRatherThanSecondaryEnglish() {
        for (tags in listOf(emptyList(), listOf("fr-FR"), listOf("de-DE", "en-US"), listOf(""))) {
            assertEquals(tags.toString(), "es", DeviceLocalePolicy.resolve(tags, delivered))
        }
    }

    @Test fun completedBundlesDoNotSilentlyChangeUndeliveredPolicyDefaults() {
        assertEquals("es", DeviceLocalePolicy.resolve(listOf("en-US")))
        assertEquals("es", DeviceLocalePolicy.resolve(listOf("ca-ES")))
    }
}
