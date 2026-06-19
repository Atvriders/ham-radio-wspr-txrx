package com.atvriders.wsprtxrx.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.atvriders.wsprtxrx.data.model.SourceId
import com.atvriders.wsprtxrx.data.prefs.AppSettings
import com.atvriders.wsprtxrx.data.prefs.SettingsStore
import com.atvriders.wsprtxrx.ui.theme.ThemeMode
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(private val store: SettingsStore) : ViewModel() {

    val settings: StateFlow<AppSettings> =
        store.settings.stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    fun toggleSource(source: SourceId, enabled: Boolean) = viewModelScope.launch {
        val current = settings.value.enabledSources.toMutableSet()
        if (enabled) current.add(source) else current.remove(source)
        store.setEnabledSources(current.ifEmpty { setOf(SourceId.WSPR_LIVE) })
    }

    fun setQrz(username: String, password: String) = viewModelScope.launch {
        store.setQrz(username.trim(), password)
    }

    fun setTimeRange(minutes: Int) = viewModelScope.launch { store.setDefaultTimeRange(minutes) }

    fun setUseMiles(useMiles: Boolean) = viewModelScope.launch { store.setUseMiles(useMiles) }

    fun setTheme(mode: ThemeMode) = viewModelScope.launch { store.setThemeMode(mode) }

    fun setBandColor(bandName: String, color: Long) = viewModelScope.launch {
        val overrides = settings.value.bandColorOverrides.toMutableMap()
        overrides[bandName] = color
        store.setBandColorOverrides(overrides)
    }

    fun resetBandColors() = viewModelScope.launch { store.setBandColorOverrides(emptyMap()) }

    fun removeRecentCall(call: String) = viewModelScope.launch {
        store.setRecentCalls(settings.value.recentCalls.filterNot { it == call })
    }
}
