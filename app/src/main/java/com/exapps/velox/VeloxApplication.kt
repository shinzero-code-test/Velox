package com.exapps.velox

import android.app.Application
import com.exapps.velox.core.data.preferences.VeloxLocaleManager
import java.io.File
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

@HiltAndroidApp
class VeloxApplication : Application() {

    @Inject lateinit var localeManager: VeloxLocaleManager

    companion object {
        const val LAST_CRASH_FILE = "last_crash.txt"
    }

    override fun onCreate() {
        super.onCreate()

        // Phase 1.1 "Crash & ANR hardening": persist the last crash so it can be
        // inspected/shared from Settings → About even after the process is gone.
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching {
                File(filesDir, LAST_CRASH_FILE).writeText(
                    buildString {
                        appendLine("time_epoch_ms=" + System.currentTimeMillis())
                        appendLine("thread=" + thread.name)
                        appendLine(android.util.Log.getStackTraceString(throwable))
                    },
                )
            }
            previousHandler?.uncaughtException(thread, throwable)
        }
        // Loads the persisted app language before any activity's attachBaseContext
        // runs — a single small DataStore read, paid once at process start.
        runBlocking { localeManager.load() }
        VeloxLocaleManager.instance = localeManager
    }
}
