package com.atvriders.wsprtxrx.data.qrz

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QrzServiceTest {
    @Test fun parsesSessionKey() {
        val xml = "<QRZDatabase><Session><Key>abc123KEY</Key><Count>99</Count></Session></QRZDatabase>"
        assertEquals("abc123KEY", QrzService.parseSessionKey(xml))
    }

    @Test fun parsesCallsignRecord() {
        val xml = """
            <QRZDatabase><Callsign>
            <call>K1ABC</call><fname>Joe</fname><name>Taylor</name>
            <addr2>Princeton</addr2><state>NJ</state>
            <grid>FN42</grid><country>United States</country>
            </Callsign></QRZDatabase>
        """.trimIndent()
        val info = QrzService.parseCallsign(xml)!!
        assertEquals("K1ABC", info.call)
        assertEquals("Joe Taylor", info.name)
        assertEquals("FN42", info.grid)
        assertEquals("United States", info.country)
        assertEquals("Princeton, NJ", info.qth)
    }

    @Test fun returnsNullWhenNoCall() {
        assertNull(QrzService.parseCallsign("<QRZDatabase><Session><Error>Not found</Error></Session></QRZDatabase>"))
    }
}
