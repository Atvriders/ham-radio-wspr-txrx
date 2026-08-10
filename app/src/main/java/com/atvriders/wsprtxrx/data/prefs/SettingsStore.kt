package com.atvriders.wsprtxrx.data.prefs

import android.content.Context
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.atvriders.wsprtxrx.data.model.SourceId
import com.atvriders.wsprtxrx.ui.theme.ThemeMode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.io.IOException

/**
 * A corrupt preferences file used to be a crash loop on every launch with no recovery
 * short of clearing app data: the flow was collected unprotected in three places. The
 * corruption handler covers `CorruptionException` (bad file) and the `.catch` on
 * [SettingsStore.safeData] covers the rest of the IO surface — both are needed, because
 * the handler alone leaves ordinary IO errors unhandled and the catch alone would leave
 * the bad file in place forever.
 */
private val Context.dataStore by preferencesDataStore(
    name = "wspr_settings",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
)

/** User-facing settings backed by Preferences DataStore. */
data class AppSettings(
    val enabledSources: Set<SourceId> = setOf(SourceId.WSPR_LIVE),
    val qrzUsername: String = "",
    val qrzPassword: String = "",
    val defaultTimeRangeMinutes: Int = 30,
    val useMiles: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val recentCalls: List<String> = emptyList(),
    val bandColorOverrides: Map<String, Long> = emptyMap(),
    /** Per-key epoch-millis of the last rate-limited fetch (e.g. PSKReporter). */
    val rateLimitStamps: Map<String, Long> = emptyMap(),
    /** True once the user has acknowledged they hold a valid amateur licence to transmit. */
    val licenceAcknowledged: Boolean = false,
)

/**
 * The settings slice the spot screens need. Lets [com.atvriders.wsprtxrx.ui.SpotsViewModel]
 * be unit-tested without a DataStore (and therefore without an Android Context).
 */
interface SpotsSettings {
    val settings: Flow<AppSettings>
    suspend fun addRecentCall(call: String)
}

