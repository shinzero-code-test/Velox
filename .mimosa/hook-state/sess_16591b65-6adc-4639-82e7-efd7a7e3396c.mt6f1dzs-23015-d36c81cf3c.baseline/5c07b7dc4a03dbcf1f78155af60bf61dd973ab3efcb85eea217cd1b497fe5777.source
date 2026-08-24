package com.exapps.velox.core.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * SCREEN_ONBOARDING.md: "Shown once (unless reset from Settings)". Kept as its own
 * small class rather than folded into MediaLibraryRepositoryImpl's DataStore usage
 * (which owns the "has scanned" flag) — onboarding-complete and has-scanned are
 * related but not the same event (a user could, in principle, decline the media
 * permission during onboarding and finish it without a scan ever running).
 */
@Singleton
class OnboardingPreferences @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    val isOnboardingComplete: Flow<Boolean> =
        dataStore.data.map { it[ONBOARDING_COMPLETE_KEY] ?: false }

    suspend fun setOnboardingComplete(complete: Boolean) {
        dataStore.edit { it[ONBOARDING_COMPLETE_KEY] = complete }
    }

    private companion object {
        val ONBOARDING_COMPLETE_KEY = booleanPreferencesKey("onboarding_complete")
    }
}
