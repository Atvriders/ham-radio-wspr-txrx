package com.atvriders.wsprtxrx.core

import java.time.Instant
import java.time.ZoneOffset
import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * Computes the subsolar point and the day/night terminator ("grey line") for a given
 * UTC instant. Uses a simplified solar model accurate to roughly half a degree, which
 * is ample for drawing the grey line on a propagation map.
 */
object SolarTerminator {

    /** The point on Earth where the sun is directly overhead at [epochSec] (UTC). */
    fun subsolarPoint(epochSec: Long): LatLon {
        val zdt = Instant.ofEpochSecond(epochSec).atZone(ZoneOffset.UTC)
        val dayOfYear = zdt.dayOfYear
        val secondOfDay = zdt.toLocalTime().toSecondOfDay()

        // Cooper's equation for solar declination (degrees).
        val decl = 23.45 * sin(Math.toRadians(360.0 * (284 + dayOfYear) / 365.0))

        // Subsolar longitude: where it is local solar noon. 12:00 UTC -> 0deg, moving
        // 15 deg west per hour. (Equation of time is neglected.)
        var lon = 180.0 - secondOfDay / 240.0
        lon = ((lon + 180.0) % 360.0 + 360.0) % 360.0 - 180.0
        return LatLon(decl, lon)
    }

    /**
     * The terminator as a closed ring of [steps] points, each lying 90 degrees of arc
     * from the subsolar point (the great circle dividing day from night).
     */
    fun terminatorPolygon(epochSec: Long, steps: Int = 180): List<LatLon> {
        val sun = subsolarPoint(epochSec)
        val lat1 = Math.toRadians(sun.lat)
        val lon1 = Math.toRadians(sun.lon)
        val d = PI / 2.0 // 90 degrees
        val out = ArrayList<LatLon>(steps)
        for (i in 0 until steps) {
            val brng = 2.0 * PI * i / steps
            // Destination point at angular distance d on bearing brng. With d = 90deg,
            // cos d = 0 and sin d = 1, simplifying the standard formula.
            val lat2 = asin(cos(lat1) * cos(brng))
            val lon2 = lon1 + atan2(sin(brng) * cos(lat1), -sin(lat1) * sin(lat2))
            out.add(LatLon(Math.toDegrees(lat2), normalizeLon(Math.toDegrees(lon2))))
        }
        return out
    }

    private fun normalizeLon(lon: Double): Double = ((lon + 180.0) % 360.0 + 360.0) % 360.0 - 180.0
}
