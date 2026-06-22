package com.atvriders.wsprtxrx.data.source

import com.atvriders.wsprtxrx.data.model.SourceId
import com.atvriders.wsprtxrx.data.model.Spot
import java.time.Instant
import java.time.ZoneOffset

/**
 * Parses a single Reverse Beacon Network telnet "DX de" spot line into a [Spot].
 *
 * Example:
 * `DX de SM6FMB-#:    7016.0  S57DX          CW    19 dB  28 WPM  CQ      1849Z`
 *
 * The skimmer (spotter) is the receiver; the DX call is the transmitter. RBN lines
 * carry no grid, and the timestamp is HHMM UTC with no date, so the date is taken
 * from [nowMs].
 *
 * Parsing is intentionally *positional and tolerant* rather than column-rigid: real RBN
 * front-ends vary the spacing and the order/presence of the WPM, dB, comment and mode
 * fields. We anchor on three robust landmarks that appear in essentially every spot
 * line — the frequency (the first decimal number after the skimmer call), the SNR (the
 * signed integer immediately preceding a `dB`/`db` token), and the 4-digit `Z` time at
 * the end — and read the DX call and mode relative to those. Anything that isn't a
 * recognizable "DX de" spot line (banners, prompts, blanks) returns null.
 */
object RbnLineParser {
    /** Lines must begin with the RBN spot prefix. */
    private val PREFIX = Regex("""^\s*DX de\s+(\S+?):?\s+(.*)$""")

    /** First decimal/number token = frequency in kHz, and what follows it. */
    private val FREQ = Regex("""([0-9]+(?:\.[0-9]+)?)\s+(.*)$""")

    /** Signed integer immediately before a dB token = SNR. */
    private val SNR_DB = Regex("""(-?\d+)\s*dB""", RegexOption.IGNORE_CASE)

    /** Trailing 4-digit Zulu time. */
    private val ZTIME = Regex("""(\d{4})\s*Z""", RegexOption.IGNORE_CASE)

    /** A plausible amateur callsign (lets us pick the DX call out of the remainder). */
    private val CALLSIGN = Regex("""[A-Z0-9]{1,3}[0-9][A-Z0-9]*(?:/[A-Z0-9]+)?""", RegexOption.IGNORE_CASE)

    fun parseSpotLine(line: String, nowMs: Long = System.currentTimeMillis()): Spot? {
        val prefix = PREFIX.find(line) ?: return null
        val skimmer = prefix.groupValues[1].substringBefore('-')
        val afterCall = prefix.groupValues[2]

        // Frequency: first numeric token after the skimmer call.
        val freqMatch = FREQ.find(afterCall) ?: return null
        val freqKHz = freqMatch.groupValues[1].toDoubleOrNull() ?: return null
        // Reject obviously non-frequency leading numbers (RBN freqs are >= ~135 kHz).
        if (freqKHz < 100.0) return null
        val afterFreq = freqMatch.groupValues[2]

        // SNR: the signed integer just before a dB token; required for a real spot line.
        val snr = SNR_DB.find(afterFreq)?.groupValues?.get(1)?.toIntOrNull() ?: return null

        // Time: trailing 4-digit Z; required.
        val hhmm = ZTIME.find(afterFreq)?.groupValues?.get(1) ?: return null
        if (!isValidHhmm(hhmm)) return null

        // DX call: the first callsign-shaped token after the frequency.
        val dxCall = CALLSIGN.find(afterFreq)?.value?.uppercase() ?: return null

        // Mode: the token immediately following the DX call, if it isn't the dB/WPM field.
        val mode = modeAfter(afterFreq, dxCall)

        return Spot(
            txCall = dxCall,
            txGrid = null,
            rxCall = skimmer,
            rxGrid = null,
            freqHz = (freqKHz * 1000.0).toLong(),
            snr = snr,
            timeUtc = epochForHhmm(hhmm, nowMs),
            source = SourceId.RBN,
            mode = mode,
        )
    }

    /** Picks the mode token (the word right after the DX call), defaulting to "CW". */
    private fun modeAfter(afterFreq: String, dxCall: String): String {
        val idx = afterFreq.indexOf(dxCall, ignoreCase = true)
        if (idx < 0) return "CW"
        val rest = afterFreq.substring(idx + dxCall.length).trim()
        val token = rest.split(Regex("\\s+")).firstOrNull()?.uppercase() ?: return "CW"
        // The next token is the mode unless it's a numeric (SNR) or a dB/WPM marker.
        return if (token.isNotEmpty() && token.all { it.isLetter() }) token else "CW"
    }

    private fun isValidHhmm(hhmm: String): Boolean {
        val h = hhmm.substring(0, 2).toIntOrNull() ?: return false
        val m = hhmm.substring(2, 4).toIntOrNull() ?: return false
        return h in 0..23 && m in 0..59
    }

    /** Resolves an HHMM UTC time-of-day against the date implied by [nowMs]. */
    private fun epochForHhmm(hhmm: String, nowMs: Long): Long {
        val h = hhmm.substring(0, 2).toInt()
        val min = hhmm.substring(2, 4).toInt()
        val nowDate = Instant.ofEpochMilli(nowMs).atZone(ZoneOffset.UTC).toLocalDate()
        val candidate = nowDate.atTime(h, min).toEpochSecond(ZoneOffset.UTC)
        val nowSec = nowMs / 1000
        // If the computed time is far in the future, it belongs to the previous day.
        return if (candidate - nowSec > 12 * 3600) candidate - 86_400 else candidate
    }
}
