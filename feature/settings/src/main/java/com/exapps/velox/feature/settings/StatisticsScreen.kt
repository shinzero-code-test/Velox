package com.exapps.velox.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.exapps.velox.core.data.local.dao.DayPlayCount
import com.exapps.velox.core.data.local.dao.StatsDao
import com.exapps.velox.core.data.local.dao.TrackPlayAggregate
import com.exapps.velox.core.common.util.formatDuration
import com.exapps.velox.core.ui.components.GlassCard
import com.exapps.velox.core.ui.components.VeloxEmptyState
import com.exapps.velox.core.ui.theme.VeloxColors
import com.exapps.velox.core.ui.theme.VeloxSpacing
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class StatisticsViewModel @Inject constructor(statsDao: StatsDao) : ViewModel() {

    val totals = statsDao.observeTotals()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val topTracks = statsDao.observeMostPlayedTracks(10)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * M5 (features review): bucket by local-day, not UTC-day, so the
     * Statistics screen's "today" matches the user's calendar.
     */
    val dailyPlays = statsDao.observeDailyPlays(
        days = 30,
        offsetSeconds = java.util.TimeZone.getDefault().getOffset(System.currentTimeMillis()) / 1000L,
    ).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}

/** Phase 2 "Playback statistics & history": totals, 30-day activity, top tracks. */
@Composable
fun StatisticsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: StatisticsViewModel = hiltViewModel(),
) {
    val totals by viewModel.totals.collectAsStateWithLifecycle()
    val topTracks by viewModel.topTracks.collectAsStateWithLifecycle()
    val daily by viewModel.dailyPlays.collectAsStateWithLifecycle()

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = VeloxSpacing.lg, vertical = VeloxSpacing.sm),
        ) {
            androidx.compose.material3.IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(com.exapps.velox.feature.settings.R.string.cd_back))
            }
            Text(
                text = stringResource(R.string.statistics_title),
                style = com.exapps.velox.core.ui.theme.VeloxTheme.typography.headlineMedium,
                color = VeloxColors.OnBackground,
            )
        }

        // M7 (features review): the totals flow seeds with `null` (loading)
        // before the first DB emission. The old `(totals?.plays ?: 0) == 0`
        // check would flash the empty-state card for one frame on every cold
        // open. Distinguish "loading" (totals == null) from "loaded with no
        // plays" (totals != null && totals.plays == 0) and show a spinner in
        // the former case.
        if (totals == null) {
            androidx.compose.foundation.layout.Box(
                Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                androidx.compose.material3.CircularProgressIndicator()
            }
            return
        }
        if ((totals?.plays ?: 0) == 0) {
            VeloxEmptyState(
                icon = Icons.Filled.Info,
                title = stringResource(R.string.stats_empty_title),
                body = stringResource(R.string.stats_empty_body),
            )
            return
        }

        LazyColumn(
            contentPadding = PaddingValues(VeloxSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(VeloxSpacing.md),
        ) {
            item {
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(VeloxSpacing.lg), verticalArrangement = Arrangement.spacedBy(VeloxSpacing.xs)) {
                        StatRow(stringResource(R.string.stats_total_plays), "${totals?.plays ?: 0}")
                        StatRow(stringResource(R.string.stats_distinct_tracks), "${totals?.distinctTracks ?: 0}")
                        StatRow(stringResource(R.string.stats_listening_time), formatDuration(totals?.totalMs ?: 0L))
                    }
                }
            }
            if (daily.isNotEmpty()) {
                item {
                    // M6 (features review): the header used to read "Last N days"
                    // with daily.size (≤30) but the card only rendered 7. Either
                    // render everything (the limit is now 7 below) or pass the
                    // rendered-row count to the header. Choose the latter so the
                    // header always matches what the user sees.
                    val shownCount = minOf(daily.size, 7)
                    Text(
                        stringResource(R.string.stats_last_days, shownCount),
                        style = com.exapps.velox.core.ui.theme.VeloxTheme.typography.labelLarge,
                        color = VeloxColors.OnSurfaceVariant,
                    )
                    Spacer(Modifier.height(VeloxSpacing.xs))
                    GlassCard(modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(VeloxSpacing.md)) {
                            daily.take(7).forEach { day ->
                                DayRow(day)
                            }
                        }
                    }
                }
            }
            item {
                Text(stringResource(R.string.stats_top_tracks), style = com.exapps.velox.core.ui.theme.VeloxTheme.typography.labelLarge, color = VeloxColors.OnSurfaceVariant)
            }
            items(topTracks, key = { it.mediaItemId }) { track ->
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(VeloxSpacing.md)) {
                        Column(Modifier.weight(1f)) {
                            Text(track.title, style = com.exapps.velox.core.ui.theme.VeloxTheme.typography.titleMedium, color = VeloxColors.OnSurface, maxLines = 1)
                            Text(track.artistName.orEmpty(), style = com.exapps.velox.core.ui.theme.VeloxTheme.typography.bodyMedium, color = VeloxColors.OnSurfaceVariant, maxLines = 1)
                        }
                        Text("×${track.plays}", style = com.exapps.velox.core.ui.theme.VeloxTheme.typography.labelLarge, color = com.exapps.velox.core.ui.theme.accentColor())
                    }
                }
            }
        }
    }
}

@Composable
private fun DayRow(day: DayPlayCount) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(java.text.DateFormat.getDateInstance(java.text.DateFormat.SHORT).format(java.util.Date(day.dayEpochSeconds * 1000)), style = com.exapps.velox.core.ui.theme.VeloxTheme.typography.bodyMedium, color = VeloxColors.OnSurfaceVariant)
        Text("${day.plays}", style = com.exapps.velox.core.ui.theme.VeloxTheme.typography.bodyMedium, color = VeloxColors.OnSurface)
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = com.exapps.velox.core.ui.theme.VeloxTheme.typography.bodyLarge, color = VeloxColors.OnSurfaceVariant)
        Text(value, style = com.exapps.velox.core.ui.theme.VeloxTheme.typography.bodyLarge, color = VeloxColors.OnSurface)
    }
}
