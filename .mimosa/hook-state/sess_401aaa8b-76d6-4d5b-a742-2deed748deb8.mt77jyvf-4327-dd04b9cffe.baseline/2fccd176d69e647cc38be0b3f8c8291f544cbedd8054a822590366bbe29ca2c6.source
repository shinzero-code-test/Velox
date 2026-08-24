package com.exapps.velox.player.engine

import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.Virtualizer
import com.exapps.velox.core.domain.player.AudioEffectsController
import com.exapps.velox.core.domain.player.EqualizerBand
import com.exapps.velox.core.domain.player.EqualizerPreset
import com.exapps.velox.core.domain.player.EqualizerState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The android.media.audiofx half of SCREEN_EQUALIZER.md, attached to the single
 * ExoPlayer's audio session (the session id arrives asynchronously — hence
 * [attachToAudioSession] being called from the player's listener rather than at
 * construction).
 *
 * Device band counts vary (5 is common); Velox presets and persistence are defined
 * on the canonical 10 frequencies and mapped by nearest frequency at the edges of
 * the app — see [EqualizerPreset.gainsFor] and EqualizerPreferences.
 *
 * Every platform call is wrapped in runCatching: audiofx is a vendor HAL surface
 * and throws unchecked on devices without EQ support, which should degrade to
 * "standing by" rather than crash the player.
 */
@Singleton
class AndroidAudioEffectsController @Inject constructor() : AudioEffectsController {

    private val _state = MutableStateFlow<EqualizerState?>(null)
    override val state: StateFlow<EqualizerState?> = _state.asStateFlow()

    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var virtualizer: Virtualizer? = null

    private var enabled = false
    private var bassStrength = 0
    private var virtualizerStrength = 0
    private var activePresetId: String? = null

    /** Called by [VeloxExoPlayerFactory]'s listener whenever the session id is set or changes. */
    fun attachToAudioSession(sessionId: Int) {
        if (sessionId == 0) return
        releaseEffects()
        runCatching {
            val eq = Equalizer(/* priority = */ 0, sessionId)
            val bass = BassBoost(0, sessionId)
            val virt = Virtualizer(0, sessionId)
            equalizer = eq
            bassBoost = bass
            virtualizer = virt
            eq.enabled = enabled
            bass.enabled = enabled
            virt.enabled = enabled
            if (bassStrength > 0) bass.setStrength(bassStrength.coerceIn(0, 1000).toShort())
            if (virtualizerStrength > 0) virt.setStrength(virtualizerStrength.coerceIn(0, 1000).toShort())
            publishState()
        }
    }

    override fun setEnabled(enabled: Boolean) {
        this.enabled = enabled
        runCatching {
            equalizer?.enabled = enabled
            bassBoost?.enabled = enabled
            virtualizer?.enabled = enabled
        }
        publishState()
    }

    override fun setBandLevel(bandIndex: Int, levelMillibel: Int) {
        val eq = equalizer ?: return
        runCatching {
            val range = eq.bandLevelRange
            eq.setBandLevel(bandIndex.toShort(), levelMillibel.coerceIn(range[0].toInt(), range[1].toInt()).toShort())
        }
        // A manual drag always leaves preset territory (SCREEN_EQUALIZER.md §4 "User").
        activePresetId = null
        publishState()
    }

    override fun setBassBoostStrength(strength: Int) {
        bassStrength = strength.coerceIn(0, 1000)
        runCatching {
            bassBoost?.takeIf { enabled }?.setStrength(bassStrength.toShort())
        }
        publishState()
    }

    override fun setVirtualizerStrength(strength: Int) {
        virtualizerStrength = strength.coerceIn(0, 1000)
        runCatching {
            virtualizer?.takeIf { enabled }?.setStrength(virtualizerStrength.toShort())
        }
        publishState()
    }

    override fun applyPreset(preset: EqualizerPreset) {
        activePresetId = preset.name
        val bands = _state.value?.bands.orEmpty()
        preset.gainsFor(bands).forEachIndexed { index, level ->
            setBandLevel(index, level)
        }
        activePresetId = preset.name // setBandLevel clears it; re-assert after the loop
        publishState()
    }

    override fun reset() {
        val bands = _state.value?.bands.orEmpty()
        bands.forEach { setBandLevel(it.index, 0) }
        activePresetId = EqualizerPreset.NORMAL.name
        setBassBoostStrength(0)
        setVirtualizerStrength(0)
        publishState()
    }

    /** Called from VeloxPlaybackService.onDestroy so effect objects don't outlive the player. */
    fun release() {
        releaseEffects()
        _state.value = null
    }

    private fun releaseEffects() {
        runCatching { equalizer?.enabled = false }
        runCatching { bassBoost?.enabled = false }
        runCatching { virtualizer?.enabled = false }
        runCatching { equalizer?.release() }
        runCatching { bassBoost?.release() }
        runCatching { virtualizer?.release() }
        equalizer = null
        bassBoost = null
        virtualizer = null
    }

    private fun publishState() {
        val eq = equalizer ?: run {
            _state.value = null
            return
        }
        runCatching {
            val count = eq.numberOfBands.toInt()
            val range = eq.bandLevelRange
            val bands = (0 until count).map { i ->
                val band = i.toShort()
                EqualizerBand(
                    index = i,
                    centerFrequencyMilliHz = eq.getCenterFreq(band).toLong(),
                    levelMillibel = eq.getBandLevel(band).toInt(),
                    minLevelMillibel = range[0].toInt(),
                    maxLevelMillibel = range[1].toInt(),
                )
            }
            _state.update {
                EqualizerState(
                    enabled = enabled,
                    bands = bands,
                    bassBoostStrength = bassStrength,
                    virtualizerStrength = virtualizerStrength,
                    activePresetId = activePresetId,
                )
            }
        }
    }
}
