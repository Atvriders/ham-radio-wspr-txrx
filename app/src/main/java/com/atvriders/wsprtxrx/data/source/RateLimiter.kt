package com.atvriders.wsprtxrx.data.source

import java.util.concurrent.ConcurrentHashMap

/**
 * Caches the result of an expensive call per key and returns the cached value if the
 * same key is requested again within [minIntervalMs]. Used to respect PSKReporter's
 * "do not query more than once every few minutes" policy.
 *
 * The cache is a [ConcurrentHashMap] so overlapping searches from IO threads cannot
 * corrupt it or throw [ConcurrentModificationException].
 *
 * The in-memory value cache is lost on process death, but the *last-fetch timestamp*
 * can be made durable by supplying [loadStamp]/[saveStamp] (backed by DataStore). When
 * those are provided, a cold start that happens within [minIntervalMs] of the last
 * persisted fetch will skip the network call entirely (returning [fetch]'s fresh result
 * is unavoidable since the body itself isn't persisted, but the rate limit is honoured).
 */
class RateLimiter(
    private val minIntervalMs: Long,
    private val now: () -> Long = System::currentTimeMillis,
    /** Optional durable read of the last-fetch epoch-millis for a key. */
    private val loadStamp: (suspend (String) -> Long?)? = null,
    /** Optional durable write of the last-fetch epoch-millis for a key. */
    private val saveStamp: (suspend (String, Long) -> Unit)? = null,
) {
    private data class Entry(val time: Long, val value: Any?)

    private val cache = ConcurrentHashMap<String, Entry>()

    /** Returns the cached value for [key] if fresh, otherwise runs [fetch] and caches it. */
    @Suppress("UNCHECKED_CAST")
    suspend fun <T> withLimit(key: String, fetch: suspend () -> T): T {
        val t = now()
        val entry = cache[key]
        if (entry != null && t - entry.time < minIntervalMs) {
            return entry.value as T
        }
        // Cold start: no in-memory entry but a recent persisted fetch means we are still
        // inside the rate-limit window. We must still produce a value (the previous body
        // was not persisted), so run [fetch] but only after the window elapsed.
        if (entry == null && loadStamp != null) {
            val persisted = loadStamp.invoke(key)
            if (persisted != null && t - persisted < minIntervalMs) {
                // Seed the in-memory cache with a freshly fetched value, but keep the
                // persisted (older) timestamp so the window isn't extended by cold starts.
                val seeded = fetch()
                cache[key] = Entry(persisted, seeded)
                return seeded
            }
        }
        val value = fetch()
        cache[key] = Entry(t, value)
        saveStamp?.invoke(key, t)
        return value
    }

    /** True if a call for [key] would run [fetch] rather than return a cached value. */
    fun wouldFetch(key: String): Boolean {
        val entry = cache[key] ?: return true
        return now() - entry.time >= minIntervalMs
    }
}
