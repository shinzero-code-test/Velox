package com.exapps.velox.core.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.exapps.velox.core.domain.player.EqualizerPreset
import com.exapps.velox.core.domain.player.EqualizerSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * SCREEN_EQUALIZER.md §7: "Back → persist current settings". The canonical 10-band
 * curve is stored (not device band levels) so settings survive across devices with
 * different band counts; EqualizerViewModel maps both directions via nearest
 * frequency, the same way presets do.
 *
 * Phase 3 / L6 (deferred-backlog): the [EqualizerSettings] data class moved to
 * `:core:domain` so the player engine can depend on the port
 * [com.exapps.velox.core.domain.player.EqualizerPreferencesStore] without
 * re-introducing a `:core:data` edge. This class still owns the DataStore
 * representation; the port adapter (in `EqualizerPreferencesStoreAdapter`)
 * bridges to and from the domain shape.
 */
@Singleton
class EqualizerPreferences @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {

    val settings: Flow<EqualizerSettings> = dataStore.data.map { prefs ->
        val size = EqualizerPreset.NORMAL.frequenciesHz.size
        val storedGains = prefs[GAINS_KEY]
            ?.split(',')
            ?.mapNotNull { it.trim().toIntOrNull() }
            .orEmpty()
        EqualizerSettings(
            enabled = prefs[ENABLED_KEY] ?: false,
            presetId = prefs[PRESET_KEY],
            bandGainsMillibel = List(size) { i -> storedGains.getOrElse(i) { 0 } },
            bassBoostStrength = prefs[BASS_KEY] ?: 0,
            virtualizerStrength = prefs[VIRTUALIZER_KEY] ?: 0,
        )
    }

    suspend fun save(settings: EqualizerSettings) {
        dataStore.edit { prefs ->
            prefs[ENABLED_KEY] = settings.enabled
            // Capture the presetId in a local — the property is
            // declared in a different module (`:core:domain`) so the
            // compiler can't smart-cast it across the null check
            // without a local copy.
            val presetId = settings.presetId
            if (presetId == null) prefs.remove(PRESET_KEY) else prefs[PRESET_KEY] = presetId
            prefs[GAINS_KEY] = settings.bandGainsMillibel.joinToString(",")
            prefs[BASS_KEY] = settings.bassBoostStrength
            prefs[VIRTUALIZER_KEY] = settings.virtualizerStrength
        }
    }

    private companion object {
        val ENABLED_KEY = booleanPreferencesKey("eq_enabled")
        val PRESET_KEY = stringPreferencesKey("eq_preset")
        // Preferences has no Int-list type; the canonical gains are stored as a CSV string.
        val GAINS_KEY = stringPreferencesKey("eq_band_gains")
        val BASS_KEY = intPreferencesKey("eq_bass_strength")
        val VIRTUALIZER_KEY = intPreferencesKey("eq_virtualizer_strength")
    }
}
