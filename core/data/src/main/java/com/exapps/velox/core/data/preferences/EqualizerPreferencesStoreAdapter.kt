package com.exapps.velox.core.data.preferences

import com.exapps.velox.core.common.di.ApplicationScope
import com.exapps.velox.core.domain.player.EqualizerPreferencesStore
import com.exapps.velox.core.domain.player.EqualizerSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Phase 3 / L6 (deferred-backlog): adapter from [EqualizerPreferences] to the
 * domain [EqualizerPreferencesStore] port. Wraps the underlying DataStore-
 * backed flow in a hot [StateFlow] so the audio effects controller can read
 * the most recent value at any time, not just while a flow collector is
 * alive.
 *
 * Scope: the [stateIn] coroutine is bound to the application scope so the
 * StateFlow outlives the EQ screen ViewModel — the player service subscribes
 * at attach time, before any UI consumer is created.
 */
@Singleton
class EqualizerPreferencesStoreAdapter @Inject constructor(
    private val preferences: EqualizerPreferences,
    @ApplicationScope appScope: CoroutineScope,
) : EqualizerPreferencesStore {

    override val settings: StateFlow<EqualizerSettings> = preferences.settings
        .stateIn(
            scope = appScope,
            // Eagerly start the upstream so the first reader sees the
            // persisted value, not a default. The application scope outlives
            // any single consumer.
            started = SharingStarted.Eagerly,
            initialValue = EqualizerSettings(),
        )

    override suspend fun current(): EqualizerSettings = settings.first()

    override suspend fun save(settings: EqualizerSettings) {
        preferences.save(settings)
    }
}
