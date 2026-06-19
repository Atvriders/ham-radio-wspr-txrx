package com.atvriders.wsprtxrx.data.source

import com.atvriders.wsprtxrx.data.model.SourceId
import org.junit.Assert.assertEquals
import org.junit.Test

class PskReporterParseTest {
    private fun fixture(): String =
        javaClass.getResourceAsStream("/pskreporter_sample.xml")!!.bufferedReader().readText()

    @Test fun parsesAllReceptionReports() {
        val spots = PskReporterSource.parseXml(fixture())
        assertEquals(3, spots.size) // activeReceiver and the wrapper element are ignored
    }

    @Test fun mapsFieldsCorrectly() {
        val spot = PskReporterSource.parseXml(fixture()).first()
        assertEquals("K1ABC", spot.txCall)
        assertEquals("FN42", spot.txGrid)
        assertEquals("EA1ABC", spot.rxCall)
        assertEquals("IN52", spot.rxGrid)
        assertEquals(14097020L, spot.freqHz)
        assertEquals(-21, spot.snr)
        assertEquals(1699999800L, spot.timeUtc)
        assertEquals(SourceId.PSK_REPORTER, spot.source)
        assertEquals("WSPR", spot.mode)
    }
}
