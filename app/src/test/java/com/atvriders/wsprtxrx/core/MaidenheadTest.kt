package com.atvriders.wsprtxrx.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MaidenheadTest {
    @Test fun fn42CenterIsBoston() {
        val p = Maidenhead.gridToLatLon("FN42")
        assertEquals(42.5, p.lat, 0.01)
        assertEquals(-71.0, p.lon, 0.01)
    }

    @Test fun io91CenterIsLondon() {
        val p = Maidenhead.gridToLatLon("IO91")
        assertEquals(51.5, p.lat, 0.01)
        assertEquals(-1.0, p.lon, 0.01)
    }

    @Test fun sixCharGridIsHandled() {
        val p = Maidenhead.gridToLatLon("FN42mm")
        assertEquals(42.5, p.lat, 0.1)
        assertEquals(-71.0, p.lon, 0.1)
    }

    @Test fun roundTripStartsWithSameSquare() {
        val grid = Maidenhead.latLonToGrid(42.5, -71.0)
        assertTrue("got $grid", grid.startsWith("FN42"))
    }

    @Test fun encodesKnownPoint() {
        // Center of IO91 round-trips to IO91xx.
        assertTrue(Maidenhead.latLonToGrid(51.5, -1.0).startsWith("IO91"))
    }
}
