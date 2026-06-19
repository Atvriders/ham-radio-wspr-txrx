package com.atvriders.wsprtxrx.ui.charts

import com.atvriders.wsprtxrx.core.Band
import com.atvriders.wsprtxrx.data.model.SourceId
import com.atvriders.wsprtxrx.data.model.Spot
import org.junit.Assert.assertEquals
import org.junit.Test

class ChartDataTest {
    private fun spot(tx: String, rx: String, snr: Int, t: Long, freq: Long = 14_097_100L) =
        Spot(tx, "FN42", rx, "IO91", freq, snr, timeUtc = t, source = SourceId.WSPR_LIVE)

    @Test fun head2headPairsCommonTransmissions() {
        val spots = listOf(
            spot("X", "A", -10, 1000),
            spot("X", "B", -15, 1000), // same 2-min slot as above
            spot("Y", "A", -5, 1000),  // only A heard Y
            spot("Z", "B", -8, 1000),  // only B heard Z
        )
        val rows = ChartData.head2head(spots, "A", "B")
        assertEquals(1, rows.size)
        assertEquals("X", rows[0].txCall)
        assertEquals(-10, rows[0].snrA)
        assertEquals(-15, rows[0].snrB)
    }

    @Test fun head2headRespectsTimeSlots() {
        val spots = listOf(
            spot("X", "A", -10, 1000),
            spot("X", "B", -12, 5000), // different slot -> no pair
        )
        assertEquals(0, ChartData.head2head(spots, "A", "B").size)
    }

    @Test fun timeBucketsCountPerBand() {
        val now = 10_000L
        val spots = listOf(
            spot("X", "A", -10, 9_500, freq = 14_097_100L), // 20m
            spot("Y", "A", -10, 9_500, freq = 7_040_100L),  // 40m
            spot("Z", "A", -10, 9_900, freq = 14_097_100L), // 20m
        )
        val buckets = ChartData.timeBuckets(spots, nowSec = now, windowSec = 1000, buckets = 10)
        val total = buckets.sumOf { it.total }
        assertEquals(3, total)
        val m20 = buckets.sumOf { it.countsByBand[Band.M20] ?: 0 }
        assertEquals(2, m20)
    }

    @Test fun snrSeriesIsOldestFirst() {
        val spots = listOf(spot("X", "A", -10, 3000), spot("Y", "A", -20, 1000))
        val series = ChartData.snrSeries(spots)
        assertEquals(1000L, series.first().timeSec)
        assertEquals(3000L, series.last().timeSec)
    }

    @Test fun receiversAreDistinctSorted() {
        val spots = listOf(spot("X", "B", -10, 1), spot("Y", "A", -10, 2), spot("Z", "B", -10, 3))
        assertEquals(listOf("A", "B"), ChartData.receivers(spots))
    }
}
