package com.exapps.velox.player.engine

import androidx.media3.common.Player

/**
 * The render seam for video (SCREEN_VIDEO_PLAYER.md): Compose needs an
 * android.view.Surface hosting a real media3 [Player] to draw frames, but
 * `:core:domain`'s [com.exapps.velox.core.domain.player.PlayerController] must stay
 * framework-free. The controller implementation in `:player:service` implements
 * this accessor; [VeloxVideoSurface] (this module) is the only consumer, so no
 * feature module ever touches a media3 type.
 */
interface Media3PlayerAccessor {
    /** The session's player (a MediaController), or null before the session connects. */
    val media3Player: Player?
}
