package com.atvriders.wsprtxrx.ui

import com.atvriders.wsprtxrx.data.SpotRepository
import com.atvriders.wsprtxrx.data.local.SpotDao
import com.atvriders.wsprtxrx.data.local.SpotEntity
import com.atvriders.wsprtxrx.data.model.SourceId
import com.atvriders.wsprtxrx.data.model.Spot
import com.atvriders.wsprtxrx.data.model.SpotQuery
import com.atvriders.wsprtxrx.data.prefs.AppSettings
import com.atvriders.wsprtxrx.data.prefs.SpotsSettings
import com.atvriders.wsprtxrx.data.qrz.CallsignLookup
import com.atvriders.wsprtxrx.data.qrz.QrzInfo
import com.atvriders.wsprtxrx.data.source.SpotSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * Regression test for H4: `runCatching` around `repository.search(...)` caught
 * `Throwable`, including the `JobCancellationException` raised by the single-flight
 * `searchJob?.cancel()`. Because `onFailure` is non-suspending it ran anyway, and
 * `onSuccess` never reset `error`, so a permanent "Couldn't load spots /
 * StandaloneCoroutine was cancelled" banner ended up painted over correct data.
 *
 * The ordering is deterministic, not racy, and reproducing it requires modelling the
 * *uninterruptible* blocking OkHttp call the real sources make inside
 * `withContext(Dispatchers.IO)`: `cancel()` cannot interrupt `execute()`, so search #1
 * unwinds only after search #2 has already cleared the error. A naive fake that is
 * promptly cancellable false-passes against the buggy code.
 *
 * Verified to FAIL against the pre-fix ViewModel and pass after it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SpotsViewModelCancellationTest {

    private val dispatcher = StandardTestDispatcher()
    private val scheduler get() = dispatcher.scheduler

    /** A source whose work cannot be interrupted, like a blocking OkHttp execute(). */
    private class UninterruptibleSource(
        override val id: SourceId,
        private val workMs: Long,
        private val spots: List<Spot>,
    ) : SpotSource {
        override suspend fun query(q: SpotQuery): Result<List<Spot>> {
            withContext(NonCancellable) { delay(workMs) }
            return Result.success(spots)
        }
    }

    private class FakeDao : SpotDao {
        override suspend fun upsertAll(spots: List<SpotEntity>) {}
        override suspend fun recent(limit: Int): List<SpotEntity> = emptyList()
        override suspend fun deleteOlderThan(cutoffEpochSec: Long) {}
        override suspend fun clear() {}
    }

    private val spot = Spot(
        "K1ABC", "FN42", "G0XYZ", "IO91", 14_097_100L, -20,
        timeUtc = 1_700_000_000L, source = SourceId.WSPR_LIVE,
    )

    @Before fun setUp() = Dispatchers.setMain(dispatcher)

    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun aCancelledSearchNeverPaintsAnErrorBannerOverTheResultsThatReplacedIt() =
        runTest(dispatcher) {
            val repo = SpotRepository(
                sources = listOf(UninterruptibleSource(SourceId.WSPR_LIVE, 1_000L, listOf(spot))),
                dao = FakeDao(),
                enabledProvider = { setOf(SourceId.WSPR_LIVE) },
            )
            // The ViewModel kicks off a search in init; a second, overlapping search is
            // exactly what a recent-call chip / filter Apply / IME Search / Retry tap
            // during cold start produces.
            val vm = SpotsViewModel(repo, FakeSettings(), FakeLookup())

            scheduler.advanceTimeBy(100L) // init search in flight
            vm.search() // cancels #1; #1's blocking work still has 900 ms to unwind
            scheduler.advanceUntilIdle()

            assertNull(
                "a cancelled search must not leave an error banner over good data",
                vm.ui.value.error,
            )
            assertFalse("loading must not be left stuck by the cancelled search", vm.ui.value.loading)
            assertEquals(1, vm.ui.value.spots.size)
        }

    private class FakeSettings : SpotsSettings {
        override val settings: Flow<AppSettings> = flowOf(AppSettings())
        override suspend fun addRecentCall(call: String) {}
    }

    private class FakeLookup : CallsignLookup {
        override suspend fun lookup(callsign: String): Result<QrzInfo> =
            Result.failure(IllegalStateException("not configured"))
    }
}
