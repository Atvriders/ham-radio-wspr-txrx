package com.atvriders.wsprtxrx.data.source

import com.atvriders.wsprtxrx.data.model.Direction
import com.atvriders.wsprtxrx.data.model.SourceId
import com.atvriders.wsprtxrx.data.model.Spot
import com.atvriders.wsprtxrx.data.model.SpotQuery
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Fetches reception reports from PSKReporter. PSKReporter enforces a strict query rate
 * limit, so results are cached per query via [rateLimiter] (default 5 minutes). The
 * response is XML; reports are parsed from `<receptionReport .../>` elements.
 *
 * Multimode by design: unlike a WSPR-only client, this app intentionally surfaces *all*
 * reception reports PSKReporter returns (WSPR plus FT8/FT4/CW skimmer spots), and the
 * RBN source adds CW/FT skimmer data too. We therefore deliberately do NOT add a
 * `mode=WSPR` filter here — the parsed [Spot.mode] field carries the real mode so the UI
 * can label/group it. Each report's mode is preserved verbatim.
 *
 * Per PSKReporter operator policy we identify ourselves with an [appcontact] parameter
 * so a mass-deployed client can be contacted before being throttled/blocked.
 */
class PskReporterSource(
    private val client: OkHttpClient,
    private val rateLimiter: RateLimiter = RateLimiter(5 * 60_000L),
    private val baseUrl: String = "https://retrieve.pskreporter.info/query",
    private val appContact: String = APP_CONTACT,
) : SpotSource {

    override val id = SourceId.PSK_REPORTER

    override suspend fun query(q: SpotQuery): Result<List<Spot>> = withContext(Dispatchers.IO) {
        runCatching {
            val key = cacheKey(q)
            rateLimiter.withLimit(key) {
                val url = baseUrl.toHttpUrl().newBuilder().apply {
                    addQueryParameter("flowStartSeconds", "-${q.timeRangeMinutes.coerceIn(1, 1440) * 60}")
                    addQueryParameter("rronly", "1")
                    // PSKReporter contact-identification policy (do not remove).
                    addQueryParameter("appcontact", appContact)
                    q.cleanCallsign?.let { call ->
                        when (q.direction) {
                            Direction.TX, Direction.BOTH -> addQueryParameter("senderCallsign", call)
                            Direction.RX -> addQueryParameter("receiverCallsign", call)
                        }
                    }
                }.build()
                val request = Request.Builder().url(url)
                    .header("User-Agent", WsprLiveSource.USER_AGENT).build()
                client.newCall(request).execute().use { resp ->
                    if (!resp.isSuccessful) error("PSKReporter HTTP ${resp.code}")
                    parseXml(resp.body?.string().orEmpty())
                }
            }
        }
    }

    private fun cacheKey(q: SpotQuery): String =
        "${q.cleanCallsign}|${q.direction}|${q.timeRangeMinutes}"

    companion object {
        /** Contact address sent to PSKReporter per their operator policy. */
        const val APP_CONTACT = "ham-radio-wspr-txrx@users.noreply.github.com"

        private val REPORT = Regex("<receptionReport\\b([^>]*?)/?>")
        private val ATTR = Regex("(\\w+)=\"([^\"]*)\"")

        /** Parses a PSKReporter XML body into spots. */
        fun parseXml(body: String): List<Spot> {
            val out = ArrayList<Spot>()
            for (match in REPORT.findAll(body)) {
                val attrs = HashMap<String, String>()
                for (a in ATTR.findAll(match.groupValues[1])) {
                    attrs[a.groupValues[1]] = a.groupValues[2]
                }
                val tx = attrs["senderCallsign"] ?: continue
                val rx = attrs["receiverCallsign"] ?: continue
                out.add(
                    Spot(
                        txCall = tx,
                        txGrid = attrs["senderLocator"]?.takeIf { it.isNotBlank() },
                        rxCall = rx,
                        rxGrid = attrs["receiverLocator"]?.takeIf { it.isNotBlank() },
                        freqHz = attrs["frequency"]?.toLongOrNull() ?: 0L,
                        snr = attrs["sNR"]?.toIntOrNull() ?: 0,
                        timeUtc = attrs["flowStartSeconds"]?.toLongOrNull() ?: 0L,
                        source = SourceId.PSK_REPORTER,
                        mode = attrs["mode"] ?: "",
                    )
                )
            }
            return out
        }
    }
}
