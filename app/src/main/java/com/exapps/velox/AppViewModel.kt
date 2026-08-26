package com.exapps.velox

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.exapps.velox.core.data.preferences.UserSettings
import com.exapps.velox.core.data.preferences.UserSettingsPreferences
import com.exapps.velox.core.data.preferences.OnboardingPreferences
import com.exapps.velox.navigation.VeloxRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AppViewModel @Inject constructor(
    private val onboardingPreferences: OnboardingPreferences,
    userSettingsPreferences: UserSettingsPreferences,
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
