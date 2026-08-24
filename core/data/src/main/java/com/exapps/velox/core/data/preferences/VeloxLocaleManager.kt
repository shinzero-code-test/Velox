package com.exapps.velox.core.data.preferences

import android.content.Context
import android.content.res.Configuration
import kotlinx.coroutines.flow.first
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * In-app language switching without AppCompat (TECHNICAL_PLAN.md §7: "Date, time,
 * number formatting via proper locale" and SCREEN_SETTINGS.md §7 "Immediate apply").
 *
 * [applyTo] wraps an activity's base context with the persisted locale; the cached
 * [current] value is loaded once in Application.onCreate (before any activity's
 * attachBaseContext runs) and refreshed whenever the user changes the language,
 * after which the Settings screen recreates the activity.
 */
@Singleton
class VeloxLocaleManager @Inject constructor(
    private val preferences: UserSettingsPreferences,
) {

    @Volatile
    var current: AppLanguage = AppLanguage.SYSTEM
        private set

    suspend fun load() {
        current = preferences.settings.first().language
    }

    /**
     * Publishes a just-chosen language immediately, so the Settings screen's
     * activity recreate() picks it up in attachBaseContext without waiting for
     * the DataStore round-trip (or a full process restart) — SCREEN_SETTINGS.md
     * §7 "Immediate apply".
     */
    fun applyNow(language: AppLanguage) {
        current = language
    }

    fun applyTo(context: Context): Context {
        val locale = when (current) {
            AppLanguage.SYSTEM -> return context
            AppLanguage.ARABIC -> Locale("ar")
            AppLanguage.ENGLISH -> Locale("en")
        }
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        config.setLayoutDirection(locale)
        return context.createConfigurationContext(config)
    }

    companion object {
        /** attachBaseContext runs before Hilt field injection on an Activity, so the
         * Application publishes the singleton here right after it loads it. */
        @Volatile
        var instance: VeloxLocaleManager? = null
    }
}
