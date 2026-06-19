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
}
