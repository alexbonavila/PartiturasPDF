package com.abdev.partituraspdf.ui.components

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.abdev.partituraspdf.MainActivity
import com.abdev.partituraspdf.ui.theme.PartiturasPDFTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FeedbackPanelTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun messageWithoutActionIsDisplayedAndHasNoClickAction() {
        render { FeedbackPanel(message = "Example message") }

        compose.onNodeWithText("Example message").assertIsDisplayed()
        compose.onAllNodes(hasClickAction()).assertCountEquals(0)
    }

    @Test
    fun enabledActionInvokesCallbackOncePerClick() {
        var clicks = 0
        render {
            FeedbackPanel("Example message", actionLabel = "Try again", onActionClick = { clicks++ })
        }

        compose.onNodeWithText("Example message").assertIsDisplayed()
        val button = compose.onNodeWithText("Try again")
        button.assertIsDisplayed().assertIsEnabled().assertHasClickAction()
        button.performClick()
        compose.runOnIdle { assertEquals(1, clicks) }
        button.performClick()
        compose.runOnIdle { assertEquals(2, clicks) }
    }

    @Test
    fun disabledActionExposesDisabledStateAndIgnoresTouch() {
        var clicks = 0
        render {
            FeedbackPanel(
                "Example message", actionLabel = "Try again", actionEnabled = false,
                onActionClick = { clicks++ },
            )
        }

        compose.onNodeWithText("Example message").assertIsDisplayed()
        compose.onNodeWithText("Try again")
            .assertIsDisplayed().assertIsNotEnabled().performTouchInput { click() }
        compose.runOnIdle { assertEquals(0, clicks) }
    }

    @Test
    fun labelWithoutCallbackIsRejectedDuringComposition() {
        assertInvalidAction(label = "Try again", callback = null)
    }

    @Test
    fun callbackWithoutLabelIsRejectedDuringComposition() {
        assertInvalidAction(label = null, callback = {})
    }

    @Test
    fun recompositionDoesNotInvokeCallbacksAndClickUsesCurrentCallback() {
        val updated = mutableStateOf(false)
        var originalClicks = 0
        var currentClicks = 0
        render {
            FeedbackPanel(
                message = if (updated.value) "Updated message" else "Original message",
                actionLabel = "Try again",
                onActionClick = if (updated.value) ({ currentClicks++ }) else ({ originalClicks++ }),
            )
        }

        compose.runOnIdle {
            assertEquals(0, originalClicks)
            assertEquals(0, currentClicks)
            updated.value = true
        }
        compose.onNodeWithText("Updated message").assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(0, originalClicks)
            assertEquals(0, currentClicks)
        }
        compose.onNodeWithText("Try again").performClick()
        compose.runOnIdle {
            assertEquals(0, originalClicks)
            assertEquals(1, currentClicks)
        }
    }

    @Test
    fun longTextWrapsInNarrowScrollableViewport() {
        assertResponsiveContent(width = 240, fontScale = 1f)
    }

    @Test
    fun longTextWrapsInWidestAvailableViewport() {
        assertResponsiveContent(width = 800, fontScale = 1f)
    }

    @Test
    fun increasedFontScaleKeepsTextCompleteAndActionReachable() {
        assertResponsiveContent(width = 320, fontScale = 2f)
    }

    @Test
    fun lightThemeSupportsFeedbackAndAccessibleForegroundContrast() {
        assertTheme(dark = false)
    }

    @Test
    fun darkThemeSupportsFeedbackAndAccessibleForegroundContrast() {
        assertTheme(dark = true)
    }

    @Test
    fun messageAndButtonExposeSeparateSemanticsWithoutDescriptions() {
        val enabled = mutableStateOf(true)
        render {
            FeedbackPanel(
                "Example message", actionLabel = "Try again",
                actionEnabled = enabled.value, onActionClick = {},
            )
        }

        val message = compose.onNodeWithText("Example message")
        val button = compose.onNodeWithText("Try again")
        message.assertIsDisplayed().assert(
            SemanticsMatcher.keyNotDefined(SemanticsActions.OnClick),
        )
        button.assertHasClickAction().assertIsEnabled().assert(
            SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button),
        ).assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
        assertTrue(message.fetchSemanticsNode().id != button.fetchSemanticsNode().id)
        compose.onAllNodes(
            SemanticsMatcher.keyIsDefined(SemanticsProperties.ContentDescription),
            useUnmergedTree = true,
        ).assertCountEquals(0)
        compose.runOnIdle { enabled.value = false }
        button.assertIsNotEnabled()
    }

    @Test
    fun libraryEmptyFolderInputSupportsChooseFolderAction() {
        assertConsumer("This folder is empty.", "Choose another folder")
    }

    @Test
    fun readerOpeningFailureInputSupportsRetryAction() {
        assertConsumer("The PDF could not be opened. Try again.", "Retry")
    }

    @Test
    fun alreadyLocalizedEnglishCatalanAndSpanishInputsRemainAccessible() {
        // Caller-supplied strings only; this does not test future resource integration.
        val inputs = listOf(
            "This folder is empty." to "Choose another folder",
            "Aquesta carpeta és buida." to "Tria una altra carpeta",
            "Esta carpeta está vacía." to "Elige otra carpeta",
        )
        val selected = mutableStateOf(0)
        var clicks = 0
        render {
            val (message, label) = inputs[selected.value]
            FeedbackPanel(message, actionLabel = label, onActionClick = { clicks++ })
        }
        inputs.forEachIndexed { index, (message, label) ->
            compose.runOnIdle { selected.value = index }
            compose.onNodeWithText(message).assertIsDisplayed()
            compose.onNodeWithText(label).assertIsDisplayed().performClick()
            compose.runOnIdle { assertEquals(index + 1, clicks) }
        }
    }

    private fun render(content: @Composable () -> Unit) {
        // MainActivity already sets content; replace it using the foundation convention.
        compose.runOnUiThread {
            compose.activity.setContent {
                PartiturasPDFTheme { Surface { content() } }
            }
        }
        compose.waitForIdle()
    }

    private fun assertInvalidAction(label: String?, callback: (() -> Unit)?) {
        val invalid = mutableStateOf(false)
        render {
            FeedbackPanel(
                "Example message",
                actionLabel = if (invalid.value) label else null,
                onActionClick = if (invalid.value) callback else null,
            )
        }
        // Include the rule's idle boundary: recomposition errors are asynchronous and
        // are surfaced by Compose synchronization, not by the state assignment alone.
        val error = assertThrows(IllegalArgumentException::class.java) {
            compose.runOnIdle { invalid.value = true }
            compose.waitForIdle()
        }
        assertTrue(error.message.orEmpty().contains("actionLabel"))
        assertTrue(error.message.orEmpty().contains("onActionClick"))
    }

    private fun assertResponsiveContent(width: Int, fontScale: Float) {
        val message = (1..12).joinToString("\n") { "Line $it: Feedback remains readable across container sizes." }
        var clicks = 0
        render {
            BoxWithConstraints {
                val viewportWidth = minOf(width.dp, maxWidth)
                val originalDensity = LocalDensity.current
                // Preserve the real emulator pixel density. Override font scale only.
                CompositionLocalProvider(
                    LocalDensity provides Density(originalDensity.density, fontScale),
                ) {
                    Column(
                        Modifier.width(viewportWidth).heightIn(max = 240.dp)
                            .verticalScroll(rememberScrollState()),
                    ) {
                        FeedbackPanel(message, actionLabel = "Try again", onActionClick = { clicks++ })
                    }
                }
            }
        }
        compose.onNodeWithText(message).assertIsDisplayed()
        assertCompleteText(message)
        compose.onNodeWithText("Try again").performScrollTo().assertIsDisplayed().performClick()
        assertCompleteText("Try again")
        compose.runOnIdle { assertEquals(1, clicks) }
    }

    private fun assertCompleteText(text: String) {
        val results = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(text, useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }

        assertEquals(1, results.size)
        val layout = results.single()
        assertEquals(text, layout.layoutInput.text.text)
        assertFalse("Text must not overflow vertically", layout.didOverflowHeight)
        assertTrue("Text must contain at least one line", layout.lineCount > 0)
        assertEquals(
            "Every text character must be included in the layout",
            text.length,
            layout.getLineEnd(layout.lineCount - 1),
        )
        for (line in 0 until layout.lineCount) {
            assertFalse("Text must not be ellipsized", layout.isLineEllipsized(line))
        }
    }

    private fun assertTheme(dark: Boolean) {
        var messageContrast = 0f
        var actionContrast = 0f
        var clicks = 0
        compose.runOnUiThread {
            compose.activity.setContent {
                PartiturasPDFTheme(darkTheme = dark) {
                    val colors = MaterialTheme.colorScheme
                    messageContrast = contrast(colors.onSurface, colors.surface)
                    actionContrast = contrast(colors.onPrimary, colors.primary)
                    Surface {
                        FeedbackPanel("Example message", actionLabel = "Try again", onActionClick = { clicks++ })
                    }
                }
            }
        }
        compose.onNodeWithText("Example message").assertIsDisplayed()
        compose.onNodeWithText("Try again").assertIsDisplayed().performClick()
        compose.runOnIdle {
            assertEquals(1, clicks)
            assertTrue("Message contrast must reach 4.5:1", messageContrast >= 4.5f)
            assertTrue("Action contrast must reach 4.5:1", actionContrast >= 4.5f)
        }
    }

    private fun contrast(foreground: Color, background: Color): Float {
        val first = foreground.luminance()
        val second = background.luminance()
        return (maxOf(first, second) + 0.05f) / (minOf(first, second) + 0.05f)
    }

    private fun assertConsumer(message: String, label: String) {
        var clicks = 0
        render { FeedbackPanel(message, actionLabel = label, onActionClick = { clicks++ }) }
        compose.onNodeWithText(message).assertIsDisplayed()
        compose.runOnIdle { assertEquals(0, clicks) }
        compose.onNodeWithText(label).assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(1, clicks) }
    }
}
