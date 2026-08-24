package com.exapps.velox.player.engine

import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.Virtualizer
import com.exapps.velox.core.common.di.ApplicationScope
import com.exapps.velox.core.data.preferences.EqualizerPreferences
import com.exapps.velox.core.data.preferences.EqualizerSettings
import com.exapps.velox.core.domain.player.AudioEffectsController
import com.exapps.velox.core.domain.player.EqualizerBand
import com.exapps.velox.core.domain.player.EqualizerPreset
import com.exapps.velox.core.domain.player.EqualizerState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

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
 *
 * Why this class owns persisted-state restore (and not the EQ screen's ViewModel):
 * the effects only exist once an audio session does, so pre-playback interactions
 * used to be lost or overwritten depending on which of screen/attach happened
 * first. All desired values are cached here from the moment they're set, applied
 * whenever hardware attaches, and re-hydrated from DataStore on attach unless this
 * process already has unsaved user changes (those win, and are flushed out).
 */
@Singleton
class AndroidAudioEffectsController @Inject constructor(
    private val preferences: EqualizerPreferences,
    @ApplicationScope private val scope: CoroutineScope,
) : AudioEffectsController {

    private val _state = MutableStateFlow<EqualizerState?>(null)
    override val state: StateFlow<EqualizerState?> = _state.asStateFlow()

    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var virtualizer: Virtualizer? = null

    private var enabled = false
    private var bassStrength = 0
    private var virtualizerStrength = 0
    private var activePresetId: String? = null

    /** Device-band-index → millibel. Survives detach/attach within the process. */
    private val desiredBandLevels = mutableMapOf<Int, Int>()

    /** True once this process has user changes not yet reflected in DataStore. */
    @Volatile
    private var dirtyInSession = false

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
            applyDesiredToHardware()
            publishState()
        }
        // Rehydrate whatever the user last saved (unless this process has fresher,
        // un-persisted edits — those win here and get flushed to DataStore instead).
        scope.launch {
            runCatching { onAttachedRestoreOrFlush() }
        }
    }

    /** Applies every cached desired value to the freshly-attached effect objects. */
    private fun applyDesiredToHardware() {
        val eq = equalizer ?: return
        runCatching {
            eq.enabled = enabled
            bassBoost?.enabled = enabled
            virtualizer?.enabled = enabled

            val range = eq.bandLevelRange
            desiredBandLevels.forEach { (band, level) ->
                eq.setBandLevel(band.toShort(), level.coerceIn(range[0].toInt(), range[1].toInt()).toShort())
            }
            if (bassStrength > 0) bassBoost?.setStrength(bassStrength.coerceIn(0, 1000).toShort())
            if (virtualizerStrength > 0) virtualizer?.setStrength(virtualizerStrength.coerceIn(0, 1000).toShort())
        }
    }

    /**
     * Post-attach hook: either restore the persisted curve (fresh process) or flush
     * this process's pre-attach edits into persistence so they survive restarts.
     */
    private suspend fun onAttachedRestoreOrFlush() {
        val bands = _state.value?.bands.orEmpty()
        if (bands.isEmpty()) return
        if (dirtyInSession) {
            persistCurrentDesired(bands)
        } else {
            val saved = preferences.settings.first()
            enabled = saved.enabled
            bassStrength = saved.bassBoostStrength
            virtualizerStrength = saved.virtualizerStrength
            activePresetId = saved.presetId
            EqualizerPreset.NORMAL.frequenciesHz.forEachIndexed { canonicalIndex, freqHz ->
                val target = bands.minBy { abs(it.centerFrequencyMilliHz / 1000.0 - freqHz) }
                desiredBandLevels[target.index] = saved.bandGainsMillibel.getOrNull(canonicalIndex) ?: 0
            }
            applyDesiredToHardware()
            publishState()
        }
    }

    /** Canonical 10-frequency snapshot of the current desired state → DataStore. */
    private suspend fun persistCurrentDesired(bands: List<EqualizerBand>) {
        val canonical = EqualizerPreset.NORMAL.frequenciesHz.map { freqHz ->
            bands.minBy { abs(it.centerFrequencyMilliHz / 1000.0 - freqHz) }
                ?.let { desiredBandLevels[it.index] ?: it.levelMillibel } ?: 0
        }
        runCatching {
            preferences.save(
                EqualizerSettings(
                    enabled = enabled,
                    presetId = activePresetId,
                    bandGainsMillibel = canonical,
                    bassBoostStrength = bassStrength,
                    virtualizerStrength = virtualizerStrength,
                ),
            )
        }
    }

    override fun setEnabled(enabled: Boolean) {
        this.enabled = enabled
        dirtyInSession = true
        runCatching {
            equalizer?.enabled = enabled
            bassBoost?.enabled = enabled
            virtualizer?.enabled = enabled
        }
        publishState()
    }

    override fun setBandLevel(bandIndex: Int, levelMillibel: Int) {
        dirtyInSession = true
        val range = equalizer?.bandLevelRange
        val coerced = levelMillibel.coerceIn(range?.get(0)?.toInt() ?: -1500, range?.get(1)?.toInt() ?: 1500)
        desiredBandLevels[bandIndex] = coerced
        runCatching { equalizer?.setBandLevel(bandIndex.toShort(), coerced.toShort()) }
        // A manual drag always leaves preset territory (SCREEN_EQUALIZER.md §4 "User").
        activePresetId = null
        publishState()
    }

    override fun setBassBoostStrength(strength: Int) {
        bassStrength = strength.coerceIn(0, 1000)
        dirtyInSession = true
        runCatching {
            bassBoost?.takeIf { enabled }?.setStrength(bassStrength.toShort())
        }
        publishState()
    }

    override fun setVirtualizerStrength(strength: Int) {
        virtualizerStrength = strength.coerceIn(0, 1000)
        dirtyInSession = true
        runCatching {
            virtualizer?.takeIf { enabled }?.setStrength(virtualizerStrength.toShort())
        }
        publishState()
    }

    override fun applyPreset(preset: EqualizerPreset) {
        dirtyInSession = true
        val bands = _state.value?.bands.orEmpty()
        preset.gainsFor(bands).forEachIndexed { index, level ->
            setBandLevel(index, level)
        }
        activePresetId = preset.name // setBandLevel clears it; re-assert after the loop
        publishState()
    }

    override fun reset() {
        dirtyInSession = true
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
                    levelMillibel = desiredBandLevels[i] ?: eq.getBandLevel(band).toInt(),
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
