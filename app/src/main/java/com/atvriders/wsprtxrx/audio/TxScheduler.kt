package com.atvriders.wsprtxrx.audio

/**
 * Timing for WSPR transmissions, which must begin one second into an even UTC minute.
 * Even minutes are aligned to the Unix epoch (minute 0 is even), so a two-minute window
 * measured from epoch milliseconds gives the correct cadence.
 */
object TxScheduler {
    private const val PERIOD_MS = 120_000L
    private const val START_OFFSET_MS = 1_000L

    /**
     * Milliseconds from [nowMs] (Unix epoch millis, UTC) until the start of the next
     * WSPR transmit slot (even minute + 1 s).
     */
    fun msUntilNextEvenMinute(nowMs: Long): Long {
        val phase = ((nowMs % PERIOD_MS) + PERIOD_MS) % PERIOD_MS
        return if (phase <= START_OFFSET_MS) {
            START_OFFSET_MS - phase
        } else {
            PERIOD_MS - phase + START_OFFSET_MS
        }
    }
}
