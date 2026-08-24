package com.exapps.velox.feature.equalizer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.exapps.velox.core.data.preferences.EqualizerPreferences
import com.exapps.velox.core.data.preferences.EqualizerSettings
import com.exapps.velox.core.domain.player.AudioEffectsController
import com.exapps.velox.core.domain.player.EqualizerPreset
import com.exapps.velox.core.domain.player.EqualizerState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * SCREEN_EQUALIZER.md. Live edits go straight to the effects controller (§7 "drag
 * slider → live audio update"); persistence (§7 "Back → persist") happens on each
 * completed interaction, stored as the canonical 10-band curve so settings
 * transfer across devices with different band counts.
 */
@HiltViewModel
class EqualizerViewModel @Inject constructor(
    private val effects: AudioEffectsController,
    private val preferences: EqualizerPreferences,
) : ViewModel() {

    val state: StateFlow<EqualizerState?> = effects.state.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = null,
    )

    init {
        // Restore the saved curve once an audio session exists (the platform EQ
        // can't be configured before then). Levels are applied while the effect is
        // still off, then the switch flips — no audible pop.
        viewModelScope.launch {
            val saved = preferences.settings.first()
            effects.state.filterNotNull().first()
            val preset = saved.presetId?.let { runCatching { EqualizerPreset.valueOf(it) }.getOrNull() }
            if (preset != null) {
                effects.applyPreset(preset)
            } else {
                applyCanonicalGains(saved.bandGainsMillibel)
            }
            effects.setBassBoostStrength(saved.bassBoostStrength)
            effects.setVirtualizerStrength(saved.virtualizerStrength)
            effects.setEnabled(saved.enabled)
        }
    }

    fun onEnabledChange(enabled: Boolean) {
        effects.setEnabled(enabled)
        persist()
    }

    fun onBandLevelChange(bandIndex: Int, levelMillibel: Int) {
        effects.setBandLevel(bandIndex, levelMillibel)
    }

    /** Called on drag end — persisting every drag frame would hammer DataStore. */
    fun onBandLevelChangeFinished() = persist()

    fun onBassBoostChange(strength: Int) {
        effects.setBassBoostStrength(strength)
        persist()
    }

    fun onVirtualizerChange(strength: Int) {
        effects.setVirtualizerStrength(strength)
        persist()
    }

    fun onPresetSelected(preset: EqualizerPreset) {
        effects.applyPreset(preset)
        persist()
    }

    fun onReset() {
        effects.reset()
        persist()
    }

    private fun persist() {
        val current = state.value ?: return
        viewModelScope.launch {
            preferences.save(
                EqualizerSettings(
                    enabled = current.enabled,
                    presetId = current.activePresetId,
                    bandGainsMillibel = canonicalGainsFrom(current),
                    bassBoostStrength = current.bassBoostStrength,
                    virtualizerStrength = current.virtualizerStrength,
                ),
            )
        }
    }

    private fun applyCanonicalGains(canonical: List<Int>) {
        val bands = state.value?.bands.orEmpty()
        if (bands.isEmpty()) return
        EqualizerPreset.NORMAL.frequenciesHz.forEachIndexed { canonicalIndex, freqHz ->
            val band = bands.minBy { abs(it.centerFrequencyMilliHz / 1000.0 - freqHz) }
            val gain = canonical.getOrNull(canonicalIndex) ?: 0
            effects.setBandLevel(band.index, gain)
        }
    }

    /** Device band levels → the canonical 10-frequency curve (nearest-frequency). */
    private fun canonicalGainsFrom(state: EqualizerState): List<Int> =
        EqualizerPreset.NORMAL.frequenciesHz.map { freqHz ->
            state.bands
                .minBy { abs(it.centerFrequencyMilliHz / 1000.0 - freqHz) }
                .levelMillibel
        }
}

/** 31250 milliHz → "31", 16000000 → "16k" (SCREEN_EQUALIZER.md §3 frequency labels). */
internal fun formatBandFrequency(milliHz: Long): String {
    val hz = milliHz / 1000
    return if (hz >= 1000) "${(hz / 1000.0).roundToInt()}k" else hz.toString()
}
