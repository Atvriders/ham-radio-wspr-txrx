package com.atvriders.wsprtxrx.data.source

import com.atvriders.wsprtxrx.core.Band
import com.atvriders.wsprtxrx.data.model.Direction
import com.atvriders.wsprtxrx.data.model.SpotQuery
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WsprLiveQueryBuilderTest {
    @Test fun includesTimeWindowAndFormat() {
        val sql = WsprLiveQueryBuilder.buildSql(SpotQuery(timeRangeMinutes = 30))
        assertTrue(sql.contains("subtractMinutes(now(), 30)"))
        assertTrue(sql.endsWith("FORMAT JSONEachRow"))
        assertTrue(sql.contains("FROM rx"))
    }

    @Test fun includesBandCodes() {
        val sql = WsprLiveQueryBuilder.buildSql(SpotQuery(bands = setOf(Band.M20, Band.M40)))
        assertTrue(sql.contains("band IN (7,14)"))
    }

    @Test fun includesCallsignForBothDirections() {
        val sql = WsprLiveQueryBuilder.buildSql(SpotQuery(callsign = "k1abc", direction = Direction.BOTH))
        assertTrue(sql.contains("(tx_sign = 'K1ABC' OR rx_sign = 'K1ABC')"))
    }

    @Test fun txDirectionFiltersSenderOnly() {
        val sql = WsprLiveQueryBuilder.buildSql(SpotQuery(callsign = "K1ABC", direction = Direction.TX))
        assertTrue(sql.contains("tx_sign = 'K1ABC'"))
        assertFalse(sql.contains("rx_sign = 'K1ABC'"))
    }

    @Test fun includesDistanceAndPower() {
        val sql = WsprLiveQueryBuilder.buildSql(SpotQuery(maxDistanceKm = 5000, maxPowerDbm = 23))
        assertTrue(sql.contains("distance <= 5000"))
        assertTrue(sql.contains("power <= 23"))
    }

    @Test fun sanitizesInjectionAttempt() {
        val sql = WsprLiveQueryBuilder.buildSql(SpotQuery(callsign = "K1'; DROP TABLE rx;--"))
        // Dangerous SQL syntax (quotes-to-break-out, semicolons, comments) is stripped;
        // only [A-Z0-9/] survive, so the callsign collapses to harmless text.
        assertFalse(sql.contains("DROP TABLE"))
        assertFalse(sql.contains(";"))
        assertFalse(sql.contains("--"))
        assertTrue(sql.contains("'K1DROPTABLERX'"))
    }
}
