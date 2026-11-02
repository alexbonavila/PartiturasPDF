package com.abdev.partituraspdf.library

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.abdev.partituraspdf.R
import com.abdev.partituraspdf.core.document.*
import com.abdev.partituraspdf.storage.api.FolderNavigationState
import com.abdev.partituraspdf.ui.theme.PartiturasPDFTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Synthetic screen/Compose saved state only; not real Activity or OS-process recreation. */
class LibraryScreenStateRestorationTest {
    @get:Rule val compose = createComposeRule()
    private val root = FolderRef("restoration-test", "root")
    private val actions = mutableListOf<LibraryAction>()
    private fun text(id: Int) = InstrumentationRegistry.getInstrumentation().targetContext.getString(id)
    private fun ready(navigation: FolderNavigationState, generation: Long = 1): LibraryUiState {
        val storage = StorageDocumentRef("restoration-test", "pdf")
        val entry = DocumentEntry(storage, DocumentInfo("Sonata.pdf", "application/pdf"), navigation.current)
        return LibraryUiState(location = LibraryLocation.Available(navigation), content = LibraryContent.Ready(
            listOf(LibraryRow.Pdf(LibraryRowKey(storage, navigation.version, generation), entry, LibraryAssociation.Unregistered)), true))
    }
    private fun find(matcher: SemanticsMatcher): SemanticsNodeInteraction {
        compose.onNodeWithTag("library_list").performScrollToNode(matcher)
        return compose.onNode(matcher).performScrollTo()
    }
    private fun configure() {
        find(hasTestTag("library_search")).performTextInput("sonata")
        find(hasTestTag("library_filter_PDFS")).performClick()
        find(hasTestTag("library_sort")).performClick()
        compose.onNodeWithText(text(R.string.library_sort_desc)).performClick()
    }
    private fun retained() {
        find(hasTestTag("library_search")).assertTextContains("sonata")
        find(hasTestTag("library_filter_PDFS")).assertIsSelected()
        find(hasTestTag("library_sort")).assertTextContains(text(R.string.library_sort_desc))
        find(hasText("Sonata.pdf")).assertIsDisplayed()
        compose.runOnIdle { assertTrue(actions.isEmpty()) }
    }
    @Test fun savedControlsRestoreOnlyWithinSameFolderScope() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent { PartiturasPDFTheme { LibraryScreen(ready(FolderNavigationState(root, listOf(root), 7)), actions::add) } }
        configure()
        restoration.emulateSavedInstanceStateRestore()
        retained()
        find(hasTestTag("library_search")).performTextReplacement("absent")
        find(hasText(text(R.string.library_no_matches))).assertIsDisplayed()
    }
    @Test fun savedControlsFromOldFolderAreRejectedDuringRestorationIntoNewFolder() {
        // Deliberately not Compose state: switch only the next reconstructed composition.
        // The current composition still saves A's nondefault controls before B is mounted.
        var navigation = FolderNavigationState(root, listOf(root), 7)
        val restoration = StateRestorationTester(compose)
        restoration.setContent { PartiturasPDFTheme { LibraryScreen(ready(navigation), actions::add) } }
        configure()
        compose.runOnIdle { navigation = FolderNavigationState(root, listOf(root, FolderRef("restoration-test", "child")), 8) }
        restoration.emulateSavedInstanceStateRestore()
        find(hasText(text(R.string.library_clear_search))).assertIsNotEnabled()
        find(hasTestTag("library_filter_ALL")).assertIsSelected()
        find(hasTestTag("library_sort")).assertTextContains(text(R.string.library_sort_asc))
        find(hasText("Sonata.pdf")).assertIsDisplayed()
        compose.runOnIdle { assertTrue(actions.isEmpty()) }
    }
    @Test fun newLoadGenerationPreservesAllControlsAndStillRestoresThem() {
        val navigation = FolderNavigationState(root, listOf(root), 7)
        val state = mutableStateOf(ready(navigation))
        val restoration = StateRestorationTester(compose)
        restoration.setContent { PartiturasPDFTheme { LibraryScreen(state.value, actions::add) } }
        configure()
        compose.runOnIdle { state.value = ready(navigation, generation = 99) }
        retained()
        restoration.emulateSavedInstanceStateRestore()
        retained()
    }
}
