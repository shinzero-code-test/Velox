package com.exapps.velox.core.network.net

import com.exapps.velox.core.network.model.NetworkEntry
import com.exapps.velox.core.network.model.NetworkServer
import org.apache.commons.net.ftp.FTPClient
import org.apache.commons.net.ftp.FTPFile
import java.io.InputStream

/**
 * FTP client over Apache commons-net (Phase 2 "Network browsing"). Passive mode +
 * binary transfer; credentials from the server record (anonymous when blank).
 */
class FtpClientHolder @javax.inject.Inject constructor() : NetworkClient {

    private fun connect(server: NetworkServer): FTPClient = FTPClient().apply {
        connectTimeout = 10_000
        defaultTimeout = 15_000
        connect(server.host, server.port)
        // M10 (data-layer review): without UTF-8 control encoding, non-ASCII
        // filenames mangle in listings and RETR paths.
        setControlEncoding("UTF-8")
        enterLocalPassiveMode()
        soTimeout = 15_000
        val user = server.username.ifBlank { "anonymous" }
        val pass = if (server.username.isBlank()) "velox@example.com" else server.password
        if (!login(user, pass)) {
            throw IllegalStateException("FTP login failed for ${server.host}")
        }
        setFileType(FTPClient.BINARY_FILE_TYPE)
    }

    /** FTP path without scheme/host — everything after the host part of the URL. */
    private fun ftpPath(url: String): String {
        val noScheme = url.substringAfter("://")
        return "/" + noScheme.substringAfter('/', "")
    }

    override fun list(server: NetworkServer, url: String): List<NetworkEntry> {
        val client = connect(server)
        try {
            val path = ftpPath(url)
            val files: Array<FTPFile> = client.listFiles(path)
            return files
                .filter { it.name != "." && it.name != ".." }
                .map {
                    NetworkEntry(
                        name = it.name,
                        url = NetworkUrls.child(url, it.name),
                        isDirectory = it.isDirectory,
                        sizeBytes = it.size.toLong(),
                    )
                }
                .sortedWith(compareByDescending<NetworkEntry> { it.isDirectory }.thenBy { it.name.lowercase() })
        } finally {
            runCatching { client.logout() }
            runCatching { client.disconnect() }
        }
    }

    override fun openStream(server: NetworkServer, url: String, positionMs: Long): InputStream {
        val client = connect(server)
        // RETR supports a byte restart offset — same ~128 kbps audio estimate as
        // SMB; ExoPlayer corrects via its own re-seeks through the DataSource.
        val approxBytesPerMs = BYTES_PER_MS
        val offset = positionMs * approxBytesPerMs
        if (offset > 0) client.setRestartOffset(offset)
        val stream = client.retrieveFileStream(ftpPath(url))
            ?: throw IllegalStateException("FTP RETR failed: ${client.replyCode}")
        // Wrap so closing the stream also completes the control connection cleanly.
        return object : InputStream() {
            override fun read(): Int = stream.read()
            override fun read(b: ByteArray, off: Int, len: Int): Int = stream.read(b, off, len)
            override fun close() {
                runCatching { stream.close() }
                runCatching { client.completePendingCommand() }
                runCatching { client.logout() }
                runCatching { client.disconnect() }
            }
        }
    }

    override fun test(server: NetworkServer): Boolean = runCatching {
        val client = connect(server)
        try {
            client.logout()
        } finally {
            runCatching { client.disconnect() }
        }
        true
    }.getOrDefault(false)

    private companion object {
        /** H3 (data-layer review): 16 bytes/ms ≈ 128 kbps audio. The old 2048 B/ms
         * value was a ×1024 typo that overshot past EOF on every audio resume. */
        const val BYTES_PER_MS = 16L
    }
}
