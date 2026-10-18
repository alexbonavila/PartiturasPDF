package com.abdev.partituraspdf.library

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import com.abdev.partituraspdf.core.document.*
import com.abdev.partituraspdf.core.result.*
import com.abdev.partituraspdf.storage.api.FolderNavigationState
import com.abdev.partituraspdf.ui.theme.PartiturasPDFTheme

/** Synthetic immutable samples only; previews never construct dependencies or acquire permissions. */
class LibraryPreviewStates : PreviewParameterProvider<LibraryUiState> {
    override val values: Sequence<LibraryUiState> get() {
        val root = FolderRef("preview", "root")
        val folder = DocumentEntry(StorageDocumentRef("preview", "folder"), DocumentInfo("Partituras de cámara", "vnd.android.document/directory"), root)
        val pdf = DocumentEntry(StorageDocumentRef("preview", "pdf"), DocumentInfo("Cuarteto de cuerda.pdf", "application/pdf", 4096), root)
        val key = LibraryRowKey(pdf.storage, 1, 1)
        val document = DocumentRef(DocumentId("preview-logical-id"))
        val registered = LibraryAssociation.Registered(RegisteredDocument(document, pdf.storage, DocumentRevision.Unknown(document), DocumentAvailability.UNKNOWN), Observation.Ready(DocumentMetadata(true)))
        val base = LibraryUiState(location = LibraryLocation.Available(FolderNavigationState(root, listOf(root), 1)))
        val rows = listOf(LibraryRow.Directory(LibraryRowKey(folder.storage, 1, 1), folder), LibraryRow.Pdf(key, pdf, registered),
            LibraryRow.Pdf(key.copy(storage = StorageDocumentRef("preview", "unregistered")), pdf.copy(storage = StorageDocumentRef("preview", "unregistered"), info = DocumentInfo(null, "application/pdf")), LibraryAssociation.Unregistered))
        return sequenceOf(
            LibraryUiState(location = LibraryLocation.NoRoot),
            base.copy(content = LibraryContent.Ready(emptyList(), true)),
            base.copy(content = LibraryContent.Ready(rows, true)),
            base.copy(content = LibraryContent.Ready(rows, false)),
            base.copy(content = LibraryContent.Loading(1)),
            base.copy(content = LibraryContent.Failed(LibraryFailure(LibraryFailureOperation.LISTING, AppError.Access.PERMISSION_REVOKED))),
        )
    }
}

@Preview(name = "Library states", showBackground = true, widthDp = 360, heightDp = 720)
@Preview(name = "Tablet dark", showBackground = true, widthDp = 840, heightDp = 700, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Narrow large font", showBackground = true, widthDp = 320, heightDp = 720, fontScale = 2f)
@Composable
private fun LibraryStatePreview(@PreviewParameter(LibraryPreviewStates::class) state: LibraryUiState) {
    PartiturasPDFTheme { LibraryScreen(state, onAction = { }) }
}
