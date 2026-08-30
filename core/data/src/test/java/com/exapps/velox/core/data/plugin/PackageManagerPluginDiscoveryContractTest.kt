package com.exapps.velox.core.data.plugin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * Phase 3 / Wave 3 / Round 3.5e — contract tests for the
 * constant surface (action, permission name, meta-data key) of
 * the plugin discovery. The full PackageManager walk isn't
 * unit-testable here (it requires a real Context), so this test
 * only guards the names that ship in the host's manifest.
 *
 * The plugin contract:
 *  - Intent action: com.exapps.velox.MEDIA_SOURCE_PROVIDER
 *  - Permission:   com.exapps.velox.permission.PLUGIN_HOST
 *  - Meta-data:    com.exapps.velox.MEDIA_SOURCE_PROVIDER
 *
 * If any of these change, the host's manifest update must ship
 * alongside. The test fails if the constants drift from the
 * documented values.
 */
class PackageManagerPluginDiscoveryContractTest {

    @Test
    fun `intent action is stable`() {
        assertEquals(
            "com.exapps.velox.MEDIA_SOURCE_PROVIDER",
            PackageManagerPluginDiscovery.ACTION_MEDIA_SOURCE_PROVIDER,
        )
    }

    @Test
    fun `permission is stable`() {
        assertEquals(
            "com.exapps.velox.permission.PLUGIN_HOST",
            PackageManagerPluginDiscovery.PLUGIN_HOST_PERMISSION,
        )
    }

    @Test
    fun `meta-data key is stable`() {
        assertEquals(
            "com.exapps.velox.MEDIA_SOURCE_PROVIDER",
            PackageManagerPluginDiscovery.META_KEY,
        )
    }

    @Test
    fun `action and meta-data key share the same string — by design`() {
        // The plugin contract uses the same string for both the
        // intent action and the meta-data key, so a single
        // constant covers both. If they ever diverge, the plugin
        // manifest template needs to change too — that should be
        // a deliberate decision, not a copy-paste.
        assertEquals(
            PackageManagerPluginDiscovery.ACTION_MEDIA_SOURCE_PROVIDER,
            PackageManagerPluginDiscovery.META_KEY,
        )
    }

    @Test
    fun `permission and action are different — signature gate is not the same as the action`() {
        assertNotEquals(
            PackageManagerPluginDiscovery.ACTION_MEDIA_SOURCE_PROVIDER,
            PackageManagerPluginDiscovery.PLUGIN_HOST_PERMISSION,
        )
    }
}
