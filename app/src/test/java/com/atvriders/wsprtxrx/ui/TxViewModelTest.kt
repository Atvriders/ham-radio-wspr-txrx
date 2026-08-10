package com.atvriders.wsprtxrx.ui

import com.atvriders.wsprtxrx.audio.AudioFocusUnavailableException
import com.atvriders.wsprtxrx.audio.TxAudioSink
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * Covers the H2/H3 transmit-reliability contract:
 *  - the keep-alive (foreground service) starts on the *tap*, before the wait, while the
 *    app is demonstrably foreground — not after it, when the start would be refused,
 *  - the notification's Stop action cancels the transmit **job**, not just the service,
 *  - a frozen-then-thawed process re-arms for the next slot instead of keying up late,
 *  - Stop leaves no bogus "…was cancelled" error in the UI state,
 *  - a denied audio focus aborts instead of transmitting into someone else's audio.
 *
 * Both clocks are driven off the virtual test scheduler so the up-to-two-minute wait is
 * exercised deterministically and in zero wall-clock time.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TxViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val scheduler get() = dispatcher.scheduler

    /** Records keep-alive lifecycle so ordering against the wait can be asserted. */
    private class FakeKeepAlive : TxKeepAlive {
        val starts = AtomicInteger(0)
        val stops = AtomicInteger(0)
        val endAt = AtomicLong(0L)
        var startResult = true
        override fun start(): Boolean {
            starts.incrementAndGet()
            return startResult
        }
        override fun transmitting(endAtMs: Long) {
            endAt.set(endAtMs)
        }
        override fun stop() {
            stops.incrementAndGet()
        }
    }

    /** Suspends for the full transmission so the job can be observed mid-flight. */
    private class SlowSink(private val playMs: Long) : TxAudioSink {
        val started = AtomicBoolean(false)
        val finished = AtomicBoolean(false)
        override suspend fun play(pcm: ShortArray, onProgress: (Float) -> Unit) {
            started.set(true)
            delay(playMs)
            onProgress(1f)
            finished.set(true)
        }
    }

    @Before fun setUp() = Dispatchers.setMain(dispatcher)

    @After fun tearDown() = Dispatchers.resetMain()

    private fun vmWith(
        keepAlive: TxKeepAlive,
        sink: TxAudioSink,
        now: () -> Long,
        monotonic: () -> Long = { scheduler.currentTime },
    ) = TxViewModel(
        player = sink,
        now = now,
        monotonicMs = monotonic,
        keepAlive = keepAlive,
        computeDispatcher = dispatcher,
    ).also { it.setCallsign("N0CALL"); it.setGrid("FN42") }

    @Test
    fun keepAliveStartsOnTheTapBeforeTheWait() = runTest(dispatcher) {
        val keepAlive = FakeKeepAlive()
        // 30 s past an even minute: the next slot is 91 s away, so the wait is long.
        val vm = vmWith(keepAlive, SlowSink(1_000L), now = { scheduler.currentTime + 30_000L })

        vm.transmit()

        // Started synchronously on the tap. Deferring it until after the wait — the old
        // behaviour — meant the process was already background by then, so
        // startForegroundService() (and from Android 15, audio focus) was refused.
        assertEquals(1, keepAlive.starts.get())

        scheduler.advanceTimeBy(1_000L)
        assertEquals(TxPhase.WAITING, vm.ui.value.phase)
        assertEquals("keep-alive must be held across the wait", 0, keepAlive.stops.get())
        vm.stop()
    }

    @Test
    fun stopRequestFromTheNotificationCancelsTheTransmitJob() = runTest(dispatcher) {
        val keepAlive = FakeKeepAlive()
        val sink = SlowSink(110_600L)
        val vm = vmWith(keepAlive, sink, now = { scheduler.currentTime })
        vm.setWaitForEvenMinute(false)

        vm.transmit()
        scheduler.advanceTimeBy(1_000L)
        assertEquals(TxPhase.TRANSMITTING, vm.ui.value.phase)
        assertTrue(sink.started.get())

        // Exactly what TxForegroundService's ACTION_STOP invokes through
        // AppContainer.onTxStopRequested.
        val stopSignal: () -> Unit = vm::stop
        stopSignal()
        scheduler.advanceUntilIdle()

        assertFalse("the tone must stop, not just the notification", sink.finished.get())
        assertEquals(TxPhase.IDLE, vm.ui.value.phase)
        // Cancellation is not an error: the old code surfaced the raw
        // "StandaloneCoroutine was cancelled" text in the UI.
        assertNull(vm.ui.value.error)
        assertTrue(keepAlive.stops.get() >= 1)
    }

    @Test
    fun aThawAfterTheSlotHasPassedReArmsInsteadOfKeyingUpLate() = runTest(dispatcher) {
        val keepAlive = FakeKeepAlive()
        val sink = SlowSink(10L)
        // Wall clock runs 5 s ahead of monotonic time from t=1000 on: the signature of a
        // frozen process thawed after its slot boundary.
        val vm = vmWith(
            keepAlive,
            sink,
            now = {
                val t = scheduler.currentTime
                if (t >= 1_000L) t + 5_000L else t
            },
        )

        vm.transmit()
        scheduler.advanceTimeBy(2_000L)

        // The first slot was missed by 5 s. Keying up now would emit at an arbitrary
        // phase of the slot — undecodable, on shared spectrum — so it must re-arm.
        assertFalse("must not transmit out of slot", sink.started.get())
        assertEquals(TxPhase.WAITING, vm.ui.value.phase)
        vm.stop()
    }

    @Test
    fun aFailedKeepAliveSurfacesAWarningButStillTransmits() = runTest(dispatcher) {
        val keepAlive = FakeKeepAlive().apply { startResult = false }
        val sink = SlowSink(10L)
        val vm = vmWith(keepAlive, sink, now = { scheduler.currentTime })
        vm.setWaitForEvenMinute(false)

        vm.transmit()
        scheduler.advanceUntilIdle()

        assertEquals(TxWarning.KEEP_ALIVE_UNAVAILABLE, vm.ui.value.warning)
        assertEquals(TxPhase.DONE, vm.ui.value.phase)
        assertTrue(sink.finished.get())
    }

    @Test
    fun theNotificationIsGivenTheSlotEndTimeSoItCanCountDown() = runTest(dispatcher) {
        val keepAlive = FakeKeepAlive()
        val vm = vmWith(keepAlive, SlowSink(10L), now = { scheduler.currentTime })
        vm.setWaitForEvenMinute(false)

        vm.transmit()
        scheduler.advanceUntilIdle()

        // 162 symbols x 8192 samples at 12 kHz = 110.592 s.
        assertEquals(110_592L, keepAlive.endAt.get())
    }

    @Test
    fun aDeniedAudioFocusAbortsTheTransmissionWithAnError() = runTest(dispatcher) {
        val keepAlive = FakeKeepAlive()
        val failing = object : TxAudioSink {
            override suspend fun play(pcm: ShortArray, onProgress: (Float) -> Unit) {
                // Model the uninterruptible platform call that precedes the throw.
                withContext(NonCancellable) { delay(1L) }
                throw AudioFocusUnavailableException("Audio focus denied")
            }
        }
        val vm = vmWith(keepAlive, failing, now = { scheduler.currentTime })
        vm.setWaitForEvenMinute(false)

        vm.transmit()
        scheduler.advanceUntilIdle()

        assertEquals(TxPhase.IDLE, vm.ui.value.phase)
        assertEquals("Audio focus denied", vm.ui.value.error)
        assertEquals(1, keepAlive.stops.get())
    }
}
