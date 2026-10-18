package com.abdev.partituraspdf.library

import android.text.format.Formatter
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.abdev.partituraspdf.MainActivity
import com.abdev.partituraspdf.R
import com.abdev.partituraspdf.core.document.*
import com.abdev.partituraspdf.core.result.*
import com.abdev.partituraspdf.storage.api.FolderNavigationState
import com.abdev.partituraspdf.ui.theme.PartiturasPDFTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Renders a stateless screen with synthetic domain data. Never launches a picker or reader. */
@RunWith(AndroidJUnit4::class)
class LibraryScreenTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val root = FolderRef("ui-test", "private-provider-location")
    private val storage = StorageDocumentRef("ui-test", "private-provider-document")
    private val key = LibraryRowKey(storage, 7, 2)
    private val entry = DocumentEntry(storage, DocumentInfo("Sonata.pdf", "application/pdf"), root)
    private val document = DocumentRef(DocumentId("synthetic-logical-id"))
    private val record = RegisteredDocument(document, storage, DocumentRevision.Unknown(document), DocumentAvailability.UNKNOWN)
    private val actions = mutableListOf<LibraryAction>()
    private fun text(id: Int, vararg args: Any) = compose.activity.getString(id, *args)
    private fun registered(favorite: Boolean = false) = LibraryAssociation.Registered(record, Observation.Ready(DocumentMetadata(favorite)))
    private fun pdf(association: LibraryAssociation = LibraryAssociation.Unregistered, opening: LibraryOpening = LibraryOpening.Idle) = LibraryRow.Pdf(key, entry, association, opening)
    private fun ready(rows: List<LibraryRow> = emptyList(), complete: Boolean = true) = LibraryUiState(
        location = LibraryLocation.Available(FolderNavigationState(root, listOf(root), 7)), content = LibraryContent.Ready(rows, complete),
    )
    private fun render(state: LibraryUiState, dark: Boolean = false, large: Boolean = false) {
        compose.runOnUiThread { compose.activity.setContent {
            PartiturasPDFTheme(darkTheme = dark) {
                if (large) {
                    val density = LocalDensity.current
                    CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 2f)) {
                        Box(Modifier.width(320.dp).fillMaxHeight()) { LibraryScreen(state, actions::add) }
                    }
                } else LibraryScreen(state, actions::add)
            }
        } }
        compose.waitForIdle()
    }
    private fun find(matcher: SemanticsMatcher): SemanticsNodeInteraction {
        compose.onNodeWithTag("library_list").performScrollToNode(matcher)
        return compose.onNode(matcher).performScrollTo()
    }
    private fun findText(id: Int) = find(hasText(text(id)))
    private fun open() = find(hasContentDescription(text(R.string.library_open_description, entry.info.name!!)))

    @Test fun noRootSelectionIsExplicitAndNeverDispatchedByRendering() {
        render(LibraryUiState(location = LibraryLocation.NoRoot))
        findText(R.string.library_no_root).assertIsDisplayed()
        compose.runOnIdle { assertTrue(actions.isEmpty()) }
        find(hasContentDescription(text(R.string.library_select_folder_description))).assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(listOf(LibraryAction.ChooseRoot), actions) }
    }
    @Test fun pendingSelectionDisablesDuplicateLaunchAndDoesNotInventGrantedRoot() {
        render(LibraryUiState(location = LibraryLocation.NoRoot, rootSelection = LibraryOperation.Pending))
        findText(R.string.library_selection_pending).assertIsDisplayed()
        val select = find(hasContentDescription(text(R.string.library_select_folder_description)))
        select.assertIsNotEnabled().performTouchInput { click() }
        compose.onNodeWithText(text(R.string.library_root_segment)).assertDoesNotExist()
        compose.runOnIdle { assertTrue(actions.isEmpty()) }
    }
    @Test fun cancellationRemainsCancellationWithPreviouslyAvailableContent() {
        render(ready(listOf(pdf())).copy(rootSelection = LibraryOperation.Canceled))
        findText(R.string.library_selection_canceled).assertIsDisplayed()
        find(hasText(entry.info.name!!)).assertIsDisplayed()
        compose.runOnIdle { assertTrue(actions.isEmpty()) }
    }
    @Test fun selectionFailureOffersSelectionRetryAndKeepsCurrentRows() {
        render(ready(listOf(pdf())).copy(rootSelection = LibraryOperation.Failed(LibraryFailure(LibraryFailureOperation.SELECTION, AppError.Access.PERMISSION_DENIED))))
        findText(R.string.library_error_permission_denied).assertIsDisplayed()
        findText(R.string.library_retry).performClick()
        compose.runOnIdle { assertEquals(listOf(LibraryAction.Retry), actions) }
        find(hasText(entry.info.name!!)).assertIsDisplayed()
    }
    @Test fun failedRootRestoreUsesRefreshInsteadOfRepeatingAnUnrelatedSelection() {
        render(LibraryUiState(location = LibraryLocation.Failed(LibraryFailure(LibraryFailureOperation.RESTORE, AppError.Access.PERMISSION_REVOKED))))
        findText(R.string.library_error_permission_revoked).assertIsDisplayed()
        findText(R.string.library_retry).performClick()
        compose.runOnIdle { assertEquals(listOf(LibraryAction.Refresh), actions) }
    }
    @Test fun completeEmptyAndEmptyPartialResultsHaveDifferentMessages() {
        val state = mutableStateOf(ready())
        compose.runOnUiThread { compose.activity.setContent { PartiturasPDFTheme { LibraryScreen(state.value, actions::add) } } }
        findText(R.string.library_empty).assertIsDisplayed()
        compose.runOnIdle { state.value = ready(complete = false) }
        findText(R.string.library_partial).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.library_empty)).assertDoesNotExist()
    }
    @Test fun partialRowsAndLoadingRemainExplicit() {
        val state = mutableStateOf(ready(listOf(pdf()), false))
        compose.runOnUiThread { compose.activity.setContent { PartiturasPDFTheme { LibraryScreen(state.value, actions::add) } } }
        findText(R.string.library_partial).assertIsDisplayed(); find(hasText(entry.info.name!!)).assertIsDisplayed()
        compose.runOnIdle { state.value = ready().copy(content = LibraryContent.Loading(3)) }
        findText(R.string.library_loading).assertIsDisplayed()
        compose.onNode(hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate)).assertExists()
    }
    @Test fun providerUnavailableAndIoErrorsAreNotReportedAsDeletion() {
        val state = mutableStateOf(ready().copy(content = LibraryContent.Failed(LibraryFailure(LibraryFailureOperation.LISTING, AppError.Access.PROVIDER_UNAVAILABLE))))
        compose.runOnUiThread { compose.activity.setContent { PartiturasPDFTheme { LibraryScreen(state.value, actions::add) } } }
        findText(R.string.library_error_provider_unavailable).assertIsDisplayed()
        compose.runOnIdle { state.value = ready().copy(content = LibraryContent.Failed(LibraryFailure(LibraryFailureOperation.LISTING, AppError.Access.PROVIDER_IO_FAILURE))) }
        findText(R.string.library_error_provider_io).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.library_error_deleted)).assertDoesNotExist()
        // Header also has Refresh; select the action within the error surface by its context.
        compose.onAllNodesWithText(text(R.string.library_refresh)).onLast().performScrollTo().performClick()
        compose.runOnIdle { assertEquals(listOf(LibraryAction.Refresh), actions) }
    }
    @Test fun directoryIsOneAccessibleActionAndNeverOffersPdfOpening() {
        val folder = LibraryRow.Directory(key, entry.copy(info = DocumentInfo("Carpeta de cámara", "vnd.android.document/directory")))
        render(ready(listOf(folder)))
        find(hasText("Carpeta de cámara") and hasClickAction())
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)).performClick()
        compose.runOnIdle { assertEquals(listOf(LibraryAction.Enter(key)), actions) }
        compose.onNodeWithText(text(R.string.library_open)).assertDoesNotExist()
    }
    @Test fun backUsesNavigationVersionAndRootBackIsDisabled() {
        val state = mutableStateOf(ready())
        compose.runOnUiThread { compose.activity.setContent { PartiturasPDFTheme { LibraryScreen(state.value, actions::add) } } }
        findText(R.string.library_back).assertIsNotEnabled()
        val child = FolderRef("ui-test", "private-child")
        compose.runOnIdle { state.value = ready().copy(location = LibraryLocation.Available(FolderNavigationState(root, listOf(root, child), 19))) }
        findText(R.string.library_back).assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(listOf(LibraryAction.Back(19)), actions) }
    }
    @Test fun deepBreadcrumbsAreScrollableLocationInformationWithoutProviderIds() {
        val path = listOf(root) + (1..63).map { FolderRef("ui-test", "private-level-$it") }
        render(ready().copy(location = LibraryLocation.Available(FolderNavigationState(root, path, 7))))
        find(hasText(text(R.string.library_nested_segment, 63))).assertIsDisplayed().assertHasNoClickAction()
        compose.onAllNodesWithText("private-", substring = true).assertCountEquals(0)
        findText(R.string.library_back).assertIsEnabled()
    }
    @Test fun unknownNamesNeverFallBackToProviderLocations() {
        render(ready(listOf(pdf().copy(entry = entry.copy(info = DocumentInfo("  ", "application/pdf"))))))
        findText(R.string.library_unknown_pdf).assertIsDisplayed()
        compose.onAllNodesWithText(storage.value, substring = true).assertCountEquals(0)
        compose.onAllNodesWithText(root.value, substring = true).assertCountEquals(0)
    }
    @Test fun unnamedDirectoryUsesGenericFolderLabelWithoutProviderFallback() {
        val folder = LibraryRow.Directory(key, entry.copy(info = DocumentInfo(null, "vnd.android.document/directory")))
        render(ready(listOf(folder)))
        find(hasText(text(R.string.library_unknown_folder)) and hasClickAction()).performClick()
        compose.onAllNodesWithText(storage.value, substring = true).assertCountEquals(0)
        compose.runOnIdle { assertEquals(listOf(LibraryAction.Enter(key)), actions) }
    }
    @Test fun navigationPendingDisablesRowEntryAndRefresh() {
        val folder = LibraryRow.Directory(key, entry.copy(info = DocumentInfo("Carpeta", "vnd.android.document/directory")))
        render(ready(listOf(folder)).copy(navigation = LibraryOperation.Pending))
        findText(R.string.library_navigation_pending).assertIsDisplayed()
        findText(R.string.library_refresh).assertIsNotEnabled()
        find(hasText("Carpeta") and hasClickAction()).assertIsNotEnabled().performTouchInput { click() }
        compose.runOnIdle { assertTrue(actions.isEmpty()) }
    }
    @Test fun staleNavigationOffersRefreshWithoutReplayingEnterOrBack() {
        render(ready().copy(navigation = LibraryOperation.Failed(LibraryFailure(LibraryFailureOperation.NAVIGATION, AppError.Conflict.STALE_SESSION))))
        findText(R.string.library_error_stale).assertIsDisplayed()
        compose.onAllNodesWithText(text(R.string.library_refresh)).onLast().performScrollTo().performClick()
        compose.runOnIdle { assertEquals(listOf(LibraryAction.Refresh), actions) }
    }
    @Test fun knownSizeUsesPlatformFormattingAndUnknownSizeIsOmitted() {
        val state = mutableStateOf(ready(listOf(pdf().copy(entry = entry.copy(info = entry.info.copy(byteSize = 4096))))))
        compose.runOnUiThread { compose.activity.setContent { PartiturasPDFTheme { LibraryScreen(state.value, actions::add) } } }
        val size = text(R.string.library_size, Formatter.formatShortFileSize(compose.activity, 4096))
        find(hasText(size)).assertIsDisplayed()
        compose.runOnIdle { state.value = ready(listOf(pdf())) }
        compose.onNodeWithText(size).assertDoesNotExist()
    }
    @Test fun unregisteredRowRegistersExplicitlyAndCanOpenWithoutRegistering() {
        render(ready(listOf(pdf())))
        find(hasContentDescription(text(R.string.library_register_description, entry.info.name!!))).performClick()
        open().performClick()
        compose.runOnIdle { assertEquals(listOf(LibraryAction.Register(key), LibraryAction.Open(key)), actions) }
        compose.onNodeWithText(text(R.string.library_favorite_add)).assertDoesNotExist()
    }
    @Test fun lookupAndRegistrationPendingDoNotExposeRegistrationOrGuessOpeningType() {
        val state = mutableStateOf(ready(listOf(pdf(LibraryAssociation.LookingUp))))
        compose.runOnUiThread { compose.activity.setContent { PartiturasPDFTheme { LibraryScreen(state.value, actions::add) } } }
        findText(R.string.library_lookup_pending).assertIsDisplayed(); open().assertIsNotEnabled()
        compose.onNodeWithText(text(R.string.library_register)).assertDoesNotExist()
        compose.runOnIdle { state.value = ready(listOf(pdf(LibraryAssociation.Registering))) }
        findText(R.string.library_registration_pending).assertIsDisplayed(); open().assertIsNotEnabled()
        compose.onNodeWithText(text(R.string.library_register)).assertDoesNotExist()
    }
    @Test fun lookupFailureAndRegistrationFailureDispatchDifferentRetries() {
        val state = mutableStateOf(ready(listOf(pdf(LibraryAssociation.Failed(LibraryAssociationOperation.LOOKUP, AppError.Access.PRIVATE_STORAGE_UNAVAILABLE)))))
        compose.runOnUiThread { compose.activity.setContent { PartiturasPDFTheme { LibraryScreen(state.value, actions::add) } } }
        findText(R.string.library_retry_lookup).performClick(); open().assertIsNotEnabled()
        compose.runOnIdle { state.value = ready(listOf(pdf(LibraryAssociation.Failed(LibraryAssociationOperation.REGISTRATION, AppError.Conflict.REGISTRATION_CHANGED)))) }
        findText(R.string.library_retry_registration).performClick()
        compose.runOnIdle { assertEquals(listOf(LibraryAction.RetryLookup(key), LibraryAction.RetryRegistration(key)), actions) }
    }
    @Test fun registeredFavoriteStatesExposeSelectionAndOppositeTargetActions() {
        val state = mutableStateOf(ready(listOf(pdf(registered(false)))))
        compose.runOnUiThread { compose.activity.setContent { PartiturasPDFTheme { LibraryScreen(state.value, actions::add) } } }
        val add = text(R.string.library_favorite_add_description, entry.info.name!!)
        find(hasContentDescription(add)).assertIsNotSelected().performClick()
        compose.runOnIdle { state.value = ready(listOf(pdf(registered(true)))) }
        find(hasContentDescription(text(R.string.library_favorite_remove_description, entry.info.name!!))).assertIsSelected().performClick()
        compose.onNodeWithText(text(R.string.library_register)).assertDoesNotExist()
        compose.runOnIdle { assertEquals(listOf(LibraryAction.SetFavorite(key, true), LibraryAction.SetFavorite(key, false)), actions) }
    }
    @Test fun loadingOrFailedMetadataNeverAssertsFavoriteFalse() {
        val state = mutableStateOf(ready(listOf(pdf(LibraryAssociation.Registered(record)))))
        compose.runOnUiThread { compose.activity.setContent { PartiturasPDFTheme { LibraryScreen(state.value, actions::add) } } }
        findText(R.string.library_metadata_loading).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.library_favorite_add)).assertDoesNotExist()
        compose.runOnIdle { state.value = ready(listOf(pdf(LibraryAssociation.Registered(record, Observation.Failed(AppError.Access.PRIVATE_STORAGE_UNAVAILABLE))))) }
        findText(R.string.library_retry_metadata).performClick()
        compose.onNodeWithText(text(R.string.library_favorite_add)).assertDoesNotExist()
        compose.runOnIdle { assertEquals(listOf(LibraryAction.RetryMetadata(key)), actions) }
    }
    @Test fun pendingFavoriteDisablesConflictingChangeAndReconciliationHidesUnconfirmedValue() {
        val state = mutableStateOf(ready(listOf(pdf(registered().copy(favorite = LibraryFavorite.Pending(true))))))
        compose.runOnUiThread { compose.activity.setContent { PartiturasPDFTheme { LibraryScreen(state.value, actions::add) } } }
        findText(R.string.library_favorite_pending).assertIsDisplayed()
        find(hasContentDescription(text(R.string.library_favorite_add_description, entry.info.name!!))).assertIsNotEnabled()
        compose.runOnIdle { state.value = ready(listOf(pdf(registered().copy(favorite = LibraryFavorite.Reconciling(true))))) }
        findText(R.string.library_favorite_reconciling).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.library_favorite_add)).assertDoesNotExist()
    }
    @Test fun favoriteFailureOffersReconciliationRetry() {
        render(ready(listOf(pdf(registered().copy(favorite = LibraryFavorite.Failed(true, AppError.Access.PRIVATE_STORAGE_UNAVAILABLE))))))
        findText(R.string.library_retry_favorite).performClick()
        compose.runOnIdle { assertEquals(listOf(LibraryAction.RetryFavorite(key)), actions) }
    }
    @Test fun pendingAndAcceptedOpeningDoNotOfferDuplicateOpenOrClaimRenderedSuccess() {
        val state = mutableStateOf(ready(listOf(pdf(opening = LibraryOpening.Pending))))
        compose.runOnUiThread { compose.activity.setContent { PartiturasPDFTheme { LibraryScreen(state.value, actions::add) } } }
        findText(R.string.library_open_pending).assertIsDisplayed()
        compose.onNodeWithContentDescription(text(R.string.library_open_description, entry.info.name!!)).assertDoesNotExist()
        compose.runOnIdle { state.value = ready(listOf(pdf(opening = LibraryOpening.Accepted))) }
        findText(R.string.library_open_accepted).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.library_open)).assertDoesNotExist()
        compose.runOnIdle { assertTrue(actions.isEmpty()) }
    }
    @Test fun openingFailureAndRejectionExposeRetryOnlyWithConfirmedAssociation() {
        val state = mutableStateOf(ready(listOf(pdf(opening = LibraryOpening.Failed(AppError.Access.PERMISSION_REVOKED)))))
        compose.runOnUiThread { compose.activity.setContent { PartiturasPDFTheme { LibraryScreen(state.value, actions::add) } } }
        findText(R.string.library_retry_open).assertIsEnabled().performClick()
        compose.runOnIdle { state.value = ready(listOf(pdf(LibraryAssociation.LookingUp, LibraryOpening.Rejected))) }
        findText(R.string.library_retry_open).assertIsNotEnabled()
        compose.runOnIdle { assertEquals(listOf(LibraryAction.RetryOpen(key)), actions) }
    }
    @Test fun narrowLargeFontKeepsFullNameAndEssentialActionsReachableByScrolling() {
        val long = "Partitura de concierto con un nombre muy largo y caracteres áéíóú ".repeat(4)
        render(ready(listOf(pdf().copy(entry = entry.copy(info = entry.info.copy(name = long))))), large = true)
        find(hasText(long)).assertIsDisplayed()
        find(hasContentDescription(text(R.string.library_open_description, long))).assertIsDisplayed().performClick()
        find(hasContentDescription(text(R.string.library_register_description, long))).assertIsDisplayed()
        compose.runOnIdle { assertEquals(listOf(LibraryAction.Open(key)), actions) }
    }
    @Test fun lightThemeControlsHaveAccessibleNamesAndMinimumTouchTargets() { assertTheme(false) }
    @Test fun darkThemeControlsHaveAccessibleNamesAndMinimumTouchTargets() { assertTheme(true) }
    private fun assertTheme(dark: Boolean) {
        render(ready(listOf(pdf(registered(true)))), dark = dark)
        open().assertHasClickAction().assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp).performClick()
        compose.runOnIdle { assertEquals(listOf(LibraryAction.Open(key)), actions) }
    }
    @Test fun recompositionDoesNotDispatchActionsAndUsesLatestCallback() {
        val state = mutableStateOf(ready(listOf(pdf())))
        val callbacks = mutableListOf<LibraryAction>()
        val changed = mutableStateOf(false)
        compose.runOnUiThread { compose.activity.setContent { PartiturasPDFTheme {
            val callback: (LibraryAction) -> Unit = if (changed.value) ({ callbacks.add(it) }) else ({ actions.add(it) })
            LibraryScreen(state.value, callback)
        } } }
        compose.runOnIdle { changed.value = true; state.value = ready(listOf(pdf()), false) }
        open().performClick()
        compose.runOnIdle { assertTrue(actions.isEmpty()); assertEquals(listOf(LibraryAction.Open(key)), callbacks) }
    }
}
