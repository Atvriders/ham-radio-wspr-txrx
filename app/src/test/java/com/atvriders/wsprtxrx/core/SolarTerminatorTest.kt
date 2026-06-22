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
        // Ring is now closed: steps + 1 points, last == first.
        assertEquals(73, ring.size)
        assertEquals(ring.first().lat, ring.last().lat, 1e-9)
        assertEquals(ring.first().lon, ring.last().lon, 1e-9)
        val quarter = 6371.0 * Math.PI / 2.0
        for (p in ring) {
            assertEquals(quarter, GreatCircle.distanceKm(sun, p), 60.0)
        }
    }

    @Test fun segmentsSplitAtAntimeridian() {
        // A time well away from noon UTC pushes the subsolar longitude off 0°, so the
        // terminator ring crosses ±180° and must split into multiple segments.
        val t = epoch(2024, 1, 15, 3, 0)
        val segments = SolarTerminator.terminatorSegments(t, steps = 180)
        assertTrue("expected a split, got ${segments.size} segment(s)", segments.size >= 2)
        // No segment may itself contain an antimeridian jump.
        for (seg in segments) {
            for (i in 1 until seg.size) {
                assertTrue(
                    "segment jumps the antimeridian at $i",
                    abs(seg[i].lon - seg[i - 1].lon) <= 180.0,
                )
            }
        }
    }

    @Test fun segmentsCoverWholeRing() {
        val t = epoch(2024, 1, 15, 3, 0)
        val ring = SolarTerminator.terminatorPolygon(t, steps = 180)
        val segments = SolarTerminator.terminatorSegments(t, steps = 180)
        // Every ring point ends up in exactly one segment.
        assertEquals(ring.size, segments.sumOf { it.size })
    }
}
