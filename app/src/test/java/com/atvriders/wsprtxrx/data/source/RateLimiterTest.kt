package com.atvriders.wsprtxrx.data.source

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class RateLimiterTest {
    @Test fun returnsCachedValueWithinInterval() = runTest {
        var clock = 0L
        val limiter = RateLimiter(minIntervalMs = 1000L, now = { clock })
        var calls = 0

        val first = limiter.withLimit("k") { calls++; "fetched-$calls" }
        clock = 500
        val second = limiter.withLimit("k") { calls++; "fetched-$calls" }

        assertEquals("fetched-1", first)
        assertEquals("fetched-1", second) // cached, fetch not re-run
        assertEquals(1, calls)
    }

    @Test fun refetchesAfterInterval() = runTest {
        var clock = 0L
        val limiter = RateLimiter(minIntervalMs = 1000L, now = { clock })
        var calls = 0

        limiter.withLimit("k") { calls++; "v" }
        clock = 1500
        limiter.withLimit("k") { calls++; "v" }

        assertEquals(2, calls)
    }

    @Test fun separateKeysAreIndependent() = runTest {
        val limiter = RateLimiter(minIntervalMs = 1000L, now = { 0L })
        var calls = 0
        limiter.withLimit("a") { calls++; "a" }
        limiter.withLimit("b") { calls++; "b" }
        assertEquals(2, calls)
    }

    @Test fun persistsLastFetchStampAcrossInstances() = runTest {
        // Simulate a durable store shared between two limiter instances (cold start).
        val store = HashMap<String, Long>()
        var clock = 0L
        var calls = 0

        val first = RateLimiter(
            minIntervalMs = 1000L,
            now = { clock },
            loadStamp = { store[it] },
            saveStamp = { k, t -> store[k] = t },
        )
        first.withLimit("k") { calls++; "v" }
        assertEquals(1, calls)
        assertEquals(0L, store["k"]) // stamp persisted

        // New instance (process restart) with empty in-memory cache, still inside window.
        clock = 500
        val second = RateLimiter(
            minIntervalMs = 1000L,
            now = { clock },
            loadStamp = { store[it] },
            saveStamp = { k, t -> store[k] = t },
        )
        second.withLimit("k") { calls++; "v" }
        // The persisted stamp (0) is preserved, not bumped to 500.
        assertEquals(0L, store["k"])
    }

    @Test fun coldStartAfterWindowRefetchesAndBumpsStamp() = runTest {
        val store = HashMap<String, Long>()
        var clock = 0L
        var calls = 0
        val mk = {
            RateLimiter(
                minIntervalMs = 1000L,
                now = { clock },
                loadStamp = { store[it] },
                saveStamp = { k, t -> store[k] = t },
            )
        }
        mk().withLimit("k") { calls++; "v" }
        clock = 2000 // past the window
        mk().withLimit("k") { calls++; "v" }
        assertEquals(2, calls)
        assertEquals(2000L, store["k"]) // stamp bumped to the fresh fetch time
    }
}
