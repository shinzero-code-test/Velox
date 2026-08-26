package com.exapps.velox.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.exapps.velox.R
import com.exapps.velox.core.ui.components.VeloxPrimaryButton
import com.exapps.velox.core.ui.components.VeloxSecondaryButton
import com.exapps.velox.core.ui.theme.VeloxColors
import com.exapps.velox.core.ui.theme.VeloxShapes
import com.exapps.velox.core.ui.theme.VeloxSpacing
import com.exapps.velox.core.ui.theme.VeloxTheme
import com.exapps.velox.core.ui.theme.accentColor
import com.exapps.velox.core.ui.theme.glassSurfaceColor
import kotlinx.coroutines.launch

/**
 * SCREEN_ONBOARDING.md's full flow: welcome → feature highlights → permission
 * priming, as a horizontal pager with a dot indicator (§3). The permission page's
 * CTA runs the system media-permission request; granting (or skipping — §5's
 * "limited experience" path) completes onboarding and lands in the Library, where
 * the first scan kicks off automatically if permission was granted.
 */
@Composable
fun OnboardingScreen(
    onFinish: (mediaPermissionGranted: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val pagerState = rememberPagerState(pageCount = { PAGE_COUNT })
    val scope = rememberCoroutineScope()

    val permissions = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // C3 (app-shell review): also ask for notification access so the
            // playback notification isn't silently suppressed on 13+.
            arrayOf(
                Manifest.permission.READ_MEDIA_AUDIO,
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.POST_NOTIFICATIONS,
            )
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { results -> onFinish(results.values.any { it }) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(VeloxSpacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f),
        ) { page ->
            when (page) {
                0 -> WelcomePage()
                1 -> FeaturesPage()
                else -> PermissionsPage(
                    onRequestPermission = { permissionLauncher.launch(permissions) },
                    onSkip = { onFinish(false) },
                )
            }
        }

        // Dot indicator (§3 "smooth page indicator")
        Row(
            horizontalArrangement = Arrangement.spacedBy(VeloxSpacing.xs),
            modifier = Modifier.padding(vertical = VeloxSpacing.lg),
        ) {
            repeat(PAGE_COUNT) { index ->
                val selected = pagerState.currentPage == index
                Box(
                    modifier = Modifier
                        .size(if (selected) 8.dp else 6.dp)
                        .clip(CircleShape)
                        .background(if (selected) accentColor() else VeloxColors.OnSurfaceVariant.copy(alpha = 0.4f)),
                )
            }
        }

        when (pagerState.currentPage) {
            0, 1 -> VeloxPrimaryButton(
                text = stringResource(R.string.onboarding_next),
                onClick = {
                    scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                },
                modifier = Modifier.fillMaxWidth(),
            )
            else -> Unit // the permission page carries its own CTAs
        }
    }
}

@Composable
private fun WelcomePage(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(VeloxShapes.xl)
                .background(glassSurfaceColor(elevated = true)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "V",
                style = VeloxTheme.typography.displayLarge,
                color = accentColor(),
            )
        }
        Spacer(Modifier.height(VeloxSpacing.xxl))
        Text(
            text = stringResource(R.string.onboarding_welcome_title),
            style = VeloxTheme.typography.displayMedium,
            color = VeloxColors.OnBackground,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(VeloxSpacing.sm))
        Text(
            text = stringResource(R.string.onboarding_welcome_body),
            style = VeloxTheme.typography.bodyLarge,
            color = VeloxColors.OnSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun FeaturesPage(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        FeaturePoint(
            icon = Icons.Filled.PlayCircle,
            title = stringResource(R.string.onboarding_feature_playback_title),
            body = stringResource(R.string.onboarding_feature_playback_body),
        )
        Spacer(Modifier.height(VeloxSpacing.xl))
        FeaturePoint(
            icon = Icons.Outlined.Palette,
            title = stringResource(R.string.onboarding_feature_design_title),
            body = stringResource(R.string.onboarding_feature_design_body),
        )
        Spacer(Modifier.height(VeloxSpacing.xl))
        FeaturePoint(
            icon = Icons.Filled.Language,
            title = stringResource(R.string.onboarding_feature_arabic_title),
            body = stringResource(R.string.onboarding_feature_arabic_body),
        )
    }
}

@Composable
private fun FeaturePoint(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(VeloxSpacing.md),
        modifier = modifier.fillMaxWidth().padding(horizontal = VeloxSpacing.xs),
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(VeloxShapes.md)
                .background(glassSurfaceColor(elevated = true)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = accentColor(), modifier = Modifier.size(26.dp))
        }
        Column {
            Text(title, style = VeloxTheme.typography.titleLarge, color = VeloxColors.OnBackground)
            Text(body, style = VeloxTheme.typography.bodyMedium, color = VeloxColors.OnSurfaceVariant)
        }
    }
}

@Composable
private fun PermissionsPage(
    onRequestPermission: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(VeloxShapes.xl)
                .background(glassSurfaceColor(elevated = true)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.GraphicEq, contentDescription = null, tint = accentColor(), modifier = Modifier.size(44.dp))
        }
        Spacer(Modifier.height(VeloxSpacing.xxl))
        Text(
            text = stringResource(R.string.onboarding_permissions_title),
            style = VeloxTheme.typography.displayMedium,
            color = VeloxColors.OnBackground,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(VeloxSpacing.sm))
        Text(
            text = stringResource(R.string.onboarding_permissions_body),
            style = VeloxTheme.typography.bodyLarge,
            color = VeloxColors.OnSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(VeloxSpacing.xxxl))
        VeloxPrimaryButton(
            text = stringResource(R.string.onboarding_permissions_cta),
            onClick = onRequestPermission,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(VeloxSpacing.sm))
        VeloxSecondaryButton(
            text = stringResource(R.string.onboarding_permissions_skip),
            onClick = onSkip,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private const val PAGE_COUNT = 3
