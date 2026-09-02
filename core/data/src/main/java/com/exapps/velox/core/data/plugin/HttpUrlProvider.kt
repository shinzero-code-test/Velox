package com.exapps.velox.core.data.plugin

import com.exapps.velox.core.domain.plugin.LocalizedPluginName
import com.exapps.velox.core.domain.plugin.MediaEntry
import com.exapps.velox.core.domain.plugin.MediaSourceProvider
import com.exapps.velox.core.domain.plugin.MediaStream
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Phase 3 / Milestone 4 — Plugin architecture. The first-party
 * "HTTP URL" plugin. Handles `http://` and `https://` URLs by
 * wrapping OkHttp's range-aware GET.
 *
 * ExoPlayer already plays HTTP/HTTPS URLs natively through its
 * default DataSource chain; this provider exists so the
 * [com.exapps.velox.core.domain.plugin.PluginRegistry] has at least
 * one registered provider in the MVP, exercising the SPI end-to-end.
 * The data-source routing in
 * [com.exapps.velox.player.engine.VeloxDataSourceFactory] consults
 * the registry for any URL; HTTP falls through to the native chain
 * when this provider returns the same InputStream ExoPlayer would
 * have opened anyway. The point is the contract, not the bytes.
 *
 * Round 1.5 will replace this with a real first-party plugin that
 * exercises the SPI in a non-trivial way (e.g. IPTV M3U parser
 * that surfaces channels as `MediaEntry`s).
 */
@Singleton
class HttpUrlProvider @Inject constructor() : MediaSourceProvider {

    override val id: String = "velox-http-url"

    override val displayName: LocalizedPluginName = LocalizedPluginName(
        defaultName = "HTTP / HTTPS",
        ar = "HTTP / HTTPS",
        en = "HTTP / HTTPS",
    )

    override val supportedProtocols: List<String> = listOf("http", "https")

    override suspend fun listDirectory(url: String): List<MediaEntry> {
        // HTTP servers don't expose a directory-listing protocol by
        // default. We refuse the call with a clear exception so the
        // Network browser's "browse" affordance is hidden for HTTP
        // servers — the existing URL-stream entry point remains.
        throw UnsupportedOperationException(
            "HTTP/HTTPS URLs don't support directory listing; use the URL field instead",
        )
    }

    // Reuse a single OkHttpClient — connection pooling + dispatcher reuse.
    // Creating a client per request leaked threads and broke HTTP/2 multiplexing.
    private val okHttpClient by lazy { okhttp3.OkHttpClient() }

    override suspend fun openStream(url: String, offset: Long?): MediaStream {
        // Delegate to OkHttp so we get the same range/redirect
        // handling ExoPlayer would have done natively. We stream
        // via byteStream() instead of bytes() — the previous
        // implementation loaded the entire file into RAM, which
        // OOM-killed the process on any video > ~100 MB.
        val request = okhttp3.Request.Builder().url(url).apply {
            offset?.let { header("Range", "bytes=$it-") }
        }.build()
        val response = okHttpClient.newCall(request).execute()
        if (!response.isSuccessful) {
            response.close()
            throw java.io.IOException("HTTP ${response.code} for $url")
        }
        val body = response.body ?: run {
            response.close()
            throw java.io.IOException("Empty body for $url")
        }
        // For range requests the server returns 206 with Content-Range
        // "bytes start-end/total". Parse total for seeking UI; fall back
        // to Content-Length + offset when header is absent.
        val totalSize: Long? = response.header("Content-Range")?.let { cr ->
            // e.g. "bytes 1024-2047/5000" → 5000
            cr.substringAfterLast('/').toLongOrNull()
        } ?: body.contentLength().takeIf { it != -1L }?.let { len ->
            if (offset != null) len + offset else len
        }
        val stream = body.byteStream()
        return StreamingMediaStream(
            stream = stream,
            response = response,
            offset = offset ?: 0L,
            totalSize = totalSize,
        )
    }
}

private class StreamingMediaStream(
    private val stream: java.io.InputStream,
    private val response: okhttp3.Response,
    override val offset: Long,
    override val totalSize: Long?,
) : MediaStream {
    override fun read(): java.io.InputStream = stream

    override fun close() {
        runCatching { stream.close() }
        runCatching { response.close() }
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class HttpUrlProviderModule {
    @Binds
    @IntoSet
    abstract fun bindHttpUrlProvider(impl: HttpUrlProvider): MediaSourceProvider
}
