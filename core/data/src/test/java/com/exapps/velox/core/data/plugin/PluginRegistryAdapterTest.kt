package com.exapps.velox.core.data.plugin

import com.exapps.velox.core.domain.plugin.LocalizedPluginName
import com.exapps.velox.core.domain.plugin.MediaEntry
import com.exapps.velox.core.domain.plugin.MediaSourceProvider
import com.exapps.velox.core.domain.plugin.MediaStream
import com.exapps.velox.core.domain.plugin.PluginDiscovery
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Phase 3 / Wave 3 / Round 3.5d — registry contract tests for
 * the merged first-party + discovery surface.
 */
class PluginRegistryAdapterTest {

    @Test
    fun `available merges first-party and discovery with first-party winning on duplicate ids`() = runTest {
        val firstParty = setOf(
            FakeProvider("velox-http", listOf("http", "https")),
            FakeProvider("velox-smb", listOf("smb")),
        )
        // Discovery has a "velox-http" duplicate (should be ignored)
        // and a brand-new "external-nfs" provider (should win through).
        val discovery = object : PluginDiscovery {
            override suspend fun discover() = listOf(
                FakeProvider("velox-http", listOf("http")),
                FakeProvider("external-nfs", listOf("nfs")),
            )
        }
        val registry = PluginRegistryAdapter(firstParty, discovery)
        val ids = registry.available().map { it.id }
        // First-party's velox-http wins; velox-smb and external-nfs both come through.
        assertEquals(listOf("external-nfs", "velox-http", "velox-smb"), ids)
    }

    @Test
    fun `available returns first-party list when discovery is empty`() = runTest {
        val registry = PluginRegistryAdapter(
            setOf(FakeProvider("velox-http", listOf("http"))),
            object : PluginDiscovery {
                override suspend fun discover() = emptyList<MediaSourceProvider>()
            },
        )
        val ids = registry.available().map { it.id }
        assertEquals(listOf("velox-http"), ids)
    }

    @Test
    fun `providerForScheme is the hot first-party lookup`() {
        val registry = PluginRegistryAdapter(
            setOf(FakeProvider("velox-smb", listOf("smb"))),
            object : PluginDiscovery {
                override suspend fun discover() =
                    listOf(FakeProvider("external-nfs", listOf("nfs")))
            },
        )
        // Hot path covers first-party only.
        assertEquals("velox-smb", registry.providerForScheme("smb")?.id)
        assertNull("discovery plugins don't appear in the hot path", registry.providerForScheme("nfs"))
        assertNull("unknown scheme", registry.providerForScheme("unknown"))
    }
}

private class FakeProvider(
    override val id: String,
    override val supportedProtocols: List<String>,
) : MediaSourceProvider {
    override val displayName: LocalizedPluginName = LocalizedPluginName(defaultName = id)
    override suspend fun listDirectory(url: String): List<MediaEntry> = emptyList()
    override suspend fun openStream(url: String, offset: Long?): MediaStream =
        object : MediaStream {
            override val offset: Long = offset ?: 0L
            override val totalSize: Long? = null
            override fun read() = java.io.ByteArrayInputStream(ByteArray(0))
            override fun close() {}
        }
}
