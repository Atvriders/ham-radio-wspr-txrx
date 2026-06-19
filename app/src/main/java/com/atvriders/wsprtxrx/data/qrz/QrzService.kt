package com.atvriders.wsprtxrx.data.qrz

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

/** A resolved QRZ.com record (only the fields the spot detail view shows). */
data class QrzInfo(
    val call: String,
    val name: String?,
    val qth: String?,
    val grid: String?,
    val country: String?,
)

/**
 * Looks up callsigns via the QRZ.com XML API. Requires a QRZ login (paid accounts get
 * the most detail). Without credentials, [lookup] returns a failure and callers simply
 * show no QRZ data.
 */
class QrzService(
    private val client: OkHttpClient,
    private val credentials: () -> Pair<String, String>,
    private val baseUrl: String = "https://xmldata.qrz.com/xml/current/",
) {
    private val mutex = Mutex()
    @Volatile private var sessionKey: String? = null

    suspend fun lookup(callsign: String): Result<QrzInfo> = withContext(Dispatchers.IO) {
        runCatching {
            val key = ensureKey() ?: error("QRZ not configured")
            val info = fetchCallsign(key, callsign)
            if (info == null) {
                // Key may have expired; re-login once.
                sessionKey = null
                val fresh = ensureKey() ?: error("QRZ login failed")
                fetchCallsign(fresh, callsign) ?: error("callsign not found")
            } else {
                info
            }
        }
    }

    private suspend fun ensureKey(): String? = mutex.withLock {
        sessionKey?.let { return it }
        val (user, pass) = credentials()
        if (user.isBlank() || pass.isBlank()) return null
        val url = baseUrl.toHttpUrl().newBuilder()
            .addQueryParameter("username", user)
            .addQueryParameter("password", pass)
            .addQueryParameter("agent", "HamRadioWsprTxRx1.0")
            .build()
        val body = get(url.toString())
        parseSessionKey(body).also { sessionKey = it }
    }

    private fun fetchCallsign(key: String, callsign: String): QrzInfo? {
        val url = baseUrl.toHttpUrl().newBuilder()
            .addQueryParameter("s", key)
            .addQueryParameter("callsign", callsign.uppercase())
            .build()
        return parseCallsign(get(url.toString()))
    }

    private fun get(url: String): String {
        val req = Request.Builder().url(url).header("User-Agent", "HamRadioWsprTxRx/1.0").build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) error("QRZ HTTP ${resp.code}")
            return resp.body?.string().orEmpty()
        }
    }

    companion object {
        private fun tag(xml: String, name: String): String? =
            Regex("<$name>(.*?)</$name>", RegexOption.IGNORE_CASE).find(xml)?.groupValues?.get(1)?.trim()
                ?.takeIf { it.isNotEmpty() }

        fun parseSessionKey(xml: String): String? = tag(xml, "Key")

        fun parseCallsign(xml: String): QrzInfo? {
            val call = tag(xml, "call") ?: return null
            val fname = tag(xml, "fname")
            val lname = tag(xml, "name")
            val fullName = listOfNotNull(fname, lname).joinToString(" ").ifBlank { null }
            val city = tag(xml, "addr2")
            val state = tag(xml, "state")
            val qth = listOfNotNull(city, state).joinToString(", ").ifBlank { null }
            return QrzInfo(
                call = call,
                name = fullName,
                qth = qth,
                grid = tag(xml, "grid"),
                country = tag(xml, "country"),
            )
        }
    }
}
