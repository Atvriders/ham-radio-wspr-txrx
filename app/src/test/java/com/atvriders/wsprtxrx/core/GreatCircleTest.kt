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
}
