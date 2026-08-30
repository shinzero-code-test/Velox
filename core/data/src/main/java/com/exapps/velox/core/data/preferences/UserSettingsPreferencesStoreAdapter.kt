package com.exapps.velox.core.data.preferences

import com.exapps.velox.core.common.di.ApplicationScope
import com.exapps.velox.core.domain.player.DecoderPreferenceStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Phase 3 / L6 (deferred-backlog): adapter from [UserSettingsPreferences] to the
 * domain [DecoderPreferenceStore] port. Translates the internal
 * [DecoderPreference] enum (which only the persistence layer knows about) into
 * the boolean the player engine needs.
 *
 * Scope: the [stateIn] coroutine is bound to the application scope so the
 * StateFlow outlives any single consumer — the player factory reads
 * [preferSoftwareCached] on the main thread before any consumer is alive, and
 * the Settings screen subscribes to [preferSoftware] over its own lifetime.
 */
@Singleton
class UserSettingsPreferencesStoreAdapter @Inject constructor(
    private val preferences: UserSettingsPreferences,
    @ApplicationScope appScope: CoroutineScope,
) : DecoderPreferenceStore {

    @Volatile
    private var cachedPreferSoftware: Boolean = false

    override fun preferSoftwareCached(): Boolean = cachedPreferSoftware

    override val preferSoftware: StateFlow<Boolean> = preferences.settings
        .map { it.decoderPreference == DecoderPreference.SOFTWARE }
        // Eagerly start the upstream so the first reader sees the primed
        // value rather than a default. The application scope outlives any
        // single consumer.
        .stateIn(
            scope = appScope,
            started = SharingStarted.Eagerly,
            initialValue = cachedPreferSoftware,
        )

    override suspend fun primeCache() {
        preferences.primeCache()
        cachedPreferSoftware = preferences.decoderPreferenceCached() == DecoderPreference.SOFTWARE
    }
}
