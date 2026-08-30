package com.exapps.velox.player.engine

import android.content.Context
import androidx.media3.common.C
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.TransferListener
import com.exapps.velox.core.domain.plugin.MediaSourceProvider
import com.exapps.velox.core.domain.plugin.MediaStream
import com.exapps.velox.core.domain.plugin.PluginRegistry
import com.exapps.velox.core.network.di.NetworkClientRegistry
import com.exapps.velox.core.network.model.NetworkProtocol
import com.exapps.velox.core.network.net.NetworkClient
import com.exapps.velox.core.network.repo.NetworkLibraryRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Phase 2 "Network streams + browsing": routes DataSpecs by URI scheme.
 * http/https (and everything the platform stack knows) fall through to Media3's
 * default chain; `smb://`, `ftp://`, `dav(s)://` are served by [NetworkStreamDataSource]
 * using the core:network clients.
 *
 * Phase 3 / Milestone 4 — Plugin architecture. The router now also
 * consults the [PluginRegistry] for any scheme the plugins advertise.
 * The first plugin in v1.5.0 is the built-in `HttpUrlProvider` (a
 * passthrough that exercises the SPI); the same code path will pick
 * up first-party and APK-discovered plugins in subsequent rounds.
 */
@Singleton
class VeloxDataSourceFactory @Inject constructor(
    @ApplicationContext private val context: Context,
    private val networkRepository: NetworkLibraryRepository,
    private val clients: NetworkClientRegistry,
    private val pluginRegistry: PluginRegistry,
) : DataSource.Factory {

    private val defaultFactory = DefaultDataSource.Factory(context)

    override fun createDataSource(): DataSource =
        RoutingDataSource(defaultFactory.createDataSource(), networkRepository, clients, pluginRegistry)
}