class SettingsStore(
    private val context: Context,
    private val crypto: SecretCrypto = SecretCrypto.NONE,
) : SpotsSettings {

    /** The only read path: corruption- and IO-guarded. Never collect `dataStore.data` directly. */
    private val safeData: Flow<Preferences> = context.dataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }

    override val settings: Flow<AppSettings> = safeData.map { it.toSettings() }

    suspend fun setEnabledSources(sources: Set<SourceId>) = edit {
        it[Keys.SOURCES] = sources.map(SourceId::name).toSet()
    }

    /**
     * Persists the QRZ username and password. The password is encrypted at rest with an
     * AndroidKeystore AES/GCM key (see [SecretCrypto]) and stored as ciphertext; the
     * legacy plaintext key is cleared. If encryption fails for any reason we deliberately
     * do NOT persist the password (it stays empty) rather than store plaintext or crash.
     */
    suspend fun setQrz(username: String, password: String) = edit {
        it[Keys.QRZ_USER] = username
        // Always clear any legacy plaintext value.
        it.remove(Keys.QRZ_PASS)
        val enc = if (password.isEmpty()) "" else crypto.encrypt(password)
        if (enc != null) {
            it[Keys.QRZ_PASS_ENC] = enc
        } else {
            // Encryption unavailable/failed: drop the password rather than leak it.
            it.remove(Keys.QRZ_PASS_ENC)
        }
    }

    suspend fun setDefaultTimeRange(minutes: Int) = edit { it[Keys.TIME_RANGE] = minutes }

    suspend fun setUseMiles(useMiles: Boolean) = edit { it[Keys.USE_MILES] = useMiles }

    suspend fun setThemeMode(mode: ThemeMode) = edit { it[Keys.THEME] = mode.name }

    /**
     * Persists the one-time amateur-licence acknowledgement required before first TX.
     * Returns false if the write failed, so the TX gate is never shown as accepted when
     * it was not actually persisted.
     */
    suspend fun setLicenceAcknowledged(ack: Boolean): Boolean = edit { it[Keys.LICENCE_ACK] = ack }

    suspend fun setRecentCalls(calls: List<String>) = edit {
        it[Keys.RECENT_CALLS] = json.encodeToString(ListSerializer(String.serializer()), calls)
    }

    override suspend fun addRecentCall(call: String) {
        val current = settingsSnapshot().recentCalls
        val updated = (listOf(call.uppercase()) + current.filterNot { it.equals(call, true) }).take(20)
        setRecentCalls(updated)
    }

    suspend fun setBandColorOverrides(overrides: Map<String, Long>) = edit {
        it[Keys.BAND_COLORS] = json.encodeToString(
            MapSerializer(String.serializer(), Long.serializer()), overrides,
        )
    }

    /**
     * Persists the epoch-millis of the last successful fetch for a rate-limited [key]
     * (e.g. PSKReporter), so the per-key rate limit survives process death / cold starts.
     * Stored as a JSON map under a single preference key.
     */
    suspend fun setRateLimitStamp(key: String, timeMs: Long) {
        val current = settingsSnapshot().rateLimitStamps.toMutableMap()
        current[key] = timeMs
        edit {
            it[Keys.RATE_LIMIT_STAMPS] = json.encodeToString(
                MapSerializer(String.serializer(), Long.serializer()), current,
            )
        }
    }

    /** Reads the persisted last-fetch epoch-millis for [key], or null if none. */
    suspend fun rateLimitStamp(key: String): Long? = settingsSnapshot().rateLimitStamps[key]

    /**
     * One-time migration of a legacy plaintext QRZ password into the encrypted key. Safe
     * to call repeatedly; no-ops once the plaintext key is gone. Never throws.
     */
    suspend fun migratePlaintextQrz() {
        runCatching {
            val prefs = safeData.first()
            val legacy = prefs[Keys.QRZ_PASS] ?: return
            // Re-encrypt under the Keystore key, then drop the plaintext.
            val enc = if (legacy.isEmpty()) "" else crypto.encrypt(legacy)
            edit {
                it.remove(Keys.QRZ_PASS)
                if (enc != null && enc.isNotEmpty()) it[Keys.QRZ_PASS_ENC] = enc
            }
        }
    }

    private suspend fun settingsSnapshot(): AppSettings = safeData.first().toSettings()

    /**
     * The single write path for all setters. Returns false instead of throwing so a
     * disk-full or IO error cannot take the app down from any of them.
     */
    private suspend fun edit(
        block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit,
    ): Boolean = try {
        context.dataStore.edit(block)
        true
    } catch (e: CancellationException) {
        throw e // never swallow cancellation in a suspend function
    } catch (_: Exception) {
        false
    }

    private fun Preferences.toSettings(): AppSettings = AppSettings(
        enabledSources = (this[Keys.SOURCES] ?: setOf(SourceId.WSPR_LIVE.name))
            .mapNotNull { runCatching { SourceId.valueOf(it) }.getOrNull() }.toSet()
            .ifEmpty { setOf(SourceId.WSPR_LIVE) },
        qrzUsername = this[Keys.QRZ_USER] ?: "",
        // Prefer the Keystore-encrypted value; decrypt fail-safe (null -> empty). Fall
        // back to a legacy plaintext value only until migration replaces it.
        qrzPassword = this[Keys.QRZ_PASS_ENC]?.let { crypto.decrypt(it) }
            ?: this[Keys.QRZ_PASS]
            ?: "",
        defaultTimeRangeMinutes = this[Keys.TIME_RANGE] ?: 30,
        useMiles = this[Keys.USE_MILES] ?: false,
        themeMode = runCatching { ThemeMode.valueOf(this[Keys.THEME] ?: "SYSTEM") }
            .getOrDefault(ThemeMode.SYSTEM),
        recentCalls = this[Keys.RECENT_CALLS]?.let {
            runCatching { json.decodeFromString(ListSerializer(String.serializer()), it) }.getOrNull()
        } ?: emptyList(),
        bandColorOverrides = this[Keys.BAND_COLORS]?.let {
            runCatching {
                json.decodeFromString(MapSerializer(String.serializer(), Long.serializer()), it)
            }.getOrNull()
        } ?: emptyMap(),
        rateLimitStamps = this[Keys.RATE_LIMIT_STAMPS]?.let {
            runCatching {
                json.decodeFromString(MapSerializer(String.serializer(), Long.serializer()), it)
            }.getOrNull()
        } ?: emptyMap(),
        licenceAcknowledged = this[Keys.LICENCE_ACK] ?: false,
    )

    private object Keys {
        val SOURCES = stringSetPreferencesKey("enabled_sources")
        val QRZ_USER = stringPreferencesKey("qrz_user")
        /** Legacy plaintext key, kept only for read-time migration. */
        val QRZ_PASS = stringPreferencesKey("qrz_pass")
        /** AndroidKeystore-encrypted password: base64(iv||ciphertext). */
        val QRZ_PASS_ENC = stringPreferencesKey("qrz_pass_enc")
        val TIME_RANGE = intPreferencesKey("time_range")
        val USE_MILES = booleanPreferencesKey("use_miles")
        val THEME = stringPreferencesKey("theme")
        val RECENT_CALLS = stringPreferencesKey("recent_calls")
        val BAND_COLORS = stringPreferencesKey("band_colors")
        val RATE_LIMIT_STAMPS = stringPreferencesKey("rate_limit_stamps")
        val LICENCE_ACK = booleanPreferencesKey("licence_acknowledged")
    }

    private companion object {
        val json = Json { ignoreUnknownKeys = true }
    }
}
