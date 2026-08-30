package com.exapps.velox.core.domain.player

/**
 * SCREEN_EQUALIZER.md §7: "Back → persist current settings". The canonical 10-band
 * curve is stored (not device band levels) so settings survive across devices with
 * different band counts; the audio effects controller and the EQ feature both map
 * to / from this shape via nearest frequency, the same way [EqualizerPreset] does.
 *
 * Phase 3 / L6 (deferred-backlog): moved from `:core:data` to `:core:domain` so
 * the player engine can depend on the port [EqualizerPreferencesStore] without
 * re-introducing the `:core:data` edge. The `:core:data` adapter maps this
 * shape to and from its DataStore representation.
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
