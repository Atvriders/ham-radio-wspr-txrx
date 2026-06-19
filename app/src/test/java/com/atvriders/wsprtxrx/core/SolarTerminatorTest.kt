package com.atvriders.wsprtxrx.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlin.math.abs

class SolarTerminatorTest {
    private fun epoch(y: Int, mo: Int, d: Int, h: Int, mi: Int): Long =
        LocalDateTime.of(y, mo, d, h, mi, 0).toEpochSecond(ZoneOffset.UTC)

    @Test fun subsolarLatitudeNearZeroAtEquinox() {
        val p = SolarTerminator.subsolarPoint(epoch(2024, 3, 20, 12, 0))
        assertTrue("decl=${p.lat}", abs(p.lat) < 2.0)
    }

    @Test fun subsolarLongitudeIsZeroAtNoonUtc() {
        val p = SolarTerminator.subsolarPoint(epoch(2024, 3, 20, 12, 0))
        assertEquals(0.0, p.lon, 1.0)
    }

    @Test fun subsolarLatitudeWithinTropics() {
        val solstice = SolarTerminator.subsolarPoint(epoch(2024, 6, 21, 12, 0))
        assertTrue("decl=${solstice.lat}", abs(solstice.lat) <= 23.5)
        assertTrue(solstice.lat > 20.0)
    }

    @Test fun terminatorPointsAreNinetyDegreesFromSun() {
        val t = epoch(2024, 3, 20, 12, 0)
        val sun = SolarTerminator.subsolarPoint(t)
        val ring = SolarTerminator.terminatorPolygon(t, steps = 72)
        assertEquals(72, ring.size)
        val quarter = 6371.0 * Math.PI / 2.0
        for (p in ring) {
            assertEquals(quarter, GreatCircle.distanceKm(sun, p), 60.0)
        }
    }
}
