package com.exapps.velox.player.engine

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import com.exapps.velox.core.domain.player.DecoderPreferenceStore
import dagger.hilt.android.qualifiers.ApplicationContext
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
    private val decoderPreferences: DecoderPreferenceStore,
) {
    fun create(): ExoPlayer {
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()

        // Slightly larger buffers than the ExoPlayer default: FEATURES.md's "Gapless
        // and configurable crossfade where feasible" and smooth scrubbing on flaky
        // storage (SD cards, network shares added later) both benefit from more
        // headroom than the stock 50s/50s min/max buffer (yes, both 50s — the
        // ExoPlayer default treats min == max). Phase 2 doubles down for
        // network playback specifically.
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                /* minBufferMs = */ 30_000,
                /* maxBufferMs = */ 90_000,
                /* bufferForPlaybackMs = */ 1_500,
                /* bufferForPlaybackAfterRebufferMs = */ 3_000,
            )
            .build()

        // H5 (player-stack review): the previous `runBlocking { userSettings.settings.first() }`
        // blocked the main thread on a DataStore disk read every time the service
        // recreated (every cold start, every low-memory restart). We now read the
        // cache that [DecoderPreferenceStore.primeCache] warmed at app start.
        // If priming didn't run yet (very first call before Application.onCreate
        // completes, which is rare) the cache still returns the safe default.
        //
        // Phase 3 / L6 (deferred-backlog): the engine now reads the decoder
        // preference through the [DecoderPreferenceStore] domain port instead
        // of importing `UserSettingsPreferences` from `:core:data`.
        val preferSoftware = decoderPreferences.preferSoftwareCached()

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
            // M4 (player-stack review): WAKE_MODE_LOCAL keeps the CPU
            // running while the app is foregrounded (or holding a
            // foreground service notification), which is fine for
            // local-file playback but does NOT keep the radio/wifi
            // link alive for SMB/FTP/WebDAV streams. WAKE_MODE_NETWORK
            // does both. We use NETWORK unconditionally so background
            // network playback of long tracks doesn't get killed when
            // the device tries to suspend radios.
            .setWakeMode(C.WAKE_MODE_NETWORK)
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
