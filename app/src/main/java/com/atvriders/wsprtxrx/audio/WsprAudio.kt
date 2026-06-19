package com.atvriders.wsprtxrx.audio

import kotlin.math.PI
import kotlin.math.sin

/**
 * Renders WSPR channel symbols to 16-bit PCM audio as a phase-continuous 4-FSK signal.
 *
 * The WSPR keying rate and tone spacing are both 12000/8192 ≈ 1.4648 Hz; each symbol
 * is 8192 samples at the canonical 12 kHz rate, giving the standard ~110.6 s
 * transmission for 162 symbols. The four tones are centerHz + symbol * toneSpacing.
 */
object WsprAudio {
    const val SAMPLE_RATE = 12000
    const val SAMPLES_PER_SYMBOL = 8192

    /** WSPR symbol/tone spacing in Hz (also the baud rate). */
    val TONE_SPACING: Double = SAMPLE_RATE.toDouble() / SAMPLES_PER_SYMBOL

    /**
     * Renders [symbols] (each 0..3) to mono 16-bit PCM at [sampleRate].
     *
     * @param centerHz audio frequency of tone 0 (USB), default 1500 Hz.
     * @param amplitude peak amplitude as a fraction of full scale (0..1).
     */
    fun renderPcm(
        symbols: IntArray,
        centerHz: Double = 1500.0,
        sampleRate: Int = SAMPLE_RATE,
        amplitude: Double = 0.7,
    ): ShortArray {
        val samplesPerSymbol = (sampleRate.toLong() * SAMPLES_PER_SYMBOL / SAMPLE_RATE).toInt()
        val out = ShortArray(symbols.size * samplesPerSymbol)
        val peak = (amplitude.coerceIn(0.0, 1.0) * Short.MAX_VALUE)
        val twoPi = 2.0 * PI
        var phase = 0.0
        var idx = 0
        for (sym in symbols) {
            val freq = centerHz + sym * TONE_SPACING
            val dPhase = twoPi * freq / sampleRate
            for (s in 0 until samplesPerSymbol) {
                out[idx++] = (sin(phase) * peak).toInt().toShort()
                phase += dPhase
                if (phase >= twoPi) phase -= twoPi
            }
        }
        return out
    }
}
