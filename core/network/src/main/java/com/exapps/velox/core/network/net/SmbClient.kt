package com.exapps.velox.core.network.net

import com.exapps.velox.core.network.model.NetworkEntry
import com.exapps.velox.core.network.model.NetworkServer
import jcifs.CIFSContext
import jcifs.config.BaseConfiguration
import jcifs.context.BaseContext
import jcifs.smb.NtlmPasswordAuthenticator
import jcifs.smb.SmbFile
import java.io.InputStream

/**
 * SMB/Windows-share client over jCIFS-ng (Phase 2 "Network browsing"). Shares are
 * addressed as `smb://host/share/...`; listing the bare root enumerates shares.
 */
class SmbClient @javax.inject.Inject constructor() : NetworkClient {

    // jcifs-ng 2.1.x: BaseContext takes a Configuration; the public BaseConfiguration
    // ctor builds the full default property set.
    private val baseContext: CIFSContext = BaseContext(BaseConfiguration(true))

    private fun contextFor(server: NetworkServer): CIFSContext = if (server.username.isBlank()) {
        baseContext.withAnonymousCredentials()
    } else {
        baseContext.withCredentials(NtlmPasswordAuthenticator(server.username, server.password))
    }

    private fun smbFile(server: NetworkServer, url: String): SmbFile =
        SmbFile(url, contextFor(server))

    override fun list(server: NetworkServer, url: String): List<NetworkEntry> {
        val dir = smbFile(server, url)
        val children = dir.listFiles() ?: return emptyList()
        return children
            .filter { it.name !in setOf(".", "..") }
            .map {
                NetworkEntry(
                    name = it.name.trimEnd('/'),
                    url = it.url.toString(),
                    isDirectory = it.isDirectory,
                    sizeBytes = it.length(),
                )
            }
            .sortedWith(compareByDescending<NetworkEntry> { it.isDirectory }.thenBy { it.name.lowercase() })
    }

    override fun openStream(server: NetworkServer, url: String, positionMs: Long): InputStream {
        val file = smbFile(server, url)
        val stream = file.getInputStream()
        // SMB supports seek natively, but re-opening with a range is not exposed by
        // SmbFileInputStream; skipping bytes keeps semantics correct for resume and
        // is bounded by the requested offset.
        if (positionMs > 0) {
            // Rough byte-position estimate: assume ~128 kbit/s average bitrate when
            // duration metadata is absent — ExoPlayer will correct via its own seeks.
            val approxBytesPerMs = 16L * 1024L / 8L
            var toSkip = positionMs * approxBytesPerMs
            while (toSkip > 0) {
                val skipped = stream.skip(toSkip)
                if (skipped <= 0) break
                toSkip -= skipped
            }
        }
        return stream
    }

    override fun test(server: NetworkServer): Boolean = runCatching {
        smbFile(server, NetworkUrls.root(server)).let { root ->
            if (root.exists()) true else {
                // Root may be a workgroup-level path; a successful list of any kind counts.
                root.listFiles() != null
            }
        }
    }.getOrDefault(false)
}
