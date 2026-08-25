package com.exapps.velox.core.network.model

import kotlinx.serialization.Serializable

/** Which protocol a configured server speaks. */
enum class NetworkProtocol { SMB, FTP, WEBDAV }

/**
 * One saved network server (Phase 2 "Network browsing"). [basePath] is the
 * server-relative directory to open first (e.g. `/music` for WebDAV, the share +
 * path for SMB). Credentials are stored locally in DataStore — same trust level
 * as the rest of the app's preferences; documented in PROGRESS.md.
 */
@Serializable
data class NetworkServer(
    val id: Long,
    val name: String,
    val protocol: NetworkProtocol,
    val host: String,
    val port: Int,
    val username: String = "",
    val password: String = "",
    /** Server-relative base path, always starting with '/'. */
    val basePath: String = "/",
    /** True when the server advertises HTTPS (WebDAV only). */
    val secure: Boolean = false,
)

/** One row in a browsed network directory. */
data class NetworkEntry(
    val name: String,
    /** Fully-qualified playable/browsable URL for this entry. */
    val url: String,
    val isDirectory: Boolean,
    val sizeBytes: Long = -1L,
)

/** Default ports per protocol so the add-server dialog can prefill sensibly.
 * (Secure WebDAV usually means :443 — users type it explicitly.) */
fun NetworkProtocol.defaultPort(): Int = when (this) {
    NetworkProtocol.SMB -> 445
    NetworkProtocol.FTP -> 21
    NetworkProtocol.WEBDAV -> 80
}
