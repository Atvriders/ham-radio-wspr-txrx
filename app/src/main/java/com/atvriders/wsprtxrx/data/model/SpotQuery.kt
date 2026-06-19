package com.atvriders.wsprtxrx.data.model

import com.atvriders.wsprtxrx.core.Band

/** Which side of the report a callsign/grid filter applies to. */
enum class Direction { TX, RX, BOTH }

/**
 * The parameters of a spot search. An empty [bands] set means "all bands". [callsign]
 * and [grid] are optional filters; [direction] selects whether they match the
 * transmitter, receiver, or either.
 */
data class SpotQuery(
    val callsign: String? = null,
    val grid: String? = null,
    val bands: Set<Band> = emptySet(),
    val timeRangeMinutes: Int = 30,
    val maxDistanceKm: Int? = null,
    val maxPowerDbm: Int? = null,
    val direction: Direction = Direction.BOTH,
    val uniqueOnly: Boolean = false,
) {
    val cleanCallsign: String?
        get() = callsign?.uppercase()?.filter { it.isLetterOrDigit() || it == '/' }?.takeIf { it.isNotEmpty() }

    val cleanGrid: String?
        get() = grid?.filter { it.isLetterOrDigit() }?.takeIf { it.length >= 2 }
}
