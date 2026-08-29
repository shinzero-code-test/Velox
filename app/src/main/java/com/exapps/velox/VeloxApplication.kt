package com.exapps.velox

import android.app.Application
import com.exapps.velox.core.data.preferences.UserSettingsPreferences
import com.exapps.velox.core.data.preferences.VeloxLocaleManager
import java.io.File
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

@HiltAndroidApp
class VeloxApplication : Application() {

    @Inject lateinit var localeManager: VeloxLocaleManager
    @Inject lateinit var userSettings: UserSettingsPreferences

    companion object {
        const val LAST_CRASH_FILE = "last_crash.txt"
        const val PREV_CRASH_FILE = "last_crash_prev.txt"
    }

    override fun onCreate() {
        super.onCreate()

        // Phase 1.1 "Crash & ANR hardening": persist the last crash so it can be
        // inspected/shared from Settings → About even after the process is gone.
        // L10 (app-shell review): keep one previous report so consecutive crashes
        // don't erase each other before the About screen can read them.
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching {
                val current = File(filesDir, LAST_CRASH_FILE)
                if (current.isFile) {
                    current.renameTo(File(filesDir, PREV_CRASH_FILE))
                }
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
        // H5 (player-stack review): warm the caches the playback service reads
        // synchronously on its main thread (locale → attachBaseContext, decoder
        // preference → renderers factory). Doing it here, while the process is
        // still cold-starting, costs a single DataStore read per key rather
        // than one per service create.
        runBlocking {
            localeManager.load()
            userSettings.primeCache()
        }
        VeloxLocaleManager.instance = localeManager
    }
}
