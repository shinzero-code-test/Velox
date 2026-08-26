package com.exapps.velox.core.network.net

import com.exapps.velox.core.network.model.NetworkProtocol
import com.exapps.velox.core.network.model.NetworkServer
import com.exapps.velox.core.network.model.defaultPort

/**
 * URL builders for the three protocols. Canonical forms:
 *  - SMB:   `smb://host/SHARE/path/Name.mp3`
 *  - FTP:   `ftp://host[:port]/path/Name.mp3` (credentials travel via the server record)
 *  - DAV:   `dav://host[:port]/path/Name.mp3`, secure variant `davs://`
 *
 * Custom schemes keep the rest of the app protocol-agnostic: Media3 routes them to
 * our DataSource, and the browsing UI never special-cases beyond display.
 */
object NetworkUrls {

    fun scheme(protocol: NetworkProtocol): String = when (protocol) {
        NetworkProtocol.SMB -> "smb"
        NetworkProtocol.FTP -> "ftp"
        NetworkProtocol.WEBDAV -> "dav"
    }

    fun scheme(server: NetworkServer): String = when (server.protocol) {
        NetworkProtocol.WEBDAV -> if (server.secure) "davs" else "dav"
        else -> scheme(server.protocol)
    }

    /** Builds a child URL from a directory URL + entry name (URL-encoded). */
    fun child(directoryUrl: String, name: String): String {
        val sep = if (directoryUrl.endsWith("/")) "" else "/"
        val encoded = java.net.URLEncoder.encode(name, "UTF-8").replace("+", "%20")
        return "$directoryUrl$sep$encoded"
    }

    /** Root URL for a server: scheme://host[:port] + basePath (normalised to end with '/').
     * M1 (data-layer review): custom SMB ports are honoured too — jcifs accepts
     * `smb://host:1445/share`. M8: each basePath segment is percent-encoded so
     * values like `/my music` can't crash OkHttp's URL parser. */
    fun root(server: NetworkServer): String {
        val explicitPort = if (server.port == server.protocol.defaultPort()) "" else ":${server.port}"
        val base = server.basePath.trim().ifEmpty { "/" }
        val encodedBase = base.split('/')
            .filter { it.isNotEmpty() }
            .joinToString("/") { segment ->
                java.net.URLEncoder.encode(segment, "UTF-8").replace("+", "%20")
            }
            .let { if (it.isEmpty()) "" else "/$it/" }
        return "${scheme(server)}://${server.host}$explicitPort$encodedBase"
    }

    fun displayName(url: String): String {
        val raw = url.trimEnd('/').substringAfterLast('/').ifEmpty { url }
        // Low nit (data-layer review): URLDecoder throws on malformed escapes
        // (e.g. a pasted "...100%.mp3"); fall back to the raw segment instead.
        return runCatching { java.net.URLDecoder.decode(raw, "UTF-8") }.getOrDefault(raw)
    }

    /** Parent directory URL, or null at the authority/share boundary — returning a
     * bare `scheme://host` used to imply a workgroup listing (M9, data-layer review). */
    fun parentOf(url: String): String? {
        val trimmed = url.trimEnd('/')
        val authorityAndPath = trimmed.substringAfter("://")
        val path = authorityAndPath.substringAfter('/', missingDelimiterValue = "")
        if (!path.contains('/')) return null // already at the share/root level
        val parent = trimmed.substringBeforeLast('/')
        return "$parent/"
    }
}
