package com.abdev.partituraspdf.app.integration

import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.espresso.Espresso.pressBack
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.abdev.partituraspdf.MainActivity
import com.abdev.partituraspdf.R
import com.abdev.partituraspdf.app.host.AppHostState
import com.abdev.partituraspdf.app.host.AppHostViewModel
import com.abdev.partituraspdf.app.navigation.AppDestination
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Production launcher graph; no picker, grants, private fixture writes or PDF session. */
@RunWith(AndroidJUnit4::class)
class AppHostActivityTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private fun owner(): AppHostViewModel {
        lateinit var retained: AppHostViewModel
        compose.activityRule.scenario.onActivity { retained = ViewModelProvider(it)[AppHostViewModel::class.java] }
        return retained
    }
    private fun ready(): AppHostViewModel {
        val host = owner()
        compose.waitUntil(10_000) { host.state.value is AppHostState.Ready }
        compose.waitForIdle()
        return host
    }
    @Test fun launcherShowsLibraryInsteadOfStarterGreeting() {
        val host = ready()
        compose.onNodeWithTag("app_shell").assertIsDisplayed()
        compose.onNodeWithTag("app_settings").assertHasClickAction().assertHeightIsAtLeast(48.dp)
        compose.onNodeWithText(compose.activity.getString(R.string.starter_greeting, "Android")).assertDoesNotExist()
        assertEquals(AppDestination.LIBRARY, host.navigation.value.destination)
    }
    @Test fun settingsAndToolbarBackReuseTheSameFeatureOwners() {
        val host = ready(); val features = (host.state.value as AppHostState.Ready).features
        compose.onNodeWithTag("app_settings").performClick()
        compose.onNodeWithTag("settings_content").assertExists()
        compose.onNodeWithTag("app_back").performClick()
        compose.onNodeWithTag("app_settings").assertIsDisplayed()
        assertSame(features, (host.state.value as AppHostState.Ready).features)
        assertEquals(2L, host.navigation.value.revision)
    }
    @Test fun androidBackReturnsFromSettingsWithoutDisposingGraph() {
        val host = ready()
        compose.onNodeWithTag("app_settings").performClick()
        compose.waitForIdle(); pressBack()
        compose.onNodeWithTag("app_settings").assertIsDisplayed()
        assertTrue(host.state.value is AppHostState.Ready)
        assertEquals(AppDestination.LIBRARY, host.navigation.value.destination)
    }
    @Test fun recreationRetainsViewModelNavigatorControllersAndSettingsDestination() {
        val host = ready(); val features = (host.state.value as AppHostState.Ready).features
        compose.onNodeWithTag("app_settings").performClick()
        compose.activityRule.scenario.recreate()
        compose.waitForIdle()
        assertSame(host, owner())
        val retained = (host.state.value as AppHostState.Ready).features
        assertSame(features.folders, retained.folders)
        assertSame(features.library, retained.library); assertSame(features.settings, retained.settings)
        assertEquals(AppDestination.SETTINGS, host.navigation.value.destination)
        assertEquals(1L, host.navigation.value.revision)
        compose.onNodeWithTag("settings_content").assertExists()
    }
    @Test fun oldCallbacksAfterBackCannotReopenSettingsOrEnterReader() {
        val host = ready()
        val oldClick = checkNotNull(compose.onNodeWithTag("app_settings").fetchSemanticsNode()
            .config[SemanticsActions.OnClick].action)
        compose.onNodeWithTag("app_settings").performClick()
        compose.onNodeWithTag("app_back").performClick()
        compose.waitForIdle()
        compose.runOnIdle {
            oldClick() // A callback from the prior rendering must retain revision zero.
            host.back(1)
            assertEquals(AppDestination.LIBRARY, host.navigation.value.destination)
            assertEquals(2L, host.navigation.value.revision)
        }
        compose.onNodeWithTag("app_settings").assertIsDisplayed()
    }
    @Test fun rootBackLeavesForegroundThroughNormalDispatcher() {
        val host = ready()
        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        // Android may finish the root or background its task; leave that policy to the platform.
        compose.waitUntil(10_000) {
            !compose.activityRule.scenario.state.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)
        }
        assertEquals(AppDestination.LIBRARY, host.navigation.value.destination)
        assertEquals(0L, host.navigation.value.revision)
    }
}
