package com.abdev.partituraspdf.app.integration

import android.content.res.Configuration
import android.os.LocaleList
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.abdev.partituraspdf.MainActivity
import com.abdev.partituraspdf.R
import com.abdev.partituraspdf.app.host.AppHostViewModel
import com.abdev.partituraspdf.app.host.AppHostState
import com.abdev.partituraspdf.app.localization.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

/** Real resource contexts and Activity recreation; no device/global-language mutation. */
@RunWith(AndroidJUnit4::class)
class AppLocaleIntegrationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun deliveredPrimaryLanguagesResolveRealNavigationFeatureAndAccessibilityResources() {
        val expected = mapOf(
            "es" to listOf("Ajustes", "Biblioteca de partituras", "Siguiente", "Volver a la biblioteca"),
            "ca" to listOf("Configuració", "Biblioteca de partitures", "Següent", "Torna a la biblioteca"),
            "en" to listOf("Settings", "Score library", "Next", "Back to library"),
        )
        val keys = listOf(R.string.app_settings, R.string.library_title, R.string.reader_next, R.string.app_back)
        for ((tag, language) in listOf("es-ES" to "es", "es-MX" to "es", "ca-ES" to "ca", "en-US" to "en", "en-GB" to "en")) {
            val local = localizedActivityContext(context, appLanguage(listOf(tag)))
            assertEquals(language, local.resources.configuration.locales[0].language)
            assertEquals(expected[language], keys.map(local::getString))
            assertEquals(local.getString(R.string.app_settings), local.getString(R.string.settings_title))
            assertFalse(local.getString(R.string.library_open_description,"score.pdf").isBlank())
        }
    }
    @Test fun unsupportedPrimaryNeverUsesSupportedSecondaryAndEmptyInputsUseSpanishResources() {
        for (tags in listOf(listOf("fr-FR","en-US"),listOf("de-DE","ca-ES"),emptyList(),listOf(""))) {
            val local=localizedActivityContext(context,appLanguage(tags))
            assertEquals("es",local.resources.configuration.locales[0].language)
            assertEquals("Ajustes",local.getString(R.string.settings_title))
            assertEquals("Opciones de lectura",local.getString(R.string.reader_options))
        }
    }
    @Test fun configurationContextPreservesNightOrientationDensityFontScaleAndLeavesBaseAndGlobalUntouched() {
        val global=Locale.getDefault(); val baseConfig=Configuration(context.resources.configuration)
        val configured=Configuration(baseConfig).apply {
            orientation=Configuration.ORIENTATION_LANDSCAPE; fontScale=1.8f; densityDpi=240
            uiMode=(uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or Configuration.UI_MODE_NIGHT_YES
        }
        val base=context.createConfigurationContext(configured)
        val beforeBase=Configuration(base.resources.configuration)
        val local=localizedActivityContext(base,appLanguage(listOf("ca-ES")))
        val result=local.resources.configuration
        assertEquals(Configuration.ORIENTATION_LANDSCAPE,result.orientation); assertEquals(1.8f,result.fontScale,0f)
        assertEquals(240,result.densityDpi); assertEquals(Configuration.UI_MODE_NIGHT_YES,result.uiMode and Configuration.UI_MODE_NIGHT_MASK)
        assertEquals(beforeBase,base.resources.configuration); assertEquals(baseConfig,context.resources.configuration)
        assertEquals(global,Locale.getDefault())
    }
    @Test fun productionActivityUsesActualSystemPolicyAndRecreationRetainsHostAndLocalizedControls() {
        var old: AppHostViewModel?=null
        compose.activityRule.scenario.onActivity { old=ViewModelProvider(it)[AppHostViewModel::class.java] }
        compose.waitUntil(15_000) { old!!.state.value is AppHostState.Ready }
        assertEquals(systemAppLanguage(),compose.activity.resources.configuration.locales[0].language)
        compose.onNodeWithTag("app_settings").performClick()
        compose.activityRule.scenario.recreate(); compose.waitForIdle()
        compose.activityRule.scenario.onActivity {
            assertSame(old,ViewModelProvider(it)[AppHostViewModel::class.java])
            assertEquals(systemAppLanguage(),it.resources.configuration.locales[0].language)
        }
        compose.onNodeWithTag("settings_content").assertExists()
        compose.onNodeWithText(compose.activity.getString(R.string.app_back)).assertHasClickAction()
    }
    @Test fun contextualRecoveryMessagesStayTranslatedAndDistinctWithoutExposingTechnicalPaths() {
        val keys=listOf(R.string.reader_error_revoked,R.string.reader_error_permission,R.string.reader_error_provider,
            R.string.reader_error_io,R.string.reader_error_deleted,R.string.reader_error_password,R.string.app_reader_unavailable)
        for (language in listOf("es","ca","en")) {
            val local=localizedActivityContext(context,appLanguage(listOf(language)))
            val messages=keys.map {local.getString(it)}
            assertEquals(keys.size,messages.toSet().size)
            messages.forEach {assertFalse(it.isBlank());assertFalse(it.contains("content://"));assertFalse(it.contains("java.lang"))}
        }
    }

}
