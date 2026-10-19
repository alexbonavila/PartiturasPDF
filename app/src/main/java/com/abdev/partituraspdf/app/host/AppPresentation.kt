package com.abdev.partituraspdf.app.host

import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import android.view.ViewTreeObserver
import android.view.WindowManager
import com.abdev.partituraspdf.ui.theme.PartiturasPDFTheme
import kotlinx.coroutines.launch

/** One effect per Activity composition, never retained by the ViewModel or a feature controller. */
@Composable
internal fun AppPresentation(owner: AppHostViewModel, activity: ComponentActivity, content: @Composable () -> Unit) {
    val preferences by owner.preferences.collectAsState()
    val dark = appDarkTheme(preferences, isSystemInDarkTheme())
    DisposableEffect(owner, activity) {
        fun update() {
            val keep = keepReaderScreenOn(owner.readerVisible(), activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED),
                activity.window.decorView.hasWindowFocus())
            if (keep) activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            else activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        val lifecycle = LifecycleEventObserver { _, _ -> update() }
        val focus = ViewTreeObserver.OnWindowFocusChangeListener { update() }
        val tree = activity.window.decorView.viewTreeObserver
        activity.lifecycle.addObserver(lifecycle)
        tree.addOnWindowFocusChangeListener(focus)
        val job = activity.lifecycleScope.launch {
            activity.repeatOnLifecycle(Lifecycle.State.STARTED) { owner.readerVisibility().collect { update() } }
        }
        update()
        onDispose {
            job.cancel()
            activity.lifecycle.removeObserver(lifecycle)
            val currentTree = activity.window.decorView.viewTreeObserver
            if (currentTree.isAlive) currentTree.removeOnWindowFocusChangeListener(focus)
            if (tree !== currentTree && tree.isAlive) tree.removeOnWindowFocusChangeListener(focus)
            activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
    PartiturasPDFTheme(darkTheme = dark) {
        val surface = MaterialTheme.colorScheme.surface.toArgb()
        SideEffect {
            // Keep bars visible; AndroidX provides API26 icon/scrim compatibility.
            val style = SystemBarStyle.auto(surface, surface) { dark }
            activity.enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
        }
        content()
    }
}
