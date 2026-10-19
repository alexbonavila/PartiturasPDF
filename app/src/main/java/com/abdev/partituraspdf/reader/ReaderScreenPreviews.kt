package com.abdev.partituraspdf.reader

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import com.abdev.partituraspdf.core.result.AppError
import com.abdev.partituraspdf.ui.theme.PartiturasPDFTheme

/** Chrome/state previews only: no fake PDF, adapter, bitmap allocation or simulated authorization. */
class ReaderPreviewStates : PreviewParameterProvider<ReaderUiState> {
    override val values = sequenceOf(
        ReaderUiState(),
        ReaderUiState(phase = ReaderPhase.OPENING),
        ReaderUiState(phase = ReaderPhase.RENDERING),
        ReaderUiState(phase = ReaderPhase.QUIESCED),
        ReaderUiState(phase = ReaderPhase.ERROR,
            failure = ReaderFailure(ReaderStage.OPEN, AppError.Access.PERMISSION_REVOKED, ReaderRetry.OPEN)),
    )
}

@Preview(name = "Reader chrome", widthDp = 360, heightDp = 720)
@Preview(name = "Tablet dark", widthDp = 840, heightDp = 700, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Landscape large font", widthDp = 900, heightDp = 420, fontScale = 2f)
@Composable
private fun ReaderStatePreview(@PreviewParameter(ReaderPreviewStates::class) state: ReaderUiState) {
    PartiturasPDFTheme { ReaderScreen(state, onAction = {}, onBack = {}) }
}
