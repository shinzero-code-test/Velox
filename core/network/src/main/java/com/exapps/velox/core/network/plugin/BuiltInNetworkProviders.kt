package com.exapps.velox.core.network.plugin

import com.exapps.velox.core.domain.plugin.LocalizedPluginName
import com.exapps.velox.core.domain.plugin.MediaEntry
import com.exapps.velox.core.domain.plugin.MediaSourceProvider
import com.exapps.velox.core.domain.plugin.MediaStream
import com.exapps.velox.core.network.model.NetworkEntry
import com.exapps.velox.core.network.model.NetworkServer
import com.exapps.velox.core.network.net.FtpClientHolder
import com.exapps.velox.core.network.net.SmbClient
import com.exapps.velox.core.network.net.WebDavClient
import com.exapps.velox.core.network.repo.NetworkLibraryRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Phase 3 / Wave 3 / Round 3.5b — built-in `MediaSourceProvider`
 * adapters for the three first-party network clients (SMB, FTP,
 * WebDAV). Each wraps a concrete client and uses the existing
 * [NetworkLibraryRepository.findServerCached] to resolve the
 * credential context for a URL — same path the `RoutingDataSource`
 * uses, so behaviour matches the legacy browsing/streaming path.
 *
 * The Hilt wiring aggregates all three via `@IntoSet` so they
 * land in the `PluginRegistry`'s multibound set, alongside the
 * `HttpUrlProvider` from `:core:data`. After this change, the
 * Settings → About → Plugins list shows all four first-party
 * providers, and the engine router picks the right one by scheme.
 *
 * The `positionMs` arg to `openStream` is `0L` for now — the
 * engine's `NetworkStreamDataSource` already has a skip() loop
 * that catches up if the underlying protocol can't range-read.
 * A future round can wire `offset` through to the SMB/FTP/WebDAV
 * `Range` headers; the round-1.5 implementation defers that.
 */
@Singleton
class SmbMediaSourceProvider @Inject constructor(
    private val client: SmbClient,
    private val repository: NetworkLibraryRepository,
) : MediaSourceProvider {
    override val id: String = "velox-smb"
    override val displayName: LocalizedPluginName = LocalizedPluginName(
        defaultName = "SMB / CIFS",
        ar = "SMB / CIFS",
        en = "SMB / CIFS",
    )
    override val supportedProtocols: List<String> = listOf("smb")
    override suspend fun listDirectory(url: String): List<MediaEntry> = withContext(Dispatchers.IO) {
        val server = requireServer(url)
        client.list(server, url).map { it.toMediaEntry() }
    }
    override suspend fun openStream(url: String, offset: Long?): MediaStream = withContext(Dispatchers.IO) {
        val server = requireServer(url)
        val stream = client.openStream(server, url, positionMs = 0L)
        smbStream(stream, offset)
    }
}

@Singleton
class FtpMediaSourceProvider @Inject constructor(
    private val client: FtpClientHolder,
    private val repository: NetworkLibraryRepository,
) : MediaSourceProvider {
    override val id: String = "velox-ftp"
    override val displayName: LocalizedPluginName = LocalizedPluginName(
        defaultName = "FTP / FTPS",
        ar = "FTP / FTPS",
        en = "FTP / FTPS",
    )
    override val supportedProtocols: List<String> = listOf("ftp", "ftps")
    override suspend fun listDirectory(url: String): List<MediaEntry> = withContext(Dispatchers.IO) {
        val server = requireServer(url)
        client.list(server, url).map { it.toMediaEntry() }
    }
    override suspend fun openStream(url: String, offset: Long?): MediaStream = withContext(Dispatchers.IO) {
        val server = requireServer(url)
        val stream = client.openStream(server, url, positionMs = 0L)
        ftpStream(stream, offset)
    }
}

@Singleton
class WebDavMediaSourceProvider @Inject constructor(
    private val client: WebDavClient,
    private val repository: NetworkLibraryRepository,
) : MediaSourceProvider {
    override val id: String = "velox-webdav"
    override val displayName: LocalizedPluginName = LocalizedPluginName(
        defaultName = "WebDAV",
        ar = "WebDAV",
        en = "WebDAV",
    )
    override val supportedProtocols: List<String> = listOf("dav", "davs")
    override suspend fun listDirectory(url: String): List<MediaEntry> = withContext(Dispatchers.IO) {
        val server = requireServer(url)
        client.list(server, url).map { it.toMediaEntry() }
    }
    override suspend fun openStream(url: String, offset: Long?): MediaStream = withContext(Dispatchers.IO) {
        val server = requireServer(url)
        val stream = client.openStream(server, url, positionMs = 0L)
        webDavStream(stream, offset)
    }
}

/** Hilt module — three providers in one multibound set. */
@Module
@InstallIn(SingletonComponent::class)
abstract class BuiltInNetworkProvidersModule {

    @Binds
    @IntoSet
    abstract fun bindSmbProvider(impl: SmbMediaSourceProvider): MediaSourceProvider

    @Binds
    @IntoSet
    abstract fun bindFtpProvider(impl: FtpMediaSourceProvider): MediaSourceProvider

    @Binds
    @IntoSet
    abstract fun bindWebDavProvider(impl: WebDavMediaSourceProvider): MediaSourceProvider
}

// --- private helpers ---------------------------------------------------------

private suspend fun NetworkLibraryRepository.requireServer(url: String): NetworkServer =
    findServerCached(url) ?: error("No saved network server matches $url")

/**
 * Wrap the SMB client's blocking [java.io.InputStream] in a
 * [MediaStream]. The SMB stream's `close()` releases the
 * underlying jcifs handle; we forward that to the MediaStream
 * contract.
 */
private fun smbStream(
    raw: java.io.InputStream,
    offset: Long?,
): MediaStream = object : MediaStream {
    override val offset: Long = offset ?: 0L
    override val totalSize: Long? = null
    override fun read(): java.io.InputStream = raw
    override fun close() = runCatching { raw.close() }
}

private fun ftpStream(
    raw: java.io.InputStream,
    offset: Long?,
): MediaStream = object : MediaStream {
    override val offset: Long = offset ?: 0L
    override val totalSize: Long? = null
    override fun read(): java.io.InputStream = raw
    override fun close() = runCatching { raw.close() }
}

private fun webDavStream(
    raw: java.io.InputStream,
    offset: Long?,
): MediaStream = object : MediaStream {
    override val offset: Long = offset ?: 0L
    override val totalSize: Long? = null
    override fun read(): java.io.InputStream = raw
    override fun close() = runCatching { raw.close() }
}

private fun NetworkEntry.toMediaEntry(): MediaEntry = MediaEntry(
    name = name,
    url = url,
    isDirectory = isDirectory,
    sizeBytes = sizeBytes.takeIf { it >= 0 },
    lastModifiedEpochSeconds = null,
    mimeType = null,
)
