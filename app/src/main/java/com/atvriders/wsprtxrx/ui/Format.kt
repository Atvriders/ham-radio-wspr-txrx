package com.atvriders.wsprtxrx.ui

import androidx.compose.ui.graphics.Color
import com.atvriders.wsprtxrx.core.Band
import com.atvriders.wsprtxrx.data.model.Spot
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

private val TIME_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("MM-dd HH:mm'Z'")

object Format {
    fun freqMHz(hz: Long): String = String.format("%.6f", hz / 1_000_000.0)

    fun timeUtc(epochSec: Long): String =
        Instant.ofEpochSecond(epochSec).atZone(ZoneOffset.UTC).format(TIME_FMT)

    fun distance(km: Double?, useMiles: Boolean): String {
        if (km == null) return "—"
        return if (useMiles) "${(km * 0.621371).toInt()} mi" else "${km.toInt()} km"
    }

    fun azimuth(deg: Double?): String = deg?.let { "${it.toInt()}°" } ?: "—"

    fun bandColor(band: Band?, overrides: Map<String, Long>): Color {
        val argb = band?.let { overrides[it.name] ?: it.defaultColor } ?: 0xFF9E9E9E
        return Color(argb)
    }

    /** Green→amber→red scale for SNR readability (stronger = greener). */
    fun snrColor(snr: Int): Color = when {
        snr >= -10 -> Color(0xFF2E7D32)
        snr >= -20 -> Color(0xFF9E9D24)
        snr >= -25 -> Color(0xFFEF6C00)
        else -> Color(0xFFC62828)
    }

    fun spotTitle(spot: Spot): String = "${spot.txCall} → ${spot.rxCall}"

    private val DBM_WATTS = mapOf(
        0 to "1 mW", 3 to "2 mW", 7 to "5 mW", 10 to "10 mW", 13 to "20 mW",
        17 to "50 mW", 20 to "100 mW", 23 to "200 mW", 27 to "500 mW",
        30 to "1 W", 33 to "2 W", 37 to "5 W", 40 to "10 W", 43 to "20 W",
        47 to "50 W", 50 to "100 W", 53 to "200 W", 57 to "500 W", 60 to "1 kW",
    )

    /** Human-readable power for a dBm value, e.g. 37 → "5 W", 20 → "100 mW". */
    fun watts(dbm: Int): String = DBM_WATTS[dbm] ?: run {
        val w = Math.pow(10.0, (dbm - 30) / 10.0)
        if (w >= 1.0) String.format("%.1f W", w) else String.format("%.0f mW", w * 1000)
    }

    /** "37 dBm (5 W)" */
    fun powerLabel(dbm: Int): String = "$dbm dBm (${watts(dbm)})"
}
