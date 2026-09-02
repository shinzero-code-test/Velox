package com.example.veloxrefplugin

import com.exapps.velox.core.domain.plugin.LocalizedPluginName
import com.exapps.velox.core.domain.plugin.MediaEntry
import com.exapps.velox.core.domain.plugin.MediaSourceProvider
import com.exapps.velox.core.domain.plugin.MediaStream

/**
 * Minimal reference plugin for ADR 0001. Build as a separate APK signed with the
 * same key as the Velox host, install alongside Velox, and the host's
 * PackageManagerPluginDiscovery will load it via PathClassLoader.
 *
 * Replace `myproto` with your own scheme and implement real list/open logic.
 */
class ExamplePluginProvider : MediaSourceProvider {

    override val id: String = "velox-ref-sample"

    override val displayName: LocalizedPluginName = LocalizedPluginName(
        defaultName = "Reference Plugin",
        en = "Reference Plugin",
        ar = "الإضافة المرجعية",
    )

    override val supportedProtocols: List<String> = listOf("myproto")

    override suspend fun listDirectory(url: String): List<MediaEntry> {
        // Synthetic listing — replace with real directory read (e.g. S3 ListObjects).
        return listOf(
            MediaEntry(name = "sample.mp3", url = "myproto://demo/sample.mp3", isDirectory = false, sizeBytes = 12345),
            MediaEntry(name = "subdir", url = "myproto://demo/subdir", isDirectory = true),
        )
    }

    override suspend fun openStream(url: String, offset: Long?): MediaStream {
        // Demonstrates correct offset handling (range-aware). Real implementation would
        // open an HTTP Range request or file channel at `offset` and stream.
        val bytes = ByteArray(0) // placeholder
        return object : MediaStream {
            override val offset: Long = offset ?: 0L
            override val totalSize: Long? = null
            override fun read(): java.io.InputStream = bytes.inputStream()
            override fun close() = Unit
        }
    }
}
