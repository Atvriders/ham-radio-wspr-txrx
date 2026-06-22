package com.atvriders.wsprtxrx.core.wspr

/**
 * Source encoding (bit packing) of a WSPR type-1 message: callsign (28 bits) plus
 * 4-character grid locator and power in dBm (22 bits) = 50 bits, laid out into the
 * first 7 of 11 bytes (the remaining bytes are the convolutional-encoder zero tail).
 *
 * Constants and layout follow the WSJT-X reference (wsprsim_utils.c). Verified against
 * the published WSPRcode vectors: "K1ABC FN42 37" -> F7 0C 23 8B 0D 19 40 and
 * "KO7M CN87 20" -> 8B CC 46 9D 56 B5 00.
 */
object WsprMessage {

    /** The power values (dBm) offered by standard WSPR software (ending in 0, 3 or 7). */
    val VALID_POWERS = listOf(0, 3, 7, 10, 13, 17, 20, 23, 27, 30, 33, 37, 40, 43, 47, 50, 53, 57, 60)

    private fun callValue(ch: Char): Int = when {
        ch in '0'..'9' -> ch - '0'
        ch == ' ' -> 36
        ch in 'A'..'Z' -> ch - 'A' + 10
        else -> throw IllegalArgumentException("invalid callsign character: '$ch'")
    }

    private fun locValue(ch: Char): Int = when {
        ch in '0'..'9' -> ch - '0'
        ch == ' ' -> 36
        ch in 'A'..'Z' -> ch - 'A'
        else -> throw IllegalArgumentException("invalid locator character: '$ch'")
    }

    /**
     * Canonical WSPR type-1 callsign: 1-6 letters/digits, with an optional single `/`
     * separator (e.g. a compound prefix/suffix). No interior spaces. The packed form only
     * supports characters A-Z, 0-9 and the implicit space pad, so the guard rejects
     * anything else early with a clear message rather than producing a corrupt frame.
     */
    private val CALLSIGN_RE = Regex("^[A-Z0-9]{1,6}(?:/[A-Z0-9]{1,6})?$")

    /**
     * Right-justifies the callsign so its third character is a digit and pads to 6
     * characters with trailing spaces.
     *
     * Rejects empty callsigns and any callsign with interior spaces or illegal
     * characters (e.g. "K1 BC") with an explicit message.
     */
    fun normalize(callsign: String): String {
        val c = callsign.trim().uppercase()
        require(c.isNotEmpty()) { "callsign must not be empty" }
        require(' ' !in c) { "callsign must not contain interior spaces: '$callsign'" }
        require(CALLSIGN_RE.matches(c)) {
            "not a valid WSPR callsign (letters/digits, optional one '/'): '$callsign'"
        }
        require(c.length <= 6) { "callsign longer than 6 characters: $callsign" }
        val padded = (c + "      ").substring(0, 6)
        return when {
            padded[2] in '0'..'9' -> padded
            padded.length >= 2 && c.length >= 2 && c[1] in '0'..'9' ->
                (" $c" + "      ").substring(0, 6)
            else -> throw IllegalArgumentException("cannot place a digit in the 3rd position: $callsign")
        }
    }

    /** Packs the (normalized) callsign into a 28-bit integer. */
    fun packCallsign(callsign: String): Int {
        val c = normalize(callsign)
        var n = callValue(c[0])
        n = n * 36 + callValue(c[1])
        n = n * 10 + callValue(c[2])
        n = n * 27 + (callValue(c[3]) - 10)
        n = n * 27 + (callValue(c[4]) - 10)
        n = n * 27 + (callValue(c[5]) - 10)
        return n
    }

    /** Packs a 4-character grid locator plus power (dBm) into a 22-bit integer. */
    fun packGridPower(grid4: String, dbm: Int): Int {
        val g = grid4.trim().uppercase()
        require(g.length >= 4) { "grid must be at least 4 characters: $grid4" }
        // The field letters must be A..R: anything beyond R makes the packed value
        // negative, so a corrupt locator can never be transmitted as a valid frame.
        require(g[0] in 'A'..'R' && g[1] in 'A'..'R') { "grid field out of range (A-R): $grid4" }
        require(g[2] in '0'..'9' && g[3] in '0'..'9') { "grid square must be digits: $grid4" }
        require(dbm in VALID_POWERS) { "power not a valid WSPR dBm value: $dbm" }
        var m = (179 - 10 * locValue(g[0]) - locValue(g[2])) * 180 + 10 * locValue(g[1]) + locValue(g[3])
        m = m * 128 + dbm + 64
        return m
    }

    /**
     * Returns the 11-byte source-encoded message (as ints 0..255). The first 7 bytes
     * carry the 50 message bits; bytes 7..10 are the zero tail.
     */
    fun sourceBytes(callsign: String, grid4: String, dbm: Int): IntArray {
        val n = packCallsign(callsign)
        val m = packGridPower(grid4, dbm)
        val data = IntArray(11)
        data[0] = 0xFF and (n shr 20)
        data[1] = 0xFF and (n shr 12)
        data[2] = 0xFF and (n shr 4)
        data[3] = ((n and 0x0F) shl 4) or ((m shr 18) and 0x0F)
        data[4] = 0xFF and (m shr 10)
        data[5] = 0xFF and (m shr 2)
        data[6] = (m and 0x03) shl 6
        return data
    }
}
