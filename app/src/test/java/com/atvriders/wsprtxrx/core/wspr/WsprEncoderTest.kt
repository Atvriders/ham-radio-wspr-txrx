package com.atvriders.wsprtxrx.core.wspr

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WsprEncoderTest {

    // Golden 162-symbol output for "K1ABC FN42 37", validated bit-exactly against the
    // WSJT-X encoder (and the published source-encoded bytes F7 0C 23 8B 0D 19 40).
    private val k1abcFn42_37 = intArrayOf(
        3, 3, 0, 0, 2, 0, 0, 0, 1, 0, 2, 0, 1, 3, 1, 2, 2, 2, 1, 0,
        0, 3, 2, 3, 1, 3, 3, 2, 2, 0, 2, 0, 0, 0, 3, 2, 0, 1, 2, 3,
        2, 2, 0, 0, 2, 2, 3, 2, 1, 1, 0, 2, 3, 3, 2, 1, 0, 2, 2, 1,
        3, 2, 1, 2, 2, 2, 0, 3, 3, 0, 3, 0, 3, 0, 1, 2, 1, 0, 2, 1,
        2, 0, 3, 2, 1, 3, 2, 0, 0, 3, 3, 2, 3, 0, 3, 2, 2, 0, 3, 0,
        2, 0, 2, 0, 1, 0, 2, 3, 0, 2, 1, 1, 1, 2, 3, 3, 0, 2, 3, 1,
        2, 1, 2, 2, 2, 1, 3, 3, 2, 0, 0, 0, 0, 1, 0, 3, 2, 0, 1, 3,
        2, 2, 2, 2, 2, 0, 2, 3, 3, 2, 3, 2, 3, 3, 2, 0, 0, 3, 1, 2,
        2, 2,
    )

    @Test fun normalizeK1abcAddsLeadingSpace() {
        assertEquals(" K1ABC", WsprMessage.normalize("K1ABC"))
    }

    @Test fun normalizeKo7mKeepsDigitInThirdPosition() {
        assertEquals("KO7M  ", WsprMessage.normalize("KO7M"))
    }

    @Test fun packsCallsign() {
        assertEquals(0xF70C238, WsprMessage.packCallsign("K1ABC"))
    }

    @Test fun packsGridAndPower() {
        assertEquals(0x2C3465, WsprMessage.packGridPower("FN42", 37))
    }

    @Test fun sourceBytesMatchPublishedVector() {
        val src = WsprMessage.sourceBytes("K1ABC", "FN42", 37)
        val first7 = src.copyOfRange(0, 7)
        assertArrayEquals(intArrayOf(0xF7, 0x0C, 0x23, 0x8B, 0x0D, 0x19, 0x40), first7)
    }

    @Test fun sourceBytesMatchSecondPublishedVector() {
        val src = WsprMessage.sourceBytes("KO7M", "CN87", 20)
        val first7 = src.copyOfRange(0, 7)
        assertArrayEquals(intArrayOf(0x8B, 0xCC, 0x46, 0x9D, 0x56, 0xB5, 0x00), first7)
    }

    @Test fun encodeProduces162SymbolsInRange() {
        val syms = WsprEncoder.encode("K1ABC", "FN42", 37)
        assertEquals(162, syms.size)
        assertTrue(syms.all { it in 0..3 })
    }

    @Test fun encodeSyncParityMatchesSyncVector() {
        val syms = WsprEncoder.encode("K1ABC", "FN42", 37)
        for (i in syms.indices) {
            assertEquals("sync mismatch at $i", WsprSync.VECTOR[i], syms[i] % 2)
        }
    }

    @Test fun encodeMatchesGoldenVector() {
        assertArrayEquals(k1abcFn42_37, WsprEncoder.encode("K1ABC", "FN42", 37))
    }
}
