package com.atvriders.wsprtxrx.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.atvriders.wsprtxrx.data.model.SourceId
import com.atvriders.wsprtxrx.data.model.Spot

/** Room row for a cached spot. Keyed by [Spot.dedupKey] so duplicates collapse. */
@Entity(tableName = "spots", indices = [Index("timeUtc")])
data class SpotEntity(
    @PrimaryKey val key: String,
    val txCall: String,
    val txGrid: String?,
    val rxCall: String,
    val rxGrid: String?,
    val freqHz: Long,
    val snr: Int,
    val drift: Int,
    val powerDbm: Int?,
    val timeUtc: Long,
    val source: String,
    val mode: String,
    val txLat: Double?,
    val txLon: Double?,
    val rxLat: Double?,
    val rxLon: Double?,
    val distanceKm: Double?,
    val azimuthDeg: Double?,
)

fun Spot.toEntity(): SpotEntity = SpotEntity(
    key = dedupKey(),
    txCall = txCall, txGrid = txGrid, rxCall = rxCall, rxGrid = rxGrid,
    freqHz = freqHz, snr = snr, drift = drift, powerDbm = powerDbm,
    timeUtc = timeUtc, source = source.name, mode = mode,
    txLat = txLat, txLon = txLon, rxLat = rxLat, rxLon = rxLon,
    distanceKm = distanceKm, azimuthDeg = azimuthDeg,
)

fun SpotEntity.toSpot(): Spot = Spot(
    txCall = txCall, txGrid = txGrid, rxCall = rxCall, rxGrid = rxGrid,
    freqHz = freqHz, snr = snr, drift = drift, powerDbm = powerDbm,
    timeUtc = timeUtc,
    source = runCatching { SourceId.valueOf(source) }.getOrDefault(SourceId.WSPR_LIVE),
    mode = mode,
    txLat = txLat, txLon = txLon, rxLat = rxLat, rxLon = rxLon,
    distanceKm = distanceKm, azimuthDeg = azimuthDeg,
)
