package com.exapps.velox.core.network.net

import com.exapps.velox.core.network.model.NetworkEntry
import com.exapps.velox.core.network.model.NetworkServer

/**
 * Blocking network-client contract shared by SMB/FTP/WebDAV. Deliberately
 * synchronous — callers are either ExoPlayer's loader thread ([openStream] via the
 * engine's DataSource) or coroutines on Dispatchers.IO (browsing UI).
 */
interface NetworkClient {

    /** Lists [url] (a directory URL built by [NetworkUrls]). Sorted: folders first, then name. */
    fun list(server: NetworkServer, url: String): List<NetworkEntry>

    /**
     * Opens [url] for streaming read. Implementations should honour [positionMs]
     * when the protocol allows cheap range requests (WebDAV/FTP do; SMB streams
     * from the start and skips) — returning early bytes before [positionMs] is
     * acceptable but wasteful.
     */
    fun openStream(server: NetworkServer, url: String, positionMs: Long = 0L): java.io.InputStream

    /** Cheap connectivity/credential probe used by the add-server dialog. */
    fun test(server: NetworkServer): Boolean

    companion object {
        /** Media-type guess from a filename — drives how Media3 treats the stream. */
        fun guessMimeType(name: String): String {
            val ext = name.substringAfterLast('.', "").lowercase()
            return when (ext) {
                "mp4", "m4v", "mov" -> "video/mp4"
                "mkv" -> "video/x-matroska"
                "webm" -> "video/webm"
                "avi" -> "video/x-msvideo"
                "ts", "m2ts" -> "video/mp2t"
                "mp3" -> "audio/mpeg"
                "flac" -> "audio/flac"
                "m4a" -> "audio/mp4a-latm"
                "aac" -> "audio/aac"
                "ogg", "opus" -> "audio/ogg"
                "wav" -> "audio/wav"
                else -> "application/octet-stream"
            }
        }
    }
}
