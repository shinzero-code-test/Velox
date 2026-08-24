package com.exapps.velox.player.engine

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Builds the single ExoPlayer instance the app uses. ARCHITECTURE.md §6: "Single
 * authoritative player instance owned by the service layer" — this factory is
 * called exactly once, from `VeloxPlaybackService.onCreate()` in `:player:service`.
 * Nothing else should construct an `ExoPlayer` directly.
 */
@Singleton
class VeloxExoPlayerFactory @Inject constructor(
    @ApplicationContext private val context: Context,
    private val audioEffects: AndroidAudioEffectsController,
) {
    fun create(): ExoPlayer {
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()

        // Slightly larger buffers than the ExoPlayer default: FEATURES.md's "Gapless
        // and configurable crossfade where feasible" and smooth scrubbing on flaky
        // storage (SD cards, network shares added later) both benefit from more
        // headroom than the stock 15s/50s min/max buffer.
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                /* minBufferMs = */ 20_000,
                /* maxBufferMs = */ 60_000,
                /* bufferForPlaybackMs = */ 1_500,
                /* bufferForPlaybackAfterRebufferMs = */ 3_000,
            )
            .build()

        return ExoPlayer.Builder(context)
            .setAudioAttributes(audioAttributes, /* handleAudioFocus = */ true)
            .setHandleAudioBecomingNoisy(true) // ARCHITECTURE.md / FEATURES.md: "noisy handling"
            .setLoadControl(loadControl)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()
            .also { player ->
                // The EQ/effects chain attaches to the audio session, which exists
                // only once output is initialized — re-attach whenever it moves.
                player.addListener(object : androidx.media3.common.Player.Listener {
                    override fun onAudioSessionIdChanged(audioSessionId: Int) {
                        audioEffects.attachToAudioSession(audioSessionId)
                    }
                })
            }
    }
}
