package com.exapps.velox.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.video.VideoFrameDecoder
import com.exapps.velox.core.common.util.formatDuration
import com.exapps.velox.core.domain.model.Album
import com.exapps.velox.core.domain.model.Artist
import com.exapps.velox.core.domain.model.Folder
import com.exapps.velox.core.domain.model.MediaType
import com.exapps.velox.core.domain.model.Genre
import com.exapps.velox.core.domain.model.MediaItem
import com.exapps.velox.core.ui.components.ClickableGlassCard
import com.exapps.velox.core.ui.theme.VeloxColors
import com.exapps.velox.core.ui.theme.VeloxShapes
import com.exapps.velox.core.ui.theme.VeloxSpacing
import com.exapps.velox.core.ui.theme.VeloxTheme
import com.exapps.velox.core.ui.theme.accentColor
import com.exapps.velox.core.ui.theme.glassSurfaceColor

/** Phase 1.1 "Improved artwork & thumbnail pipeline": video rows decode a frame
 * from the media itself via coil-video; audio keeps the album-art URI. */
@Composable
private fun trackArtworkModel(track: MediaItem): Any = if (track.mediaType == MediaType.VIDEO) {
    ImageRequest.Builder(LocalContext.current)
        .data(track.uri)
        .decoderFactory(VideoFrameDecoder.Factory())
        .build()
} else {
    track.artworkUri ?: track.uri
}

@Composable
fun LibraryTabChip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    // A11y (features review): announce the selected state explicitly so
    // TalkBack says "Songs, selected" / "Songs, not selected" rather
    // than just reading the label.
    val stateDesc = androidx.compose.ui.res.stringResource(
        if (selected) R.string.cd_tab_selected else R.string.cd_tab_not_selected,
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(VeloxShapes.full)
            .background(if (selected) accentColor() else glassSurfaceColor())
            .clickable(onClick = onClick)
            .semantics {
                role = androidx.compose.ui.semantics.Role.Tab
                this.stateDescription = stateDesc
            }
            .padding(horizontal = VeloxSpacing.md, vertical = VeloxSpacing.xs),
    ) {
        Text(
            text = label,
            style = VeloxTheme.typography.labelLarge,
            color = if (selected) VeloxColors.currentBackground else VeloxColors.OnSurface,
        )
    }
}

@Composable
fun LibraryContentView(
    content: LibraryContent,
    onTrackClick: (MediaItem, List<MediaItem>) -> Unit,
    onToggleFavorite: (MediaItem) -> Unit,
    onAlbumClick: (Album) -> Unit,
    onArtistClick: (Artist) -> Unit,
    onFolderClick: (Folder) -> Unit,
    onGenreClick: (Genre) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (content) {
        is LibraryContent.Tracks -> TrackList(content.items, onTrackClick, onToggleFavorite, modifier)
        is LibraryContent.Videos -> TrackList(content.items, onTrackClick, onToggleFavorite, modifier)
        is LibraryContent.Albums -> AlbumGrid(content.items, onAlbumClick, modifier)
        is LibraryContent.Artists -> ArtistList(content.items, onArtistClick, modifier)
        is LibraryContent.Folders -> FolderList(content.items, onFolderClick, modifier)
        is LibraryContent.Genres -> GenreList(content.items, onGenreClick, modifier)
    }
}

@Composable
private fun TrackList(
    tracks: List<MediaItem>,
    onTrackClick: (MediaItem, List<MediaItem>) -> Unit,
    onToggleFavorite: (MediaItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = VeloxSpacing.lg, vertical = VeloxSpacing.xs),
        verticalArrangement = Arrangement.spacedBy(VeloxSpacing.xs),
    ) {
        // M6 (features review): keys include the list index as a
        // tie-breaker so a rescan race that surfaces the same id twice
        // doesn't crash Compose with a duplicate-key exception.
        itemsIndexed(tracks, key = { index, item -> "${item.id}-$index" }) { _, track ->
            TrackRow(
                track = track,
                onClick = { onTrackClick(track, tracks) },
                onFavoriteClick = { onToggleFavorite(track) },
            )
        }
    }
}

