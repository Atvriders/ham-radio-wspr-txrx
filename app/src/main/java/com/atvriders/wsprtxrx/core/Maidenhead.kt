package com.atvriders.wsprtxrx.core

import kotlin.math.floor

/**
 * Maidenhead grid-locator conversions. Supports 4-character ("FN42") and
 * 6-character ("FN42aa") locators. [gridToLatLon] returns the center of the square.
 */
object Maidenhead {

    /**
     * Returns the latitude/longitude of the center of the given grid square, or null
     * if the locator is malformed (wrong length or out-of-range characters).
     */
    fun gridToLatLonOrNull(grid: String): LatLon? =
        runCatching { gridToLatLon(grid) }.getOrNull()

    /**
     * Returns the latitude/longitude of the center of the given grid square.
     * Validates each position: field [A-R][A-R], square [0-9][0-9], optional
     * subsquare [A-X][A-X]. Throws [IllegalArgumentException] on malformed input.
     */
    fun gridToLatLon(grid: String): LatLon {
        val g = grid.trim().uppercase()
        require(g.length >= 4) { "grid must be at least 4 characters: $grid" }
        require(g[0] in 'A'..'R' && g[1] in 'A'..'R') { "grid field out of range (A-R): $grid" }
        require(g[2] in '0'..'9' && g[3] in '0'..'9') { "grid square must be digits: $grid" }
        if (g.length >= 6) {
            require(g[4] in 'A'..'X' && g[5] in 'A'..'X') { "grid subsquare out of range (A-X): $grid" }
        }

        var lon = (g[0] - 'A') * 20.0 - 180.0
        var lat = (g[1] - 'A') * 10.0 - 90.0
        lon += (g[2] - '0') * 2.0
        lat += (g[3] - '0') * 1.0

        var lonCell = 2.0
        var latCell = 1.0
        if (g.length >= 6) {
            lon += (g[4] - 'A') * (2.0 / 24.0)
            lat += (g[5] - 'A') * (1.0 / 24.0)
            lonCell = 2.0 / 24.0
            latCell = 1.0 / 24.0
        }
        return LatLon(lat + latCell / 2.0, lon + lonCell / 2.0)
    }

    /** Encodes a coordinate into a Maidenhead locator of the given [precision] (4 or 6). */
    fun latLonToGrid(lat: Double, lon: Double, precision: Int = 6): String {
        var lonAdj = (lon + 180.0).coerceIn(0.0, 359.999999)
        var latAdj = (lat + 90.0).coerceIn(0.0, 179.999999)
        val sb = StringBuilder()

        val f1 = floor(lonAdj / 20.0).toInt(); sb.append('A' + f1); lonAdj -= f1 * 20.0
        val f2 = floor(latAdj / 10.0).toInt(); sb.append('A' + f2); latAdj -= f2 * 10.0

        val s1 = floor(lonAdj / 2.0).toInt(); sb.append('0' + s1); lonAdj -= s1 * 2.0
        val s2 = floor(latAdj / 1.0).toInt(); sb.append('0' + s2); latAdj -= s2 * 1.0

        if (precision >= 6) {
            val u1 = floor(lonAdj / (2.0 / 24.0)).toInt().coerceIn(0, 23)
            val u2 = floor(latAdj / (1.0 / 24.0)).toInt().coerceIn(0, 23)
            sb.append(('a' + u1))
            sb.append(('a' + u2))
        }
        return sb.toString()
    }
}
