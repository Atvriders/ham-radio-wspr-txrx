package com.atvriders.wsprtxrx.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.atvriders.wsprtxrx.audio.TxAudioSink
import com.atvriders.wsprtxrx.audio.TxScheduler
import com.atvriders.wsprtxrx.audio.WsprAudio
import com.atvriders.wsprtxrx.core.Maidenhead
import com.atvriders.wsprtxrx.core.wspr.WsprEncoder
import com.atvriders.wsprtxrx.core.wspr.WsprMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    /** Non-fatal condition: transmit continues, but with reduced protection. */
    val warning: TxWarning? = null,
) {
    val validCallsign: Boolean get() = callsign.isNotBlank() && runCatching { WsprMessage.packCallsign(callsign) }.isSuccess
    val validGrid: Boolean get() = grid.length >= 4 && Maidenhead.gridToLatLonOrNull(grid) != null
    val canTransmit: Boolean
        get() = validCallsign && validGrid && (phase == TxPhase.IDLE || phase == TxPhase.DONE)
}

/** Non-fatal conditions the UI renders as a warning rather than an error. */
enum class TxWarning { KEEP_ALIVE_UNAVAILABLE }

/**
 * Hooks for keeping the transmit alive outside the composition (a foreground service).
 * Defaults to a no-op so the ViewModel stays unit-testable without Android.
 */
interface TxKeepAlive {
    /** Begin keeping the process/audio alive; return false if it couldn't be started. */
    fun start(): Boolean

    /**
     * Signals that the wait is over and the tone is now playing, ending at [endAtMs]
     * (wall-clock epoch millis) — lets the notification show a live count-down.
     */
    fun transmitting(endAtMs: Long) {}

    /** Stop keeping alive (idempotent). */
    fun stop()

    companion object {
        val NONE: TxKeepAlive = object : TxKeepAlive {
            override fun start(): Boolean = false
            override fun stop() {}
        }
    }
}

/** Drives the WSPR transmit flow: validate, encode, optionally wait for the even UTC minute, play. */
class TxViewModel(
    private val player: TxAudioSink = TxAudioSink.NONE,
    private val now: () -> Long = System::currentTimeMillis,
    /**
     * Monotonic milliseconds, used to time the wait. Wall-clock alone is unsafe: an NTP
     * step or a user clock change during the countdown would skew it. Android injects
     * `SystemClock::elapsedRealtime`, which also keeps counting through Doze.
     */
    private val monotonicMs: () -> Long = { System.nanoTime() / 1_000_000L },
    private val keepAlive: TxKeepAlive = TxKeepAlive.NONE,
    /** Where the ~1.3 M sin() calls of PCM rendering run. Injected so tests stay virtual-time. */
    private val computeDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {

    private val _ui = MutableStateFlow(TxUiState())
    val ui: StateFlow<TxUiState> = _ui.asStateFlow()

    private var txJob: Job? = null

    /**
     * Guards the keep-alive against a stale job's `finally` tearing down the keep-alive
     * that a *newer* transmit just started (cancellation is asynchronous).
     */
    private var generation = 0

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

        // Start the keep-alive synchronously, on the tap, while the app is demonstrably
        // in the foreground. Deferring it until after the up-to-two-minute wait — as the
        // old code did — meant the process was already background by then, so
        // startForegroundService() was refused and, from Android 15, audio focus with it.
        // The one scenario the service exists for was the one it could not cover.
        val gen = ++generation
        val keepAliveStarted = runCatching { keepAlive.start() }.getOrDefault(false)

        txJob = viewModelScope.launch {
            try {
                update {
                    it.copy(
                        error = null,
                        warning = if (keepAliveStarted) null else TxWarning.KEEP_ALIVE_UNAVAILABLE,
                    )
                }

                val symbols = WsprEncoder.encode(state.callsign, state.grid.take(4), state.powerDbm)
                // 162 * 8192 sin() calls: off the main dispatcher.
                val pcm = withContext(computeDispatcher) { WsprAudio.renderPcm(symbols) }

                if (state.waitForEvenMinute) {
                    if (!awaitSlot()) {
                        update {
                            it.copy(
                                phase = TxPhase.IDLE,
                                secondsUntilStart = 0,
                                error = "Missed the transmit slot (device was asleep). Try again.",
                            )
                        }
                        return@launch
                    }
                }

                update { it.copy(phase = TxPhase.TRANSMITTING, secondsUntilStart = 0, progress = 0f) }
                val durationMs = pcm.size * 1000L / WsprAudio.SAMPLE_RATE
                runCatching { keepAlive.transmitting(now() + durationMs) }
                player.play(pcm) { p -> update { it.copy(progress = p) } }
                update { it.copy(phase = TxPhase.DONE, progress = 1f) }
            } catch (e: CancellationException) {
                // Stop() / onCleared() — not an error. Rethrow so the coroutine machinery
                // sees a normal cancellation instead of leaking "StandaloneCoroutine was
                // cancelled" into the UI as an error string.
                throw e
            } catch (e: Exception) {
                update { it.copy(phase = TxPhase.IDLE, error = e.message ?: "Transmit failed") }
            } finally {
                if (gen == generation) runCatching { keepAlive.stop() }
            }
        }
    }

    /**
     * Waits for the next WSPR slot (even UTC minute + 1 s), counting down monotonically.
     *
     * Returns false if the slot could not be hit: if the process was frozen and thawed
     * late, transmitting immediately would emit at an arbitrary phase of the slot — an
     * out-of-slot, undecodable signal on shared spectrum. In that case the wait re-arms
     * for the following slot, and only gives up after [MAX_SLOT_ATTEMPTS] tries.
     */
    private suspend fun awaitSlot(): Boolean {
        update { it.copy(phase = TxPhase.WAITING, error = null) }
        repeat(MAX_SLOT_ATTEMPTS) {
            val slotWall = now() + TxScheduler.msUntilNextEvenMinute(now())
            // Anchor the countdown to the monotonic clock but keep the wall-clock target
            // so lateness can be measured against real UTC afterwards.
            val slotMono = monotonicMs() + (slotWall - now()).coerceAtLeast(0L)
            while (true) {
                val remaining = slotMono - monotonicMs()
                if (remaining <= 0L) break
                update { current -> current.copy(secondsUntilStart = ceil(remaining / 1000.0).toInt()) }
                delay(remaining.coerceAtMost(TICK_MS))
            }
            if (now() - slotWall <= MAX_SLOT_DRIFT_MS) return true
        }
        return false
    }

    fun stop() {
        generation++
        txJob?.cancel()
        txJob = null
        runCatching { keepAlive.stop() }
        update { it.copy(phase = TxPhase.IDLE, progress = 0f, secondsUntilStart = 0, error = null) }
    }

    override fun onCleared() {
        generation++
        txJob?.cancel()
        runCatching { keepAlive.stop() }
        super.onCleared()
    }

    private inline fun update(block: (TxUiState) -> TxUiState) {
        _ui.value = block(_ui.value)
    }

    private companion object {
        /** Countdown UI tick. */
        const val TICK_MS = 250L

        /**
         * How late the slot start may be before the emission is considered out-of-slot.
         * WSPR decoders tolerate roughly ±1 s of start error; 1.5 s is already outside
         * anything useful, so past that it is better to re-arm than to key up.
         */
        const val MAX_SLOT_DRIFT_MS = 1_500L

        /** Re-arms before giving up (each attempt covers at most one 120 s slot). */
        const val MAX_SLOT_ATTEMPTS = 3
    }
}
