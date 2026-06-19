package com.atvriders.wsprtxrx.data.source

import com.atvriders.wsprtxrx.data.model.SourceId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RbnLineParserTest {
    @Test fun parsesStandardSpotLine() {
        val line = "DX de SM6FMB-#:    7016.0  S57DX          CW    19 dB  28 WPM  CQ      1849Z"
        val spot = RbnLineParser.parseSpotLine(line, nowMs = 1_700_000_000_000L)!!
        assertEquals("S57DX", spot.txCall)
        assertEquals("SM6FMB", spot.rxCall)
        assertEquals(7_016_000L, spot.freqHz)
        assertEquals(19, spot.snr)
        assertEquals("CW", spot.mode)
        assertEquals(SourceId.RBN, spot.source)
    }

    @Test fun parsesNegativeSnr() {
        val line = "DX de W3LPL-#:    14025.5  K1ABC          CW    -5 dB  22 WPM  CQ      0301Z"
        val spot = RbnLineParser.parseSpotLine(line, nowMs = 1_700_000_000_000L)!!
        assertEquals("K1ABC", spot.txCall)
        assertEquals(-5, spot.snr)
        assertEquals(14_025_500L, spot.freqHz)
    }

    @Test fun returnsNullForNonSpotLine() {
        assertNull(RbnLineParser.parseSpotLine("Welcome to the Reverse Beacon Network!"))
        assertNull(RbnLineParser.parseSpotLine(""))
    }
}
