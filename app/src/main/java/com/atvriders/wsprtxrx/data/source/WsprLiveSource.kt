package com.atvriders.wsprtxrx.data.source

import com.atvriders.wsprtxrx.data.model.SourceId
import com.atvriders.wsprtxrx.data.model.Spot
import com.atvriders.wsprtxrx.data.model.SpotQuery
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Fetches WSPR spots from wspr.live's ClickHouse HTTP endpoint. The query is built by
 * [WsprLiveQueryBuilder] and the response is parsed line-by-line (JSONEachRow).
 */
class WsprLiveSource(
    private val client: OkHttpClient,
    private val baseUrl: String = "https://db1.wspr.live/",
) : SpotSource {

    override val id = SourceId.WSPR_LIVE

    override suspend fun query(q: SpotQuery): Result<List<Spot>> = withContext(Dispatchers.IO) {
        runCatching {
            val url = baseUrl.toHttpUrl().newBuilder()
                .addQueryParameter("query", WsprLiveQueryBuilder.buildSql(q))
                .build()
            val request = Request.Builder().url(url).header("User-Agent", USER_AGENT).build()
            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) error("wspr.live HTTP ${resp.code}")
                parseJsonEachRow(resp.body?.string().orEmpty())
            }
        }
    }

    companion object {
        const val USER_AGENT = "HamRadioWsprTxRx/1.0"
        private val FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

        /** Parses a JSONEachRow body (one JSON object per line) into spots. */
        fun parseJsonEachRow(body: String): List<Spot> {
            val out = ArrayList<Spot>()
            for (line in body.lineSequence()) {
                val trimmed = line.trim()
                if (trimmed.isEmpty()) continue
                val obj = runCatching { Json.parseToJsonElement(trimmed) as? JsonObject }.getOrNull() ?: continue
                val spot = runCatching { obj.toSpot() }.getOrNull() ?: continue
                out.add(spot)
            }
            return out
        }

        private fun JsonObject.str(key: String): String? =
            (this[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }

        private fun JsonObject.dbl(key: String): Double? = this[key]?.let {
            (it as? JsonPrimitive)?.contentOrNull?.toDoubleOrNull()
        }

        private fun JsonObject.lng(key: String): Long? = dbl(key)?.toLong()
        private fun JsonObject.int(key: String): Int? = dbl(key)?.toInt()

        private fun JsonObject.toSpot(): Spot {
            val timeStr = str("time") ?: error("missing time")
            val epoch = LocalDateTime.parse(timeStr, FMT).toEpochSecond(ZoneOffset.UTC)
            return Spot(
                txCall = str("tx_sign") ?: error("missing tx_sign"),
                txGrid = str("tx_loc"),
                rxCall = str("rx_sign") ?: error("missing rx_sign"),
                rxGrid = str("rx_loc"),
                freqHz = lng("frequency") ?: 0L,
                snr = int("snr") ?: 0,
                drift = int("drift") ?: 0,
                powerDbm = int("power"),
                timeUtc = epoch,
                source = SourceId.WSPR_LIVE,
                txLat = dbl("tx_lat"),
                txLon = dbl("tx_lon"),
                rxLat = dbl("rx_lat"),
                rxLon = dbl("rx_lon"),
                distanceKm = dbl("distance"),
                azimuthDeg = dbl("azimuth"),
            )
        }
    }
}
