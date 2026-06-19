package com.atvriders.wsprtxrx.data.source

/**
 * Caches the result of an expensive call per key and returns the cached value if the
 * same key is requested again within [minIntervalMs]. Used to respect PSKReporter's
 * "do not query more than once every few minutes" policy.
 */
class RateLimiter(
    private val minIntervalMs: Long,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private data class Entry(val time: Long, val value: Any?)

    private val cache = HashMap<String, Entry>()

    /** Returns the cached value for [key] if fresh, otherwise runs [fetch] and caches it. */
    @Suppress("UNCHECKED_CAST")
    suspend fun <T> withLimit(key: String, fetch: suspend () -> T): T {
        val t = now()
        val entry = cache[key]
        if (entry != null && t - entry.time < minIntervalMs) {
            return entry.value as T
        }
        val value = fetch()
        cache[key] = Entry(t, value)
        return value
    }

    /** True if a call for [key] would run [fetch] rather than return a cached value. */
    fun wouldFetch(key: String): Boolean {
        val entry = cache[key] ?: return true
        return now() - entry.time >= minIntervalMs
    }
}
