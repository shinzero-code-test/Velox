package com.exapps.velox.feature.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.exapps.velox.core.data.preferences.AppLanguage
import com.exapps.velox.core.data.preferences.ThemePreferences
import com.exapps.velox.core.data.preferences.UserSettings
import com.exapps.velox.core.data.preferences.UserSettingsPreferences
import com.exapps.velox.core.data.preferences.VeloxLocaleManager
import com.exapps.velox.core.domain.recommendation.RecommendationEngine
import com.exapps.velox.core.domain.repository.MediaLibraryRepository
import com.exapps.velox.core.domain.theme.ThemeDefinition
import com.exapps.velox.core.domain.theme.ThemeRegistry
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferences: UserSettingsPreferences,
    private val localeManager: VeloxLocaleManager,
    private val backupManager: com.exapps.velox.core.data.backup.BackupManager,
    private val libraryRepository: MediaLibraryRepository,
    private val themeRegistry: ThemeRegistry,
    private val themePreferences: ThemePreferences,
    // Phase 3 / Wave 3 / Round 3 — Milestone 7. Used by
    // [resetRecommendations] to drop the in-memory co-occurrence
    // matrix; the next read re-builds it from play history.
    private val recommendationEngine: RecommendationEngine,
) : ViewModel() {

    val settings: StateFlow<UserSettings> = preferences.settings.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = UserSettings(),
    )

    /**
     * Phase 3 / Milestone 2 — Theme engine. The list of themes the user
     * can pick from (bundled + imported). The list is re-fetched on
     * subscription and after [importTheme] succeeds; a manual refresh
     * via [refreshThemes] is also available for the settings UI.
     */
    private val _availableThemes = MutableStateFlow<List<ThemeDefinition>>(emptyList())
    val availableThemes: StateFlow<List<ThemeDefinition>> = _availableThemes.asStateFlow()

    val activeTheme: StateFlow<ThemeDefinition> = themeRegistry.active

    /**
     * Phase 1.1 crash hardening: last crash summary (null = none on record).
     *
     * L12 (features review): the previous `by lazy { ... }` did a disk read
     * on first access, which on the Settings screen's first composition
     * blocked the UI thread until the read finished. We now load the file
     * once at VM init time on Dispatchers.IO and expose the result as a
     * StateFlow.
     */
    private val _lastCrashSummary = MutableStateFlow<String?>(null)
    val lastCrashSummary: StateFlow<String?> = _lastCrashSummary.asStateFlow()

    init {
        viewModelScope.launch {
            val text = withContext(kotlinx.coroutines.Dispatchers.IO) { readCrashSummary() }
            _lastCrashSummary.value = text
        }
        // Phase 3 / Milestone 2: load the available themes (bundled +
        // imported) on first composition. The list is short — at most
        // a dozen or so JSON files — so the IO cost is negligible and
        // we don't bother with a debounce.
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            _availableThemes.value = themeRegistry.available()
        }
    }

    private fun readCrashSummary(): String? = runCatching {
        val f = java.io.File(context.filesDir, "last_crash.txt")
        if (!f.isFile) return null
        val lines = f.readLines()
        val time = lines.firstOrNull()?.removePrefix("time_epoch_ms=")?.toLongOrNull()
        val at = time?.let {
            android.text.format.DateFormat.getDateFormat(context).format(java.util.Date(it)) +
                " " + android.text.format.DateFormat.getTimeFormat(context).format(java.util.Date(it))
        }
        at ?: "unknown"
    }.getOrNull()

    fun lastCrashFullText(): String? = runCatching {
        java.io.File(context.filesDir, "last_crash.txt").readText().takeIf { it.isNotBlank() }
    }.getOrNull()

    /** SCREEN_SETTINGS.md §9: app version + build, read from PackageManager. */
    val versionName: String = runCatching {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        info.versionName ?: "?"
    }.getOrDefault("?")

    private val _historyCleared = MutableStateFlow(false)
    val historyCleared: StateFlow<Boolean> = _historyCleared.asStateFlow()

    fun setAmoled(amoled: Boolean) = viewModelScope.launch { preferences.setAmoled(amoled) }
    fun setAccentIndex(index: Int) = viewModelScope.launch { preferences.setAccentIndex(index) }

    // Phase 3 / Milestone 2 — Theme engine.
    fun selectTheme(themeId: String) = viewModelScope.launch {
        themeRegistry.setActive(themeId)
    }

    fun refreshAvailableThemes() = viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
        _availableThemes.value = themeRegistry.available()
    }

    /**
     * Import a theme file via SAF. Returns the new theme id on success
     * (the caller can navigate to it), or `null` on parse failure
     * (the caller surfaces a localized error). IO is on
     * `Dispatchers.IO`; the registry call also runs on the calling
     * coroutine but is non-blocking (just DataStore + file copy).
     */
    suspend fun importTheme(uri: android.net.Uri): String? {
        return runCatching {
            themePreferences.importFromUri(uri)
            val themes = themeRegistry.available()
            _availableThemes.value = themes
            themes.firstOrNull { it.id != null }?.id
        }.getOrNull()
    }
    fun setLanguage(language: AppLanguage) {
        // Update the in-memory locale BEFORE persisting so the recreate() triggered by
        // onLanguageChanged attaches the new locale synchronously (SCREEN_SETTINGS.md
        // §7 "Immediate apply") instead of only after a full process restart.
        // H1 (features review): wrap the persist in NonCancellable — recreation
        // tears down the viewModelScope before the DataStore write commits, so the
        // previous language silently returns on next process start.
        localeManager.applyNow(language)
        viewModelScope.launch(kotlinx.coroutines.NonCancellable) { preferences.setLanguage(language) }
    }
    fun setSeekIncrementSeconds(seconds: Int) = viewModelScope.launch { preferences.setSeekIncrementSeconds(seconds) }
    fun setAutoPip(enabled: Boolean) = viewModelScope.launch { preferences.setAutoPipOnLeave(enabled) }
    fun setResumePlayback(enabled: Boolean) = viewModelScope.launch { preferences.setResumePlayback(enabled) }
    fun setIntelligentSilenceEnabled(enabled: Boolean) = viewModelScope.launch {
        preferences.setIntelligentSilenceEnabled(enabled)
    }
    fun setAutoChapterGenerationEnabled(enabled: Boolean) = viewModelScope.launch {
        preferences.setAutoChapterGenerationEnabled(enabled)
    }
    fun setSubtitleScalePercent(percent: Int) = viewModelScope.launch { preferences.setSubtitleScalePercent(percent) }
    fun setSubtitlePositionBottom(bottom: Boolean) = viewModelScope.launch { preferences.setSubtitlePositionBottom(bottom) }

    // Phase 2
    fun setDecoderPreference(pref: com.exapps.velox.core.data.preferences.DecoderPreference) =
        viewModelScope.launch { preferences.setDecoderPreference(pref) }

    fun setGestureLongPressSpeedBoost(enabled: Boolean) =
        viewModelScope.launch { preferences.setGestureLongPressSpeedBoost(enabled) }

    fun setGestureHorizontalSeekDrag(enabled: Boolean) =
        viewModelScope.launch { preferences.setGestureHorizontalSeekDrag(enabled) }

    fun setGestureVerticalDragMapping(mapping: com.exapps.velox.core.data.preferences.VerticalDragMapping) =
        viewModelScope.launch { preferences.setGestureVerticalDragMapping(mapping) }

    /** Phase 2 backup/restore — SAF transport handled by the screen; this wraps IO. */
    fun exportBackup(uri: android.net.Uri, onDone: (String) -> Unit) {
        viewModelScope.launch {
            val message = runCatching {
                val bytes = backupManager.exportTo(uri)
                context.getString(com.exapps.velox.feature.settings.R.string.settings_backup_done_size, bytes)
            }.getOrElse {
                // M15 (features review): log raw exception for debugging but
                // show the user a localized, generic message.
                android.util.Log.e("VeloxSettings", "Backup export failed", it)
                context.getString(com.exapps.velox.feature.settings.R.string.settings_backup_failed)
            }
            onDone(message)
        }
    }

    fun restoreBackup(uri: android.net.Uri, onDone: (String) -> Unit) {
        viewModelScope.launch {
            val message = runCatching {
                val applied = backupManager.restoreFrom(uri)
                context.getString(com.exapps.velox.feature.settings.R.string.settings_restore_done, applied)
            }.getOrElse {
                // M15: same pattern — log raw exception, surface localized message.
                android.util.Log.e("VeloxSettings", "Backup restore failed", it)
                context.getString(com.exapps.velox.feature.settings.R.string.settings_restore_failed)
            }
            onDone(message)
        }
    }
    fun setAutoLoadExternalSubtitles(enabled: Boolean) = viewModelScope.launch { preferences.setAutoLoadExternalSubtitles(enabled) }
    fun resetRecommendations() = viewModelScope.launch { recommendationEngine.invalidate() }

    fun clearPlayHistory() = viewModelScope.launch {
        libraryRepository.clearPlayHistory()
        // Phase 3 / Wave 3 / Round 3 — clearing the history also
        // invalidates the recommendation matrix.
        recommendationEngine.invalidate()
        // L13 (features review): a one-shot event (true → consume) so the
        // screen can show a confirmation snackbar without having to track
        // "previously-cleared" state. The screen calls [ackHistoryCleared]
        // after surfacing the message.
        _historyCleared.value = true
    }

    /** L13: called by the screen after it has shown the snackbar. */
    fun ackHistoryCleared() {
        _historyCleared.value = false
    }
}
