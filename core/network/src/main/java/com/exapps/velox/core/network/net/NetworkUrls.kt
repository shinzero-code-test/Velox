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

    /** Root URL for a server: scheme://host[:port] + basePath (normalised to end with '/'). */
    fun root(server: NetworkServer): String {
        val defaultPort = server.protocol.defaultPort()
        val explicitPort =
            if (server.protocol == NetworkProtocol.SMB || server.port == defaultPort) "" else ":${server.port}"
        val base = server.basePath.trim().ifEmpty { "/" }
        val baseNorm = if (base.endsWith("/")) base else "$base/"
        return "${scheme(server)}://${server.host}$explicitPort$baseNorm"
    }

    fun displayName(url: String): String =
        url.trimEnd('/').substringAfterLast('/').ifEmpty { url }
            .let { java.net.URLDecoder.decode(it, "UTF-8") }

    fun parentOf(url: String): String? {
        val trimmed = url.trimEnd('/')
        val parent = trimmed.substringBeforeLast('/', missingDelimiterValue = "")
        return parent.takeIf { it.isNotEmpty() && it.any { c -> c == '/' } }
    }
}
