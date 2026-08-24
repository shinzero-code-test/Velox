package com.exapps.velox

import android.app.PictureInPictureParams
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.exapps.velox.core.data.preferences.UserSettings
import com.exapps.velox.core.data.preferences.VeloxLocaleManager
import com.exapps.velox.core.domain.model.MediaType
import com.exapps.velox.core.domain.player.PlayerController
import androidx.compose.ui.Modifier
import com.exapps.velox.core.ui.theme.VeloxAccentOptions
import com.exapps.velox.core.ui.theme.VeloxColors
import com.exapps.velox.core.ui.theme.VeloxTheme
import com.exapps.velox.navigation.VeloxNavHost
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Master prompt Phase 0 item 4: "Single-activity host + Navigation skeleton". Every
 * screen in the app is a composable reached through [VeloxNavHost]; this Activity's
 * jobs are the platform-required setup (splash, edge-to-edge, locale, theme root),
 * resolving where the nav graph should start, and the two Activity-level platform
 * hooks Compose can't own: language recreation and auto-PiP on leave.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val appViewModel: AppViewModel by viewModels()

    @Inject lateinit var playerController: PlayerController

    /** Latest settings snapshot for onUserLeaveHint, which can't suspend. */
    private val latestSettings = MutableStateFlow(UserSettings())

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(VeloxLocaleManager.instance?.applyTo(newBase) ?: newBase)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)

        // Holds the splash screen (system-drawn, not a fake in-app screen — see
        // Theme.Velox.Splash) up until AppViewModel has resolved onboarding state,
        // so the very first frame the person sees is already the correct one.
        splashScreen.setKeepOnScreenCondition { appViewModel.startDestination.value == null }

        enableEdgeToEdge()

        lifecycleScope.launch {
            appViewModel.settings.collect { latestSettings.value = it }
        }

        setContent {
            val settings by appViewModel.settings.collectAsStateWithLifecycle()
            // The window background comes from the XML theme (a fixed color
            // resource), and screens draw on top of it transparently — so without
            // this root the AMOLED toggle never reached what the user actually sees.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(VeloxColors.currentBackground),
            ) {
                VeloxTheme(
                    amoled = settings.amoled,
                    accent = VeloxAccentOptions.getOrElse(settings.accentIndex) { VeloxAccentOptions.first() },
                ) {
                    val startDestination by appViewModel.startDestination.collectAsStateWithLifecycle()
                    startDestination?.let { destination ->
                        VeloxNavHost(startDestination = destination)
                    }
                }
            }
        }
    }

    /** SCREEN_VIDEO_PLAYER.md §9 / Settings → Playback → "Auto PiP on leave":
     * leaving the app while a video plays minimizes to picture-in-picture. */
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        val settings = latestSettings.value
        val playback = playerController.state.value
        val playingVideo = playback.isPlaying && playback.currentItem?.mediaType == MediaType.VIDEO
        if (settings.autoPipOnLeave && playingVideo) {
            enterPictureInPictureMode(PictureInPictureParams.Builder().build())
        }
    }
}
