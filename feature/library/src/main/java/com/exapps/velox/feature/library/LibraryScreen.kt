package com.exapps.velox.feature.library

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lan
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.exapps.velox.core.common.util.ScreenState
import com.exapps.velox.core.domain.model.Album
import com.exapps.velox.core.domain.model.Artist
import com.exapps.velox.core.domain.model.Folder
import com.exapps.velox.core.domain.model.Genre
import com.exapps.velox.core.domain.model.LibraryGroup
import com.exapps.velox.core.domain.model.MediaItem
import com.exapps.velox.core.domain.model.SortOrder
import com.exapps.velox.core.domain.model.applicableTo
import com.exapps.velox.core.ui.components.VeloxEmptyState
import com.exapps.velox.core.ui.components.VeloxErrorRow
import com.exapps.velox.core.ui.components.VeloxFullScreenLoading
import com.exapps.velox.core.ui.components.VeloxGlassIconButton
import com.exapps.velox.core.ui.layout.DefaultWindowSizeClass
import com.exapps.velox.core.ui.layout.isCompact
import com.exapps.velox.core.ui.theme.VeloxColors
import com.exapps.velox.core.ui.theme.VeloxSpacing
import com.exapps.velox.core.ui.theme.VeloxTheme
import com.exapps.velox.core.ui.theme.accentColor
import com.exapps.velox.core.ui.theme.glassOutlineColor
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * SCREEN_HOME_LIBRARY.md. This is the screen the master prompt's Phase 0 exit
 * criteria points at: "shows a glass-themed shell, and can play a local file."
 * Every state on this screen (permission / empty / loading / error / content)
 * is real — nothing here is mocked.
 *
 * Phase 3 / Milestone 3 completion — Better tablet layouts. At medium and
 * expanded widths the Library switches to a two-pane list-detail layout:
 * the grouping list stays on the leading 1/3 of the screen and the
 * selected album / artist / folder / genre's tracks render in a
 * second pane. At compact width the screen behaves exactly as before
 * (the selected collection navigates to a `CollectionDetailScreen`
 * route).
 */
@Composable
fun LibraryScreen(
    onMediaItemClick: (MediaItem) -> Unit,
    modifier: Modifier = Modifier,
    onOpenNetworkBrowser: () -> Unit = {},
    onAlbumClick: (Album) -> Unit = {},
    onArtistClick: (Artist) -> Unit = {},
    onFolderClick: (Folder) -> Unit = {},
    onGenreClick: (Genre) -> Unit = {},
    windowSizeClass: WindowSizeClass = DefaultWindowSizeClass,
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    if (windowSizeClass.isCompact) {
        LibrarySinglePane(
            onMediaItemClick = onMediaItemClick,
            onOpenNetworkBrowser = onOpenNetworkBrowser,
            onAlbumClick = onAlbumClick,
            onArtistClick = onArtistClick,
            onFolderClick = onFolderClick,
            onGenreClick = onGenreClick,
            modifier = modifier,
            viewModel = viewModel,
        )
    } else {
        LibraryTwoPane(
            onMediaItemClick = onMediaItemClick,
            onOpenNetworkBrowser = onOpenNetworkBrowser,
            modifier = modifier,
            viewModel = viewModel,
        )
    }
}

/**
 * Compact-width variant. The four collection clicks translate to
 * navigation callbacks that the host (`VeloxNavHost`) routes to the
 * corresponding `CollectionDetailScreen` route.
 */
