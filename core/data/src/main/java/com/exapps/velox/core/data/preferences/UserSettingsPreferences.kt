package com.exapps.velox.core.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
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

    val settings: Flow<UserSettings> = dataStore.data.map { prefs ->
        UserSettings(
            language = prefs[LANGUAGE_KEY]?.let(AppLanguage::valueOf) ?: AppLanguage.SYSTEM,
            amoled = prefs[AMOLED_KEY] ?: false,
            accentIndex = prefs[ACCENT_KEY] ?: 0,
            seekIncrementSeconds = prefs[SEEK_INCREMENT_KEY] ?: 10,
            autoPipOnLeave = prefs[AUTO_PIP_KEY] ?: true,
            resumePlayback = prefs[RESUME_KEY] ?: true,
            subtitleScalePercent = prefs[SUBTITLE_SCALE_KEY] ?: 100,
            subtitlePositionBottom = prefs[SUBTITLE_BOTTOM_KEY] ?: true,
            autoLoadExternalSubtitles = prefs[SUBTITLE_AUTOLOAD_KEY] ?: true,
            decoderPreference = prefs[DECODER_PREF_KEY]?.let(DecoderPreference::valueOf) ?: DecoderPreference.AUTO,
            gestureLongPressSpeedBoost = prefs[GESTURE_SPEED_BOOST_KEY] ?: true,
            gestureHorizontalSeekDrag = prefs[GESTURE_H_SEEK_KEY] ?: true,
            gestureVerticalDragMapping = prefs[GESTURE_V_DRAG_KEY]
                ?.let(VerticalDragMapping::valueOf)
                ?: VerticalDragMapping.BRIGHTNESS_LEFT_VOLUME_RIGHT,
        )
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

    // Phase 2
    suspend fun setDecoderPreference(pref: DecoderPreference) = dataStore.edit { it[DECODER_PREF_KEY] = pref.name }
    suspend fun setGestureLongPressSpeedBoost(enabled: Boolean) =
        dataStore.edit { it[GESTURE_SPEED_BOOST_KEY] = enabled }

    suspend fun setGestureHorizontalSeekDrag(enabled: Boolean) =
        dataStore.edit { it[GESTURE_H_SEEK_KEY] = enabled }

    suspend fun setGestureVerticalDragMapping(mapping: VerticalDragMapping) =
        dataStore.edit { it[GESTURE_V_DRAG_KEY] = mapping.name }

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
    }
}
