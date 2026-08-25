package com.exapps.velox.core.network.repo

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.exapps.velox.core.network.model.NetworkServer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persistence for Phase 2 network features (DataStore JSON): configured servers,
 * recently-opened network/stream URLs. Same trust level as every other local
 * preference — credentials never leave the device; noted in PROGRESS.md.
 */
@Singleton
class NetworkLibraryRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {

    private val json = Json { ignoreUnknownKeys = true }

    // ---- Servers -----------------------------------------------------------------

    fun observeServers(): Flow<List<NetworkServer>> = dataStore.data.map { prefs ->
        prefs[SERVERS_KEY]?.let { runCatching { json.decodeFromString<List<NetworkServer>>(it) }.getOrNull() }.orEmpty()
    }

    suspend fun servers(): List<NetworkServer> = observeServers().first()

    suspend fun upsertServer(server: NetworkServer) {
        dataStore.edit { prefs ->
            val current = prefs[SERVERS_KEY]
                ?.let { runCatching { json.decodeFromString<List<NetworkServer>>(it) }.getOrNull() }
                .orEmpty()
            val updated = (current.filterNot { it.id == server.id } + server)
                .sortedBy { it.name.lowercase() }
            prefs[SERVERS_KEY] = json.encodeToString(updated)
        }
    }

    suspend fun deleteServer(serverId: Long) {
        dataStore.edit { prefs ->
            val current = prefs[SERVERS_KEY]
                ?.let { runCatching { json.decodeFromString<List<NetworkServer>>(it) }.getOrNull() }
                .orEmpty()
            prefs[SERVERS_KEY] = json.encodeToString(current.filterNot { it.id == serverId })
        }
    }

    /** Matches a playable URL back to its server record (engine DataSource routing). */
    suspend fun findServer(url: String): NetworkServer? {
        val schemePrefixes = listOf("smb://", "ftp://", "dav://", "davs://")
        if (schemePrefixes.none { url.startsWith(it) }) return null
        val host = url.substringAfter("://").substringBefore('/').substringBefore(':')
        return servers().firstOrNull { it.host == host }
    }

    /** Server for the engine's DataSource when only protocol+host are known. */
    suspend fun findServerByHost(host: String): NetworkServer? =
        servers().firstOrNull { it.host.equals(host, ignoreCase = true) }

    // ---- Recent streams ----------------------------------------------------------

    fun observeRecentStreams(): Flow<List<String>> = dataStore.data.map { prefs ->
        prefs[RECENT_STREAMS_KEY]
            ?.let { runCatching { json.decodeFromString<List<String>>(it) }.getOrNull() }
            .orEmpty()
    }

    suspend fun addRecentStream(url: String) {
        if (!url.startsWith("http")) return
        dataStore.edit { prefs ->
            val current = prefs[RECENT_STREAMS_KEY]
                ?.let { runCatching { json.decodeFromString<List<String>>(it) }.getOrNull() }
                .orEmpty()
            prefs[RECENT_STREAMS_KEY] = json.encodeToString((listOf(url) + current.filterNot { it == url }).take(10))
        }
    }

    /** Backup/restore surface (Phase 2). */
    suspend fun exportState(): Pair<List<NetworkServer>, List<String>> =
        servers() to observeRecentStreams().first()

    suspend fun importState(servers: List<NetworkServer>, recentStreams: List<String>) {
        dataStore.edit { prefs ->
            prefs[SERVERS_KEY] = json.encodeToString(servers)
            prefs[RECENT_STREAMS_KEY] = json.encodeToString(recentStreams)
        }
    }

    private companion object {
        val SERVERS_KEY = stringPreferencesKey("network_servers_v1")
        val RECENT_STREAMS_KEY = stringPreferencesKey("recent_streams_v1")
    }
}
