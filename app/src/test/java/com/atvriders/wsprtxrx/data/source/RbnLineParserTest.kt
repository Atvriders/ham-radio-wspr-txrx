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
        assertNull(RbnLineParser.parseSpotLine("Please enter your call: "))
    }

    // --- Real-world line variants: spacing, column order and field presence vary. ---

    @Test fun parsesFt8SpotWithDifferentSpacing() {
        // FT8 skimmer spot: extra columns, lower-case "db", different spacing.
        val line = "DX de DK9IP-#:   14074.0  EA5GIE         FT8    -7 db  CQ            1455Z"
        val spot = RbnLineParser.parseSpotLine(line, nowMs = 1_700_000_000_000L)!!
        assertEquals("EA5GIE", spot.txCall)
        assertEquals("DK9IP", spot.rxCall)
        assertEquals(14_074_000L, spot.freqHz)
        assertEquals(-7, spot.snr)
        assertEquals("FT8", spot.mode)
    }

    @Test fun parsesLineWithoutWpmAndExtraComment() {
        // No WPM column, comment field present before the Z time.
        val line = "DX de OH6BG-#:    3573.0  OH2BH   FT4  12 dB  CQ DX   0023Z"
        val spot = RbnLineParser.parseSpotLine(line, nowMs = 1_700_000_000_000L)!!
        assertEquals("OH2BH", spot.txCall)
        assertEquals(3_573_000L, spot.freqHz)
        assertEquals(12, spot.snr)
        assertEquals("FT4", spot.mode)
    }

    @Test fun parsesCallWithSlashSuffix() {
        val line = "DX de W3LPL-#:   21025.0  DL1ABC/P       CW    8 dB   25 WPM  CQ   1230Z"
        val spot = RbnLineParser.parseSpotLine(line, nowMs = 1_700_000_000_000L)!!
        assertEquals("DL1ABC/P", spot.txCall)
        assertEquals(21_025_000L, spot.freqHz)
        assertEquals(8, spot.snr)
    }

    @Test fun parsesTightlySpacedLine() {
        // Minimal whitespace between columns.
        val line = "DX de N2QT-#: 28012.3 K5ZD CW 31 dB 30 WPM CQ 0902Z"
        val spot = RbnLineParser.parseSpotLine(line, nowMs = 1_700_000_000_000L)!!
        assertEquals("K5ZD", spot.txCall)
        assertEquals(28_012_300L, spot.freqHz)
        assertEquals(31, spot.snr)
        assertEquals("CW", spot.mode)
    }
}
