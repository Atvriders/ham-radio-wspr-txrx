package com.atvriders.wsprtxrx

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.atvriders.wsprtxrx.ui.theme.WsprTheme

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val windowSizeClass = calculateWindowSizeClass(this)
            WsprTheme {
                Placeholder(windowSizeClass)
            }
        }
    }
}

@Composable
private fun Placeholder(windowSizeClass: WindowSizeClass) {
    Scaffold(modifier = Modifier.fillMaxSize()) { inner ->
        Text(
            text = "Ham Radio WSPR TX/RX — width=${windowSizeClass.widthSizeClass}",
            modifier = Modifier.padding(inner),
        )
    }
}