@Composable
private fun LibrarySinglePane(
    onMediaItemClick: (MediaItem) -> Unit,
    onOpenNetworkBrowser: () -> Unit,
    onAlbumClick: (Album) -> Unit,
    onArtistClick: (Artist) -> Unit,
    onFolderClick: (Folder) -> Unit,
    onGenreClick: (Genre) -> Unit,
    modifier: Modifier,
    viewModel: LibraryViewModel,
) {
    val content by viewModel.content.collectAsStateWithLifecycle()
    val selectedTab by viewModel.selectedTab.collectAsStateWithLifecycle()
    val isScanning by viewModel.isScanning.collectAsStateWithLifecycle()
    val sortOrder by viewModel.sortOrder.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val permissionsToRequest = rememberMediaPermissions()
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { results ->
        val mediaGranted = results
            .filterKeys { it != Manifest.permission.POST_NOTIFICATIONS }
            .values.any { it }
        viewModel.onMediaPermissionResult(mediaGranted)
    }
    val activity = context as? android.app.Activity
    val isPermanentlyDenied = remember(permissionsToRequest) {
        permissionsToRequest
            .filter { it != Manifest.permission.POST_NOTIFICATIONS }
            .any { permission ->
                ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED &&
                    activity != null && !activity.shouldShowRequestPermissionRationale(permission)
            }
    }

    LaunchedEffect(Unit) {
        val alreadyGranted = permissionsToRequest
            .filter { it != Manifest.permission.POST_NOTIFICATIONS }
            .any { permission ->
                ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
            }
        viewModel.onMediaPermissionResult(alreadyGranted)
    }

    Column(modifier = modifier.fillMaxSize()) {
        LibraryHeader(
            isScanning = isScanning,
            sortOrder = sortOrder,
            selectedTab = selectedTab,
            onSortSelected = viewModel::onSortSelected,
            onOpenNetworkBrowser = onOpenNetworkBrowser,
            onRescan = viewModel::rescan,
        )
        LibraryTabRow(selected = selectedTab, onSelect = viewModel::onTabSelected)

        when (val state = content) {
            is ScreenState.Loading -> VeloxFullScreenLoading()
            is ScreenState.PermissionRequired -> LibraryPermissionState(
                isPermanentlyDenied = isPermanentlyDenied,
                permissionsToRequest = permissionsToRequest,
                onGrant = { permissionLauncher.launch(permissionsToRequest) },
                onOpenSettings = {
                    activity?.let { act ->
                        val intent = android.content.Intent(
                            android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            android.net.Uri.fromParts("package", act.packageName, null),
                        ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                        act.startActivity(intent)
                    }
                },
            )
            is ScreenState.Empty -> VeloxEmptyState(
                icon = Icons.Filled.LibraryMusic,
                title = stringResource(R.string.library_empty_title),
                body = stringResource(R.string.library_empty_body),
                primaryActionLabel = stringResource(R.string.library_empty_action),
                onPrimaryAction = viewModel::rescan,
            )
            is ScreenState.Error -> VeloxErrorRow(
                message = state.message ?: stringResource(R.string.library_error_body),
                retryLabel = stringResource(R.string.library_error_retry),
                onRetry = viewModel::rescan,
            )
            is ScreenState.Content -> Column(modifier = Modifier.fillMaxSize()) {
                // Phase 3 / Wave 3 / Round 3 — Milestone 7.
                // "Recommended" row above the active tab. The row
                // renders itself only when non-empty.
                val recommended by viewModel.recommended.collectAsStateWithLifecycle()
                RecommendedRow(
                    items = recommended.items,
                    onTrackClick = { track ->
                        // Play the recommended track with the
                        // recommendations list as the queue.
                        viewModel.onTrackClick(track, recommended.items)
                        onMediaItemClick(track)
                    },
                )
                LibraryContentView(
                    content = state.data,
                    onTrackClick = { track, queue ->
                        viewModel.onTrackClick(track, queue)
                        onMediaItemClick(track)
                    },
                    onToggleFavorite = viewModel::onToggleFavorite,
                    onCollectionSelected = { key ->
                        when (key) {
                            is CollectionKey.AlbumKey -> onAlbumClick(
                                Album(
                                    id = key.albumId,
                                    title = key.title,
                                    artistName = null,
                                    artworkUri = null,
                                    trackCount = 0,
                                ),
                            )
                            is CollectionKey.ArtistKey -> onArtistClick(
                                Artist(
                                    id = key.artistId,
                                    name = key.title,
                                    trackCount = 0,
                                    albumCount = 0,
                                ),
                            )
                            is CollectionKey.FolderKey -> onFolderClick(
                                Folder(
                                    path = key.folderPath,
                                    displayName = key.title,
                                    itemCount = 0,
                                    parentPath = null,
                                ),
                            )
                            is CollectionKey.GenreKey -> onGenreClick(
                                Genre(name = key.genre, trackCount = 0),
                            )
                        }
                    },
                )
            }
        }
    }
}

/**
 * Two-pane variant (medium/expanded widths). The grouping list lives
 * on the leading half of the screen; the selected collection's tracks
 * render in the trailing half. A 1-px glass divider sits between them.
 *
 * Selection is held in `rememberSaveable` so a foldable hinge (or a
 * rotation) doesn't lose the user's pick. Switching tabs doesn't
 * reset the selection; the detail pane hides itself if the previous
 * selection's type no longer matches the new tab's content (e.g. an
 * AlbumKey in the Artists tab).
 */
@Composable
private fun LibraryTwoPane(
    onMediaItemClick: (MediaItem) -> Unit,
    onOpenNetworkBrowser: () -> Unit,
    modifier: Modifier,
    viewModel: LibraryViewModel,
) {
    val content by viewModel.content.collectAsStateWithLifecycle()
    val selectedTab by viewModel.selectedTab.collectAsStateWithLifecycle()
    val isScanning by viewModel.isScanning.collectAsStateWithLifecycle()
    val sortOrder by viewModel.sortOrder.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val permissionsToRequest = rememberMediaPermissions()
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { results ->
        val mediaGranted = results
            .filterKeys { it != Manifest.permission.POST_NOTIFICATIONS }
            .values.any { it }
        viewModel.onMediaPermissionResult(mediaGranted)
    }
    val activity = context as? android.app.Activity
    val isPermanentlyDenied = remember(permissionsToRequest) {
        permissionsToRequest
            .filter { it != Manifest.permission.POST_NOTIFICATIONS }
            .any { permission ->
                ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED &&
                    activity != null && !activity.shouldShowRequestPermissionRationale(permission)
            }
    }

    LaunchedEffect(Unit) {
        val alreadyGranted = permissionsToRequest
            .filter { it != Manifest.permission.POST_NOTIFICATIONS }
            .any { permission ->
                ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
            }
        viewModel.onMediaPermissionResult(alreadyGranted)
    }

    var selectedKey by rememberSaveable(stateSaver = CollectionKeySaver) {
        mutableStateOf<CollectionKey?>(null)
    }

    Column(modifier = modifier.fillMaxSize()) {
        LibraryHeader(
            isScanning = isScanning,
            sortOrder = sortOrder,
            selectedTab = selectedTab,
            onSortSelected = viewModel::onSortSelected,
            onOpenNetworkBrowser = onOpenNetworkBrowser,
            onRescan = viewModel::rescan,
        )
        LibraryTabRow(selected = selectedTab, onSelect = viewModel::onTabSelected)

        when (val state = content) {
            is ScreenState.Loading -> VeloxFullScreenLoading()
            is ScreenState.PermissionRequired -> LibraryPermissionState(
                isPermanentlyDenied = isPermanentlyDenied,
                permissionsToRequest = permissionsToRequest,
                onGrant = { permissionLauncher.launch(permissionsToRequest) },
                onOpenSettings = {
                    activity?.let { act ->
                        val intent = android.content.Intent(
                            android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            android.net.Uri.fromParts("package", act.packageName, null),
                        ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                        act.startActivity(intent)
                    }
                },
            )
            is ScreenState.Empty -> VeloxEmptyState(
                icon = Icons.Filled.LibraryMusic,
                title = stringResource(R.string.library_empty_title),
                body = stringResource(R.string.library_empty_body),
                primaryActionLabel = stringResource(R.string.library_empty_action),
                onPrimaryAction = viewModel::rescan,
            )
            is ScreenState.Error -> VeloxErrorRow(
                message = state.message ?: stringResource(R.string.library_error_body),
                retryLabel = stringResource(R.string.library_error_retry),
                onRetry = viewModel::rescan,
            )
            is ScreenState.Content -> Column(modifier = Modifier.fillMaxSize()) {
                // Phase 3 / Wave 3 / Round 3 — "Recommended" row
                // spans the full width above the panes.
                val recommended by viewModel.recommended.collectAsStateWithLifecycle()
                RecommendedRow(
                    items = recommended.items,
                    onTrackClick = { track ->
                        viewModel.onTrackClick(track, recommended.items)
                        onMediaItemClick(track)
                    },
                )
                Row(modifier = Modifier.fillMaxSize()) {
                    Box(modifier = Modifier.fillMaxHeight().weight(1f)) {
                        LibraryContentView(
                            content = state.data,
                            onTrackClick = { track, queue ->
                                viewModel.onTrackClick(track, queue)
                                onMediaItemClick(track)
                            },
                            onToggleFavorite = viewModel::onToggleFavorite,
                            onCollectionSelected = { key -> selectedKey = key },
                        )
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(1.dp)
                            .background(glassOutlineColor()),
                    )
                    Box(modifier = Modifier.fillMaxHeight().weight(1.4f)) {
                    val key = selectedKey
                    if (key == null) {
                        TwoPaneEmptyHint()
                    } else {
                        val typeMatches = when (key) {
                            is CollectionKey.AlbumKey -> state.data is LibraryContent.Albums
                            is CollectionKey.ArtistKey -> state.data is LibraryContent.Artists
                            is CollectionKey.FolderKey -> state.data is LibraryContent.Folders
                            is CollectionKey.GenreKey -> state.data is LibraryContent.Genres
                        }
                        if (typeMatches) {
                            CollectionPaneContent(
                                key = key,
                                viewModel = viewModel,
                                onMediaItemClick = onMediaItemClick,
                            )
                        } else {
                            TwoPaneEmptyHint()
                        }
                    }
                }
            }
        }
    }
}

/**
 * The detail pane's content for the currently-selected [key]. Owns
 * its own state-in flow so a new key cancels the previous one cleanly.
 */
@Composable
private fun CollectionPaneContent(
    key: CollectionKey,
    viewModel: LibraryViewModel,
    onMediaItemClick: (MediaItem) -> Unit,
) {
    val coroutineScope = rememberCoroutineScope()
    val tracksState = remember(key) {
        viewModel.tracksFor(key)
            .map<List<MediaItem>, ScreenState<List<MediaItem>>> { items ->
                if (items.isEmpty()) ScreenState.Empty else ScreenState.Content(items)
            }
            .stateIn(
                scope = coroutineScope,
                started = SharingStarted.Eagerly,
                initialValue = ScreenState.Loading,
            )
    }
    CollectionDetailContent(
        title = key.title,
        tracks = tracksState,
        onTrackClick = { track ->
            viewModel.onCollectionTrackClick(key, track)
            onMediaItemClick(track)
        },
        onToggleFavorite = viewModel::onToggleFavorite,
    )
}

@Composable
private fun LibraryPermissionState(
    isPermanentlyDenied: Boolean,
    permissionsToRequest: Array<String>,
    onGrant: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    VeloxEmptyState(
        icon = Icons.Filled.LockOpen,
        title = stringResource(R.string.library_permission_title),
        body = if (isPermanentlyDenied) {
            stringResource(R.string.library_permission_body_permanently_denied)
        } else {
            stringResource(R.string.library_permission_body)
        },
        primaryActionLabel = if (isPermanentlyDenied) {
            stringResource(R.string.library_permission_open_settings)
        } else {
            stringResource(R.string.library_permission_action)
        },
        onPrimaryAction = if (isPermanentlyDenied) onOpenSettings else onGrant,
    )
}

@Composable
private fun LibraryHeader(
    isScanning: Boolean,
    sortOrder: SortOrder,
    selectedTab: LibraryGroup,
    onSortSelected: (SortOrder) -> Unit,
    onOpenNetworkBrowser: () -> Unit,
    onRescan: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = VeloxSpacing.lg, vertical = VeloxSpacing.sm),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.library_title),
            style = VeloxTheme.typography.headlineLarge,
            color = VeloxColors.OnBackground,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(VeloxSpacing.xs)) {
            VeloxGlassIconButton(
                icon = Icons.Filled.Lan,
                contentDescription = stringResource(R.string.library_open_network),
                onClick = onOpenNetworkBrowser,
            )
            SortMenuButton(
                sortOrder = sortOrder,
                group = selectedTab,
                onSortSelected = onSortSelected,
            )
            if (!isScanning) {
                VeloxGlassIconButton(
                    icon = Icons.Filled.Refresh,
                    contentDescription = stringResource(R.string.library_rescan),
                    onClick = onRescan,
                )
            }
        }
    }
}

