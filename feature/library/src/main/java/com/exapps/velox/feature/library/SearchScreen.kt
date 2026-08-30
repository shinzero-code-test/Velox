package com.exapps.velox.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.exapps.velox.core.common.util.formatDuration
import com.exapps.velox.core.ui.components.ClickableGlassCard
import com.exapps.velox.core.ui.components.VeloxEmptyState
import com.exapps.velox.core.ui.theme.VeloxColors
import com.exapps.velox.core.ui.theme.VeloxShapes
import com.exapps.velox.core.ui.theme.VeloxSpacing
import com.exapps.velox.core.ui.theme.VeloxTheme
import com.exapps.velox.core.ui.theme.accentColor
import com.exapps.velox.core.ui.theme.glassSurfaceColor
import com.exapps.velox.core.domain.model.MediaItem
import com.exapps.velox.core.domain.model.MediaType

@Composable
fun SearchScreen(
    onResultClick: (MediaItem) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val query by viewModel.query.collectAsStateWithLifecycle()
    val results by viewModel.results.collectAsStateWithLifecycle()
    // Search UX (features review): hasQueried is true only after the
    // 250ms debounce + a non-blank query has run. We use it to suppress
    // the "no results" empty state during the debounce window so the
    // prompt doesn't flash.
    val hasQueried by viewModel.hasQueried.collectAsStateWithLifecycle()
    // Phase 3 / Wave 3 / Round 3.5 — "Because you listened to X"
    // surface. Non-null when the search has exactly one result and
    // the engine has at least one neighbour for it.
    val becauseYouListened by viewModel.becauseYouListened.collectAsStateWithLifecycle()

    Column(modifier = modifier.fillMaxSize().padding(horizontal = VeloxSpacing.lg)) {
        Text(
            text = stringResource(R.string.search_title),
            style = VeloxTheme.typography.headlineLarge,
            color = VeloxColors.OnBackground,
            modifier = Modifier.padding(vertical = VeloxSpacing.sm),
        )

        TextField(
            value = query,
            onValueChange = viewModel::onQueryChange,
            placeholder = { Text(stringResource(R.string.search_hint), color = VeloxColors.OnSurfaceVariant) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = VeloxColors.OnSurfaceVariant) },
            // Search UX: clear-text trailing icon, hidden when the
            // field is empty so we don't waste horizontal space.
            trailingIcon = if (query.isNotEmpty()) {
                {
                    IconButton(onClick = viewModel::onClearQuery) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.search_clear), tint = VeloxColors.OnSurfaceVariant)
                    }
                }
            } else null,
            singleLine = true,
            shape = VeloxShapes.full,
            colors = TextFieldDefaults.colors(
                focusedContainerColor = glassSurfaceColor(elevated = true),
                unfocusedContainerColor = glassSurfaceColor(),
                focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                cursorColor = accentColor(),
            ),
            modifier = Modifier
                .fillMaxWidth()
                .clip(VeloxShapes.full),
        )

        when {
            query.isBlank() -> VeloxEmptyState(
                icon = Icons.Filled.Search,
                title = stringResource(R.string.search_prompt_title),
                body = stringResource(R.string.search_prompt_body),
            )
            // Only show the "no results" empty state once a query has
            // actually been looked up; during the debounce window we
            // show nothing rather than a flash of "no results".
            !hasQueried -> Unit
            results.isEmpty() -> VeloxEmptyState(
                icon = Icons.Filled.SearchOff,
                title = stringResource(R.string.search_empty_title),
                body = stringResource(R.string.search_empty_body),
            )
            else -> LazyColumn(
                contentPadding = PaddingValues(vertical = VeloxSpacing.sm),
                verticalArrangement = Arrangement.spacedBy(VeloxSpacing.xs),
            ) {
                // Phase 3 / Wave 3 / Round 3.5 — "Because you
                // listened to X" surface, only when the engine has
                // at least one neighbour for the search's only
                // result. Sits at the top of the result list with a
                // small header.
                becauseYouListened?.let { rec ->
                    if (rec.items.isNotEmpty()) {
                        item(key = "becauseYouListened") {
                            Text(
                                text = stringResource(R.string.search_because_you_listened),
                                style = VeloxTheme.typography.labelLarge,
                                color = VeloxColors.OnSurfaceVariant,
                                modifier = Modifier.padding(bottom = VeloxSpacing.xs),
                            )
                        }
                    }
                }
                itemsIndexed(results, key = { index, item -> "${item.id}-$index" }) { _, item ->
                    ClickableGlassCard(
                        onClick = { viewModel.onResultClick(item); onResultClick(item) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(VeloxSpacing.md)) {
                            Column {
                                Text(item.title, style = VeloxTheme.typography.titleMedium, color = VeloxColors.OnSurface)
                                Text(
                                    text = "${item.artistName ?: item.albumTitle ?: ""} • ${formatDuration(item.durationMs)}",
                                    style = VeloxTheme.typography.bodyMedium,
                                    color = VeloxColors.OnSurfaceVariant,
                                )
                            }
                            if (item.mediaType == MediaType.VIDEO) {
                                Icon(
                                    Icons.Filled.PlayCircle,
                                    contentDescription = stringResource(R.string.cd_video_result),
                                    tint = accentColor(),
                                )
                            }
                        }
                    }
                }
                // The recommendation rows follow the same result rows.
                becauseYouListened?.let { rec ->
                    itemsIndexed(rec.items, key = { index, item -> "by-${item.id}-$index" }) { _, item ->
                        ClickableGlassCard(
                            onClick = { viewModel.onRecommendationClick(item) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(VeloxSpacing.md)) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = item.title,
                                        style = VeloxTheme.typography.titleMedium,
                                        color = VeloxColors.OnSurface,
                                        maxLines = 1,
                                    )
                                    Text(
                                        text = item.artistName ?: "",
                                        style = VeloxTheme.typography.bodyMedium,
                                        color = VeloxColors.OnSurfaceVariant,
                                        maxLines = 1,
                                    )
                                }
                                Icon(
                                    Icons.Filled.PlayCircle,
                                    contentDescription = stringResource(R.string.cd_video_result),
                                    tint = accentColor(),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
