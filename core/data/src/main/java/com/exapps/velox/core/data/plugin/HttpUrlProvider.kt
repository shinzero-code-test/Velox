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

    override suspend fun openStream(url: String, offset: Long?): MediaStream {
        // Delegate to OkHttp so we get the same range/redirect
        // handling ExoPlayer would have done natively. The
        // DataSource layer in `:player:engine` only uses the
        // returned `InputStream`, so any HTTP client that
        // implements `InputStream` would do.
        val request = okhttp3.Request.Builder().url(url).apply {
            offset?.let { header("Range", "bytes=$it-") }
        }.build()
        val call = okhttp3.OkHttpClient().newCall(request)
        val response = call.execute()
        if (!response.isSuccessful) {
            response.close()
            throw java.io.IOException("HTTP ${response.code} for $url")
        }
        val bytes = response.body?.bytes()
            ?: throw java.io.IOException("Empty body for $url")
        return ByteArrayMediaStream(
            bytes = bytes,
            offset = offset ?: 0L,
        )
    }
}

private class ByteArrayMediaStream(
    private val bytes: ByteArray,
    override val offset: Long,
) : MediaStream {
    override val totalSize: Long = bytes.size.toLong()

    override fun read(): java.io.InputStream = java.io.ByteArrayInputStream(bytes)

    override fun close() {
        // ByteArrayInputStream doesn't need closing; the array is
        // GC'd when this wrapper is released. ExoPlayer's
        // DataSource contract guarantees the host calls close()
        // when the read is finished.
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class HttpUrlProviderModule {
    @Binds
    @IntoSet
    abstract fun bindHttpUrlProvider(impl: HttpUrlProvider): MediaSourceProvider
}
