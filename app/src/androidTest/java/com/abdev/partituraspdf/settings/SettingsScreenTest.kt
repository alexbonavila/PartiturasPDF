package com.abdev.partituraspdf.settings

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.abdev.partituraspdf.MainActivity
import com.abdev.partituraspdf.R
import com.abdev.partituraspdf.core.foundation.SettingsEffect
import com.abdev.partituraspdf.core.preferences.*
import com.abdev.partituraspdf.core.result.*
import com.abdev.partituraspdf.ui.theme.PartiturasPDFTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Stateless Compose smoke tests. No production persistence, navigation or controller is started. */
@RunWith(AndroidJUnit4::class)
class SettingsScreenTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val current = mutableStateOf(SettingsUiState())
    private val actions = mutableListOf<SettingsAction>()
    private val callback = mutableStateOf<(SettingsAction) -> Unit>({ action -> actions.add(action); Unit })
    private val dark = SettingsEffect.Theme(ThemeSelection.DARK)
    private val width = SettingsEffect.Fit(PageFit.WIDTH)
    private val storage = AppError.Access.PRIVATE_STORAGE_UNAVAILABLE

    private fun ready(theme: ThemeSelection = ThemeSelection.SYSTEM, fit: PageFit = PageFit.PAGE) =
        SettingsUiState(Observation.Ready(AppPreferences(theme = theme, reader = ReaderPreferences(fit))))

    private fun render(
        state: SettingsUiState,
        darkAppearance: Boolean = false,
        largeText: Boolean = false,
        narrow: Boolean = false,
        shortViewport: Boolean = false,
    ) {
        current.value = state
        compose.runOnUiThread {
            compose.activity.setContent {
                PartiturasPDFTheme(darkTheme = darkAppearance) {
                    val density = LocalDensity.current
                    // Change fontScale alone; never squeeze a simulated tablet using fake density.
                    CompositionLocalProvider(LocalDensity provides Density(
                        density.density, if (largeText) 2f else density.fontScale,
                    )) {
                        val width = if (narrow) Modifier.widthIn(max = 320.dp) else Modifier
                        val height = if (shortViewport) Modifier.heightIn(max = 240.dp) else Modifier
                        Box(width.then(height).fillMaxSize()) {
                            SettingsScreen(current.value, callback.value)
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    private fun node(tag: String) = compose.onNodeWithTag(tag).performScrollTo()
    private fun text(id: Int) = compose.activity.getString(id)
    private fun noActions() = compose.runOnIdle { assertTrue(actions.isEmpty()) }

    @Test fun loadingHasAccessibleProgressAndNoEnabledOrSelectedDefaults() {
        render(SettingsUiState())
        node("settings_loading").assert(SemanticsMatcher.expectValue(
            SemanticsProperties.ProgressBarRangeInfo, ProgressBarRangeInfo.Indeterminate,
        ))
        compose.onAllNodes(isSelected()).assertCountEquals(0)
        for (tag in listOf("settings_theme_system", "settings_theme_light", "settings_theme_dark",
            "settings_fit_page", "settings_fit_width")) node(tag).assertIsNotEnabled().assertIsNotSelected()
        noActions()
    }

    @Test fun readyShowsActualSelectionsAndSingleSelectionRadioSemantics() {
        render(ready(ThemeSelection.DARK, PageFit.WIDTH))
        node("settings_theme_dark").assertIsSelected()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
        node("settings_theme_system").assertIsNotSelected()
        node("settings_theme_light").assertIsNotSelected()
        node("settings_fit_width").assertIsSelected()
        node("settings_fit_page").assertIsNotSelected()
        compose.onAllNodes(isSelected()).assertCountEquals(2)
        compose.onNodeWithContentDescription(text(R.string.settings_theme_title)).assertExists()
        compose.onNodeWithContentDescription(text(R.string.settings_fit_title)).assertExists()
        noActions()
    }

    @Test fun themeSelectionDispatchesTypedActionWithoutOptimisticSelection() {
        render(ready())
        node("settings_theme_light").performClick()
        compose.runOnIdle { assertEquals(listOf(SettingsAction.SelectTheme(ThemeSelection.LIGHT)), actions) }
        node("settings_theme_system").assertIsSelected()
        node("settings_theme_light").assertIsNotSelected()
    }

    @Test fun fitSelectionDispatchesTypedActionWithoutChangingThemeOrSelection() {
        render(ready(ThemeSelection.DARK))
        node("settings_fit_width").performClick()
        compose.runOnIdle { assertEquals(listOf(SettingsAction.SelectFit(PageFit.WIDTH)), actions) }
        node("settings_fit_page").assertIsSelected()
        node("settings_theme_dark").assertIsSelected()
    }

    @Test fun clickingAlreadySelectedValuesDoesNotDispatchRedundantActions() {
        render(ready())
        node("settings_theme_system").performClick()
        node("settings_fit_page").performClick()
        noActions()
    }

    @Test fun storageReadFailureOffersRetryWithoutInventingSelections() {
        render(SettingsUiState(Observation.Failed(storage)))
        compose.onNodeWithText(text(R.string.settings_error_storage)).assertExists()
        compose.onAllNodes(isSelected()).assertCountEquals(0)
        node("settings_theme_dark").assertIsNotEnabled()
        node("settings_fit_width").assertIsNotEnabled()
        noActions()
        node("settings_retry_read").assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(listOf(SettingsAction.RetryObservation), actions) }
    }

    @Test fun otherReadFailureUsesSafeGenericMessageAndExplicitRetry() {
        render(SettingsUiState(Observation.Failed(AppError.Access.PERMISSION_DENIED)))
        compose.onNodeWithText(text(R.string.settings_error_other)).assertExists()
        compose.onNodeWithText("PERMISSION_DENIED").assertDoesNotExist()
        node("settings_retry_read").performClick()
        compose.runOnIdle { assertEquals(listOf(SettingsAction.RetryObservation), actions) }
    }

    @Test fun themeWriteFailurePreservesSelectionAndDispatchesOnlyThemeRetry() {
        render(ready().copy(themeOperation = SettingsOperation.Failed(dark, storage)))
        node("settings_theme_system").assertIsSelected()
        node("settings_theme_dark").assertIsNotSelected().assertIsNotEnabled()
        compose.onNodeWithText(text(R.string.settings_theme_write_error)).assertExists()
        noActions()
        node("settings_theme_retry").assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(listOf(SettingsAction.RetryTheme), actions) }
    }

    @Test fun fitWriteFailurePreservesSelectionAndDispatchesOnlyFitRetry() {
        render(ready().copy(fitOperation = SettingsOperation.Failed(width, storage)))
        node("settings_fit_page").assertIsSelected()
        node("settings_fit_width").assertIsNotSelected().assertIsNotEnabled()
        compose.onNodeWithText(text(R.string.settings_fit_write_error)).assertExists()
        node("settings_fit_retry").assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(listOf(SettingsAction.RetryFit), actions) }
    }

    @Test fun pendingThemeDisablesItsGroupButFitCanQueueAnIndependentIntent() {
        render(ready().copy(themeOperation = SettingsOperation.Pending(dark)))
        node("settings_theme_status").assert(SemanticsMatcher.expectValue(
            SemanticsProperties.ProgressBarRangeInfo, ProgressBarRangeInfo.Indeterminate,
        ))
        node("settings_theme_system").assertIsSelected().assertIsNotEnabled()
        node("settings_theme_dark").assertIsNotSelected().assertIsNotEnabled()
        node("settings_fit_width").assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(listOf(SettingsAction.SelectFit(PageFit.WIDTH)), actions) }
    }

    @Test fun reconciliationAllowsExplicitReadRecoveryEvenWhileSnapshotIsLoading() {
        val verifying = ready().copy(themeOperation = SettingsOperation.Reconciling(dark, uncertain = true))
        render(verifying)
        node("settings_theme_system").assertIsSelected()
        node("settings_theme_dark").assertIsNotSelected().assertIsNotEnabled()
        node("settings_theme_verify").assertIsEnabled().performClick()
        compose.runOnIdle { current.value = verifying.copy(preferences = Observation.Loading) }
        node("settings_theme_verify").assertIsEnabled()
        compose.onAllNodes(isSelected()).assertCountEquals(0)
        compose.runOnIdle { assertEquals(listOf(SettingsAction.RetryObservation), actions) }
    }

    @Test fun independentOperationStatesRemainVisibleAndWriteRetryRequiresReady() {
        render(ready().copy(themeOperation = SettingsOperation.Pending(dark),
            fitOperation = SettingsOperation.Failed(width, storage)))
        node("settings_theme_status").assertExists()
        node("settings_fit_retry").assertIsEnabled()
        compose.onNodeWithText(text(R.string.settings_fit_write_error)).assertExists()
        compose.runOnIdle { current.value = current.value.copy(preferences = Observation.Failed(storage)) }
        node("settings_fit_retry").assertIsNotEnabled()
        node("settings_retry_read").assertIsEnabled()
        noActions()
    }

    @Test fun newObservedStateAndCallbackAreUsedAfterRecompositionWithoutNavigation() {
        render(ready().copy(fitOperation = SettingsOperation.Reconciling(width)))
        node("settings_fit_page").assertIsSelected()
        val replacementActions = mutableListOf<SettingsAction>()
        compose.runOnIdle {
            current.value = ready(ThemeSelection.DARK, PageFit.WIDTH)
            callback.value = { action -> replacementActions.add(action); Unit }
        }
        node("settings_fit_width").assertIsSelected()
        compose.onNodeWithTag("settings_fit_verify").assertDoesNotExist()
        node("settings_theme_light").assertIsEnabled().performClick()
        compose.runOnIdle {
            assertTrue(actions.isEmpty())
            assertEquals(listOf(SettingsAction.SelectTheme(ThemeSelection.LIGHT)), replacementActions)
        }
    }
    @Test fun defaultReadyHasOnlyTwoSelectionGroupsAndFiveRadioOptions() {
        render(ready())
        node("settings_theme").assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.SelectableGroup))
        node("settings_fit").assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.SelectableGroup))
        compose.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton)).assertCountEquals(5)
        compose.onAllNodes(hasClickAction()).assertCountEquals(5)
        node("settings_theme_system").assertIsSelected()
        node("settings_fit_page").assertIsSelected()
        for (label in listOf(R.string.settings_theme_system, R.string.settings_theme_light,
            R.string.settings_theme_dark, R.string.settings_fit_page, R.string.settings_fit_width)) {
            compose.onNodeWithText(text(label)).assertExists()
        }
        noActions()
    }

    @Test fun darkAndSystemThemeChoicesDispatchTheirExactTypedSelections() {
        render(ready(ThemeSelection.LIGHT))
        node("settings_theme_dark").performClick()
        compose.runOnIdle { current.value = ready(ThemeSelection.DARK) }
        node("settings_theme_system").performClick()
        compose.runOnIdle {
            assertEquals(listOf(SettingsAction.SelectTheme(ThemeSelection.DARK),
                SettingsAction.SelectTheme(ThemeSelection.SYSTEM)), actions)
        }
    }

    @Test fun wholePageChoiceIsTypedAndRetainsObservedWidthUntilNewState() {
        render(ready(ThemeSelection.DARK, PageFit.WIDTH))
        node("settings_fit_page").performClick()
        node("settings_fit_width").assertIsSelected()
        node("settings_theme_dark").assertIsSelected()
        compose.runOnIdle { assertEquals(listOf(SettingsAction.SelectFit(PageFit.PAGE)), actions) }
    }

    @Test fun radioRowsHaveSingleAccessibleClickTargetAndMinimumTouchBounds() {
        render(ready())
        for (tag in listOf("settings_theme_system", "settings_theme_light", "settings_theme_dark",
            "settings_fit_page", "settings_fit_width")) {
            node(tag).assertHasClickAction()
                .assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp)
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
            compose.onAllNodes(hasAnyAncestor(hasTestTag(tag)) and hasClickAction(),
                useUnmergedTree = true).assertCountEquals(0)
        }
        node("settings_theme_light").performClick()
        compose.runOnIdle { assertEquals(listOf(SettingsAction.SelectTheme(ThemeSelection.LIGHT)), actions) }
    }

    @Test fun bothErrorsHaveIndependentDiscoverableRetriesAndMinimumTouchBounds() {
        render(ready().copy(themeOperation = SettingsOperation.Failed(dark, storage),
            fitOperation = SettingsOperation.Failed(width, storage)))
        compose.onNodeWithText(text(R.string.settings_theme_write_error))
            .assert(hasAnyAncestor(hasTestTag("settings_theme")))
        compose.onNodeWithText(text(R.string.settings_fit_write_error))
            .assert(hasAnyAncestor(hasTestTag("settings_fit")))
        node("settings_theme_retry").assertHasClickAction().assertIsEnabled()
            .assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp).performClick()
        node("settings_fit_retry").assertHasClickAction().assertIsEnabled()
            .assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp).performClick()
        compose.runOnIdle { assertEquals(listOf(SettingsAction.RetryTheme, SettingsAction.RetryFit), actions) }
    }

    @Test fun progressAndReconciliationArePoliteAndRecompositionDoesNotDispatch() {
        render(ready().copy(themeOperation = SettingsOperation.Pending(dark),
            fitOperation = SettingsOperation.Reconciling(width)))
        for (tag in listOf("settings_theme_status", "settings_fit_status")) {
            node(tag).assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.ProgressBarRangeInfo,
                    ProgressBarRangeInfo.Indeterminate))
        }
        node("settings_fit_status").assertTextContains(
            compose.activity.getString(R.string.settings_reconciling, text(R.string.settings_fit_width)))
        compose.runOnIdle {
            current.value = current.value.copy(preferences = Observation.Ready(
                AppPreferences(language = LanguageSelection.CATALAN)))
        }
        node("settings_theme_system").assertIsSelected()
        node("settings_fit_page").assertIsSelected()
        noActions()
    }

    @Test fun failedThemeTargetRequiresRetryWhileAnotherSelectionRemainsAvailable() {
        render(ready().copy(themeOperation = SettingsOperation.Failed(dark, storage)))
        node("settings_theme_dark").assertIsNotEnabled()
        node("settings_theme_light").assertIsEnabled().performClick()
        node("settings_theme_retry").assertIsEnabled()
        node("settings_fit_width").assertIsEnabled()
        compose.runOnIdle { assertEquals(listOf(SettingsAction.SelectTheme(ThemeSelection.LIGHT)), actions) }
    }

    @Test fun narrowLargeTextKeepsCompleteLabelsAndRetryControlsReachable() {
        render(ready().copy(themeOperation = SettingsOperation.Failed(dark, storage),
            fitOperation = SettingsOperation.Failed(width, storage)), largeText = true, narrow = true)
        for ((tag, label) in listOf(
            "settings_theme_system" to R.string.settings_theme_system,
            "settings_theme_light" to R.string.settings_theme_light,
            "settings_theme_dark" to R.string.settings_theme_dark,
            "settings_fit_page" to R.string.settings_fit_page,
            "settings_fit_width" to R.string.settings_fit_width,
        )) node(tag).assertTextContains(text(label)).assertIsDisplayed().assertHeightIsAtLeast(48.dp)
        node("settings_theme_light").assertIsEnabled().performClick()
        node("settings_fit_retry").assertTextContains(text(R.string.settings_retry_fit))
            .assertIsDisplayed().assertHeightIsAtLeast(48.dp).performClick()
        compose.runOnIdle { assertEquals(listOf(SettingsAction.SelectTheme(ThemeSelection.LIGHT),
            SettingsAction.RetryFit), actions) }
        // Semantics/reachability are covered here; actual glyph clipping still needs visual review.
    }

    @Test fun shortViewportCanScrollToBottomRetryAndBackToThemeHeading() {
        render(ready().copy(fitOperation = SettingsOperation.Failed(width, storage)),
            shortViewport = true, darkAppearance = true)
        compose.onNodeWithTag("settings_content").assert(hasScrollAction())
        node("settings_fit_retry").assertIsDisplayed().performClick()
        compose.onNodeWithText(text(R.string.settings_theme_title)).performScrollTo().assertIsDisplayed()
        node("settings_theme_light").assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(listOf(SettingsAction.RetryFit,
            SettingsAction.SelectTheme(ThemeSelection.LIGHT)), actions) }
        // A constrained short viewport is not certification of physical landscape/tablet behavior.
    }

    @OptIn(ExperimentalTestApi::class)
    @Test fun keyboardEnterActivatesFocusedRadioRowAndRetry() {
        render(ready().copy(themeOperation = SettingsOperation.Failed(dark, storage)))
        val light = node("settings_theme_light")
        light.performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        light.assertIsFocused().performKeyInput { pressKey(Key.Enter) }
        val retry = node("settings_theme_retry")
        retry.performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        retry.assertIsFocused().performKeyInput { pressKey(Key.Enter) }
        compose.runOnIdle { assertEquals(listOf(SettingsAction.SelectTheme(ThemeSelection.LIGHT),
            SettingsAction.RetryTheme), actions) }
    }

    @Test fun explicitRemountAfterActivityRecreationUsesSuppliedStateWithoutSideEffects() {
        val supplied = ready(ThemeSelection.DARK, PageFit.WIDTH)
        render(supplied, darkAppearance = true)
        noActions()
        compose.activityRule.scenario.recreate()
        render(supplied, darkAppearance = true) // Test host explicitly injects the same immutable state.
        node("settings_theme_dark").assertIsSelected()
        node("settings_fit_width").assertIsSelected()
        noActions()
        node("settings_theme_light").performClick()
        compose.runOnIdle { assertEquals(listOf(SettingsAction.SelectTheme(ThemeSelection.LIGHT)), actions) }
        // This proves only independent presentation remounting, not Phase I controller retention.
    }

}
