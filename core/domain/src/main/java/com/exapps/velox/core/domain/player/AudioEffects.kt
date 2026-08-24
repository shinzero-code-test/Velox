package com.exapps.velox.core.domain.player

import kotlinx.coroutines.flow.StateFlow

/**
 * The user-facing shape of the system equalizer (SCREEN_EQUALIZER.md). Gains are in
 * millibels — that's android.media.audiofx.Equalizer's native unit (1500 = +15dB)
 * and keeping it avoids a second conversion layer for no benefit.
 */
data class EqualizerBand(
    /** Index into the device equalizer's own band list (0..numberOfBands-1). */
    val index: Int,
    val centerFrequencyMilliHz: Long,
    val levelMillibel: Int,
    val minLevelMillibel: Int,
    val maxLevelMillibel: Int,
)

data class EqualizerState(
    val enabled: Boolean = false,
    val bands: List<EqualizerBand> = emptyList(),
    val bassBoostStrength: Int = 0,
    val virtualizerStrength: Int = 0,
    /** The named preset the current band levels came from, or null once the user
     * has dragged a slider past a preset (SCREEN_EQUALIZER.md §4's "مستخدم / User"). */
    val activePresetId: String? = null,
)

/**
 * Velox's own preset curves, defined on the classic 10 EQ frequencies. Devices
 * expose a varying number of bands (5 is common, 10 on some); each band picks the
 * preset value of its nearest canonical frequency — the standard approach for
 * preset portability across devices. Values are in dB, clamped to ±15.
 */
enum class EqualizerPreset(val frequenciesHz: IntArray, val gainsDb: IntArray) {
    NORMAL(
        intArrayOf(31, 62, 125, 250, 500, 1000, 2000, 4000, 8000, 16000),
        intArrayOf(0, 0, 0, 0, 0, 0, 0, 0, 0, 0),
    ),
    POP(
        intArrayOf(31, 62, 125, 250, 500, 1000, 2000, 4000, 8000, 16000),
        intArrayOf(-1, 1, 3, 4, 3, 0, -1, -1, -1, -1),
    ),
    ROCK(
        intArrayOf(31, 62, 125, 250, 500, 1000, 2000, 4000, 8000, 16000),
        intArrayOf(5, 4, 3, 1, -1, -1, 1, 3, 4, 5),
    ),
    JAZZ(
        intArrayOf(31, 62, 125, 250, 500, 1000, 2000, 4000, 8000, 16000),
        intArrayOf(3, 2, 1, 2, -1, -1, 0, 1, 3, 4),
    ),
    CLASSICAL(
        intArrayOf(31, 62, 125, 250, 500, 1000, 2000, 4000, 8000, 16000),
        intArrayOf(4, 3, 2, 0, -1, -1, 0, 2, 3, 4),
    ),
    BASS(
        intArrayOf(31, 62, 125, 250, 500, 1000, 2000, 4000, 8000, 16000),
        intArrayOf(6, 5, 4, 3, 1, 0, 0, 0, 0, 0),
    ),
    VOCAL(
        intArrayOf(31, 62, 125, 250, 500, 1000, 2000, 4000, 8000, 16000),
        intArrayOf(-2, -1, 0, 1, 2, 3, 3, 2, 1, 0),
    ),
    ELECTRONIC(
        intArrayOf(31, 62, 125, 250, 500, 1000, 2000, 4000, 8000, 16000),
        intArrayOf(4, 3, 1, 0, -2, 1, 1, 2, 3, 4),
    );

    /** Maps this preset's curve onto [bands] by nearest canonical frequency. */
    fun gainsFor(bands: List<EqualizerBand>): List<Int> = bands.map { band ->
        val freqHz = (band.centerFrequencyMilliHz / 1000.0)
        val nearest = frequenciesHz.indices.minBy { kotlin.math.abs(frequenciesHz[it] - freqHz) }
        (gainsDb[nearest] * 100).coerceIn(band.minLevelMillibel, band.maxLevelMillibel)
    }
}

/**
 * The audio-effects seam, mirroring [PlayerController]: `:core:domain` knows only
 * this interface; `:player:engine` owns the real android.media.audiofx effects
 * attached to the ExoPlayer's audio session. The equalizer screen collects [state]
 * and pushes changes through the setters; nothing else in the app touches the
 * platform effect APIs.
 *
 * [state] is null until an audio session exists (no playback yet) — the UI treats
 * that as "effects standing by", not an error.
 */
interface AudioEffectsController {

    val state: StateFlow<EqualizerState?>

    fun setEnabled(enabled: Boolean)
    fun setBandLevel(bandIndex: Int, levelMillibel: Int)
    fun setBassBoostStrength(strength: Int)
    fun setVirtualizerStrength(strength: Int)
    fun applyPreset(preset: EqualizerPreset)
    fun reset()
}
