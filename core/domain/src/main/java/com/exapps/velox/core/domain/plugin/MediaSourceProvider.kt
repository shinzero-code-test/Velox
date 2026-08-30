package com.exapps.velox.core.domain.plugin

import kotlinx.coroutines.flow.Flow

/**
 * Phase 3 / Milestone 4 — Plugin architecture for media sources.
 *
 * A plugin is anything that can list a directory's contents and
 * open a stream for a single entry, without `:player:engine`
 * knowing the protocol. SMB, FTP, and WebDAV (the built-in
 * clients in `:core:network`) implement this contract; first-
 * party plugins (NFS, S3, podcast feeds, IPTV) can too.
 *
 * The contract is intentionally narrow: `listDirectory` returns a
 * flat list, `openStream` is an `openInput`-style call. Anything
 * more complex (e.g. server-side cursors, multi-step auth) is the
 * plugin's problem; the host only needs a `List<MediaEntry>` and
 * a `Long?` resume offset.
 *
 * Discovery is the host's responsibility (see
 * [PluginRegistry]); providers don't self-register on the classpath
 * in this version. The reason: a Service-Loader entry would couple
 * plugins to the host's classloader, which doesn't survive APK
 * isolation. The MVP discovery is "first-party plugins live in the
 * same APK as the host and are bound by Hilt" — see the round-1
 * ADR in `velox-docs/adr/0001-plugin-architecture.md`.
 */
interface MediaSourceProvider {

    /** Stable id used in diagnostics and the Settings → Plugins list. */
    val id: String

    /**
     * User-visible display name, localised in the host's current
     * locale ("ar" or "en"; falls back to `defaultName`).
     */
    val displayName: LocalizedPluginName

    /**
     * URL schemes this provider can handle. Used by the
     * [com.exapps.velox.player.engine.VeloxDataSourceFactory] router
     * to pick the right provider for a given URI.
     *
     * Examples: `["smb", "smbs"]` for SMB, `["ftp", "ftps"]` for FTP.
     */
    val supportedProtocols: List<String>

    /**
     * List the contents of [url] (a directory in the plugin's
     * scheme). Returns a flow so large directories can stream.
     */
    suspend fun listDirectory(url: String): List<MediaEntry>

    /**
     * Open a stream for [url], optionally at [offset] bytes.
     * Returns a [MediaStream] that the host can hand to ExoPlayer's
     * DataSource pipeline.
     */
    suspend fun openStream(url: String, offset: Long? = null): MediaStream
}

/**
 * Display name, in the same shape as the theme engine's
 * [com.exapps.velox.core.domain.theme.LocalizedText]. Kept as a
 * separate type to avoid pulling the theme module into the player
 * stack's domain surface (the two might diverge in the future).
 */
data class LocalizedPluginName(
    val defaultName: String,
    val ar: String? = null,
    val en: String? = null,
) {
    fun forLocale(locale: String): String {
        val want = locale.lowercase()
        return when {
            want.startsWith("ar") && ar != null -> ar
            want.startsWith("en") && en != null -> en
            else -> defaultName
        }
    }
}

/**
 * One entry in a directory listing. Carries enough metadata for
 * the Network browser to render a row and decide whether the entry
 * is a sub-directory (drill-down) or a file (play).
 */
data class MediaEntry(
    /** Display name (file basename, or directory name). */
    val name: String,
    /** Absolute URL — feed straight into [MediaSourceProvider.openStream] for files. */
    val url: String,
    /** True if this is a sub-directory; false if it's a playable file. */
    val isDirectory: Boolean,
    /** Best-effort size in bytes; null when unknown (e.g. SMB without quota). */
    val sizeBytes: Long? = null,
    /** Last-modified epoch seconds; null when unknown. */
    val lastModifiedEpochSeconds: Long? = null,
    /** MIME type hint for files; null for directories. */
    val mimeType: String? = null,
)

/**
 * A seekable input stream. ExoPlayer's `DataSpec` accepts an
 * `InputStream`; the plugin can wrap a `FileInputStream`,
 * `SmbFileInputStream`, an HTTP range response, etc. The
 * [offset] carried over from `openStream(url, offset)` is exposed
 * here so the host's `RoutingDataSource` can short-circuit an
 * `open()` that lands on a partial read.
 */
interface MediaStream : AutoCloseable {
    val offset: Long
    val totalSize: Long?
    fun read(): java.io.InputStream
    override fun close()
}