@Composable
private fun TwoPaneEmptyHint() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = stringResource(R.string.library_two_pane_hint),
            style = VeloxTheme.typography.bodyLarge,
            color = VeloxColors.OnSurfaceVariant,
        )
    }
}

@Composable
private fun LibraryTabRow(
    selected: LibraryGroup,
    onSelect: (LibraryGroup) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tabs = listOf(
        LibraryGroup.TRACKS to R.string.library_tab_tracks,
        LibraryGroup.ALBUMS to R.string.library_tab_albums,
        LibraryGroup.ARTISTS to R.string.library_tab_artists,
        LibraryGroup.FOLDERS to R.string.library_tab_folders,
        LibraryGroup.GENRES to R.string.library_tab_genres,
        LibraryGroup.VIDEOS to R.string.library_tab_videos,
    )
    androidx.compose.foundation.lazy.LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            horizontal = VeloxSpacing.lg,
            vertical = VeloxSpacing.xs,
        ),
        horizontalArrangement = Arrangement.spacedBy(VeloxSpacing.xs),
    ) {
        items(tabs.size) { index ->
            val (group, labelRes) = tabs[index]
            LibraryTabChip(
                label = stringResource(labelRes),
                selected = group == selected,
                onClick = { onSelect(group) },
            )
        }
    }
}