@Composable
private fun TrackRow(
    track: MediaItem,
    onClick: () -> Unit,
    onFavoriteClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ClickableGlassCard(onClick = onClick, modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(VeloxSpacing.md)) {
            AsyncImage(
                model = trackArtworkModel(track),
                contentDescription = null,
                modifier = Modifier
                    .size(48.dp)
                    .clip(VeloxShapes.sm),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.title,
                    style = VeloxTheme.typography.titleMedium,
                    color = VeloxColors.OnSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stringResource(
                        R.string.library_track_subtitle,
                        track.artistName ?: "—",
                        formatDuration(track.durationMs),
                    ),
                    style = VeloxTheme.typography.bodyMedium,
                    color = VeloxColors.OnSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            // 40dp clickable box around the icon — the icon glyph itself is smaller,
            // but the tap target meets the accessibility minimum (SCREEN_PATTERNS.md §11).
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onFavoriteClick),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (track.isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    contentDescription = stringResource(
                        if (track.isFavorite) R.string.cd_favorite_remove else R.string.cd_favorite_add,
                    ),
                    tint = if (track.isFavorite) accentColor() else VeloxColors.OnSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun AlbumGrid(albums: List<Album>, onAlbumClick: (Album) -> Unit, modifier: Modifier = Modifier) {
    LazyVerticalGrid(
        // Phase 2 large screens: 2 columns on phones, up to 4 on expanded widths.
        columns = GridCells.Adaptive(minSize = 160.dp),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(VeloxSpacing.lg),
        horizontalArrangement = Arrangement.spacedBy(VeloxSpacing.sm),
        verticalArrangement = Arrangement.spacedBy(VeloxSpacing.sm),
    ) {
        // Qualified `lazy.grid.itemsIndexed` because both `lazy.itemsIndexed`
        // and `lazy.grid.itemsIndexed` are imported in this file.
        androidx.compose.foundation.lazy.grid.itemsIndexed(
            items = albums,
            key = { index, item: Album -> "${item.id}-$index" },
        ) { _, album ->
            // A11y (features review): announce as a Button with an
            // onClickLabel so TalkBack says "Open album <title>" instead
            // of just reading the cell text.
            val openLabel = androidx.compose.ui.res.stringResource(
                com.exapps.velox.feature.library.R.string.cd_open_album,
                album.title,
            )
            Column(
                modifier = Modifier
                    .clickable(onClickLabel = openLabel, onClick = { onAlbumClick(album) })
                    .semantics(mergeDescendants = true) {
                        role = androidx.compose.ui.semantics.Role.Button
                    },
            ) {
                AsyncImage(
                    model = album.artworkUri,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(VeloxShapes.md)
                        .background(glassSurfaceColor()),
                )
                Text(
                    text = album.title,
                    style = VeloxTheme.typography.titleMedium,
                    color = VeloxColors.OnSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = VeloxSpacing.xs),
                )
                album.artistName?.let {
                    Text(
                        text = it,
                        style = VeloxTheme.typography.bodyMedium,
                        color = VeloxColors.OnSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun ArtistList(artists: List<Artist>, onArtistClick: (Artist) -> Unit, modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = VeloxSpacing.lg, vertical = VeloxSpacing.xs),
        verticalArrangement = Arrangement.spacedBy(VeloxSpacing.xs),
    ) {
        itemsIndexed(artists, key = { index, item -> "${item.id}-$index" }) { _, artist ->
            // A11y: see note on AlbumCell.
            val openLabel = androidx.compose.ui.res.stringResource(R.string.cd_open_artist, artist.name)
            ClickableGlassCard(
                onClick = { onArtistClick(artist) },
                onClickLabel = openLabel,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(VeloxSpacing.md)) {
                    Icon(Icons.Filled.Person, contentDescription = null, tint = accentColor())
                    Column {
                        Text(artist.name, style = VeloxTheme.typography.titleMedium, color = VeloxColors.OnSurface)
                        Text("${artist.trackCount}", style = VeloxTheme.typography.bodyMedium, color = VeloxColors.OnSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun FolderList(folders: List<Folder>, onFolderClick: (Folder) -> Unit, modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = VeloxSpacing.lg, vertical = VeloxSpacing.xs),
        verticalArrangement = Arrangement.spacedBy(VeloxSpacing.xs),
    ) {
        itemsIndexed(folders, key = { index, item -> "${item.path}-$index" }) { _, folder ->
            val openLabel = androidx.compose.ui.res.stringResource(R.string.cd_open_folder, folder.displayName)
            ClickableGlassCard(
                onClick = { onFolderClick(folder) },
                onClickLabel = openLabel,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(VeloxSpacing.md)) {
                    Icon(Icons.Filled.Folder, contentDescription = null, tint = accentColor())
                    Text(folder.displayName, style = VeloxTheme.typography.titleMedium, color = VeloxColors.OnSurface)
                }
            }
        }
    }
}


@Composable
private fun GenreList(genres: List<Genre>, onGenreClick: (Genre) -> Unit, modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = VeloxSpacing.lg, vertical = VeloxSpacing.xs),
        verticalArrangement = Arrangement.spacedBy(VeloxSpacing.xs),
    ) {
        itemsIndexed(genres, key = { index, item -> "${item.name}-$index" }) { _, genre ->
            val openLabel = androidx.compose.ui.res.stringResource(R.string.cd_open_genre, genre.name)
            ClickableGlassCard(
                onClick = { onGenreClick(genre) },
                onClickLabel = openLabel,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(VeloxSpacing.md)) {
                    Icon(Icons.Filled.LibraryMusic, contentDescription = null, tint = accentColor())
                    Column {
                        Text(genre.name, style = VeloxTheme.typography.titleMedium, color = VeloxColors.OnSurface)
                        Text("${genre.trackCount}", style = VeloxTheme.typography.bodyMedium, color = VeloxColors.OnSurfaceVariant)
                    }
                }
            }
        }
    }
}
