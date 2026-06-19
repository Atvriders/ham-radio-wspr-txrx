package com.atvriders.wsprtxrx.di

import android.content.Context
import androidx.room.Room
import com.atvriders.wsprtxrx.data.SpotRepository
import com.atvriders.wsprtxrx.data.local.AppDatabase
import com.atvriders.wsprtxrx.data.prefs.AppSettings
import com.atvriders.wsprtxrx.data.prefs.SettingsStore
import com.atvriders.wsprtxrx.data.qrz.QrzService
import com.atvriders.wsprtxrx.data.source.PskReporterSource
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
    private val appContext = context.applicationContext
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

    val settingsStore: SettingsStore by lazy { SettingsStore(appContext) }

    val qrzService: QrzService by lazy {
        QrzService(httpClient, credentials = {
            settingsSnapshot.qrzUsername to settingsSnapshot.qrzPassword
        })
    }

    private val sources: List<SpotSource> by lazy {
        listOf(
            WsprLiveSource(httpClient),
            PskReporterSource(httpClient),
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
            settingsStore.settings.collect { settingsSnapshot = it }
        }
    }
}
