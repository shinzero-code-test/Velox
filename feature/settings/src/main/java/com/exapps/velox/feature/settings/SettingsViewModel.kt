package com.exapps.velox.feature.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.exapps.velox.core.data.preferences.AppLanguage
import com.exapps.velox.core.data.preferences.UserSettings
import com.exapps.velox.core.data.preferences.UserSettingsPreferences
import com.exapps.velox.core.data.preferences.VeloxLocaleManager
import com.exapps.velox.core.domain.repository.MediaLibraryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext context: Context,
    private val preferences: UserSettingsPreferences,
    private val localeManager: VeloxLocaleManager,
    private val libraryRepository: MediaLibraryRepository,
) : ViewModel() {

    val settings: StateFlow<UserSettings> = preferences.settings.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = UserSettings(),
    )

    /** SCREEN_SETTINGS.md §9: app version + build, read from PackageManager. */
    val versionName: String = runCatching {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        info.versionName ?: "?"
    }.getOrDefault("?")

    private val _historyCleared = MutableStateFlow(false)
    val historyCleared: StateFlow<Boolean> = _historyCleared.asStateFlow()

    fun setAmoled(amoled: Boolean) = viewModelScope.launch { preferences.setAmoled(amoled) }
    fun setAccentIndex(index: Int) = viewModelScope.launch { preferences.setAccentIndex(index) }
    fun setLanguage(language: AppLanguage) {
        // Update the in-memory locale BEFORE persisting so the recreate() triggered by
        // onLanguageChanged attaches the new locale synchronously (SCREEN_SETTINGS.md
        // §7 "Immediate apply") instead of only after a full process restart.
        localeManager.applyNow(language)
        viewModelScope.launch { preferences.setLanguage(language) }
    }
    fun setSeekIncrementSeconds(seconds: Int) = viewModelScope.launch { preferences.setSeekIncrementSeconds(seconds) }
    fun setAutoPip(enabled: Boolean) = viewModelScope.launch { preferences.setAutoPipOnLeave(enabled) }
    fun setResumePlayback(enabled: Boolean) = viewModelScope.launch { preferences.setResumePlayback(enabled) }
    fun setSubtitleScalePercent(percent: Int) = viewModelScope.launch { preferences.setSubtitleScalePercent(percent) }
    fun setSubtitlePositionBottom(bottom: Boolean) = viewModelScope.launch { preferences.setSubtitlePositionBottom(bottom) }
    fun setAutoLoadExternalSubtitles(enabled: Boolean) = viewModelScope.launch { preferences.setAutoLoadExternalSubtitles(enabled) }

    fun clearPlayHistory() = viewModelScope.launch {
        libraryRepository.clearPlayHistory()
        _historyCleared.value = true
    }
}
