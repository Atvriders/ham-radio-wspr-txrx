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
 */
object RbnLineParser {
    private val LINE = Regex(
        """DX de\s+(\S+?):?\s+([0-9.]+)\s+(\S+)\s+(\S+)\s+(-?\d+)\s*dB.*?(\d{4})Z""",
    )

    fun parseSpotLine(line: String, nowMs: Long = System.currentTimeMillis()): Spot? {
        val m = LINE.find(line) ?: return null
        val skimmer = m.groupValues[1].substringBefore('-')
        val freqKHz = m.groupValues[2].toDoubleOrNull() ?: return null
        val dxCall = m.groupValues[3]
        val mode = m.groupValues[4]
        val snr = m.groupValues[5].toIntOrNull() ?: return null
        val hhmm = m.groupValues[6]
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
