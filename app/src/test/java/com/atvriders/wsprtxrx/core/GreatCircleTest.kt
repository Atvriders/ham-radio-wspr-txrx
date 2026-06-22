package com.atvriders.wsprtxrx.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GreatCircleTest {
    private val london = LatLon(51.5, -0.13)
    private val newYork = LatLon(40.7, -74.0)

    @Test fun londonToNewYorkDistance() {
        val d = GreatCircle.distanceKm(london, newYork)
        assertEquals(5570.0, d, 60.0)
    }

    @Test fun londonToNewYorkBearingIsWestNorthWest() {
        val az = GreatCircle.azimuthDeg(london, newYork)
        assertEquals(288.0, az, 3.0)
    }

    @Test fun zeroDistanceForSamePoint() {
        assertEquals(0.0, GreatCircle.distanceKm(london, london), 1e-6)
    }

    @Test fun bearingIsNormalized() {
        val az = GreatCircle.azimuthDeg(newYork, london)
        assertTrue(az in 0.0..360.0)
    }

    @Test fun nearAntipodalDistanceIsNotNaN() {
        // Antipodal pairs push the haversine radicand slightly past 1.0 and would
        // produce NaN without the clamp. Expect ~half Earth circumference (~20015 km).
        val p = LatLon(0.0, 0.0)
        val antipode = LatLon(0.0, 180.0)
        val d = GreatCircle.distanceKm(p, antipode)
        assertTrue("expected finite distance, got $d", d.isFinite())
        assertEquals(20015.0, d, 50.0)

        // Near-antipodal grid-center pair (real-data shape).
        val a = LatLon(42.5, -71.0)
        val b = LatLon(-42.5, 109.0)
        assertTrue(GreatCircle.distanceKm(a, b).isFinite())
    }
}
