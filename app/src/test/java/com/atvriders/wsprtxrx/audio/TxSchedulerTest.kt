package com.atvriders.wsprtxrx.audio

import org.junit.Assert.assertEquals
import org.junit.Test

class TxSchedulerTest {
    @Test fun atEvenMinuteBoundaryWaitsOneSecond() {
        assertEquals(1000L, TxScheduler.msUntilNextEvenMinute(0L))
        assertEquals(1000L, TxScheduler.msUntilNextEvenMinute(120_000L))
    }

    @Test fun beforeStartOffsetCountsDownToIt() {
        assertEquals(500L, TxScheduler.msUntilNextEvenMinute(500L))
        assertEquals(0L, TxScheduler.msUntilNextEvenMinute(1_000L))
    }

    @Test fun justAfterStartWaitsAlmostFullPeriod() {
        assertEquals(119_999L, TxScheduler.msUntilNextEvenMinute(1_001L))
    }

    @Test fun midWindowWaitsUntilNextSlot() {
        // 00:01:30 -> next even minute (02:00) + 1 s = 31 s away.
        assertEquals(31_000L, TxScheduler.msUntilNextEvenMinute(90_000L))
    }
}
