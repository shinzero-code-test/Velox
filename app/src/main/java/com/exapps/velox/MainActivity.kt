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
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import com.exapps.velox.core.domain.model.MediaType
import com.exapps.velox.core.domain.player.PlayerController
import androidx.compose.ui.Modifier
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
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
        handleViewIntent(intent)

        // Holds the splash screen (system-drawn, not a fake in-app screen — see
        // Theme.Velox.Splash) up until AppViewModel has resolved onboarding state,
        // so the very first frame the person sees is already the correct one.
        splashScreen.setKeepOnScreenCondition { appViewModel.startDestination.value == null }

        enableEdgeToEdge()

        lifecycleScope.launch {
            appViewModel.settings.collect { latestSettings.value = it }
        }

        setContent {
            val themeSpec by appViewModel.themeSpec.collectAsStateWithLifecycle()
            // Phase 3 / Milestone 3 — Better tablet layouts.
            // `calculateWindowSizeClass(activity)` reads the activity's
            // current window metrics and buckets them into
            // Compact/Medium/Expanded. We hoist it once at the root and
            // let the screen-level layouts decide between single-pane
            // (Compact) and two-/three-pane (Medium/Expanded).
            val windowSizeClass = calculateWindowSizeClass(this)
            // The window background comes from the XML theme (a fixed color
            // resource), and screens draw on top of it transparently — so without
            // this root the AMOLED toggle never reached what the user actually sees.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(themeSpec.background),
            ) {
                VeloxTheme(spec = themeSpec) {
                    val startDestination by appViewModel.startDestination.collectAsStateWithLifecycle()
                    startDestination?.let { destination ->
                        VeloxNavHost(
                            startDestination = destination,
                            appViewModel = appViewModel,
                            windowSizeClass = windowSizeClass,
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleViewIntent(intent)
    }

    /**
     * Phase 1 M4 "File association": ACTION_VIEW on a content/file audio or video
     * URI starts playing it immediately and asks the nav host to surface the player
     * chrome. The synthetic MediaItem uses a negative id so it can never collide
     * with library rows; duration is resolved by the player itself.
     */
    private fun handleViewIntent(intent: Intent?) {
        if (intent?.action != Intent.ACTION_VIEW) return
        val data = intent.data ?: return
        lifecycleScope.launch {
            val title = resolveDisplayName(data) ?: data.lastPathSegment ?: "Unknown"
            // M5 (app-shell review): many pickers send null/octet-stream — fall back
            // to an extension-based sniff before defaulting to audio.
            val mime = intent.type ?: dataMimeType(data)
            val resolvedMime = if (mime.startsWith("audio") || mime.startsWith("video")) {
                mime
            } else {
                MimeTypeMap.getSingleton().getMimeTypeFromExtension(
                    title.substringAfterLast('.', "").lowercase(),
                ) ?: mime
            }
            val isVideo = resolvedMime.startsWith("video")
            val item = com.exapps.velox.core.domain.model.MediaItem(
                id = -(System.currentTimeMillis()),
                uri = data.toString(),
                title = title,
                mediaType = if (isVideo) MediaType.VIDEO else MediaType.AUDIO,
                durationMs = 0L, // resolved by the player itself
            )
            playerController.play(listOf(item))
            appViewModel.onExternalMediaStarted(item.id)
        }
    }

    private fun dataMimeType(data: Uri): String =
        runCatching { contentResolver.getType(data) }.getOrNull() ?: "audio/*"

    private fun resolveDisplayName(data: Uri): String? = runCatching {
        contentResolver.query(data, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor: Cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
    }.getOrNull()

    /** SCREEN_VIDEO_PLAYER.md §9 / Settings → Playback → "Auto PiP on leave":
     * leaving the app while a video plays minimizes to picture-in-picture.
     * M6 (app-shell review): fixed 16:9 aspect + auto-enter on S+ so the system
     * transition doesn't letterbox; onUserLeaveHint remains the pre-S fallback. */
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        val settings = latestSettings.value
        val playback = playerController.state.value
        val playingVideo = playback.isPlaying && playback.currentItem?.mediaType == MediaType.VIDEO
        if (settings.autoPipOnLeave && playingVideo) {
            enterPictureInPictureMode(buildPipParams())
        }
    }

    private fun buildPipParams(): PictureInPictureParams =
        PictureInPictureParams.Builder()
            .setAspectRatio(android.util.Rational(16, 9))
            .apply { if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) setAutoEnterEnabled(true) }
            .build()
}
