package com.exapps.velox.core.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.exapps.velox.core.domain.player.EqualizerPreset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * SCREEN_EQUALIZER.md §7: "Back → persist current settings". The canonical 10-band
 * curve is stored (not device band levels) so settings survive across devices with
 * different band counts; EqualizerViewModel maps both directions via nearest
 * frequency, the same way presets do.
 */
data class EqualizerSettings(
    val enabled: Boolean = false,
    /** Named preset these levels came from; null once the user drags a band (custom). */
    val presetId: String? = null,
    /** Canonical gains in millibel for the 10 frequencies in [EqualizerPreset.NORMAL]. */
    val bandGainsMillibel: List<Int> = List(EqualizerPreset.NORMAL.frequenciesHz.size) { 0 },
    val bassBoostStrength: Int = 0,
    val virtualizerStrength: Int = 0,
)

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
            if (settings.presetId == null) prefs.remove(PRESET_KEY) else prefs[PRESET_KEY] = settings.presetId
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
