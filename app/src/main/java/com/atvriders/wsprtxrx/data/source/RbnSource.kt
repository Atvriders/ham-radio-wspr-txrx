package com.atvriders.wsprtxrx.data.source

import com.atvriders.wsprtxrx.core.bandForFreq
import com.atvriders.wsprtxrx.data.model.Direction
import com.atvriders.wsprtxrx.data.model.SourceId
import com.atvriders.wsprtxrx.data.model.Spot
import com.atvriders.wsprtxrx.data.model.SpotQuery
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Collects a time-boxed snapshot of recent spots from the Reverse Beacon Network telnet
 * cluster. RBN streams continuously, so [query] reads lines for [snapshotMs] then
 * returns whatever was gathered. Any failure (host unreachable, timeout) yields a
 * [Result.failure] so the rest of the app keeps working.
 */
class RbnSource(
    private val loginCall: String = "N0CALL",
    private val host: String = "telnet.reversebeacon.net",
    private val port: Int = 7000,
    private val snapshotMs: Long = 6_000L,
    private val maxLines: Int = 400,
    private val socketFactory: () -> Socket = { Socket() },
) : SpotSource {

    override val id = SourceId.RBN

    override suspend fun query(q: SpotQuery): Result<List<Spot>> = withContext(Dispatchers.IO) {
        runCatching {
            val spots = ArrayList<Spot>()
            val socket = socketFactory()
            try {
                socket.connect(InetSocketAddress(host, port), 5_000)
                socket.soTimeout = 2_000
                socket.getOutputStream().apply {
                    write("$loginCall\r\n".toByteArray())
                    flush()
                }
                val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
                withTimeoutOrNull(snapshotMs) {
                    var count = 0
                    while (count < maxLines) {
                        val line = runCatching { reader.readLine() }.getOrNull() ?: break
                        count++
                        val spot = RbnLineParser.parseSpotLine(line) ?: continue
                        if (matches(spot, q)) spots.add(spot)
                    }
                }
            } finally {
                runCatching { socket.close() }
            }
            spots
        }.recoverCatching { e ->
            if (e is TimeoutCancellationException) emptyList() else throw e
        }
    }

    private fun matches(spot: Spot, q: SpotQuery): Boolean {
        if (q.bands.isNotEmpty() && bandForFreq(spot.freqHz) !in q.bands) return false
        val call = q.cleanCallsign ?: return true
        return when (q.direction) {
            Direction.TX -> spot.txCall.equals(call, ignoreCase = true)
            Direction.RX -> spot.rxCall.equals(call, ignoreCase = true)
            Direction.BOTH -> spot.txCall.equals(call, ignoreCase = true) ||
                spot.rxCall.equals(call, ignoreCase = true)
        }
    }
}
