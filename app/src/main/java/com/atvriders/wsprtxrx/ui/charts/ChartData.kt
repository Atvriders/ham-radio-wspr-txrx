package com.atvriders.wsprtxrx.ui.charts

import com.atvriders.wsprtxrx.core.Band
import com.atvriders.wsprtxrx.data.model.Spot

/** A time bucket holding per-band spot counts. */
data class TimeBucket(val startSec: Long, val countsByBand: Map<Band, Int>) {
    val total: Int get() = countsByBand.values.sum()
}

/** One point of an SNR-over-time series. */
data class SnrPoint(val timeSec: Long, val snr: Int, val band: Band?)

/** A transmission heard by both stations, with each station's reported SNR (no averaging). */
data class Head2HeadRow(val txCall: String, val timeSec: Long, val snrA: Int, val snrB: Int)

/**
 * Pure transforms of spot lists into chart-ready series. Kept free of Android so the
 * bucketing and pairing logic can be unit-tested.
 */
object ChartData {

    /**
     * Buckets [spots] into [buckets] equal time slices ending at [nowSec], counting
     * spots per band in each slice. Returned oldest-first.
     */
    fun timeBuckets(spots: List<Spot>, nowSec: Long, windowSec: Long, buckets: Int = 24): List<TimeBucket> {
        if (buckets <= 0 || windowSec <= 0) return emptyList()
        val sliceSec = windowSec / buckets
        val start = nowSec - windowSec
        val acc = Array(buckets) { HashMap<Band, Int>() }
        for (s in spots) {
            val band = s.band ?: continue
            if (s.timeUtc < start || s.timeUtc > nowSec) continue
            val idx = (((s.timeUtc - start) / sliceSec).toInt()).coerceIn(0, buckets - 1)
            acc[idx][band] = (acc[idx][band] ?: 0) + 1
        }
        return acc.mapIndexed { i, m -> TimeBucket(start + i * sliceSec, m) }
    }

    /** SNR-over-time series, oldest first. */
    fun snrSeries(spots: List<Spot>): List<SnrPoint> =
        spots.sortedBy { it.timeUtc }.map { SnrPoint(it.timeUtc, it.snr, it.band) }

    /**
     * Transmissions received by BOTH [stationA] and [stationB] in the same 2-minute slot,
     * pairing each station's reported SNR. Does not average — one row per shared
     * transmission, newest first.
     */
    fun head2head(spots: List<Spot>, stationA: String, stationB: String): List<Head2HeadRow> {
        fun slotKey(s: Spot) = "${s.txCall}|${s.timeUtc / 120}"
        val aBySlot = spots.filter { it.rxCall.equals(stationA, ignoreCase = true) }
            .associateBy { slotKey(it) }
        val rows = ArrayList<Head2HeadRow>()
        for (s in spots.filter { it.rxCall.equals(stationB, ignoreCase = true) }) {
            val a = aBySlot[slotKey(s)] ?: continue
            rows.add(Head2HeadRow(s.txCall, s.timeUtc, a.snr, s.snr))
        }
        return rows.sortedByDescending { it.timeSec }
    }

    /** Distinct receiver callsigns present in the spots (for Head2Head pickers). */
    fun receivers(spots: List<Spot>): List<String> =
        spots.map { it.rxCall }.distinct().sorted()
}
