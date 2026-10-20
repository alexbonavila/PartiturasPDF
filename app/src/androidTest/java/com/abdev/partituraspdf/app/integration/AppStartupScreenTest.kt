package com.abdev.partituraspdf.app.integration

import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.abdev.partituraspdf.app.host.AppStartupFailure
import com.abdev.partituraspdf.app.host.AppStartupLoading
import com.abdev.partituraspdf.ui.theme.PartiturasPDFTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppStartupScreenTest {
    @get:Rule val compose = createComposeRule()
    @Test fun loadingHasProgressSemanticsAndNoActionThatCouldCreateDuplicateStartup() {
        compose.setContent { PartiturasPDFTheme { AppStartupLoading() } }
        compose.onNodeWithTag("app_loading").assert(SemanticsMatcher.expectValue(
            SemanticsProperties.ProgressBarRangeInfo, ProgressBarRangeInfo.Indeterminate))
        compose.onAllNodes(hasClickAction()).assertCountEquals(0)
    }
    @Test fun recoverableFailureOffersAnAccessibleExplicitRetryOnly() {
        var retries = 0
        compose.setContent { PartiturasPDFTheme { AppStartupFailure(true) { retries++ } } }
        compose.onNodeWithTag("app_startup_failure").assertIsDisplayed()
        compose.onNode(hasClickAction()).assertHeightIsAtLeast(48.dp).assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(1, retries) }
    }
}
