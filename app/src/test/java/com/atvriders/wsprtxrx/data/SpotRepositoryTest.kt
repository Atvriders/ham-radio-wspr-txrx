package com.atvriders.wsprtxrx.data

import com.atvriders.wsprtxrx.data.local.SpotDao
import com.atvriders.wsprtxrx.data.local.SpotEntity
import com.atvriders.wsprtxrx.data.local.toSpot
import com.atvriders.wsprtxrx.data.model.SourceId
import com.atvriders.wsprtxrx.data.model.Spot
import com.atvriders.wsprtxrx.data.model.SpotQuery
import com.atvriders.wsprtxrx.data.source.SpotSource
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeSource(
    override val id: SourceId,
    private val result: Result<List<Spot>>,
) : SpotSource {
    override suspend fun query(q: SpotQuery): Result<List<Spot>> = result
}

private class FakeDao : SpotDao {
    val stored = mutableListOf<SpotEntity>()
    override suspend fun upsertAll(spots: List<SpotEntity>) { stored.addAll(spots) }
    override suspend fun recent(limit: Int): List<SpotEntity> = stored.take(limit)
    override suspend fun deleteOlderThan(cutoffEpochSec: Long) {}
    override suspend fun clear() { stored.clear() }
}

class SpotRepositoryTest {
    private val spotA = Spot("K1ABC", "FN42", "G0XYZ", "IO91", 14_097_100L, -20, timeUtc = 1_700_000_000L, source = SourceId.WSPR_LIVE)
    private val spotB = Spot("W1AW", "FN31", "JA1X", "PM95", 7_040_100L, -10, timeUtc = 1_700_000_100L, source = SourceId.WSPR_LIVE)

    @Test fun mergesDedupsAndReportsPartialFailures() = runTest {
        val duplicate = spotA.copy(source = SourceId.PSK_REPORTER, snr = -22)
        val repo = SpotRepository(
            sources = listOf(
                FakeSource(SourceId.WSPR_LIVE, Result.success(listOf(spotA, spotB))),
                FakeSource(SourceId.PSK_REPORTER, Result.success(listOf(duplicate))),
                FakeSource(SourceId.RBN, Result.failure(RuntimeException("host unreachable"))),
            ),
            dao = FakeDao(),
            enabledProvider = { setOf(SourceId.WSPR_LIVE, SourceId.PSK_REPORTER, SourceId.RBN) },
        )

        val result = repo.search(SpotQuery())

        assertEquals(2, result.spots.size) // duplicate collapsed
        assertEquals(1, result.partialFailures.size)
        assertEquals(SourceId.RBN, result.partialFailures.first().source)
        // newest first
        assertEquals("W1AW", result.spots.first().txCall)
        // geometry filled in
        assertTrue(result.spots.all { it.distanceKm != null })
    }

    @Test fun onlyQueriesEnabledSources() = runTest {
        val dao = FakeDao()
        val repo = SpotRepository(
            sources = listOf(
                FakeSource(SourceId.WSPR_LIVE, Result.success(listOf(spotA))),
                FakeSource(SourceId.PSK_REPORTER, Result.failure(RuntimeException("should not be called"))),
            ),
            dao = dao,
            enabledProvider = { setOf(SourceId.WSPR_LIVE) },
        )

        val result = repo.search(SpotQuery())

        assertEquals(1, result.spots.size)
        assertTrue(result.partialFailures.isEmpty()) // disabled source never queried
        assertEquals(1, dao.stored.size) // cached
        assertEquals("K1ABC", dao.stored.first().toSpot().txCall)
    }
}
