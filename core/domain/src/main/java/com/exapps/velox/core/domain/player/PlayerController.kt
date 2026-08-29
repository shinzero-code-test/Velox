package com.exapps.velox.core.domain.player

import com.exapps.velox.core.domain.model.MediaItem
import kotlinx.coroutines.flow.StateFlow

/** A selectable track (audio / subtitle / video) in the current media item. */
data class PlayerTrack(
    /** Stable id for selection — the Media3 Format id when present, else a hash of
     * the label+language so selection survives re-preparation. */
    val id: String,
    val type: TrackType,
    val label: String,
    val language: String?,
    val isSelected: Boolean,
)

enum class TrackType { AUDIO, TEXT, VIDEO }

/**
 * The single authoritative playback contract (ARCHITECTURE.md §4.1 / §6: "Media3 over
 * custom native player", "UI observes state via Flow / MediaController — never owns
 * the engine"). `:core:domain` only knows this interface; `:player:engine` provides
 * the real Media3-backed implementation, and `:player:service` hosts it inside a
 * foreground `MediaSessionService`. Nothing outside those two modules should import
 * androidx.media3.* directly — that's the whole point of the seam.
 */
interface PlayerController {

    val state: StateFlow<PlaybackState>

    /** Audio/text/video tracks of the current item (SCREEN_VIDEO_PLAYER.md §4/§7). */
    val tracks: StateFlow<List<PlayerTrack>>

    /** Replaces the queue and starts playback at [startIndex]. */
    fun play(queue: List<MediaItem>, startIndex: Int = 0)

    fun playPause()
    fun pause()
    fun resume()
    fun seekTo(positionMs: Long)
    fun skipNext()
    fun skipPrevious()
    fun setShuffleEnabled(enabled: Boolean)
    fun setRepeatMode(mode: RepeatMode)
    fun setPlaybackSpeed(speed: Float)
    fun setFavorite(mediaItemId: Long, favorite: Boolean)

    /** Starts playing the queue entry at [index] (queue sheet taps). */
    fun playQueueItem(index: Int)

    /** FEATURES.md — Queue management: add, remove, reorder, clear. */
    fun addToQueue(item: MediaItem)
    fun playNext(item: MediaItem)
    fun removeFromQueue(index: Int)
    fun moveQueueItem(fromIndex: Int, toIndex: Int)
    fun clearQueue()

    /**
     * Selects one track of [type] (or disables the type when [trackId] is null).
     * Text tracks: null = subtitles off.
     */
    fun selectTrack(type: TrackType, trackId: String?)

    /**
     * Side-loads an external subtitle file (SCREEN_VIDEO_PLAYER.md §7 "Open external")
     * onto the current item and selects it. [mimeType] is one of Media3's
     * MimeTypes text constants, [label] what the picker shows.
     */
    fun addExternalSubtitle(uri: String, mimeType: String, label: String)

    // --- Phase 2 -------------------------------------------------------------------

    /**
     * A-B repeat (Phase 2): loops playback between [startMs] and [endMs] of the
     * current item. Passing only [startMs] arms point A (end pending); passing both
     * null clears the loop.
     *
     * Contract (L9 in the player-stack review):
     * - If [startMs] is null, the loop is cleared.
     * - If [endMs] is non-null but `endMs <= startMs`, the loop is also
     *   cleared (the previously "undefined" behaviour is now explicit:
     *   a non-strictly-positive region is treated as no loop).
     * - If [endMs] is null, the loop is armed at [startMs] (end pending).
     */
    fun setLoopRegion(startMs: Long?, endMs: Long? = null)

    /** Session playback volume 0f..1f — used by the sleep-timer fade-out. */
    fun setVolume(scale: Float)

    fun stop()
}
