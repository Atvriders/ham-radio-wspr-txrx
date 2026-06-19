package com.atvriders.wsprtxrx.data

import com.atvriders.wsprtxrx.data.local.SpotDao
import com.atvriders.wsprtxrx.data.local.toEntity
import com.atvriders.wsprtxrx.data.local.toSpot
import com.atvriders.wsprtxrx.data.model.SourceId
import com.atvriders.wsprtxrx.data.model.Spot
import com.atvriders.wsprtxrx.data.model.SpotQuery
import com.atvriders.wsprtxrx.data.source.SpotSource
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/** A source that failed during a search, surfaced to the UI without blocking the rest. */
data class SourceFailure(val source: SourceId, val message: String)

/** Combined outcome of a search across all enabled sources. */
data class RepoResult(
    val spots: List<Spot>,
    val partialFailures: List<SourceFailure> = emptyList(),
)

/**
 * Single source of truth for spots. Fans a query out to the enabled sources in parallel,
 * merges and de-duplicates the results, fills in geometry, caches them, and reports any
 * per-source failures without failing the whole search.
 */
class SpotRepository(
    private val sources: List<SpotSource>,
    private val dao: SpotDao,
    private val enabledProvider: suspend () -> Set<SourceId>,
) {
    suspend fun search(q: SpotQuery): RepoResult = coroutineScope {
        val enabled = enabledProvider()
        val active = sources.filter { it.id in enabled }

        val results = active.map { src -> async { src.id to src.query(q) } }.awaitAll()

        val merged = ArrayList<Spot>()
        val failures = ArrayList<SourceFailure>()
        for ((id, res) in results) {
            res.onSuccess { merged.addAll(it) }
                .onFailure { failures.add(SourceFailure(id, it.message ?: "unavailable")) }
        }

        val seen = HashSet<String>()
        var processed = merged
            .asSequence()
            .map { it.withGeometry() }
            .filter { seen.add(it.dedupKey()) }
            .toList()

        if (q.uniqueOnly) {
            val unique = HashSet<String>()
            processed = processed.filter { unique.add("${it.txCall}|${it.rxCall}|${it.band?.name}") }
        }

        processed = processed.sortedByDescending { it.timeUtc }

        runCatching { dao.upsertAll(processed.map { it.toEntity() }) }

        RepoResult(processed, failures)
    }

    /** Returns the most recently cached spots (for offline / cold-start display). */
    suspend fun cached(): List<Spot> =
        runCatching { dao.recent().map { it.toSpot() } }.getOrDefault(emptyList())
}
