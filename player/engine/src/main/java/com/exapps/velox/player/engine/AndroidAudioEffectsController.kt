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

    /**
     * H3 (player-stack review): every field below is shared across the playback
     * thread (audio-session attach), the main thread (setter calls from the EQ
     * ViewModel), and the [scope]'s default dispatcher (the restore-or-flush
     * coroutine launched from [attachToAudioSession]). Without serialisation a
     * band-level set during a session-id change could be partially overwritten
     * by a half-applied restore. The single [lock] (a Java monitor) protects
     * every field, and the [generation] counter invalidates an in-flight
     * restore that started before a new attach or release ran (M7).
     */
    private val lock = Any()
    @Volatile
    private var generation: Int = 0

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
        val thisGeneration: Int
        synchronized(lock) {
            // Bump the generation first so any in-flight restore coroutine
            // launched by the previous attach becomes a no-op (M7).
            generation++
            thisGeneration = generation
            releaseEffectsLocked()
            runCatching {
                val eq = Equalizer(/* priority = */ 0, sessionId)
                val bass = BassBoost(0, sessionId)
                val virt = Virtualizer(0, sessionId)
                equalizer = eq
                bassBoost = bass
                virtualizer = virt
                applyDesiredToHardwareLocked()
                publishStateLocked()
            }
        }
        // Rehydrate whatever the user last saved (unless this process has fresher,
        // un-persisted edits — those win here and get flushed to DataStore instead).
        // M7: the generation counter invalidates this if a newer attach or
        // release runs while the coroutine is suspended on the DataStore read.
        scope.launch {
            runCatching { onAttachedRestoreOrFlush(thisGeneration) }
        }
    }

    /** Applies every cached desired value to the freshly-attached effect objects. Caller holds [lock]. */
    private fun applyDesiredToHardwareLocked() {
        val eq = equalizer ?: return
        runCatching {
            eq.enabled = enabled
            bassBoost?.enabled = enabled
            virtualizer?.enabled = enabled

            val range = eq.bandLevelRange
            desiredBandLevels.forEach { (band, level) ->
                // L11 (player-stack review): read the hardware's actual
                // value back into the desired map after each set, so
                // `publishState` reflects the truth (the device may
                // have clamped to a narrower range than what we asked).
                val coerced = level.coerceIn(range[0].toInt(), range[1].toInt())
                eq.setBandLevel(band.toShort(), coerced.toShort())
                val actual = runCatching { eq.getBandLevel(band.toShort()).toInt() }.getOrNull()
                if (actual != null) desiredBandLevels[band] = actual
            }
            if (bassStrength > 0) bassBoost?.setStrength(bassStrength.coerceIn(0, 1000).toShort())
            if (virtualizerStrength > 0) virtualizer?.setStrength(virtualizerStrength.coerceIn(0, 1000).toShort())
        }
    }

    /**
     * Post-attach hook: either restore the persisted curve (fresh process) or flush
     * this process's pre-attach edits into persistence so they survive restarts.
     * M7 (player-stack review): every operation checks the generation counter
     * after acquiring the lock — if a newer attach or release ran while the
     * coroutine was suspended on the DataStore read, this restore is dropped.
     */
    private suspend fun onAttachedRestoreOrFlush(seenGeneration: Int) {
        // Read the current band list outside the long DataStore suspension —
        // it's a single map lookup and keeps the critical section short.
        val bands: List<EqualizerBand> = synchronized(lock) {
            if (generation != seenGeneration) return
            _state.value?.bands.orEmpty()
        }
        if (bands.isEmpty()) return

        val saved: EqualizerSettings
        val shouldPersist: Boolean
        synchronized(lock) {
            if (generation != seenGeneration) return
            shouldPersist = dirtyInSession
        }
        if (shouldPersist) {
            // Flush this process's un-persisted edits so they survive restart.
            // Snapshot the state under the lock, then release it and
            // call the suspending DataStore write outside the critical
            // section (Kotlin forbids suspending inside a non-suspending
            // synchronized block).
            val snapshot = synchronized(lock) {
                if (generation != seenGeneration) return
                Snapshot(
                    enabled = enabled,
                    presetId = activePresetId,
                    bassStrength = bassStrength,
                    virtualizerStrength = virtualizerStrength,
                    canonical = EqualizerPreset.NORMAL.frequenciesHz.map { freqHz ->
                        bands.minBy { abs(it.centerFrequencyMilliHz / 1000.0 - freqHz) }
                            ?.let { desiredBandLevels[it.index] ?: it.levelMillibel } ?: 0
                    },
                )
            }
            persistSnapshot(snapshot)
            return
        }
        saved = preferences.settings.first()
        synchronized(lock) {
            if (generation != seenGeneration) return
            enabled = saved.enabled
            bassStrength = saved.bassBoostStrength
            virtualizerStrength = saved.virtualizerStrength
            activePresetId = saved.presetId
            // M9 (features review): ≤6-band devices collapse several canonical
            // frequencies onto the same physical band — first canonical wins
            // (don't overwrite an already-populated band), giving a deterministic
            // result independent of map iteration order.
            EqualizerPreset.NORMAL.frequenciesHz.forEachIndexed { canonicalIndex, freqHz ->
                val target = bands.minBy { abs(it.centerFrequencyMilliHz / 1000.0 - freqHz) }
                val savedLevel = saved.bandGainsMillibel.getOrNull(canonicalIndex) ?: 0
                if (desiredBandLevels[target.index] == null) {
                    desiredBandLevels[target.index] = savedLevel
                }
            }
            applyDesiredToHardwareLocked()
            publishStateLocked()
        }
    }

    /**
     * Snapshot of the EQ state for the suspending DataStore write. Captured
     * under [lock] so the subsequent prefs.save (which holds DataStores
     * own lock and may suspend) sees a consistent view.
     */
    private data class Snapshot(
        val enabled: Boolean,
        val presetId: String?,
        val bassStrength: Int,
        val virtualizerStrength: Int,
        val canonical: List<Int>,
    )

    /** Persist a pre-snapshotted EQ state. Safe to call outside the lock. */
    private suspend fun persistSnapshot(snapshot: Snapshot) {
        runCatching {
            preferences.save(
                EqualizerSettings(
                    enabled = snapshot.enabled,
                    presetId = snapshot.presetId,
                    bandGainsMillibel = snapshot.canonical,
                    bassBoostStrength = snapshot.bassStrength,
                    virtualizerStrength = snapshot.virtualizerStrength,
                ),
            )
        }
    }

    override fun setEnabled(enabled: Boolean) {
        synchronized(lock) {
            this.enabled = enabled
            dirtyInSession = true
            runCatching {
                equalizer?.enabled = enabled
                bassBoost?.enabled = enabled
                virtualizer?.enabled = enabled
            }
            publishStateLocked()
        }
    }

    override fun setBandLevel(bandIndex: Int, levelMillibel: Int) {
        synchronized(lock) {
            dirtyInSession = true
            val range = equalizer?.bandLevelRange
            val coerced = levelMillibel.coerceIn(range?.get(0)?.toInt() ?: -1500, range?.get(1)?.toInt() ?: 1500)
            desiredBandLevels[bandIndex] = coerced
            runCatching { equalizer?.setBandLevel(bandIndex.toShort(), coerced.toShort()) }
            // L11 (player-stack review): re-read the hardware's value in
            // case it clamped our request, so the StateFlow reflects
            // reality (rather than the user's last drag coordinate).
            runCatching { equalizer?.getBandLevel(bandIndex.toShort())?.toInt() }
                ?.getOrNull()
                ?.let { desiredBandLevels[bandIndex] = it }
            // A manual drag always leaves preset territory (SCREEN_EQUALIZER.md §4 "User").
            activePresetId = null
            publishStateLocked()
        }
    }

    override fun setBassBoostStrength(strength: Int) {
        synchronized(lock) {
            bassStrength = strength.coerceIn(0, 1000)
            dirtyInSession = true
            runCatching {
                bassBoost?.takeIf { enabled }?.setStrength(bassStrength.toShort())
            }
            publishStateLocked()
        }
    }

    override fun setVirtualizerStrength(strength: Int) {
        synchronized(lock) {
            virtualizerStrength = strength.coerceIn(0, 1000)
            dirtyInSession = true
            runCatching {
                virtualizer?.takeIf { enabled }?.setStrength(virtualizerStrength.toShort())
            }
            publishStateLocked()
        }
    }

    override fun applyPreset(preset: EqualizerPreset) {
        synchronized(lock) {
            dirtyInSession = true
            val bands = _state.value?.bands.orEmpty()
            preset.gainsFor(bands).forEachIndexed { index, level ->
                val range = equalizer?.bandLevelRange
                val coerced = level.coerceIn(range?.get(0)?.toInt() ?: -1500, range?.get(1)?.toInt() ?: 1500)
                desiredBandLevels[index] = coerced
                runCatching { equalizer?.setBandLevel(index.toShort(), coerced.toShort()) }
            }
            activePresetId = preset.name
            publishStateLocked()
        }
    }

    override fun reset() {
        synchronized(lock) {
            dirtyInSession = true
            val bands = _state.value?.bands.orEmpty()
            bands.forEach {
                val range = equalizer?.bandLevelRange
                val coerced = 0.coerceIn(range?.get(0)?.toInt() ?: -1500, range?.get(1)?.toInt() ?: 1500)
                desiredBandLevels[it.index] = coerced
                runCatching { equalizer?.setBandLevel(it.index.toShort(), coerced.toShort()) }
            }
            activePresetId = EqualizerPreset.NORMAL.name
            bassStrength = 0
            runCatching { bassBoost?.takeIf { enabled }?.setStrength(0) }
            virtualizerStrength = 0
            runCatching { virtualizer?.takeIf { enabled }?.setStrength(0) }
            publishStateLocked()
        }
    }

    /** Called from VeloxPlaybackService.onDestroy so effect objects don't outlive the player. */
    fun release() {
        synchronized(lock) {
            generation++
            releaseEffectsLocked()
        }
        _state.value = null
    }

    /** Caller holds [lock]. */
    private fun releaseEffectsLocked() {
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

    /** Caller holds [lock]. */
    private fun publishStateLocked() {
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
