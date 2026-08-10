package com.atvriders.wsprtxrx.ui

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.atvriders.wsprtxrx.audio.TxForegroundService
import com.atvriders.wsprtxrx.audio.WsprPlayer
import com.atvriders.wsprtxrx.di.AppContainer

/** Builds the app's ViewModels with their dependencies from the [AppContainer]. */
class WsprViewModelFactory(private val container: AppContainer) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = when {
        modelClass.isAssignableFrom(SpotsViewModel::class.java) ->
            SpotsViewModel(container.repository, container.settingsStore, container.qrzService) as T
        modelClass.isAssignableFrom(TxViewModel::class.java) -> createTxViewModel() as T
        modelClass.isAssignableFrom(SettingsViewModel::class.java) ->
            SettingsViewModel(container.settingsStore) as T
        else -> throw IllegalArgumentException("Unknown ViewModel: ${modelClass.name}")
    }

    private fun createTxViewModel(): TxViewModel {
        val vm = TxViewModel(
            player = WsprPlayer(container.appContext),
            // elapsedRealtime keeps counting in Doze and is immune to wall-clock steps.
            monotonicMs = SystemClock::elapsedRealtime,
            keepAlive = object : TxKeepAlive {
                override fun start(): Boolean = TxForegroundService.start(container.appContext)
                override fun transmitting(endAtMs: Long) =
                    TxForegroundService.transmitting(container.appContext, endAtMs)
                override fun stop() = TxForegroundService.stop(container.appContext)
            },
        )
        // Wire the notification's Stop action to the transmit job, not just the service.
        container.onTxStopRequested = { vm.stop() }
        return vm
    }
}
