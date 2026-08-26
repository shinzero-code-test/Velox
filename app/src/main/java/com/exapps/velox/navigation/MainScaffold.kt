package com.exapps.velox.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.exapps.velox.R
import com.exapps.velox.core.ui.theme.VeloxColors
import com.exapps.velox.core.ui.theme.VeloxShapes
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
    // M2 (app-shell review): measure the real chrome column (mini player + nav bar
    // + insets) instead of reserving a fixed 140dp — the old constant floated
    // content above the bar when the mini player was hidden and let it scroll
    // under the bar when visible.
    var chromeHeightPx by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    val chromePadding = with(androidx.compose.ui.platform.LocalDensity.current) {
        chromeHeightPx.toDp()
    }

    Box(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = chromePadding),
        ) {
            content()
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .onGloballyPositioned { chromeHeightPx = it.size.height },
        ) {
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
            .heightIn(min = 48.dp)
            // L3 (app-shell review): visible selected affordance + a11y-min height.
            .clip(VeloxShapes.md)
            .background(if (selected) glassSurfaceColor(elevated = true) else androidx.compose.ui.graphics.Color.Transparent)
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
