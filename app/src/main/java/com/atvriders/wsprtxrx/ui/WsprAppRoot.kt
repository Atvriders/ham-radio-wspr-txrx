package com.atvriders.wsprtxrx.ui

import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.ExperimentalMaterial3AdaptiveNavigationSuiteApi
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.atvriders.wsprtxrx.di.AppContainer
import com.atvriders.wsprtxrx.ui.charts.ChartsScreen
import com.atvriders.wsprtxrx.ui.map.MapScreen
import com.atvriders.wsprtxrx.ui.settings.SettingsScreen
import com.atvriders.wsprtxrx.ui.spots.SpotsScreen
import com.atvriders.wsprtxrx.ui.theme.WsprTheme
import com.atvriders.wsprtxrx.ui.tx.TxScreen

/** Root composable: adaptive navigation (bar / rail / drawer) wrapping the screens. */
@OptIn(ExperimentalMaterial3AdaptiveNavigationSuiteApi::class)
@Composable
fun WsprAppRoot(container: AppContainer, windowSizeClass: WindowSizeClass) {
    val factory = remember { WsprViewModelFactory(container) }
    val spotsVm: SpotsViewModel = viewModel(factory = factory)
    val txVm: TxViewModel = viewModel(factory = factory)
    val settingsVm: SettingsViewModel = viewModel(factory = factory)
    val settings by settingsVm.settings.collectAsState()

    var current by rememberSaveable { mutableStateOf(Destination.SPOTS) }

    WsprTheme(themeMode = settings.themeMode) {
        NavigationSuiteScaffold(
            navigationSuiteItems = {
                Destination.entries.forEach { dest ->
                    item(
                        selected = current == dest,
                        onClick = { current = dest },
                        icon = { Icon(dest.icon, contentDescription = dest.label) },
                        label = { Text(dest.label) },
                    )
                }
            },
        ) {
            when (current) {
                Destination.SPOTS -> SpotsScreen(spotsVm, windowSizeClass.widthSizeClass)
                Destination.MAP -> MapScreen(spotsVm, settings)
                Destination.CHARTS -> ChartsScreen(spotsVm)
                Destination.TX -> TxScreen(txVm)
                Destination.SETTINGS -> SettingsScreen(settingsVm)
            }
        }
    }
}
