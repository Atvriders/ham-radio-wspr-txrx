package com.atvriders.wsprtxrx.core.wspr

/**
 * Full WSPR type-1 channel encoder. Produces the 162 four-level (0..3) channel symbols
 * for transmission, given a callsign, 4-character grid locator, and power in dBm.
 *
 * Pipeline: [WsprMessage.sourceBytes] -> rate-1/2, K=32 convolutional code
 * (Layland-Lushbaugh polynomials, MSB-first, POLY1 parity before POLY2) -> 8-bit
 * bit-reversal interleave -> combine with [WsprSync]. Validated bit-exactly against
 * the WSJT-X encoder for "K1ABC FN42 37".
 */
object WsprEncoder {
    private val POLY1 = 0xF2D05351.toInt()
    private val POLY2 = 0xE4613C47.toInt()

    /** Returns the 162 channel symbols (each 0..3) for the given message. */
    fun encode(callsign: String, grid4: String, dbm: Int): IntArray {
        val data = WsprMessage.sourceBytes(callsign, grid4, dbm)

        // Convolutional encoder: feed each bit MSB-first, emit POLY1 parity then POLY2.
        val enc = IntArray(data.size * 8 * 2)
        var state = 0
        var idx = 0
        for (byte in data) {
            for (i in 7 downTo 0) {
                state = (state shl 1) or ((byte shr i) and 1)
                enc[idx++] = Integer.bitCount(state and POLY1) and 1
                enc[idx++] = Integer.bitCount(state and POLY2) and 1
            }
        }

        // Interleave the first 162 encoded bits by 8-bit index bit-reversal.
        val interleaved = IntArray(162)
        var p = 0
        var i = 0
        while (p < 162) {
            val rev = reverse8(i)
            if (rev < 162) {
                interleaved[rev] = enc[p]
                p++
            }
            i++
        }

        return IntArray(162) { k -> WsprSync.VECTOR[k] + 2 * interleaved[k] }
    }

    /** Reverses the low 8 bits of [x]. */
    private fun reverse8(x: Int): Int {
        var r = 0
        for (b in 0..7) {
            r = (r shl 1) or ((x shr b) and 1)
        }
        return r
    }
}
