package com.exapps.velox.player.engine

import androidx.media3.common.MimeTypes

/**
 * Maps a picked subtitle file's extension to a Media3 text MIME type.
 *
 * L5 (player-stack review): this lived in `:player:service` as a top-level
 * function, but it has no dependencies on the service layer (it never
 * touches the `MediaController`). Moving it to `:player:engine` keeps the
 * service module free of utility code that belongs in the engine.
 */
fun subtitleMimeTypeFor(fileName: String): String = when (fileName.substringAfterLast('.', "").lowercase()) {
    "srt" -> MimeTypes.APPLICATION_SUBRIP
    "vtt" -> MimeTypes.TEXT_VTT
    "ttml", "xml", "dfxp" -> MimeTypes.APPLICATION_TTML
    "ssa", "ass" -> MimeTypes.TEXT_SSA
    else -> MimeTypes.APPLICATION_SUBRIP
}