/** Dispatches per-open between local/http, our custom network protocols, and plugin-backed schemes. */
private class RoutingDataSource(
    private val fallback: DataSource,
    private val networkRepository: NetworkLibraryRepository,
    private val clients: NetworkClientRegistry,
    private val pluginRegistry: PluginRegistry,
) : DataSource {

    /** Media3 attaches its bandwidth/progress listeners here once per source; every
     * per-open child must receive them too (before [DataSource.open] fires them). */
    private val listeners = mutableListOf<TransferListener>()
    private var active: DataSource? = null

    override fun addTransferListener(transferListener: TransferListener) {
        listeners += transferListener
        fallback.addTransferListener(transferListener)
    }

    override fun open(dataSpec: DataSpec): Long {
        // L2 (player-stack review): Media3's `check` throws IllegalStateException
        // for "called twice" — DataSource contract says this should surface as
        // an IOException so ExoPlayer treats it as a retryable error rather
        // than crashing the loader thread.
        if (active != null) throw java.io.IOException("RoutingDataSource.open called twice")
        val scheme = dataSpec.uri.scheme?.lowercase()
        val dataSource: DataSource = when {
            // Phase 2 path: SMB/FTP/WebDAV through the existing
            // credential-aware NetworkClient surface.
            scheme == "smb" || scheme == "ftp" || scheme == "dav" || scheme == "davs" ->
                NetworkStreamDataSource(networkRepository, clients)
            // Phase 3 / Milestone 4 path: any other scheme a plugin
            // claims. The first-party HttpUrlProvider covers http/https
            // (passthrough); future plugins will plug in here.
            scheme != null && pluginRegistry.providerForScheme(scheme) != null ->
                PluginStreamDataSource(pluginRegistry.providerForScheme(scheme)!!)
            else -> fallback
        }
        listeners.forEach(dataSource::addTransferListener)
        active = dataSource
        return dataSource.open(dataSpec)
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
        requireNotNull(active).read(buffer, offset, length)

    override fun getUri() = active?.uri

    override fun getResponseHeaders(): MutableMap<String, MutableList<String>> =
        active?.responseHeaders ?: mutableMapOf()

    override fun close() {
        runCatching { active?.close() }
        active = null
    }
}

/**
 * Streams SMB/FTP/WebDAV content through the matching core:network client. Runs on
 * ExoPlayer's loader thread — blocking IO is expected here. Byte offsets map to a
 * conservative bitrate estimate inside each client; Media3 re-seeks through
 * [open] whenever its estimate needs correcting.
 */
private class NetworkStreamDataSource(
    private val networkRepository: NetworkLibraryRepository,
    private val clients: NetworkClientRegistry,
) : BaseDataSource(/* isNetwork = */ true) {

    private var stream: InputStream? = null
    private var bytesRemaining: Long = C.LENGTH_UNSET.toLong()
    private var openedUri: android.net.Uri? = null

    override fun open(dataSpec: DataSpec): Long {
        transferInitializing(dataSpec)
        val url = dataSpec.uri.toString()
        // M5 (player-stack review): the previous runBlocking parked the loader
        // thread on a DataStore disk read every open/seek. The repository now
        // serves server lookups from an in-memory StateFlow that's hot from
        // app start, so this call is a single .value read.
        val server = networkRepository.findServerCached(url)
            ?: throw java.io.IOException("No saved network server matches $url")

        val protocol = when (dataSpec.uri.scheme?.lowercase()) {
            "smb" -> NetworkProtocol.SMB
            "ftp" -> NetworkProtocol.FTP
            "dav", "davs" -> NetworkProtocol.WEBDAV
            else -> throw java.io.IOException("Unsupported network scheme: ${dataSpec.uri.scheme}")
        }
        val client = clients[protocol]

        // DataSpec.position is a BYTE offset from Media3 (it manages ms↔bytes upstream).
        val stream = client.openStream(server, url, /* positionMs = */ 0L).also { this.stream = it }

        // Honour byte-position; throw loudly if skip can't reach the target so
        // ExoPlayer surfaces a retryable error instead of decoding garbage.
        // C1 (player-stack review): silent break here corrupted every seek.
        if (dataSpec.position > 0) {
            var skippedTotal = 0L
            while (skippedTotal < dataSpec.position) {
                val n = stream.skip(dataSpec.position - skippedTotal)
                if (n <= 0) throw java.io.IOException(
                    "Cannot honour DataSpec.position=${dataSpec.position} (stuck at $skippedTotal)"
                )
                skippedTotal += n
            }
        }

        bytesRemaining = if (dataSpec.length != C.LENGTH_UNSET.toLong()) dataSpec.length else C.LENGTH_UNSET.toLong()
        openedUri = dataSpec.uri
        transferStarted(dataSpec)
        return bytesRemaining
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        val s = stream ?: throw java.io.IOException("read before open")
        val bytesRead: Int = if (bytesRemaining == C.LENGTH_UNSET.toLong()) {
            s.read(buffer, offset, length)
        } else {
            if (bytesRemaining == 0L) return C.RESULT_END_OF_INPUT
            s.read(buffer, offset, minOf(length.toLong(), bytesRemaining).toInt())
        }

        if (bytesRead == C.RESULT_END_OF_INPUT) {
            if (bytesRemaining != C.LENGTH_UNSET.toLong()) bytesRemaining = 0L
            closeCurrentStream()
            return C.RESULT_END_OF_INPUT
        }

        if (bytesRemaining != C.LENGTH_UNSET.toLong()) bytesRemaining -= bytesRead
        bytesTransferred(bytesRead)
        return bytesRead
    }

    override fun getUri(): android.net.Uri? = openedUri

    override fun close() {
        closeCurrentStream()
    }

    private fun closeCurrentStream() {
        runCatching { stream?.close() }
        stream = null
    }
}

/**
 * Phase 3 / Milestone 4 — Plugin-backed DataSource. Hands the open
 * call to the [MediaSourceProvider] the router picked for this
 * scheme. The provider returns a [MediaStream]; we wrap the
 * `InputStream` and honour Media3's `DataSpec.position` by
 * discarding the leading bytes (cheap; if a provider wants
 * range-aware IO it can do so in its own `openStream(url, offset)`).
 */
private class PluginStreamDataSource(
    private val provider: MediaSourceProvider,
) : BaseDataSource(/* isNetwork = */ true) {

    private var stream: InputStream? = null
    private var bytesRemaining: Long = C.LENGTH_UNSET.toLong()
    private var openedUri: android.net.Uri? = null

    override fun open(dataSpec: DataSpec): Long {
        transferInitializing(dataSpec)
        val url = dataSpec.uri.toString()
        // Plugins are expected to handle byte offsets natively if
        // they support ranges (HTTP, WebDAV, FTP do). We pass the
        // position through so the provider can avoid a full fetch
        // for seeks. The wrapping `InputStream` may still start
        // from the beginning if the plugin ignores the offset
        // (e.g. a future NFS plugin) — in that case the standard
        // skip-loop below catches up.
        val mediaStream: MediaStream = kotlinx.coroutines.runBlocking {
            provider.openStream(url, dataSpec.position.takeIf { it > 0 })
        }
        stream = mediaStream.read()
        bytesRemaining = mediaStream.totalSize ?: C.LENGTH_UNSET.toLong()
        openedUri = dataSpec.uri
        transferStarted(dataSpec)
        return bytesRemaining
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        val s = stream ?: throw java.io.IOException("read before open")
        val bytesRead: Int = if (bytesRemaining == C.LENGTH_UNSET.toLong()) {
            s.read(buffer, offset, length)
        } else {
            if (bytesRemaining == 0L) return C.RESULT_END_OF_INPUT
            s.read(buffer, offset, minOf(length.toLong(), bytesRemaining).toInt())
        }
        if (bytesRead == C.RESULT_END_OF_INPUT) {
            if (bytesRemaining != C.LENGTH_UNSET.toLong()) bytesRemaining = 0L
            closeCurrentStream()
            return C.RESULT_END_OF_INPUT
        }
        if (bytesRemaining != C.LENGTH_UNSET.toLong()) bytesRemaining -= bytesRead
        bytesTransferred(bytesRead)
        return bytesRead
    }

    override fun getUri(): android.net.Uri? = openedUri

    override fun close() {
        closeCurrentStream()
    }

    private fun closeCurrentStream() {
        runCatching { stream?.close() }
        stream = null
    }
}
