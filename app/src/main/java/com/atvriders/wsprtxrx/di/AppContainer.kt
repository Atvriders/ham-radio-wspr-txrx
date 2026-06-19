package com.atvriders.wsprtxrx.di

import android.content.Context

/**
 * Manual dependency-injection container. Constructs and holds app-wide singletons
 * (HTTP client, database, repository, settings, services). Expanded as the data and
 * UI layers are built out; kept deliberately small to avoid a DI framework.
 */
class AppContainer(private val appContext: Context) {
    // Singletons are added here as later phases land (OkHttp, Room, repository, etc.).
}
