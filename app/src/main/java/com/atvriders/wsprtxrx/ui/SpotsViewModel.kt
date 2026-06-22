package com.atvriders.wsprtxrx.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.atvriders.wsprtxrx.data.SourceFailure
import com.atvriders.wsprtxrx.data.SpotRepository
import com.atvriders.wsprtxrx.data.model.Spot
import com.atvriders.wsprtxrx.data.model.SpotQuery
import com.atvriders.wsprtxrx.data.prefs.AppSettings
import com.atvriders.wsprtxrx.data.prefs.SettingsStore
import com.atvriders.wsprtxrx.data.qrz.QrzInfo
import com.atvriders.wsprtxrx.data.qrz.QrzService
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Sort options for the spot table. */
enum class SpotSort { TIME, SNR, DISTANCE, BAND }

data class SpotsUiState(
    val loading: Boolean = false,
    val spots: List<Spot> = emptyList(),
    val failures: List<SourceFailure> = emptyList(),
    val error: String? = null,
    val sort: SpotSort = SpotSort.TIME,
    val selected: Spot? = null,
    val qrz: QrzInfo? = null,
    val qrzLoading: Boolean = false,
)

/**
 * Shared state for the Spots, Map and Charts screens: the current [SpotQuery], the
 * search results, and the selected-spot / QRZ detail. Hoisted once at the navigation
 * level so all three screens stay in sync.
 */
class SpotsViewModel(
    private val repository: SpotRepository,
    private val settingsStore: SettingsStore,
    private val qrzService: QrzService,
) : ViewModel() {

    private val _query = MutableStateFlow(SpotQuery())
    val query: StateFlow<SpotQuery> = _query.asStateFlow()

    private val _ui = MutableStateFlow(SpotsUiState())
    val ui: StateFlow<SpotsUiState> = _ui.asStateFlow()

    private val _settings = MutableStateFlow(AppSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private var searchJob: Job? = null

    init {
        viewModelScope.launch {
            settingsStore.settings.collect { _settings.value = it }
        }
        viewModelScope.launch {
            repository.cached().takeIf { it.isNotEmpty() }?.let {
                _ui.value = _ui.value.copy(spots = sortSpots(it, _ui.value.sort))
            }
            search()
        }
    }

    fun updateQuery(block: (SpotQuery) -> SpotQuery) {
        _query.value = block(_query.value)
    }

    fun setSort(sort: SpotSort) {
        _ui.value = _ui.value.copy(sort = sort, spots = sortSpots(_ui.value.spots, sort))
    }

    fun search() {
        // Single-flight: cancel any in-flight search so overlapping calls can't race
        // the RateLimiter cache or let a stale result win the StateFlow write.
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            _ui.value = _ui.value.copy(loading = true, error = null)
            runCatching { repository.search(_query.value) }
                .onSuccess { r ->
                    _ui.value = _ui.value.copy(
                        loading = false,
                        spots = sortSpots(r.spots, _ui.value.sort),
                        failures = r.partialFailures,
                    )
                }
                .onFailure { e ->
                    _ui.value = _ui.value.copy(loading = false, error = e.message ?: "Search failed")
                }
        }
    }

    fun select(spot: Spot?) {
        _ui.value = _ui.value.copy(selected = spot, qrz = null)
        if (spot != null) lookupQrz(spot.txCall)
    }

    private fun lookupQrz(call: String) {
        if (_settings.value.qrzUsername.isBlank()) return
        _ui.value = _ui.value.copy(qrzLoading = true)
        viewModelScope.launch {
            val info = qrzService.lookup(call).getOrNull()
            _ui.value = _ui.value.copy(qrz = info, qrzLoading = false)
        }
    }

    fun searchCallsign(call: String) {
        updateQuery { it.copy(callsign = call) }
        viewModelScope.launch { settingsStore.addRecentCall(call) }
        _ui.value = _ui.value.copy(selected = null)
        search()
    }

    private fun sortSpots(spots: List<Spot>, sort: SpotSort): List<Spot> = when (sort) {
        SpotSort.TIME -> spots.sortedByDescending { it.timeUtc }
        SpotSort.SNR -> spots.sortedByDescending { it.snr }
        SpotSort.DISTANCE -> spots.sortedByDescending { it.distanceKm ?: -1.0 }
        SpotSort.BAND -> spots.sortedBy { it.freqHz }
    }
}
