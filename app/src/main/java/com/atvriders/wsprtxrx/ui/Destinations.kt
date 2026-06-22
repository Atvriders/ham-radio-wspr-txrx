package com.atvriders.wsprtxrx.ui

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TableRows
import androidx.compose.material.icons.filled.Podcasts
import androidx.compose.ui.graphics.vector.ImageVector
import com.atvriders.wsprtxrx.R

/** Top-level navigation destinations shown in the adaptive navigation suite. */
enum class Destination(@StringRes val labelRes: Int, val icon: ImageVector) {
    SPOTS(R.string.nav_spots, Icons.Filled.TableRows),
    MAP(R.string.nav_map, Icons.Filled.Public),
    CHARTS(R.string.nav_charts, Icons.Filled.BarChart),
    TX(R.string.nav_tx, Icons.Filled.Podcasts),
    SETTINGS(R.string.nav_settings, Icons.Filled.Settings),
}
