package com.atvriders.wsprtxrx.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.atvriders.wsprtxrx.audio.WsprAudio
import com.atvriders.wsprtxrx.audio.WsprPlayer
import com.atvriders.wsprtxrx.audio.TxScheduler
import com.atvriders.wsprtxrx.core.Maidenhead
import com.atvriders.wsprtxrx.core.wspr.WsprEncoder
import com.atvriders.wsprtxrx.core.wspr.WsprMessage
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.ceil

enum class TxPhase { IDLE, WAITING, TRANSMITTING, DONE }

data class TxUiState(
    val callsign: String = "",
    val grid: String = "",
    val powerDbm: Int = 37,
    val waitForEvenMinute: Boolean = true,
    val phase: TxPhase = TxPhase.IDLE,
    val progress: Float = 0f,
    val secondsUntilStart: Int = 0,
    val error: String? = null,
) {
    val validCallsign: Boolean get() = callsign.isNotBlank() && runCatching { WsprMessage.packCallsign(callsign) }.isSuccess
    val validGrid: Boolean get() = grid.length >= 4 && Maidenhead.gridToLatLonOrNull(grid) != null
    val canTransmit: Boolean
        get() = validCallsign && validGrid && (phase == TxPhase.IDLE || phase == TxPhase.DONE)
}

/** Drives the WSPR transmit flow: validate, encode, optionally wait for the even UTC minute, play. */
class TxViewModel(
    private val player: WsprPlayer = WsprPlayer(),
    private val now: () -> Long = System::currentTimeMillis,
) : ViewModel() {

    private val _ui = MutableStateFlow(TxUiState())
    val ui: StateFlow<TxUiState> = _ui.asStateFlow()

    private var txJob: Job? = null

    fun setCallsign(value: String) = update { it.copy(callsign = value.uppercase().trim()) }
    fun setGrid(value: String) = update { it.copy(grid = value.uppercase().trim()) }
    fun setPower(dbm: Int) = update { it.copy(powerDbm = dbm) }
    fun setWaitForEvenMinute(wait: Boolean) = update { it.copy(waitForEvenMinute = wait) }

    /** Fills the grid field from a device location (6-char locator). */
    fun fillGridFromLocation(lat: Double, lon: Double) =
        update { it.copy(grid = Maidenhead.latLonToGrid(lat, lon, 6).uppercase()) }

    fun transmit() {
        val state = _ui.value
        if (!state.validCallsign || !state.validGrid) {
            update { it.copy(error = "Enter a valid callsign and grid") }
            return
        }
        txJob?.cancel()
        txJob = viewModelScope.launch {
            try {
                val symbols = WsprEncoder.encode(state.callsign, state.grid.take(4), state.powerDbm)
                val pcm = WsprAudio.renderPcm(symbols)

                if (state.waitForEvenMinute) {
                    val startAt = now() + TxScheduler.msUntilNextEvenMinute(now())
                    update { it.copy(phase = TxPhase.WAITING, error = null) }
                    while (true) {
                        val remaining = startAt - now()
                        if (remaining <= 0) break
                        update { it.copy(secondsUntilStart = ceil(remaining / 1000.0).toInt()) }
                        delay(minOf(250L, remaining))
                    }
                }

                update { it.copy(phase = TxPhase.TRANSMITTING, secondsUntilStart = 0, progress = 0f) }
                player.play(pcm) { p -> update { it.copy(progress = p) } }
                update { it.copy(phase = TxPhase.DONE, progress = 1f) }
            } catch (e: Exception) {
                update { it.copy(phase = TxPhase.IDLE, error = e.message ?: "Transmit failed") }
            }
        }
    }

    fun stop() {
        txJob?.cancel()
        update { it.copy(phase = TxPhase.IDLE, progress = 0f, secondsUntilStart = 0) }
    }

    override fun onCleared() {
        txJob?.cancel()
        super.onCleared()
    }

    private inline fun update(block: (TxUiState) -> TxUiState) {
        _ui.value = block(_ui.value)
    }
}
