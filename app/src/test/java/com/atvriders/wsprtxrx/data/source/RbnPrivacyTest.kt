package com.atvriders.wsprtxrx.data.source

import com.atvriders.wsprtxrx.data.model.SpotQuery
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.Socket
import java.net.SocketAddress

/**
 * Pins a privacy claim, not a feature.
 *
 * The RBN feed is cleartext telnet by protocol design. `docs/PRIVACY_POLICY.md` and the
 * Data safety "encrypted in transit = Yes" answer both rest on the fact that the only
 * thing ever written to that socket is the constant placeholder `N0CALL` — never the
 * user's callsign. If anyone ever wires the real callsign through (an earlier internal
 * review actually recommended it), this test fails and the policy stays true.
 */
class RbnPrivacyTest {

    private class FakeSocket(private val prompt: String) : Socket() {
        val written = ByteArrayOutputStream()
        private val input = ByteArrayInputStream(prompt.toByteArray())
        override fun connect(endpoint: SocketAddress?, timeout: Int) = Unit
        override fun getInputStream(): InputStream = input
        override fun getOutputStream(): OutputStream = written
        override fun setSoTimeout(timeout: Int) = Unit
        override fun close() = Unit
    }

    @Test
    fun onlyTheN0CALLPlaceholderIsSentOverTheCleartextTelnetLink() = runTest {
        val socket = FakeSocket("Please enter your call: ")
        val source = RbnSource(
            host = "test.invalid",
            port = 1,
            snapshotMs = 1L,
            promptTimeoutMs = 1L,
            socketFactory = { socket },
        )

        source.query(SpotQuery())

        assertEquals(
            "the RBN login must stay the N0CALL placeholder — the privacy policy and the " +
                "Data safety declaration both depend on it",
            "N0CALL\r\n",
            socket.written.toByteArray().toString(Charsets.US_ASCII),
        )
    }

}
