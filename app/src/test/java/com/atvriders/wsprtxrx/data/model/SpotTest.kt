package com.atvriders.wsprtxrx.data.model

import com.atvriders.wsprtxrx.core.Band
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SpotTest {
    private fun spot() = Spot(
        txCall = "K1ABC", txGrid = "FN42",
        rxCall = "G0XYZ", rxGrid = "IO91",
        freqHz = 14_097_100L, snr = -20, timeUtc = 1_700_000_000L,
        source = SourceId.WSPR_LIVE,
    )

    @Test fun withGeometryComputesDistanceAndAzimuth() {
        val s = spot().withGeometry()
        assertNotNull(s.distanceKm)
        assertTrue("distance ${s.distanceKm}", s.distanceKm!! in 4800.0..5400.0)
        assertTrue(s.azimuthDeg!! in 0.0..360.0)
        assertNotNull(s.txLat)
        assertNotNull(s.rxLat)
    }

    @Test fun bandIsDerivedFromFrequency() {
        assertEquals(Band.M20, spot().band)
    }

    @Test fun dedupKeyCollapsesSameReportWithinSlot() {
        val a = spot()
        val b = a.copy(source = SourceId.PSK_REPORTER, snr = -22, timeUtc = a.timeUtc + 30)
        assertEquals(a.dedupKey(), b.dedupKey())
    }

    @Test fun missingGridLeavesGeometryNull() {
        val s = spot().copy(txGrid = null).withGeometry()
        assertEquals(null, s.distanceKm)
    }
}
