package com.atvriders.wsprtxrx.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TableRows
import androidx.compose.material.icons.filled.Podcasts
import androidx.compose.ui.graphics.vector.ImageVector

/** Top-level navigation destinations shown in the adaptive navigation suite. */
enum class Destination(val label: String, val icon: ImageVector) {
    SPOTS("Spots", Icons.Filled.TableRows),
    MAP("Map", Icons.Filled.Public),
    CHARTS("Charts", Icons.Filled.BarChart),
    TX("TX", Icons.Filled.Podcasts),
    SETTINGS("Settings", Icons.Filled.Settings),
}
