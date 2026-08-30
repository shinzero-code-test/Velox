package com.exapps.velox

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.exapps.velox.core.data.preferences.UserSettings
import com.exapps.velox.core.data.preferences.UserSettingsPreferences
import com.exapps.velox.core.data.preferences.OnboardingPreferences
import com.exapps.velox.core.domain.theme.ThemeRegistry
import com.exapps.velox.core.ui.theme.VeloxAccentOptions
import com.exapps.velox.core.ui.theme.VeloxThemeSpec
import com.exapps.velox.core.ui.theme.resolveThemeSpec
import com.exapps.velox.navigation.VeloxRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AppViewModel @Inject constructor(
    private val onboardingPreferences: OnboardingPreferences,
    private val userSettingsPreferences: UserSettingsPreferences,
    private val themeRegistry: ThemeRegistry,
) : ViewModel() {

    private val _startDestination = MutableStateFlow<VeloxRoute?>(null)
    /** Null while resolving — MainActivity holds the splash screen up until this
     * is non-null, so the app never flashes onto the wrong start screen. */
    val startDestination: StateFlow<VeloxRoute?> = _startDestination.asStateFlow()

    /** Theme/language snapshot the Activity collects to root VeloxTheme with. */
    val settings: StateFlow<UserSettings> = userSettingsPreferences.settings.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = UserSettings(),
    )

    /**
     * Phase 3 / Milestone 2 — Theme engine. The resolved [VeloxThemeSpec]
     * combines the active theme (from [ThemeRegistry]) with the live
     * accent override and the AMOLED toggle (both from [settings]).
     * MainActivity collects this and feeds it to [VeloxTheme].
     */
    val themeSpec: StateFlow<VeloxThemeSpec> = combine(
        themeRegistry.active,
        userSettingsPreferences.settings,
    ) { theme, userSettings ->
        val accent = VeloxAccentOptions.getOrElse(userSettings.accentIndex) {
            VeloxAccentOptions.first()
        }
        resolveThemeSpec(theme = theme, accent = accent, amoled = userSettings.amoled)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = resolveThemeSpec(
            theme = com.exapps.velox.core.data.preferences.ThemeRegistryAdapter.DefaultDarkGlass,
            accent = VeloxAccentOptions.first(),
            amoled = false,
        ),
    )

    /** Phase 1 M4 "File association": set after an externally-opened file starts
     * playing; the nav host consumes it once the graph is up and pushes the right
     * player screen on top. */
    private val _externalPlayback = MutableStateFlow<Long?>(null)
    val externalPlayback: StateFlow<Long?> = _externalPlayback.asStateFlow()

    fun onExternalMediaStarted(itemId: Long) {
        _externalPlayback.value = itemId
    }

    fun consumeExternalPlayback() {
        _externalPlayback.value = null
    }

    init {
        viewModelScope.launch {
            val complete = onboardingPreferences.isOnboardingComplete.first()
            _startDestination.value = if (complete) VeloxRoute.Library else VeloxRoute.Onboarding
        }
    }

    suspend fun onOnboardingComplete() {
        onboardingPreferences.setOnboardingComplete(true)
    }
}
