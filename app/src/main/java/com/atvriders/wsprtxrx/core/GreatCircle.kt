package com.atvriders.wsprtxrx.core

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Great-circle distance and bearing on a spherical Earth. */
object GreatCircle {
    private const val EARTH_RADIUS_KM = 6371.0

    /** Great-circle distance between two points, in kilometers (haversine). */
    fun distanceKm(a: LatLon, b: LatLon): Double {
        val lat1 = Math.toRadians(a.lat)
        val lat2 = Math.toRadians(b.lat)
        val dLat = Math.toRadians(b.lat - a.lat)
        val dLon = Math.toRadians(b.lon - a.lon)
        val h = sin(dLat / 2).let { it * it } +
            cos(lat1) * cos(lat2) * sin(dLon / 2).let { it * it }
        return 2 * EARTH_RADIUS_KM * atan2(sqrt(h), sqrt(1 - h))
    }

    /** Initial great-circle bearing from [from] to [to], degrees clockwise from north (0..360). */
    fun azimuthDeg(from: LatLon, to: LatLon): Double {
        val lat1 = Math.toRadians(from.lat)
        val lat2 = Math.toRadians(to.lat)
        val dLon = Math.toRadians(to.lon - from.lon)
        val y = sin(dLon) * cos(lat2)
        val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLon)
        val deg = Math.toDegrees(atan2(y, x))
        return (deg + 360.0) % 360.0
    }
}
