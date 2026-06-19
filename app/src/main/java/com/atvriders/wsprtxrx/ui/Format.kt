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
}
