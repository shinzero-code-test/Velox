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
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
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
        /**
         * H2 (features review): the URL of the last successful listing, kept
         * alongside the error so the UI can offer a retry that goes to the
         * directory that actually failed (rather than the up-navigation that
         * loses your place).
         */
        val failedUrl: String? = null,
    )

    private val _browse = MutableStateFlow(BrowseState())
    val browse: StateFlow<BrowseState> = _browse.asStateFlow()

    // bulk-cleanup (features review): was a `get()` property that read
    // `_browse.value` on every recomposition. Exposing a StateFlow lets
    // the screen use `collectAsStateWithLifecycle()` so it gets a
    // deterministic snapshot per recomposition.
    val isBrowsing: StateFlow<Boolean> = _browse
        .map { it.server != null }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /**
     * H2 (features review): the in-flight `list()` call. A new navigation
     * cancels the previous one before starting, so rapid directory drilling
     * (or a quick back+forward) doesn't race two listings into the StateFlow.
     */
    private var navigatingJob: Job? = null
    private var navigationEpoch: Int = 0

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
        navigatingJob?.cancel()
        navigatingJob = null
        navigationEpoch++
        _browse.value = BrowseState()
    }

    /** H2: re-list the directory that previously errored without going up. */
    fun retry() {
        val state = _browse.value
        val server = state.server ?: return
        val url = state.failedUrl ?: state.pathStack.lastOrNull() ?: return
        navigateTo(server, state.pathStack, /* attempt = */ url)
    }

    private fun navigateTo(server: NetworkServer, pathStack: List<String>, attempt: String? = null) {
        val url = attempt ?: pathStack.last()
        navigatingJob?.cancel()
        val thisEpoch = ++navigationEpoch
        // Preserve the previous entries so the user can still see the parent
        // listing while the loader spins (H2: don't blank the screen between
        // taps). The error path returns to this preserved listing if the new
        // request fails.
        val previous = _browse.value
        _browse.value = previous.copy(
            server = server,
            pathStack = pathStack,
            isLoading = true,
            error = null,
            failedUrl = null,
        )
        navigatingJob = viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { clients[server.protocol].list(server, url) }
            }
            // If a newer navigateTo() ran while we were suspended, drop this
            // result silently — the newer job's StateFlow write wins.
            if (thisEpoch != navigationEpoch) return@launch
            _browse.value = when {
                result.isSuccess -> BrowseState(
                    server = server,
                    pathStack = pathStack,
                    entries = result.getOrDefault(emptyList()),
                    isLoading = false,
                )
                else -> {
                    // H2: keep the previous entries visible (the user can still
                    // tap something to retry the parent) and surface the failed
                    // URL so the retry button can target the exact same path.
                    BrowseState(
                        server = server,
                        pathStack = pathStack,
                        entries = previous.entries,
                        isLoading = false,
                        error = result.exceptionOrNull()?.message ?: "Connection failed",
                        failedUrl = url,
                    )
                }
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

    /**
     * M1 (features review): stream URL input previously failed silently when the
     * user typed a prefix the player can't play. The Screen now shows a snackbar
     * via [streamError]; callers should clear it on the next successful play.
     */
    private val _streamError = MutableStateFlow<String?>(null)
    val streamError: StateFlow<String?> = _streamError.asStateFlow()

    fun clearStreamError() {
        _streamError.value = null
    }

    fun addRecentStream(url: String) {
        viewModelScope.launch { repository.addRecentStream(url.trim()) }
    }

    fun playStream(url: String) {
        val trimmed = url.trim()
        val supported = listOf("http://", "https://", "rtsp://", "smb://", "ftp://", "dav://", "davs://")
        if (supported.none { trimmed.startsWith(it) }) {
            // M1: surface a clear error rather than swallowing the input. The
            // screen renders a snackbar from this StateFlow and supplies the
            // localized text from strings.xml.
            _streamError.value = UNSUPPORTED_STREAM_MARKER
            return
        }

        _streamError.value = null
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
            // M2 (features review): the port field used to silently fall back to
            // the protocol default for any value that wasn't a clean Int, so
            // "123456789" (overflow) and "abc" looked identical to the user and
            // produced a server they couldn't connect to. Now we clamp to the
            // legal range (1..65535) and surface the rejection via the same
            // streamError channel used by playStream.
            val rawPort = portText.trim().toIntOrNull()
            val port = when {
                rawPort == null -> protocol.defaultPort()
                rawPort !in 1..65535 -> {
                    _streamError.value = PORT_INVALID_MARKER
                    return@launch
                }
                else -> rawPort
            }
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

    companion object {
        /**
         * M1 / M2: opaque error markers carried through [streamError]. The
         * Composable layer maps each one to a localized strings.xml resource so
         * the ViewModel stays free of any Context dependency. Adding a new
         * marker here is a deliberate "extend the union" step.
         */
        const val UNSUPPORTED_STREAM_MARKER = "unsupported_stream"
        const val PORT_INVALID_MARKER = "port_invalid"
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
