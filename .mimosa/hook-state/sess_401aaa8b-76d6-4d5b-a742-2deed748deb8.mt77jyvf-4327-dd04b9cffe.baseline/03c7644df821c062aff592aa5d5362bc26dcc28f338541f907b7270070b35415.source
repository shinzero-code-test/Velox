package com.exapps.velox.player.service

import android.content.Intent
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.exapps.velox.player.engine.AndroidAudioEffectsController
import com.exapps.velox.player.engine.VeloxExoPlayerFactory
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * ARCHITECTURE.md §6 / master prompt Phase 1 item 8: "Player service + MediaSession +
 * notification + background audio". This owns the one and only ExoPlayer instance —
 * everything else (Now Playing, mini player, lock screen, headset buttons) talks to
 * it through a `MediaController`, never directly.
 *
 * The system notification with transport controls is provided automatically by
 * `MediaSessionService` + `MediaSession` (Media3's default `MediaNotification.Provider`)
 * once a session exists — there is no separate NotificationCompat.Builder to write here.
 */
@AndroidEntryPoint
class VeloxPlaybackService : MediaSessionService() {

    @Inject lateinit var exoPlayerFactory: VeloxExoPlayerFactory
    @Inject lateinit var audioEffects: AndroidAudioEffectsController

    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val player = exoPlayerFactory.create()
        mediaSession = MediaSession.Builder(this, player).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    /** Media3's recommended pattern (see MediaSessionService docs): stop the service
     * when the app task is swiped away while nothing is actively playing, but keep
     * it (and the notification) alive if audio is still going — that's what makes
     * background audio survive leaving the app. */
    override fun onTaskRemoved(rootIntent: Intent?) {
        val session = mediaSession ?: return super.onTaskRemoved(rootIntent)
        if (!session.player.playWhenReady || session.player.mediaItemCount == 0) {
            stopSelf()
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        // The EQ effects were attached to the (now dead) player's audio session —
        // they re-attach automatically when the next player creates a new session.
        audioEffects.release()
        super.onDestroy()
    }
}
