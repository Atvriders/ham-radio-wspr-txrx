package com.atvriders.wsprtxrx

import android.app.Application
import com.atvriders.wsprtxrx.di.AppContainer

/** Application entry point. Owns the [AppContainer] (manual dependency injection). */
class WsprApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