@Composable
private fun SortMenuButton(
    sortOrder: SortOrder,
    group: LibraryGroup,
    onSortSelected: (SortOrder) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    Column(modifier = modifier) {
        VeloxGlassIconButton(
            icon = Icons.Filled.Sort,
            contentDescription = stringResource(R.string.library_sort),
            onClick = { expanded = true },
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            SortOrder.entries
                .filter { it.applicableTo(group) }
                .forEach { order ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = sortLabel(order),
                                color = if (order == sortOrder) accentColor() else VeloxColors.OnSurface,
                            )
                        },
                        onClick = {
                            onSortSelected(order)
                            expanded = false
                        },
                    )
                }
        }
    }
}

@Composable
private fun sortLabel(order: SortOrder): String = stringResource(
    when (order) {
        SortOrder.TITLE -> R.string.library_sort_title
        SortOrder.DATE_ADDED -> R.string.library_sort_date_added
        SortOrder.DURATION -> R.string.library_sort_duration
        SortOrder.SIZE -> R.string.library_sort_size
        SortOrder.PATH -> R.string.library_sort_path
    },
)

/**
 * Compose Saver for [CollectionKey] so the two-pane selection survives
 * configuration changes. Stored as a single string
 * ("{kind}|{arg1}|{arg2}|{title}") — small, fast, and human-debuggable
 * if it ever ends up in a SavedStateHandle dump.
 */
