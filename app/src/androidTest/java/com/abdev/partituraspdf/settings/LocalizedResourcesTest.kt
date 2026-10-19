package com.abdev.partituraspdf.settings

import android.content.res.Configuration
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.abdev.partituraspdf.R
import com.abdev.partituraspdf.core.foundation.DeliveredLanguages
import com.abdev.partituraspdf.core.foundation.DeviceLocalePolicy
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** Scoped resource contexts only: never changes device locale or Activity lifecycle. */
@RunWith(AndroidJUnit4::class)
class LocalizedResourcesTest {
    private fun verify(tag: String, language: String, greeting: String, library: String, reader: String, settings: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val configuration = Configuration(context.resources.configuration).apply { setLocale(Locale.forLanguageTag(tag)) }
        val resources = context.createConfigurationContext(configuration).resources
        assertEquals(language, DeviceLocalePolicy.resolve(listOf(tag), DeliveredLanguages(english = true, catalan = true)))
        assertEquals(greeting, resources.getString(R.string.starter_greeting, "Android"))
        assertEquals(library, resources.getString(R.string.library_title))
        assertEquals(reader, resources.getString(R.string.reader_page_counter, 2, 7))
        assertEquals(settings, resources.getString(R.string.settings_title))
    }

    @Test fun spanishResourcesResolveWithoutChangingGlobalLocale() {
        verify("es-ES", "es", "¡Hola, Android!", "Biblioteca de partituras", "Página 2 de 7", "Ajustes")
    }
    @Test fun catalanResourcesResolveWithoutChangingGlobalLocale() {
        verify("ca-ES", "ca", "Hola, Android!", "Biblioteca de partitures", "Pàgina 2 de 7", "Configuració")
    }
    @Test fun englishResourcesResolveWithoutChangingGlobalLocale() {
        verify("en-US", "en", "Hello Android!", "Score library", "Page 2 of 7", "Settings")
    }
    @Test fun unsupportedLocaleUsesSpanishDefaultResources() {
        verify("fr-FR", "es", "¡Hola, Android!", "Biblioteca de partituras", "Página 2 de 7", "Ajustes")
    }
}
