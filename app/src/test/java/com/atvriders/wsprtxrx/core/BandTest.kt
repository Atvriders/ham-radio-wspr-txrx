package com.atvriders.wsprtxrx.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BandTest {
    @Test fun classifies20m() {
        assertEquals(Band.M20, bandForFreq(14_097_100))
    }

    @Test fun classifies40m() {
        assertEquals(Band.M40, bandForFreq(7_040_100))
    }

    @Test fun classifies30m() {
        assertEquals(Band.M30, bandForFreq(10_138_700))
    }

    @Test fun unknownFrequencyIsNull() {
        assertNull(bandForFreq(1L))
        assertNull(bandForFreq(9_000_000L))
    }

    @Test fun orderedIsLowToHigh() {
        val ordered = Band.ordered
        assertEquals(Band.LF2190, ordered.first())
        assertEquals(Band.M2, ordered.last())
    }
}
