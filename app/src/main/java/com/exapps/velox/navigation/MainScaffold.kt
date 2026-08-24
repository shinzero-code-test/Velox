package com.exapps.velox.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.exapps.velox.R
import com.exapps.velox.core.ui.theme.VeloxColors
import com.exapps.velox.core.ui.theme.VeloxSpacing
import com.exapps.velox.core.ui.theme.VeloxTheme
import com.exapps.velox.core.ui.theme.accentColor
import com.exapps.velox.core.ui.theme.glassSurfaceColor
import com.exapps.velox.feature.player.MiniPlayer

private data class BottomNavItem(val route: VeloxRoute, val icon: ImageVector, val labelRes: Int)

private val bottomNavItems = listOf(
    BottomNavItem(VeloxRoute.Library, Icons.Filled.LibraryMusic, R.string.nav_library),
    BottomNavItem(VeloxRoute.Playlists, Icons.Filled.QueueMusic, R.string.nav_playlists),
    BottomNavItem(VeloxRoute.Search, Icons.Filled.Search, R.string.nav_search),
    BottomNavItem(VeloxRoute.Settings, Icons.Filled.Settings, R.string.nav_settings),
)

@Composable
fun MainScaffold(
    currentRoute: VeloxRoute,
    onNavigate: (VeloxRoute) -> Unit,
    onExpandPlayer: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = BOTTOM_CHROME_RESERVED_HEIGHT),
        ) {
            content()
        }

        Column(modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
            MiniPlayer(
                onExpand = onExpandPlayer,
                modifier = Modifier.padding(bottom = VeloxSpacing.sm),
            )
            VeloxBottomNavBar(currentRoute = currentRoute, onNavigate = onNavigate)
        }
    }
}

@Composable
private fun VeloxBottomNavBar(currentRoute: VeloxRoute, onNavigate: (VeloxRoute) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(glassSurfaceColor(elevated = true))
            .navigationBarsPadding()
            .height(64.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        bottomNavItems.forEach { item ->
            BottomNavEntry(
                item = item,
                selected = item.route == currentRoute,
                onClick = { onNavigate(item.route) },
            )
        }
    }
}

@Composable
private fun RowScope.BottomNavEntry(item: BottomNavItem, selected: Boolean, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .weight(1f)
            .clickable(onClick = onClick)
            .padding(vertical = VeloxSpacing.xxs),
    ) {
        Icon(
            imageVector = item.icon,
            contentDescription = stringResource(item.labelRes),
            tint = if (selected) accentColor() else VeloxColors.OnSurfaceVariant,
        )
        Text(
            text = stringResource(item.labelRes),
            style = VeloxTheme.typography.labelSmall,
            color = if (selected) accentColor() else VeloxColors.OnSurfaceVariant,
        )
    }
}

/** Approximate combined height of the bottom nav bar + the mini player's own
 * reserved space, so scrollable content never sits underneath either — see the
 * SCREEN_PATTERNS.md requirement quoted in this file's header. A precise version
 * would measure this at runtime (WindowInsets + onGloballyPositioned); this fixed
 * value is a deliberate Phase 0 simplification — see PROGRESS.md. */
private val BOTTOM_CHROME_RESERVED_HEIGHT = 140.dp
