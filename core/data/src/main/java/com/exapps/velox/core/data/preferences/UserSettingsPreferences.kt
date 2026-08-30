package com.exapps.velox.core.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** SCREEN_SETTINGS.md §7: app language. SYSTEM follows the platform locale. */
enum class AppLanguage { SYSTEM, ARABIC, ENGLISH }

/**
 * The persisted half of SCREEN_SETTINGS.md. Everything the Settings screen edits
 * lives here as flat keys; the SettingsViewModel maps rows to these fields, and the
 * app shell (MainActivity) collects the appearance/language subset to re-theme or
 * recreate without any feature knowing DataStore exists.
 *
 * Accent is stored as its index into VeloxColors.AccentOptions to keep core:data
 * free of a Compose Color dependency.
 */
/** Phase 2 "Advanced video processing": which decoders get first pick. */
enum class DecoderPreference { AUTO, SOFTWARE }

/** Phase 2 "Custom gesture configuration": vertical-drag mapping on the video surface. */
enum class VerticalDragMapping { BRIGHTNESS_LEFT_VOLUME_RIGHT, VOLUME_LEFT_BRIGHTNESS_RIGHT }

data class UserSettings(
    val language: AppLanguage = AppLanguage.SYSTEM,
    val amoled: Boolean = false,
    val accentIndex: Int = 0,
    val seekIncrementSeconds: Int = 10,
    val autoPipOnLeave: Boolean = true,
    val resumePlayback: Boolean = true,
    val subtitleScalePercent: Int = 100,
    val subtitlePositionBottom: Boolean = true,
    val autoLoadExternalSubtitles: Boolean = true,
    // --- Phase 3 / Wave 3 / Round 2 ---
    /** Smart silence / intro detection. When on, the player
     *  schedules a one-shot RMS analysis on the first listen of
     *  each track and seeks past the detected intro on the
     *  second listen. Default ON. */
    val intelligentSilenceEnabled: Boolean = true,
    // Phase 3 / Wave 3 / Round 3.5c — auto chapter generation.
    // Default OFF: chapter detection is speculative and can be
    // noisy on mixed-content libraries. The user can opt in via
    // Settings → Playback → Auto chapter generation; when on,
    // the Markers sheet surfaces auto-generated chapters with an
    // "auto" badge so they can be distinguished from sidecar /
    // embedded chapters.
    val autoChapterGenerationEnabled: Boolean = false,
    // --- Phase 2 ---
    val decoderPreference: DecoderPreference = DecoderPreference.AUTO,
    /** Long-press 2x "speed scrub" on the video surface. */
    val gestureLongPressSpeedBoost: Boolean = true,
    /** Horizontal drag scrubs through the video. */
    val gestureHorizontalSeekDrag: Boolean = true,
    val gestureVerticalDragMapping: VerticalDragMapping =
        VerticalDragMapping.BRIGHTNESS_LEFT_VOLUME_RIGHT,
)

@Singleton
class UserSettingsPreferences @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {

    /**
     * H5 (player-stack review): the audio-effects chain and the ExoPlayer
     * factory are constructed during service create, which runs on the main
     * thread. Reading [settings] synchronously there with `runBlocking` blocked
     * the UI for the duration of a DataStore disk read. We instead cache the
     * [DecoderPreference] in an AtomicReference that is primed once at app
     * start (alongside [VeloxLocaleManager.load]) and updated on every write
     * via the [settings] collector. The factory reads the cached value
     * synchronously, falling back to AUTO on the very first call before priming
     * completes (the user's last save wins on the second open).
     */
    @Volatile
    private var cachedDecoderPreference: DecoderPreference = DecoderPreference.AUTO

