package com.abdev.partituraspdf.pdf.presentation

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.abdev.partituraspdf.MainActivity
import com.abdev.partituraspdf.R
import com.abdev.partituraspdf.core.document.StorageDocumentRef
import com.abdev.partituraspdf.core.foundation.DocumentOpenRequest
import com.abdev.partituraspdf.storage.api.*
import com.abdev.partituraspdf.ui.theme.PartiturasPDFTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Draft-only Compose assertions; no real picker or PDF write. */
@RunWith(AndroidJUnit4::class)
class MPageEditorTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private val route=MEditRoute(1,2,DocumentOpenRequest.Transient(StorageDocumentRef("test","opaque")))
    @Test fun closedGateAllowsLocalizedDraftControlsButSaveIsDisabled() {
        val observed=mutableStateOf(MEditState(MEditSource(route,2),MEditDraft.initial(2)))
        var saves=0;var closes=0
        compose.runOnUiThread { compose.activity.setContent { PartiturasPDFTheme {
            MPageEditor(observed.value,{ observed.value=observed.value.copy(draft=observed.value.draft!!.change(it)) },{ saves++ },{ closes++ })
        } } }
        compose.onNodeWithTag("m_unavailable").assertIsDisplayed()
        compose.onNodeWithTag("m_save_as").assertIsNotEnabled().assertHeightIsAtLeast(48.dp)
        compose.onNodeWithContentDescription(compose.activity.getString(R.string.m_rotate_page,1)).performClick()
        compose.runOnIdle { assertEquals(1,observed.value.draft!!.pages[0].turns);assertEquals(0,saves) }
        compose.onNodeWithContentDescription(compose.activity.getString(R.string.m_include_page,1)).assertIsToggleable().performClick()
        compose.runOnIdle { assertEquals(listOf(1),observed.value.draft!!.freeze()!!.selections.map { it.sourcePageIndex }) }
        compose.onNodeWithTag("m_close").assertHeightIsAtLeast(48.dp).performClick()
        compose.runOnIdle { assertEquals(1,closes);assertEquals(0,saves) }
    }
    @Test fun narrowDarkLargeFontLayoutRetainsAccessibleCloseAndSaveControls() {
        compose.runOnUiThread { compose.activity.setContent {
            val density=LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density,1.8f)) {
                PartiturasPDFTheme(darkTheme=true) {
                    Box(Modifier.width(320.dp)) {
                        MPageEditor(MEditState(MEditSource(route,1),MEditDraft.initial(1)),{},{},{},Modifier.widthIn(max=320.dp))
                    }
                }
            }
        } }
        compose.onNodeWithTag("m_close").assertIsDisplayed().assertHeightIsAtLeast(48.dp)
        compose.onNodeWithTag("m_save_as").assertIsDisplayed().assertIsNotEnabled()
        compose.onNodeWithTag("m_pages").performScrollToIndex(1)
        compose.onNodeWithContentDescription(compose.activity.getString(R.string.m_include_page,1)).assertHeightIsAtLeast(48.dp)
    }
    @Test fun deliveredEditorStringsResolveInSpanishCatalanEnglishAndFallback() {
        val expected=mapOf("es" to "Editar páginas","ca" to "Edita les pàgines","en" to "Edit pages","zz" to "Editar páginas")
        for((language,label) in expected) {
            val configuration=android.content.res.Configuration(compose.activity.resources.configuration)
            configuration.setLocales(android.os.LocaleList(java.util.Locale.forLanguageTag(language)))
            val localized=compose.activity.createConfigurationContext(configuration)
            assertEquals(label,localized.getString(R.string.m_edit_pages))
            val page=localized.getString(R.string.m_page_rotation,2,90)
            assertTrue(page.contains("2"));assertTrue(page.contains("90"))
        }
    }
    @Test fun cleanupPendingPublicationIsNotLabeledAsFailedOrCleaned() {
        compose.runOnUiThread { compose.activity.setContent { PartiturasPDFTheme {
            MEditOutcome(MEditState(publication=PublicationResult.VerifiedNewDocument(StorageDocumentRef("test","new"),
                com.abdev.partituraspdf.core.document.SnapshotEvidence("a".repeat(64),3),"checkpoint",SnapshotCleanupStatus.PENDING)))
        } } }
        compose.onNodeWithText(compose.activity.getString(R.string.m_saved_cleanup)).assertIsDisplayed()
        compose.onNodeWithText(compose.activity.getString(R.string.m_saved)).assertDoesNotExist()
    }
    @Test fun unresolvedOutcomeNeverEnablesSaveOrClaimsVerifiedSuccess() {
        compose.runOnUiThread { compose.activity.setContent { PartiturasPDFTheme {
            MPageEditor(MEditState(source=MEditSource(route,1),draft=MEditDraft.initial(1),
                availability=MEditAvailability.AVAILABLE,publication=PublicationResult.UncertainPublication,
                recoveryRequired=false),{},{},{})
        } } }
        compose.onNodeWithTag("m_save_as").assertIsNotEnabled()
        compose.onNodeWithText(compose.activity.getString(R.string.m_uncertain)).assertIsDisplayed()
        compose.onNodeWithText(compose.activity.getString(R.string.m_saved)).assertDoesNotExist()
        compose.onNodeWithText(compose.activity.getString(R.string.m_saved_cleanup)).assertDoesNotExist()
    }
}
