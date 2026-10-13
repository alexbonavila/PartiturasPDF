package com.abdev.partituraspdf.ui.components

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.abdev.partituraspdf.ui.theme.PartiturasPDFTheme

/** Preview-only scenarios. Sample text is deliberately supplied by the caller. */
@Composable
private fun PreviewFeedbackPanel(
    message: String,
    actionLabel: String?,
    darkTheme: Boolean = false,
) {
    PartiturasPDFTheme(darkTheme = darkTheme) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                FeedbackPanel(
                    message = message,
                    actionLabel = actionLabel,
                    onActionClick = if (actionLabel == null) null else ({}),
                )
            }
        }
    }
}

@Preview(name = "Narrow 240dp · long text", widthDp = 240, heightDp = 480, showBackground = true)
@Composable
private fun FeedbackPanelPreviewNarrow() {
    PreviewFeedbackPanel(
        message = "This folder contains no sheet music. Choose another folder to continue browsing your scores.",
        actionLabel = "Choose another folder",
    )
}

@Preview(name = "Tablet 800dp · long text", widthDp = 800, heightDp = 480, showBackground = true)
@Composable
private fun FeedbackPanelPreviewTablet() {
    PreviewFeedbackPanel(
        message = "This folder contains no sheet music. Choose another folder to continue browsing your scores.",
        actionLabel = "Choose another folder",
    )
}

@Preview(name = "Large font 2x · narrow", widthDp = 320, heightDp = 480, fontScale = 2f, showBackground = true)
@Composable
private fun FeedbackPanelPreviewLargeFont() {
    PreviewFeedbackPanel(
        message = "The PDF could not be opened. Check the file or retry the operation.",
        actionLabel = "Try again",
    )
}

@Preview(name = "Dark · Catalan", widthDp = 360, heightDp = 480, uiMode = UI_MODE_NIGHT_YES, showBackground = true)
@Composable
private fun FeedbackPanelPreviewDarkCatalan() {
    PreviewFeedbackPanel(
        message = "Aquesta carpeta és buida.",
        actionLabel = "Tria una altra carpeta",
        darkTheme = true,
    )
}

@Preview(name = "Light · Spanish without action", widthDp = 320, heightDp = 480, showBackground = true)
@Composable
private fun FeedbackPanelPreviewSpanishNoAction() {
    PreviewFeedbackPanel(
        message = "Esta carpeta está vacía.",
        actionLabel = null,
    )
}
