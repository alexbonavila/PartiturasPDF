package com.abdev.partituraspdf

import android.os.Bundle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.abdev.partituraspdf.app.host.AppHostState
import com.abdev.partituraspdf.app.host.bindRootPicker
import com.abdev.partituraspdf.storage.saf.SafTreeGrantAccess
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collectLatest
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModelProvider
import com.abdev.partituraspdf.app.host.AppHost
import com.abdev.partituraspdf.app.host.AppHostFactory
import com.abdev.partituraspdf.app.host.AppHostViewModel
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.abdev.partituraspdf.ui.theme.PartiturasPDFTheme

class MainActivity : ComponentActivity() {
    private val activityIdentity = java.util.UUID.randomUUID().toString()
    private lateinit var host: AppHostViewModel
    private var restoredPicker: String? = null

    override fun onSaveInstanceState(outState: Bundle) {
        val pending = (host.state.value as? AppHostState.Ready)?.features?.picker?.request?.value
        outState.putString("app.saf.pending", pending)
        outState.putString("app.host.lifetime", host.lifetimeId)
        outState.putString("app.navigation", host.saveNavigation())
        super.onSaveInstanceState(outState)
    }
    override fun onStart() {
        super.onStart()
        host.activityStarted(activityIdentity)
    }
    override fun onStop() {
        host.activityStopped(activityIdentity, isChangingConfigurations)
        super.onStop()
    }
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (::host.isInitialized) host.activityMemory(activityIdentity, level, isChangingConfigurations)
    }
    override fun onLowMemory() {
        super.onLowMemory()
        if (::host.isInitialized) host.activityMemory(activityIdentity, android.content.ComponentCallbacks2.TRIM_MEMORY_COMPLETE)
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        host = ViewModelProvider(this, AppHostFactory(application, savedInstanceState?.getString("app.navigation")))[AppHostViewModel::class.java]
        host.activityCreated(activityIdentity)
        restoredPicker = if (savedInstanceState?.getString("app.host.lifetime") != host.lifetimeId)
            savedInstanceState?.getString("app.saf.pending") else null
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                host.state.collectLatest { state ->
                    val picker = (state as? AppHostState.Ready)?.features?.picker ?: return@collectLatest
                    restoredPicker?.let { picker.incomplete() }
                    restoredPicker = null
                    bindRootPicker(activityResultRegistry, picker) {
                        SafTreeGrantAccess(contentResolver).treePickerIntent()
                    }
                }
            }
        }
        setContent { PartiturasPDFTheme { AppHost(host) } }
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = stringResource(R.string.starter_greeting, name),
        modifier = modifier
    )
}

@Preview(name = "English light", showBackground = true, locale = "en")
@Preview(name = "Catalan light", showBackground = true, locale = "ca")
@Preview(name = "Spanish light", showBackground = true, locale = "es")
@Preview(name = "English dark", showBackground = true, locale = "en", uiMode = 0x20)
@Preview(name = "Catalan dark", showBackground = true, locale = "ca", uiMode = 0x20)
@Preview(name = "Spanish dark", showBackground = true, locale = "es", uiMode = 0x20)
@Composable
fun GreetingPreview() {
    PartiturasPDFTheme {
        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
            Greeting(
                name = stringResource(R.string.starter_recipient),
                modifier = Modifier.padding(innerPadding)
            )
        }
    }
}
