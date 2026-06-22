package com.atvriders.wsprtxrx.di

import android.content.Context
import androidx.room.Room
import com.atvriders.wsprtxrx.data.SpotRepository
import com.atvriders.wsprtxrx.data.local.AppDatabase
import com.atvriders.wsprtxrx.data.prefs.AppSettings
import com.atvriders.wsprtxrx.data.prefs.KeystoreSecretCrypto
import com.atvriders.wsprtxrx.data.prefs.SettingsStore
import com.atvriders.wsprtxrx.data.qrz.QrzService
import com.atvriders.wsprtxrx.data.source.PskReporterSource
import com.atvriders.wsprtxrx.data.source.RateLimiter
import com.atvriders.wsprtxrx.data.source.RbnSource
import com.atvriders.wsprtxrx.data.source.SpotSource
import com.atvriders.wsprtxrx.data.source.WsprLiveSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Manual dependency-injection container. Builds and holds the app-wide singletons and
 * keeps a synchronous snapshot of [AppSettings] so non-suspending consumers (QRZ
 * credentials, enabled sources) can read the latest values.
 */
class AppContainer(context: Context) {
    val appContext: Context = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Volatile
    var settingsSnapshot: AppSettings = AppSettings()
        private set

    val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    val database: AppDatabase by lazy {
        Room.databaseBuilder(appContext, AppDatabase::class.java, "wspr.db")
            .fallbackToDestructiveMigration()
            .build()
    }

    val settingsStore: SettingsStore by lazy {
        SettingsStore(appContext, crypto = KeystoreSecretCrypto())
    }

    val qrzService: QrzService by lazy {
        QrzService(httpClient, credentials = {
            settingsSnapshot.qrzUsername to settingsSnapshot.qrzPassword
        })
    }

    private val sources: List<SpotSource> by lazy {
        // PSKReporter's ~5-min rate limit is made durable across cold starts by backing
        // the limiter's last-fetch timestamp with DataStore (via settingsStore).
        val pskRateLimiter = RateLimiter(
            minIntervalMs = 5 * 60_000L,
            loadStamp = { key -> settingsStore.rateLimitStamp(key) },
            saveStamp = { key, t -> settingsStore.setRateLimitStamp(key, t) },
        )
        listOf(
            WsprLiveSource(httpClient),
            PskReporterSource(httpClient, rateLimiter = pskRateLimiter),
            RbnSource(),
        )
    }

    val repository: SpotRepository by lazy {
        SpotRepository(
            sources = sources,
            dao = database.spotDao(),
            enabledProvider = { settingsSnapshot.enabledSources },
        )
    }

    init {
        scope.launch {
            // Migrate any legacy plaintext QRZ password to the Keystore-encrypted form.
            runCatching { settingsStore.migratePlaintextQrz() }
            settingsStore.settings.collect { settingsSnapshot = it }
        }
    }
}
