package com.exapps.velox.core.domain.player

import com.exapps.velox.core.domain.model.MediaItem

/** FEATURES.md — Common Controls: "Shuffle & Repeat (Off / One / All)". */
enum class RepeatMode { OFF, ONE, ALL }

enum class PlaybackStatus { IDLE, BUFFERING, READY, ENDED }

/**
 * The full observable playback state, as exposed by [PlayerController]. UI layers
 * (mini player, Now Playing, Video Player) all collect this rather than touching
 * ExoPlayer/MediaController directly — see ARCHITECTURE.md §5's data-flow diagram:
 * "Player state is observed from a singleton PlayerController / MediaSession."
 */
data class PlaybackState(
    val status: PlaybackStatus = PlaybackStatus.IDLE,
    val isPlaying: Boolean = false,
    val currentItem: MediaItem? = null,
    val queue: List<MediaItem> = emptyList(),
    val currentIndex: Int = -1,
    val positionMs: Long = 0L,
    val bufferedPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val shuffleEnabled: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.OFF,
    /** FEATURES.md — Video Playback: "Playback speed (0.25x – 3.0x)". */
    val playbackSpeed: Float = 1f,
    val isFavorite: Boolean = false,
)
