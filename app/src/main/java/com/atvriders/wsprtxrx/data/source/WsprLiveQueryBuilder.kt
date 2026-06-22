package com.atvriders.wsprtxrx.data.source

import com.atvriders.wsprtxrx.data.model.Direction
import com.atvriders.wsprtxrx.data.model.SpotQuery

/**
 * Builds ClickHouse SQL for the wspr.live `rx` table from a [SpotQuery]. All
 * user-supplied text is sanitized (callsign to [A-Z0-9/], grid to alphanumerics)
 * before interpolation, so the resulting SQL cannot be injected.
 */
object WsprLiveQueryBuilder {

    const val MAX_ROWS = 2000

    val COLUMNS = listOf(
        "time", "band", "rx_sign", "rx_lat", "rx_lon", "rx_loc",
        "tx_sign", "tx_lat", "tx_lon", "tx_loc",
        "distance", "azimuth", "frequency", "power", "snr", "drift",
    )

    fun buildSql(q: SpotQuery): String {
        val where = mutableListOf<String>()
        where += "time > subtractMinutes(now(), ${q.timeRangeMinutes.coerceIn(1, 1440)})"

        if (q.bands.isNotEmpty()) {
            val codes = q.bands.map { it.wsprLiveCode }.sorted().joinToString(",")
            where += "band IN ($codes)"
        }

        q.cleanCallsign?.let { call ->
            where += when (q.direction) {
                Direction.TX -> "tx_sign = '$call'"
                Direction.RX -> "rx_sign = '$call'"
                Direction.BOTH -> "(tx_sign = '$call' OR rx_sign = '$call')"
            }
        }

        q.cleanGrid?.let { grid ->
            where += when (q.direction) {
                Direction.TX -> "tx_loc LIKE '$grid%'"
                Direction.RX -> "rx_loc LIKE '$grid%'"
                Direction.BOTH -> "(tx_loc LIKE '$grid%' OR rx_loc LIKE '$grid%')"
            }
        }

        q.maxDistanceKm?.let { where += "distance <= ${it.coerceAtLeast(0)}" }
        q.maxPowerDbm?.let { where += "power <= ${it}" }

        val cols = COLUMNS.joinToString(", ")
        val whereClause = where.joinToString(" AND ")
        // The wspr.live ClickHouse HTTP user has no default database, so the table
        // must be fully qualified as `wspr.rx`.
        val core = "SELECT $cols FROM wspr.rx WHERE $whereClause " +
            "ORDER BY time DESC LIMIT $MAX_ROWS"
        // Guarantee `FORMAT JSONEachRow` is the final token, with no trailing `;`.
        return core.trimEnd().trimEnd(';').trimEnd() + " " + FORMAT_SUFFIX
    }

    private const val FORMAT_SUFFIX = "FORMAT JSONEachRow"
}
