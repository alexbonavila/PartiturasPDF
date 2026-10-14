package com.abdev.partituraspdf.data.preferences

import androidx.datastore.preferences.core.*
import com.abdev.partituraspdf.core.preferences.*
import org.junit.Assert.*
import org.junit.Test

class PreferencesMappingTest {
    @Test fun absentKeysProduceOnlyDocumentedDefaults() {
        assertEquals(AppPreferences(), PreferencesMapping.read(emptyPreferences()))
        assertEquals("theme_selection", PreferencesMapping.theme.name)
        assertEquals("language_selection", PreferencesMapping.language.name)
        assertEquals("reader_page_fit", PreferencesMapping.pageFit.name)
    }

    @Test fun everyStableValueRoundTripsExactlyAndUpdatesPreserveAllOtherKeys() {
        val extra = stringPreferencesKey("future_feature")
        for (theme in ThemeSelection.entries) for (language in LanguageSelection.entries) for (fit in PageFit.entries) {
            val values = mutablePreferencesOf(extra to "unchanged")
            PreferencesMapping.apply(values, PreferenceChange.Theme(theme))
            PreferencesMapping.apply(values, PreferenceChange.Language(language))
            PreferencesMapping.apply(values, PreferenceChange.Fit(fit))
            assertEquals(theme.key, values[PreferencesMapping.theme])
            assertEquals(language.key, values[PreferencesMapping.language])
            assertEquals(fit.key, values[PreferencesMapping.pageFit])
            assertEquals(AppPreferences(theme, language, ReaderPreferences(fit)), PreferencesMapping.read(values))
            assertEquals("unchanged", values[extra])
            val before = values.toPreferences()
            PreferencesMapping.apply(values, PreferenceChange.Fit(fit))
            assertEquals(before, values)
        }
        assertEquals(listOf("system", "light", "dark"), ThemeSelection.entries.map { it.key })
        assertEquals(listOf("system", "en", "ca", "es"), LanguageSelection.entries.map { it.key })
        assertEquals(listOf("page", "width"), PageFit.entries.map { it.key })
    }

    @Test fun unknownStringsAndWrongTypesNeverDefaultOrGetRepairedByUpdates() {
        for (key in listOf(PreferencesMapping.theme, PreferencesMapping.language, PreferencesMapping.pageFit)) {
            for (invalid in listOf("future", "", "SYSTEM", "0")) {
                val values = mutablePreferencesOf(key to invalid)
                assertThrows(IllegalStateException::class.java) { PreferencesMapping.read(values) }
                val before = values.toPreferences()
                for (change in listOf(PreferenceChange.Theme(ThemeSelection.DARK),
                    PreferenceChange.Language(LanguageSelection.SPANISH), PreferenceChange.Fit(PageFit.WIDTH))) {
                    assertThrows(IllegalStateException::class.java) { PreferencesMapping.apply(values, change) }
                    assertEquals(before, values)
                }
            }
            val wrongType = mutablePreferencesOf(intPreferencesKey(key.name) to 3)
            assertThrows(ClassCastException::class.java) { PreferencesMapping.read(wrongType) }
            assertThrows(ClassCastException::class.java) { PreferencesMapping.apply(wrongType, PreferenceChange.Theme(ThemeSelection.DARK)) }
            assertEquals(3, wrongType[intPreferencesKey(key.name)])
        }
    }
}
