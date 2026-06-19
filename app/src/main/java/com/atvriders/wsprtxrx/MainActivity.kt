package com.atvriders.wsprtxrx

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import com.atvriders.wsprtxrx.ui.WsprAppRoot

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as WsprApp).container
        setContent {
            val windowSizeClass = calculateWindowSizeClass(this)
            WsprAppRoot(container, windowSizeClass)
        }
    }
}
