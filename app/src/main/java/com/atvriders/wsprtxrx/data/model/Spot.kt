package com.atvriders.wsprtxrx.data.model

import com.atvriders.wsprtxrx.core.Band
import com.atvriders.wsprtxrx.core.GreatCircle
import com.atvriders.wsprtxrx.core.LatLon
import com.atvriders.wsprtxrx.core.Maidenhead
import com.atvriders.wsprtxrx.core.bandForFreq

/**
 * One reception report: a single receiver decoding a single transmitter at a point in
 * time. Geometry fields (lat/lon, distance, azimuth) may be null until [withGeometry]
 * fills them from the grid locators.
 */
data class Spot(
    val txCall: String,
    val txGrid: String?,
    val rxCall: String,
    val rxGrid: String?,
    val freqHz: Long,
    val snr: Int,
    val drift: Int = 0,
    val powerDbm: Int? = null,
    val timeUtc: Long, // epoch seconds
    val source: SourceId,
    val mode: String = "WSPR",
    val txLat: Double? = null,
    val txLon: Double? = null,
    val rxLat: Double? = null,
    val rxLon: Double? = null,
    val distanceKm: Double? = null,
    val azimuthDeg: Double? = null,
) {
    val band: Band? get() = bandForFreq(freqHz)

    /**
     * Returns a copy with lat/lon for both ends plus distance and azimuth (tx -> rx)
     * derived from the grid locators. Existing non-null geometry is preserved.
     */
    fun withGeometry(): Spot {
        val tx = txLatLon() ?: gridLatLon(txGrid)
        val rx = rxLatLon() ?: gridLatLon(rxGrid)
        val dist = distanceKm ?: if (tx != null && rx != null) GreatCircle.distanceKm(tx, rx) else null
        val az = azimuthDeg ?: if (tx != null && rx != null) GreatCircle.azimuthDeg(tx, rx) else null
        return copy(
            txLat = tx?.lat, txLon = tx?.lon,
            rxLat = rx?.lat, rxLon = rx?.lon,
            distanceKm = dist, azimuthDeg = az,
        )
    }

    private fun txLatLon(): LatLon? = if (txLat != null && txLon != null) LatLon(txLat, txLon) else null
    private fun rxLatLon(): LatLon? = if (rxLat != null && rxLon != null) LatLon(rxLat, rxLon) else null

    private fun gridLatLon(grid: String?): LatLon? =
        grid?.takeIf { it.length >= 4 }?.let {
            runCatching { Maidenhead.gridToLatLon(it) }.getOrNull()
        }

    /** Stable key for de-duplicating the same report seen via multiple sources. */
    fun dedupKey(): String = "$txCall|$rxCall|$freqHz|${timeUtc / 120}"
}
