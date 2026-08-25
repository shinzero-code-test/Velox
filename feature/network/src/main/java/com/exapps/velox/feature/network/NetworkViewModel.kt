package com.exapps.velox.feature.network

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.exapps.velox.core.domain.model.MediaItem
import com.exapps.velox.core.domain.model.MediaType
import com.exapps.velox.core.domain.player.PlayerController
import com.exapps.velox.core.network.model.NetworkEntry
import com.exapps.velox.core.network.model.NetworkProtocol
import com.exapps.velox.core.network.model.NetworkServer
import com.exapps.velox.core.network.model.defaultPort
import com.exapps.velox.core.network.net.NetworkClient
import com.exapps.velox.core.network.di.NetworkClientRegistry
import com.exapps.velox.core.network.net.NetworkUrls
import com.exapps.velox.core.network.repo.NetworkLibraryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Phase 2 "Network streams + Network browsing": servers list, per-server directory
 * tree, and URL playback. All protocol IO happens on Dispatchers.IO through the
 * blocking core:network clients.
 */
@HiltViewModel
class NetworkViewModel @Inject constructor(
    private val repository: NetworkLibraryRepository,
    private val clients: NetworkClientRegistry,
    private val playerController: PlayerController,
) : ViewModel() {

    val servers: StateFlow<List<NetworkServer>> = repository.observeServers()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val recentStreams: StateFlow<List<String>> = repository.observeRecentStreams()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // ---- Browse state ---------------------------------------------------------------

    data class BrowseState(
        val server: NetworkServer? = null,
        /** Directory stack for this server — last element is the current directory. */
        val pathStack: List<String> = emptyList(),
        val entries: List<NetworkEntry> = emptyList(),
        val isLoading: Boolean = false,
        val error: String? = null,
    )

    private val _browse = MutableStateFlow(BrowseState())
    val browse: StateFlow<BrowseState> = _browse.asStateFlow()

    val isBrowsing: Boolean get() = _browse.value.server != null

    fun openServer(server: NetworkServer) {
        navigateTo(server, listOf(NetworkUrls.root(server)))
    }

    fun openDirectory(entry: NetworkEntry) {
        val state = _browse.value
        val server = state.server ?: return
        navigateTo(server, state.pathStack + entry.url)
    }

    /** Returns true when up-navigation was handled inside the browser. */
    fun goUp(): Boolean {
        val state = _browse.value
        val server = state.server ?: return false
        if (state.pathStack.size <= 1) {
            closeBrowse()
            return true
        }
        navigateTo(server, state.pathStack.dropLast(1))
        return true
    }

    fun closeBrowse() {
        _browse.value = BrowseState()
    }

    private fun navigateTo(server: NetworkServer, pathStack: List<String>) {
        val url = pathStack.last()
        _browse.value = BrowseState(server = server, pathStack = pathStack, isLoading = true)
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { clients[server.protocol].list(server, url) }
            }
            _browse.value = when {
                result.isSuccess -> BrowseState(server, pathStack, result.getOrDefault(emptyList()), isLoading = false)
                else -> BrowseState(
                    server = server,
                    pathStack = pathStack.dropLast(1),
                    entries = _browse.value.entries,
                    isLoading = false,
                    error = result.exceptionOrNull()?.message ?: "Connection failed",
                )
            }
        }
    }

    // ---- Playback ---------------------------------------------------------------------

    /** Plays [entry]; every playable sibling joins the queue (library behaviour parity). */
    fun play(entry: NetworkEntry, siblings: List<NetworkEntry>) {
        val playable = siblings.filter { !it.isDirectory && NetworkClientMime.isPlayable(it.name) }
        val ordered = if (playable.any { it.url == entry.url }) playable else listOf(entry)

        val items = ordered.map {
            MediaItem(
                id = -(it.url.hashCode().toLong()),
                uri = it.url,
                title = NetworkUrls.displayName(it.url),
                mediaType = if (NetworkClientMime.isVideo(it.name)) MediaType.VIDEO else MediaType.AUDIO,
                durationMs = 0L,
            )
        }
        val startIndex = items.indexOfFirst { it.uri == entry.url }.coerceAtLeast(0)
        playerController.play(items, startIndex)
    }

    // ---- Streams (Phase 2 "Network streams") ------------------------------------------

    fun addRecentStream(url: String) {
        viewModelScope.launch { repository.addRecentStream(url.trim()) }
    }

    fun playStream(url: String) {
        val trimmed = url.trim()
        val supported = listOf("http://", "https://", "rtsp://", "smb://", "ftp://", "dav://", "davs://")
        if (supported.none { trimmed.startsWith(it) }) return

        addRecentStream(trimmed)
        playerController.play(
            listOf(
                MediaItem(
                    id = -(trimmed.hashCode().toLong()),
                    uri = trimmed,
                    title = trimmed.substringAfterLast('/').ifEmpty { trimmed },
                    mediaType = if (NetworkClientMime.isVideo(trimmed)) MediaType.VIDEO else MediaType.AUDIO,
                    durationMs = 0L,
                ),
            ),
        )
    }

    // ---- Server CRUD ------------------------------------------------------------------

    fun saveServer(
        name: String,
        protocol: NetworkProtocol,
        host: String,
        portText: String,
        username: String,
        password: String,
        basePath: String,
        secure: Boolean,
        existingId: Long?,
    ) {
        val trimmedHost = host.trim()
        if (trimmedHost.isEmpty()) return
        viewModelScope.launch {
            val port = portText.trim().toIntOrNull() ?: protocol.defaultPort()
            val base = basePath.trim().ifEmpty { "/" }.let { if (it.startsWith("/")) it else "/$it" }
            repository.upsertServer(
                NetworkServer(
                    id = existingId ?: System.nanoTime(),
                    name = name.trim().ifEmpty { trimmedHost },
                    protocol = protocol,
                    host = trimmedHost,
                    port = port,
                    username = username.trim(),
                    password = password,
                    basePath = base,
                    secure = secure && protocol == NetworkProtocol.WEBDAV,
                ),
            )
        }
    }

    fun deleteServer(id: Long) {
        viewModelScope.launch { repository.deleteServer(id) }
    }

    fun testServer(server: NetworkServer, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching { clients[server.protocol].test(server) }.getOrDefault(false)
            }
            onResult(ok)
        }
    }
}

/** Mime helpers kept file-local so the module stays self-contained. */
object NetworkClientMime {
    private val videoExtensions = setOf("mp4", "m4v", "mkv", "webm", "avi", "mov", "ts", "m2ts", "3gp")
    private val audioExtensions = setOf("mp3", "flac", "m4a", "aac", "ogg", "opus", "wav", "wma")

    fun isVideo(nameOrUrl: String): Boolean =
        nameOrUrl.substringBefore('?').substringAfterLast('.', "").lowercase() in videoExtensions

    fun isPlayable(nameOrUrl: String): Boolean {
        val ext = nameOrUrl.substringBefore('?').substringAfterLast('.', "").lowercase()
        return ext in videoExtensions || ext in audioExtensions
    }
}
