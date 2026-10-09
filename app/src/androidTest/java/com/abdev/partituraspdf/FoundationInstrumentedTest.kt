package com.abdev.partituraspdf

import android.content.res.Configuration
import android.graphics.Bitmap
import android.os.LocaleList
import android.util.Base64
import android.util.Log
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.abdev.partituraspdf.ui.theme.PartiturasPDFTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale
import java.io.ByteArrayOutputStream

@RunWith(AndroidJUnit4::class)
class FoundationInstrumentedTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun starterActivityLaunchesWithResourceGreeting() {
        val context = compose.activity
        val greeting = context.getString(
            R.string.starter_greeting, context.getString(R.string.starter_recipient)
        )
        compose.onNodeWithText(greeting).assertIsDisplayed()
    }

    @Test
    fun localizedGreetingIsAccessibleInBothThemesWithEnglishFallback() {
        val greetings = mapOf(
            "en" to "Hello Android!",
            "ca" to "Hola, Android!",
            "es" to "¡Hola, Android!",
            "fr" to "Hello Android!",
        )
        for ((language, expected) in greetings) {
            for (dark in listOf(false, true)) {
                val configuration = Configuration(compose.activity.resources.configuration)
                configuration.setLocales(LocaleList(Locale.forLanguageTag(language)))
                val context = compose.activity.createConfigurationContext(configuration)
                compose.runOnUiThread {
                    compose.activity.setContent {
                        CompositionLocalProvider(
                            LocalContext provides context,
                            LocalConfiguration provides configuration,
                        ) {
                            PartiturasPDFTheme(darkTheme = dark) {
                                Scaffold(modifier = Modifier.fillMaxSize()) { padding ->
                                    Greeting(
                                        name = stringResource(R.string.starter_recipient),
                                        modifier = Modifier.padding(padding),
                                    )
                                }
                            }
                        }
                    }
                }
                // Text semantics expose the localized greeting to accessibility services.
                compose.onNodeWithText(expected).assertIsDisplayed()
                if (language != "fr") recordScreenshot(language, dark)
            }
        }
    }

    @Test
    fun bothThemesKeepBrandColorsAndAccessibleForegrounds() {
        for (dark in listOf(false, true)) {
            var scheme: ColorScheme? = null
            compose.runOnUiThread {
                compose.activity.setContent {
                    PartiturasPDFTheme(darkTheme = dark) {
                        scheme = MaterialTheme.colorScheme
                    }
                }
            }
            compose.waitForIdle()
            val colors = requireNotNull(scheme)
            assertEquals(Color(0xFFA5D6A7), colors.primary)
            assertEquals(Color(0xFF90CAF9), colors.secondary)
            val textPairs = listOf(
                colors.onPrimary to colors.primary,
                colors.onPrimaryContainer to colors.primaryContainer,
                colors.onSecondary to colors.secondary,
                colors.onSecondaryContainer to colors.secondaryContainer,
                colors.onTertiary to colors.tertiary,
                colors.onTertiaryContainer to colors.tertiaryContainer,
                colors.onError to colors.error,
                colors.onErrorContainer to colors.errorContainer,
                colors.onBackground to colors.background,
                colors.onSurfaceVariant to colors.surfaceVariant,
                colors.inverseOnSurface to colors.inverseSurface,
            )
            for ((foreground, background) in textPairs) {
                assertContrast(foreground, background, 4.5f)
            }
            val surfaces = listOf(
                colors.surface, colors.surfaceBright, colors.surfaceDim,
                colors.surfaceContainerLowest, colors.surfaceContainerLow,
                colors.surfaceContainer, colors.surfaceContainerHigh,
                colors.surfaceContainerHighest,
            )
            for (surface in surfaces) assertContrast(colors.onSurface, surface, 4.5f)
            assertContrast(colors.outline, colors.surface, 3f)
            assertContrast(colors.outlineVariant, colors.surface, 3f)
        }
    }

    private fun assertContrast(foreground: Color, background: Color, minimum: Float) {
        val first = foreground.luminance()
        val second = background.luminance()
        val ratio = (maxOf(first, second) + 0.05f) / (minOf(first, second) + 0.05f)
        assertTrue("Contrast $ratio must be at least $minimum", ratio >= minimum)
    }

    private fun recordScreenshot(language: String, dark: Boolean) {
        // Synthetic starter UI only. CI retains these chunks in test logcat reports.
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        val output = ByteArrayOutputStream()
        assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
        val encoded = Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP)
        val id = "${language}_${if (dark) "dark" else "light"}"
        encoded.chunked(2000).forEachIndexed { index, chunk ->
            Log.i("FoundationScreenshot", "$id:$index:$chunk")
        }
    }
}
