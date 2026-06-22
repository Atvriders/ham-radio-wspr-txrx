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
     * The terminator as a closed ring of points, each lying 90 degrees of arc from the
     * subsolar point (the great circle dividing day from night). The ring is closed: the
     * first point is repeated as the last, so a renderer sees no gap (the previous
     * `0 until steps` loop left a ~2° gap between the last and first points).
     */
    fun terminatorPolygon(epochSec: Long, steps: Int = 180): List<LatLon> {
        val sun = subsolarPoint(epochSec)
        val lat1 = Math.toRadians(sun.lat)
        val lon1 = Math.toRadians(sun.lon)
        val out = ArrayList<LatLon>(steps + 1)
        for (i in 0..steps) {
            val brng = 2.0 * PI * i / steps
            // Destination point at angular distance 90deg on bearing brng. With d = 90deg,
            // cos d = 0 and sin d = 1, simplifying the standard formula.
            val lat2 = asin(cos(lat1) * cos(brng))
            val lon2 = lon1 + atan2(sin(brng) * cos(lat1), -sin(lat1) * sin(lat2))
            out.add(LatLon(Math.toDegrees(lat2), normalizeLon(Math.toDegrees(lon2))))
        }
        return out
    }

    /**
     * The terminator split into one or more continuous segments at every antimeridian
     * (±180°) crossing, so a line renderer never draws a horizontal streak across the
     * whole map when the great circle wraps. Each returned list is a contiguous polyline
     * (a MultiLineString member). For the rare case where the ring never crosses ±180°
     * (e.g. exactly at an equinox/noon UTC) a single segment is returned.
     */
    fun terminatorSegments(epochSec: Long, steps: Int = 180): List<List<LatLon>> {
        val ring = terminatorPolygon(epochSec, steps)
        val segments = ArrayList<List<LatLon>>()
        var current = ArrayList<LatLon>()
        for (i in ring.indices) {
            val p = ring[i]
            if (current.isEmpty()) {
                current.add(p)
                continue
            }
            val prev = current.last()
            // A jump of more than 180° in longitude means the segment crossed the
            // antimeridian; break the polyline here rather than drawing across the map.
            if (kotlin.math.abs(p.lon - prev.lon) > 180.0) {
                segments.add(current)
                current = ArrayList()
            }
            current.add(p)
        }
        if (current.isNotEmpty()) segments.add(current)
        return segments
    }

    private fun normalizeLon(lon: Double): Double = ((lon + 180.0) % 360.0 + 360.0) % 360.0 - 180.0
}
