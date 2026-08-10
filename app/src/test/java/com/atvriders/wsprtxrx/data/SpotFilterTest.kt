package com.atvriders.wsprtxrx.data

import com.atvriders.wsprtxrx.core.Band
import com.atvriders.wsprtxrx.data.model.Direction
import com.atvriders.wsprtxrx.data.model.SourceId
import com.atvriders.wsprtxrx.data.model.Spot
import com.atvriders.wsprtxrx.data.model.SpotQuery
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Sources differ in what they can filter server-side: wspr.live filters band/distance/power
 * in SQL and RBN filters locally, but PSKReporter's API understands only time and callsign.
 * The repository therefore applies these constraints uniformly; without that, a band-filtered
 * search silently showed PSKReporter spots from every band.
 */
class SpotFilterTest {

    private fun spot(
        freqHz: Long = 14_097_100L,
        txGrid: String? = "FN42",
        rxGrid: String? = "IO91",
        powerDbm: Int? = 37,
    ) = Spot(
        txCall = "K1ABC", txGrid = txGrid,
        rxCall = "G0XYZ", rxGrid = rxGrid,
        freqHz = freqHz, snr = -20, powerDbm = powerDbm,
        timeUtc = 1_700_000_000L, source = SourceId.PSK_REPORTER,
    ).withGeometry()

    @Test fun emptyQueryKeepsEverything() {
        assertTrue(spot().satisfies(SpotQuery()))
    }

    @Test fun bandFilterExcludesOtherBands() {
        val twentyM = spot(freqHz = 14_097_100L)
        val fortyM = spot(freqHz = 7_040_100L)
        val q = SpotQuery(bands = setOf(Band.M20))
        assertTrue(twentyM.satisfies(q))
        assertFalse("40m spot must not survive a 20m-only filter", fortyM.satisfies(q))
    }

    @Test fun maxPowerExcludesStrongerStations() {
        val q = SpotQuery(maxPowerDbm = 23)
        assertFalse(spot(powerDbm = 37).satisfies(q))
        assertTrue(spot(powerDbm = 20).satisfies(q))
    }

    @Test fun maxDistanceExcludesDxPaths() {
        // FN42 -> IO91 is roughly 5,200 km.
        assertFalse(spot().satisfies(SpotQuery(maxDistanceKm = 1_000)))
        assertTrue(spot().satisfies(SpotQuery(maxDistanceKm = 10_000)))
    }

    @Test fun gridFilterHonoursDirection() {
        val s = spot(txGrid = "FN42", rxGrid = "IO91")
        assertTrue(s.satisfies(SpotQuery(grid = "FN42", direction = Direction.TX)))
        assertFalse(s.satisfies(SpotQuery(grid = "IO91", direction = Direction.TX)))
        assertTrue(s.satisfies(SpotQuery(grid = "IO91", direction = Direction.RX)))
        assertTrue(s.satisfies(SpotQuery(grid = "IO91", direction = Direction.BOTH)))
        assertFalse(s.satisfies(SpotQuery(grid = "JO65", direction = Direction.BOTH)))
    }

    @Test fun missingDataIsNotExcludedByMaxFilters() {
        // A max-filter cannot be evaluated against an absent value; dropping the spot
        // would hide a real report.
        assertTrue(spot(powerDbm = null).satisfies(SpotQuery(maxPowerDbm = 10)))
        assertTrue(
            spot(txGrid = null, rxGrid = null).satisfies(SpotQuery(maxDistanceKm = 100)),
        )
    }
}
