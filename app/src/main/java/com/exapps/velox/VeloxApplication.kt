package com.exapps.velox

import android.app.Application
import com.exapps.velox.core.data.preferences.VeloxLocaleManager
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

@HiltAndroidApp
class VeloxApplication : Application() {

    @Inject lateinit var localeManager: VeloxLocaleManager

    override fun onCreate() {
        super.onCreate()
        // Loads the persisted app language before any activity's attachBaseContext
        // runs — a single small DataStore read, paid once at process start.
        runBlocking { localeManager.load() }
        VeloxLocaleManager.instance = localeManager
    }
}