    val settings: Flow<UserSettings> = dataStore.data.map { prefs ->
        UserSettings(
            language = prefs[LANGUAGE_KEY]?.let { runCatching { AppLanguage.valueOf(it) }.getOrNull() } ?: AppLanguage.SYSTEM,
            amoled = prefs[AMOLED_KEY] ?: false,
            accentIndex = prefs[ACCENT_KEY] ?: 0,
            seekIncrementSeconds = prefs[SEEK_INCREMENT_KEY] ?: 10,
            autoPipOnLeave = prefs[AUTO_PIP_KEY] ?: true,
            resumePlayback = prefs[RESUME_KEY] ?: true,
            subtitleScalePercent = prefs[SUBTITLE_SCALE_KEY] ?: 100,
            subtitlePositionBottom = prefs[SUBTITLE_BOTTOM_KEY] ?: true,
            autoLoadExternalSubtitles = prefs[SUBTITLE_AUTOLOAD_KEY] ?: true,
            intelligentSilenceEnabled = prefs[INTELLIGENT_SILENCE_KEY] ?: true,
            autoChapterGenerationEnabled = prefs[AUTO_CHAPTER_KEY] ?: false,
            decoderPreference = prefs[DECODER_PREF_KEY]
                ?.let { runCatching { DecoderPreference.valueOf(it) }.getOrNull() } ?: DecoderPreference.AUTO,
            gestureLongPressSpeedBoost = prefs[GESTURE_SPEED_BOOST_KEY] ?: true,
            gestureHorizontalSeekDrag = prefs[GESTURE_H_SEEK_KEY] ?: true,
            gestureVerticalDragMapping = prefs[GESTURE_V_DRAG_KEY]
                ?.let { runCatching { VerticalDragMapping.valueOf(it) }.getOrNull() }
                ?: VerticalDragMapping.BRIGHTNESS_LEFT_VOLUME_RIGHT,
        ).also { userSettings ->
            cachedDecoderPreference = userSettings.decoderPreference
        }
    }

    /** Synchronous accessor used by the player factory on the main thread. */
    fun decoderPreferenceCached(): DecoderPreference = cachedDecoderPreference

    /**
     * H5: warm the [cachedDecoderPreference] from disk. Called once from
     * [VeloxApplication.onCreate] alongside the locale load — both happen
     * before any activity's `attachBaseContext` runs, so the first service
     * create observes a hot cache.
     */
    suspend fun primeCache() {
        runCatching {
            cachedDecoderPreference = settings.first().decoderPreference
        }
    }

    suspend fun setLanguage(language: AppLanguage) = dataStore.edit { it[LANGUAGE_KEY] = language.name }
    suspend fun setAmoled(amoled: Boolean) = dataStore.edit { it[AMOLED_KEY] = amoled }
    suspend fun setAccentIndex(index: Int) = dataStore.edit { it[ACCENT_KEY] = index }
    suspend fun setSeekIncrementSeconds(seconds: Int) = dataStore.edit { it[SEEK_INCREMENT_KEY] = seconds }
    suspend fun setAutoPipOnLeave(enabled: Boolean) = dataStore.edit { it[AUTO_PIP_KEY] = enabled }
    suspend fun setResumePlayback(enabled: Boolean) = dataStore.edit { it[RESUME_KEY] = enabled }
    suspend fun setSubtitleScalePercent(percent: Int) = dataStore.edit { it[SUBTITLE_SCALE_KEY] = percent }
    suspend fun setSubtitlePositionBottom(bottom: Boolean) = dataStore.edit { it[SUBTITLE_BOTTOM_KEY] = bottom }
    suspend fun setAutoLoadExternalSubtitles(enabled: Boolean) = dataStore.edit { it[SUBTITLE_AUTOLOAD_KEY] = enabled }

    suspend fun setIntelligentSilenceEnabled(enabled: Boolean) = dataStore.edit { it[INTELLIGENT_SILENCE_KEY] = enabled }
    suspend fun setAutoChapterGenerationEnabled(enabled: Boolean) = dataStore.edit { it[AUTO_CHAPTER_KEY] = enabled }

    // Phase 2
    suspend fun setDecoderPreference(pref: DecoderPreference) = dataStore.edit { it[DECODER_PREF_KEY] = pref.name }
    suspend fun setGestureLongPressSpeedBoost(enabled: Boolean) =
        dataStore.edit { it[GESTURE_SPEED_BOOST_KEY] = enabled }