private val CollectionKeySaver: Saver<CollectionKey?, String> = Saver(
    save = { key ->
        when (key) {
            null -> ""
            is CollectionKey.AlbumKey -> "album|${key.albumId}|${key.title}"
            is CollectionKey.ArtistKey -> "artist|${key.artistId}|${key.title}"
            is CollectionKey.FolderKey -> "folder|${key.folderPath}|${key.title}"
            is CollectionKey.GenreKey -> "genre|${key.genre}|${key.title}"
        }
    },
    restore = { raw ->
        if (raw.isEmpty()) {
            null
        } else {
            val parts = raw.split("|", limit = 3)
            when (parts.getOrNull(0)) {
                "album" -> CollectionKey.AlbumKey(
                    albumId = parts.getOrNull(1)?.toLongOrNull() ?: 0L,
                    title = parts.getOrNull(2).orEmpty(),
                )
                "artist" -> CollectionKey.ArtistKey(
                    artistId = parts.getOrNull(1)?.toLongOrNull() ?: 0L,
                    title = parts.getOrNull(2).orEmpty(),
                )
                "folder" -> CollectionKey.FolderKey(
                    folderPath = parts.getOrNull(1).orEmpty(),
                    title = parts.getOrNull(2).orEmpty(),
                )
                "genre" -> CollectionKey.GenreKey(
                    genre = parts.getOrNull(1).orEmpty(),
                    title = parts.getOrNull(2).orEmpty(),
                )
                else -> null
            }
        }
    },
)

/**
 * Phase 0 / v1.0 — the runtime media permissions to request before
 * the first scan. READ_MEDIA_AUDIO + READ_MEDIA_VIDEO on
 * Android 13+; READ_EXTERNAL_STORAGE on earlier releases. POST_NOTIFICATIONS
 * is requested alongside so re-prompts can also recover the
 * playback notification (denial here must never block media access).
 *
 * READ_MEDIA_VISUAL_USER_SELECTED is the API 34+ "partial"
 * photo/video access permission. We don't request it explicitly
 * — Velox wants full library access (a media player that only
 * sees a few user-selected videos would be useless). The system
 * Settings shortcut on the permission-denied state covers that
 * path.
 */
@Composable
private fun rememberMediaPermissions(): Array<String> = remember {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        arrayOf(
            Manifest.permission.READ_MEDIA_AUDIO,
            Manifest.permission.READ_MEDIA_VIDEO,
            Manifest.permission.POST_NOTIFICATIONS,
        )
    } else {
        arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }
}
