package com.atvriders.wsprtxrx.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

class WsprAudioTest {

    @Test fun lengthIsSamplesPerSymbolTimesSymbols() {
        val pcm = WsprAudio.renderPcm(IntArray(162) { 0 })
        assertEquals(162 * WsprAudio.SAMPLES_PER_SYMBOL, pcm.size)
    }

    @Test fun toneSpacingIsWsprStandard() {
        assertEquals(1.46484, WsprAudio.TONE_SPACING, 0.0005)
    }

    @Test fun toneZeroIsPhaseContinuousAtCenterFrequency() {
        // Two symbols of tone 0 must equal one continuous 1500 Hz sine across the
        // symbol boundary — verifying both the frequency and phase continuity.
        val peak = 0.7 * Short.MAX_VALUE
        val pcm = WsprAudio.renderPcm(intArrayOf(0, 0), centerHz = 1500.0, sampleRate = 12000)
        assertEquals(2 * 8192, pcm.size)
        for (i in pcm.indices) {
            val ref = (sin(2.0 * PI * 1500.0 * i / 12000.0) * peak).toInt()
            assertTrue("sample $i: ${pcm[i]} vs $ref", abs(pcm[i] - ref) <= 2)
        }
    }

    @Test fun higherSymbolsUseHigherFrequencies() {
        // Symbol 3 should have more zero crossings than symbol 0 over one symbol.
        fun crossings(sym: Int): Int {
            val pcm = WsprAudio.renderPcm(intArrayOf(sym), centerHz = 1500.0)
            var c = 0
            for (i in 1 until pcm.size) if ((pcm[i - 1] < 0) != (pcm[i] < 0)) c++
            return c
        }
        assertTrue(crossings(3) >= crossings(0))
    }
}