    suspend fun setGestureHorizontalSeekDrag(enabled: Boolean) =
        dataStore.edit { it[GESTURE_H_SEEK_KEY] = enabled }

    suspend fun setGestureVerticalDragMapping(mapping: VerticalDragMapping) =
        dataStore.edit { it[GESTURE_V_DRAG_KEY] = mapping.name }

    /** M4-partial (data-layer review): one atomic DataStore write for a full
     * settings restore, so readers never observe a half-applied backup. */
    suspend fun applyAll(
        language: AppLanguage,
        amoled: Boolean,
        accentIndex: Int,
        seekIncrementSeconds: Int,
        autoPipOnLeave: Boolean,
        resumePlayback: Boolean,
        subtitleScalePercent: Int,
        subtitlePositionBottom: Boolean,
        autoLoadExternalSubtitles: Boolean,
        intelligentSilenceEnabled: Boolean,
        autoChapterGenerationEnabled: Boolean = false,
        decoderPreference: DecoderPreference,
        gestureLongPressSpeedBoost: Boolean,
        gestureHorizontalSeekDrag: Boolean,
        gestureVerticalDragMapping: VerticalDragMapping,
    ) = dataStore.edit { prefs ->
        prefs[LANGUAGE_KEY] = language.name
        prefs[AMOLED_KEY] = amoled
        prefs[ACCENT_KEY] = accentIndex
        prefs[SEEK_INCREMENT_KEY] = seekIncrementSeconds
        prefs[AUTO_PIP_KEY] = autoPipOnLeave
        prefs[RESUME_KEY] = resumePlayback
        prefs[SUBTITLE_SCALE_KEY] = subtitleScalePercent
        prefs[SUBTITLE_BOTTOM_KEY] = subtitlePositionBottom
        prefs[SUBTITLE_AUTOLOAD_KEY] = autoLoadExternalSubtitles
        prefs[INTELLIGENT_SILENCE_KEY] = intelligentSilenceEnabled
        prefs[AUTO_CHAPTER_KEY] = autoChapterGenerationEnabled
        prefs[DECODER_PREF_KEY] = decoderPreference.name
        prefs[GESTURE_SPEED_BOOST_KEY] = gestureLongPressSpeedBoost
        prefs[GESTURE_H_SEEK_KEY] = gestureHorizontalSeekDrag
        prefs[GESTURE_V_DRAG_KEY] = gestureVerticalDragMapping.name
    }

    private companion object {
        val LANGUAGE_KEY = stringPreferencesKey("app_language")
        val AMOLED_KEY = booleanPreferencesKey("theme_amoled")
        val ACCENT_KEY = intPreferencesKey("accent_index")
        val SEEK_INCREMENT_KEY = intPreferencesKey("seek_increment_seconds")
        val AUTO_PIP_KEY = booleanPreferencesKey("auto_pip_on_leave")
        val RESUME_KEY = booleanPreferencesKey("resume_playback")
        val SUBTITLE_SCALE_KEY = intPreferencesKey("subtitle_scale_percent")
        val SUBTITLE_BOTTOM_KEY = booleanPreferencesKey("subtitle_position_bottom")
        val SUBTITLE_AUTOLOAD_KEY = booleanPreferencesKey("subtitle_autoload_external")
        // Phase 2
        val DECODER_PREF_KEY = stringPreferencesKey("decoder_preference")
        val GESTURE_SPEED_BOOST_KEY = booleanPreferencesKey("gesture_speed_boost")
        val GESTURE_H_SEEK_KEY = booleanPreferencesKey("gesture_h_seek_drag")
        val GESTURE_V_DRAG_KEY = stringPreferencesKey("gesture_v_drag_mapping")
        // Phase 3 / Wave 3 / Round 2.
        val INTELLIGENT_SILENCE_KEY = booleanPreferencesKey("intelligent_silence_enabled")
        // Phase 3 / Wave 3 / Round 3.5c.
        val AUTO_CHAPTER_KEY = booleanPreferencesKey("auto_chapter_generation_enabled")
    }
}
