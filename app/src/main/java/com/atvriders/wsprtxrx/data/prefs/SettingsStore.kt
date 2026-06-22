package com.atvriders.wsprtxrx.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.atvriders.wsprtxrx.data.model.SourceId
import com.atvriders.wsprtxrx.ui.theme.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

private val Context.dataStore by preferencesDataStore(name = "wspr_settings")

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
)

class SettingsStore(private val context: Context) {

    val settings: Flow<AppSettings> = context.dataStore.data.map { it.toSettings() }

    suspend fun setEnabledSources(sources: Set<SourceId>) = edit {
        it[Keys.SOURCES] = sources.map(SourceId::name).toSet()
    }

    // TODO(security): the QRZ password is persisted in cleartext in this DataStore.
    // It is excluded from cloud backup and device transfer (res/xml backup rules), but
    // it should later be Android Keystore-encrypted (or replaced with the short-lived
    // QRZ session key) so it isn't stored at rest in plaintext on the device.
    suspend fun setQrz(username: String, password: String) = edit {
        it[Keys.QRZ_USER] = username
        it[Keys.QRZ_PASS] = password
    }

    suspend fun setDefaultTimeRange(minutes: Int) = edit { it[Keys.TIME_RANGE] = minutes }

    suspend fun setUseMiles(useMiles: Boolean) = edit { it[Keys.USE_MILES] = useMiles }

    suspend fun setThemeMode(mode: ThemeMode) = edit { it[Keys.THEME] = mode.name }

    suspend fun setRecentCalls(calls: List<String>) = edit {
        it[Keys.RECENT_CALLS] = json.encodeToString(ListSerializer(String.serializer()), calls)
    }

    suspend fun addRecentCall(call: String) {
        val current = settingsSnapshot().recentCalls
        val updated = (listOf(call.uppercase()) + current.filterNot { it.equals(call, true) }).take(20)
        setRecentCalls(updated)
    }

    suspend fun setBandColorOverrides(overrides: Map<String, Long>) = edit {
        it[Keys.BAND_COLORS] = json.encodeToString(
            MapSerializer(String.serializer(), Long.serializer()), overrides,
        )
    }

    private suspend fun settingsSnapshot(): AppSettings =
        context.dataStore.data.first().toSettings()

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.dataStore.edit(block)
    }

    private fun Preferences.toSettings(): AppSettings = AppSettings(
        enabledSources = (this[Keys.SOURCES] ?: setOf(SourceId.WSPR_LIVE.name))
            .mapNotNull { runCatching { SourceId.valueOf(it) }.getOrNull() }.toSet()
            .ifEmpty { setOf(SourceId.WSPR_LIVE) },
        qrzUsername = this[Keys.QRZ_USER] ?: "",
        qrzPassword = this[Keys.QRZ_PASS] ?: "",
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
    )

    private object Keys {
        val SOURCES = stringSetPreferencesKey("enabled_sources")
        val QRZ_USER = stringPreferencesKey("qrz_user")
        val QRZ_PASS = stringPreferencesKey("qrz_pass")
        val TIME_RANGE = intPreferencesKey("time_range")
        val USE_MILES = booleanPreferencesKey("use_miles")
        val THEME = stringPreferencesKey("theme")
        val RECENT_CALLS = stringPreferencesKey("recent_calls")
        val BAND_COLORS = stringPreferencesKey("band_colors")
    }

    private companion object {
        val json = Json { ignoreUnknownKeys = true }
    }
}
