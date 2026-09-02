package com.exapps.velox.core.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Per-plugin enable/disable (§6 Future Round: "Per-plugin enable/disable —
 * still always-on once loaded"). Persisted as a StringSet of disabled
 * plugin ids in the same DataStore file as UserSettings. Disabled plugins
 * are filtered out in [com.exapps.velox.core.data.plugin.PluginRegistryAdapter]
 * so the engine's RoutingDataSource never sees them and Settings → Plugins
 * shows them as toggled off. Built-in providers (velox-smb/ftp/webdav/http)
 * are treated identically to APK-discovered ones — disabling "velox-smb"
 * simply removes `smb://` handling for that device.
 */
@Singleton
class PluginPreferences @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {

    val disabledIds: Flow<Set<String>> = dataStore.data.map { prefs ->
        prefs[DISABLED_IDS_KEY] ?: emptySet()
    }

    suspend fun isEnabled(id: String): Boolean {
        val disabled = disabledIdsValue()
        return id !in disabled
    }

    suspend fun setEnabled(id: String, enabled: Boolean) {
        dataStore.edit { prefs ->
            val current = prefs[DISABLED_IDS_KEY] ?: emptySet()
            prefs[DISABLED_IDS_KEY] = if (enabled) {
                current - id
            } else {
                current + id
            }
        }
    }

    suspend fun disabledIdsValue(): Set<String> =
        dataStore.data.map { it[DISABLED_IDS_KEY] ?: emptySet() }.first()

    private companion object {
        val DISABLED_IDS_KEY = stringSetPreferencesKey("plugin_disabled_ids")
    }
}
