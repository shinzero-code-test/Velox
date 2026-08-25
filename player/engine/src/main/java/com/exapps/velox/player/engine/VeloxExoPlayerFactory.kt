package com.exapps.velox.player.engine

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import com.exapps.velox.core.data.preferences.UserSettingsPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Phase 2 "Advanced video processing": codec selector that prefers software
 * decoders (c2.android / OMX.google / SW prefixes). Used when Settings →
 * Video → "Prefer software decoding" is on — the escape hatch for devices whose
 * hardware decoders misbehave on exotic codecs.
 */
private fun softwareFirstCodecSelector(
    mimeType: String,
    requiresSecureDecoder: Boolean,
    requiresTunnelingDecoder: Boolean,
): List<androidx.media3.exoplayer.mediacodec.MediaCodecInfo> {
    // Kotlin has no checked exceptions; DecoderQueryException propagates as-is.
    val all = MediaCodecSelector.DEFAULT.getDecoderInfos(mimeType, requiresSecureDecoder, requiresTunnelingDecoder)
    return all.sortedBy { info ->
        val name = info.name.lowercase()
        when {
            "c2.android" in name || "omx.google" in name || ".sw." in name -> 0
            else -> 1
        }
    }
}

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
    private val dataSourceFactory: VeloxDataSourceFactory,
    private val userSettings: UserSettingsPreferences,
) {
    fun create(): ExoPlayer {
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()

        // Slightly larger buffers than the ExoPlayer default: FEATURES.md's "Gapless
        // and configurable crossfade where feasible" and smooth scrubbing on flaky
        // storage (SD cards, network shares added later) both benefit from more
        // headroom than the stock 15s/50s min/max buffer. Phase 2 doubles down for
        // network playback specifically.
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                /* minBufferMs = */ 30_000,
                /* maxBufferMs = */ 90_000,
                /* bufferForPlaybackMs = */ 1_500,
                /* bufferForPlaybackAfterRebufferMs = */ 3_000,
            )
            .build()

        // Phase 2 "Advanced video processing": decoder priority (auto / software).
        // Decoder fallback is always on so hardware gaps degrade gracefully; the
        // software-preferred mode biases MediaCodec selection toward c2.android /
        // OMX.google decoders first.
        val decoderPreference = runCatching {
            kotlinx.coroutines.runBlocking { userSettings.settings.first().decoderPreference }
        }.getOrDefault(com.exapps.velox.core.data.preferences.DecoderPreference.AUTO)
        val preferSoftware = decoderPreference == com.exapps.velox.core.data.preferences.DecoderPreference.SOFTWARE

        val renderersFactory = DefaultRenderersFactory(context)
            .setEnableDecoderFallback(true)
            .apply {
                if (preferSoftware) setMediaCodecSelector(::softwareFirstCodecSelector)
            }

        // Phase 2: route smb/ftp/dav(s) through our DataSource while keeping the
        // default http/file stack — and let DefaultMediaSourceFactory pick up the
        // HLS/DASH/RTSP modules present on the classpath.
        val mediaSourceFactory = androidx.media3.exoplayer.source.DefaultMediaSourceFactory(dataSourceFactory)

        return ExoPlayer.Builder(context, renderersFactory)
            .setMediaSourceFactory(mediaSourceFactory)
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
