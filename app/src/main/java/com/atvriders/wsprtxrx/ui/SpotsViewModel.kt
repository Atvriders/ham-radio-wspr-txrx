package com.atvriders.wsprtxrx.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.atvriders.wsprtxrx.data.SourceFailure
import com.atvriders.wsprtxrx.data.SpotRepository
import com.atvriders.wsprtxrx.data.model.Spot
import com.atvriders.wsprtxrx.data.model.SpotQuery
import com.atvriders.wsprtxrx.data.prefs.AppSettings
import com.atvriders.wsprtxrx.data.prefs.SpotsSettings
import com.atvriders.wsprtxrx.data.qrz.CallsignLookup
import com.atvriders.wsprtxrx.data.qrz.QrzInfo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/** Sort options for the spot table. */
enum class SpotSort { TIME, SNR, DISTANCE, BAND }

/**
 * Why a whole search failed, as a closed set the UI maps to a string resource.
 *
 * Deliberately not a raw `Throwable.message`: that leaked internal text such as
 * `Unable to resolve host "db1.wspr.live"` — and, before the cancellation fix below,
 * `StandaloneCoroutine was cancelled` — straight into the error banner.
 */
enum class SearchError { NETWORK, TIMEOUT, GENERIC }

data class SpotsUiState(
    val loading: Boolean = false,
    val spots: List<Spot> = emptyList(),
    val failures: List<SourceFailure> = emptyList(),
    val error: SearchError? = null,
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
    private val settingsStore: SpotsSettings,
    private val qrzService: CallsignLookup,
) : ViewModel() {

    private val _query = MutableStateFlow(SpotQuery())
    val query: StateFlow<SpotQuery> = _query.asStateFlow()

    private val _ui = MutableStateFlow(SpotsUiState())
    val ui: StateFlow<SpotsUiState> = _ui.asStateFlow()

    private val _settings = MutableStateFlow(AppSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private var searchJob: Job? = null
    private var qrzJob: Job? = null

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
            // TEMPORARY: pre-fix behaviour, restored only to prove the regression
            // test fails against it. Reverted immediately.
            runCatching { repository.search(_query.value) }
                .onSuccess { r ->
                    _ui.value = _ui.value.copy(
                        loading = false,
                        spots = sortSpots(r.spots, _ui.value.sort),
                        failures = r.partialFailures,
                    )
                }
                .onFailure { e ->
                    _ui.value = _ui.value.copy(loading = false, error = classify(e))
                }
        }
    }

    fun select(spot: Spot?) {
        _ui.value = _ui.value.copy(selected = spot, qrz = null)
        if (spot != null) lookupQrz(spot.txCall)
    }

    private fun lookupQrz(call: String) {
        // Single-flight, same as search: selecting a second spot must not let the first
        // lookup's late result land on top of it.
        qrzJob?.cancel()
        if (_settings.value.qrzUsername.isBlank()) return
        _ui.value = _ui.value.copy(qrzLoading = true)
        qrzJob = viewModelScope.launch {
            try {
                val info = qrzService.lookup(call).getOrNull()
                ensureActive()
                _ui.value = _ui.value.copy(qrz = info, qrzLoading = false)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // A QRZ failure is not a search failure: the detail pane already renders
                // "No QRZ data", so this must never raise the error banner.
                _ui.value = _ui.value.copy(qrz = null, qrzLoading = false)
            }
        }
    }

    /**
     * Maps a throwable to the closed [SearchError] set. Raw `message` text must never
     * reach the banner — it leaked host names and internal coroutine class names.
     */
    private fun classify(e: Throwable): SearchError = when (e) {
        is SocketTimeoutException -> SearchError.TIMEOUT
        is UnknownHostException -> SearchError.NETWORK
        is IOException -> SearchError.NETWORK
        else -> SearchError.GENERIC
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
