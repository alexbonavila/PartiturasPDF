package com.abdev.partituraspdf

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PartiturasPDFTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Greeting(
                        name = stringResource(R.string.starter_recipient),
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
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
