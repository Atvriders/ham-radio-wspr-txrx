package com.atvriders.wsprtxrx.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.atvriders.wsprtxrx.audio.TxForegroundService
import com.atvriders.wsprtxrx.di.AppContainer

/** Builds the app's ViewModels with their dependencies from the [AppContainer]. */
class WsprViewModelFactory(private val container: AppContainer) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = when {
        modelClass.isAssignableFrom(SpotsViewModel::class.java) ->
            SpotsViewModel(container.repository, container.settingsStore, container.qrzService) as T
        modelClass.isAssignableFrom(TxViewModel::class.java) ->
            TxViewModel(
                player = com.atvriders.wsprtxrx.audio.WsprPlayer(container.appContext),
                keepAlive = object : TxKeepAlive {
                    override fun start(): Boolean = TxForegroundService.start(container.appContext)
                    override fun stop() = TxForegroundService.stop(container.appContext)
                },
            ) as T
        modelClass.isAssignableFrom(SettingsViewModel::class.java) ->
            SettingsViewModel(container.settingsStore) as T
        else -> throw IllegalArgumentException("Unknown ViewModel: ${modelClass.name}")
    }
}
