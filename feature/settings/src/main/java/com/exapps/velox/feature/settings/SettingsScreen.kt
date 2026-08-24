package com.exapps.velox.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.exapps.velox.core.data.preferences.AppLanguage
import com.exapps.velox.core.ui.components.GlassCard
import com.exapps.velox.core.ui.components.VeloxDestructiveButton
import com.exapps.velox.core.ui.theme.VeloxAccentOptions
import com.exapps.velox.core.ui.theme.VeloxColors
import com.exapps.velox.core.ui.theme.VeloxShapes
import com.exapps.velox.core.ui.theme.VeloxSpacing
import com.exapps.velox.core.ui.theme.VeloxTheme
import com.exapps.velox.core.ui.theme.accentColor
import com.exapps.velox.core.ui.theme.glassOutlineColor
import com.exapps.velox.core.ui.theme.glassSurfaceColor

private val SEEK_INTERVAL_CHOICES = listOf(5, 10, 15, 30)

/**
 * SCREEN_SETTINGS.md — grouped preference rows over the persisted UserSettings:
 * Appearance, Playback, Subtitles, Language, Storage, About. Rows that need a
 * platform side effect (language recreate, theme re-apply) surface through the
 * [onLanguageChanged] callback to the app shell.
 */
@Composable
fun SettingsScreen(
    onLanguageChanged: () -> Unit,
    onReplayIntro: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    var showClearHistoryDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(VeloxSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(VeloxSpacing.sm),
    ) {
        item {
            Text(
                text = stringResource(R.string.settings_title),
                style = VeloxTheme.typography.headlineLarge,
                color = VeloxColors.OnBackground,
                modifier = Modifier.padding(bottom = VeloxSpacing.xs),
            )
        }

        // ---- Appearance (§3) ----
        item { SectionHeader(Icons.Filled.Palette, stringResource(R.string.settings_section_appearance)) }
        item {
            GlassCard {
                Column {
                    ChoiceRow(
                        title = stringResource(R.string.settings_theme_dark_glass),
                        selected = !settings.amoled,
                        onClick = { viewModel.setAmoled(false) },
                    )
                    ChoiceRow(
                        title = stringResource(R.string.settings_theme_amoled),
                        selected = settings.amoled,
                        onClick = { viewModel.setAmoled(true) },
                    )
                    Spacer(Modifier.height(VeloxSpacing.md))
                    Text(
                        text = stringResource(R.string.settings_accent_color),
                        style = VeloxTheme.typography.titleMedium,
                        color = VeloxColors.OnSurface,
                    )
                    Spacer(Modifier.height(VeloxSpacing.sm))
                    Row(horizontalArrangement = Arrangement.spacedBy(VeloxSpacing.md)) {
                        VeloxAccentOptions.forEachIndexed { index, color ->
                            AccentSwatch(
                                color = color,
                                selected = index == settings.accentIndex,
                                onClick = { viewModel.setAccentIndex(index) },
                            )
                        }
                    }
                }
            }
        }

        // ---- Playback (§4) ----
        item { SectionHeader(Icons.Filled.PlayCircle, stringResource(R.string.settings_section_playback)) }
        item {
            GlassCard {
                Column {
                    SwitchRow(
                        title = stringResource(R.string.settings_resume_playback),
                        checked = settings.resumePlayback,
                        onCheckedChange = viewModel::setResumePlayback,
                    )
                    SwitchRow(
                        title = stringResource(R.string.settings_auto_pip),
                        subtitle = stringResource(R.string.settings_auto_pip_hint),
                        checked = settings.autoPipOnLeave,
                        onCheckedChange = viewModel::setAutoPip,
                    )
                    Spacer(Modifier.height(VeloxSpacing.md))
                    Text(
                        text = stringResource(R.string.settings_seek_interval),
                        style = VeloxTheme.typography.titleMedium,
                        color = VeloxColors.OnSurface,
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(VeloxSpacing.xs),
                        modifier = Modifier.padding(top = VeloxSpacing.xs),
                    ) {
                        SEEK_INTERVAL_CHOICES.forEach { seconds ->
                            ChoiceChip(
                                label = "${seconds}s",
                                selected = settings.seekIncrementSeconds == seconds,
                                onClick = { viewModel.setSeekIncrementSeconds(seconds) },
                            )
                        }
                    }
                }
            }
        }

        // ---- Subtitles (§6) ----
        item { SectionHeader(Icons.Filled.Subtitles, stringResource(R.string.settings_section_subtitles)) }
        item {
            GlassCard {
                Column {
                    Text(
                        text = stringResource(R.string.settings_subtitle_size),
                        style = VeloxTheme.typography.titleMedium,
                        color = VeloxColors.OnSurface,
                    )
                    Slider(
                        value = (settings.subtitleScalePercent - 50) / 100f,
                        onValueChange = { viewModel.setSubtitleScalePercent((50 + it * 100).toInt().coerceIn(50, 200)) },
                        valueRange = 0f..1f,
                        colors = subtitleSliderColors(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(stringResource(R.string.settings_subtitle_small), style = VeloxTheme.typography.labelMedium, color = VeloxColors.OnSurfaceVariant)
                        Text("${settings.subtitleScalePercent}%", style = VeloxTheme.typography.labelMedium, color = VeloxColors.OnSurfaceVariant)
                        Text(stringResource(R.string.settings_subtitle_large), style = VeloxTheme.typography.labelMedium, color = VeloxColors.OnSurfaceVariant)
                    }
                    Spacer(Modifier.height(VeloxSpacing.md))
                    Text(
                        text = stringResource(R.string.settings_subtitle_position),
                        style = VeloxTheme.typography.titleMedium,
                        color = VeloxColors.OnSurface,
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(VeloxSpacing.xs),
                        modifier = Modifier.padding(top = VeloxSpacing.xs),
                    ) {
                        ChoiceChip(
                            label = stringResource(R.string.settings_subtitle_bottom),
                            selected = settings.subtitlePositionBottom,
                            onClick = { viewModel.setSubtitlePositionBottom(true) },
                        )
                        ChoiceChip(
                            label = stringResource(R.string.settings_subtitle_top),
                            selected = !settings.subtitlePositionBottom,
                            onClick = { viewModel.setSubtitlePositionBottom(false) },
                        )
                    }
                    SwitchRow(
                        title = stringResource(R.string.settings_subtitle_autoload),
                        subtitle = stringResource(R.string.settings_subtitle_autoload_hint),
                        checked = settings.autoLoadExternalSubtitles,
                        onCheckedChange = viewModel::setAutoLoadExternalSubtitles,
                    )
                }
            }
        }

        // ---- Language (§7) ----
        item { SectionHeader(Icons.Filled.Language, stringResource(R.string.settings_section_language)) }
        item {
            GlassCard {
                Column {
                    LanguageRow(
                        title = stringResource(R.string.settings_language_system),
                        selected = settings.language == AppLanguage.SYSTEM,
                        onClick = { viewModel.setLanguage(AppLanguage.SYSTEM); onLanguageChanged() },
                    )
                    LanguageRow(
                        title = "العربية",
                        selected = settings.language == AppLanguage.ARABIC,
                        onClick = { viewModel.setLanguage(AppLanguage.ARABIC); onLanguageChanged() },
                    )
                    LanguageRow(
                        title = "English",
                        selected = settings.language == AppLanguage.ENGLISH,
                        onClick = { viewModel.setLanguage(AppLanguage.ENGLISH); onLanguageChanged() },
                    )
                }
            }
        }

        // ---- Storage (§8) ----
        item { SectionHeader(Icons.Filled.GraphicEq, stringResource(R.string.settings_section_storage)) }
        item {
            GlassCard {
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.settings_clear_history),
                                style = VeloxTheme.typography.titleMedium,
                                color = VeloxColors.OnSurface,
                            )
                            Text(
                                text = stringResource(R.string.settings_clear_history_hint),
                                style = VeloxTheme.typography.bodyMedium,
                                color = VeloxColors.OnSurfaceVariant,
                            )
                        }
                        VeloxDestructiveButton(
                            text = stringResource(R.string.settings_clear_action),
                            onClick = { showClearHistoryDialog = true },
                        )
                    }
                }
            }
        }

        // ---- About (§9) ----
        item { SectionHeader(Icons.Filled.Info, stringResource(R.string.settings_section_about)) }
        item {
            GlassCard {
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = stringResource(R.string.settings_version),
                            style = VeloxTheme.typography.titleMedium,
                            color = VeloxColors.OnSurface,
                        )
                        Text(
                            text = viewModel.versionName,
                            style = VeloxTheme.typography.bodyMedium,
                            color = VeloxColors.OnSurfaceVariant,
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = stringResource(R.string.settings_replay_intro),
                            style = VeloxTheme.typography.titleMedium,
                            color = VeloxColors.OnSurface,
                        )
                        Text(
                            text = stringResource(R.string.settings_replay_intro_hint),
                            style = VeloxTheme.typography.bodyMedium,
                            color = accentColor(),
                            modifier = Modifier
                                .clip(VeloxShapes.sm)
                                .clickable(onClick = onReplayIntro)
                                .padding(VeloxSpacing.sm),
                        )
                    }
                }
            }
        }
    }

    if (showClearHistoryDialog) {
        AlertDialog(
            onDismissRequest = { showClearHistoryDialog = false },
            title = { Text(stringResource(R.string.settings_clear_history_confirm_title)) },
            text = { Text(stringResource(R.string.settings_clear_history_confirm_body)) },
            confirmButton = {
                VeloxDestructiveButton(
                    text = stringResource(R.string.settings_clear_action),
                    onClick = {
                        viewModel.clearPlayHistory()
                        showClearHistoryDialog = false
                    },
                )
            },
            dismissButton = {
                Text(
                    text = stringResource(R.string.cancel),
                    style = VeloxTheme.typography.labelLarge,
                    color = VeloxColors.OnSurfaceVariant,
                    modifier = Modifier
                        .clip(VeloxShapes.sm)
                        .clickable { showClearHistoryDialog = false }
                        .padding(VeloxSpacing.md),
                )
            },
            containerColor = VeloxColors.currentSurface,
            titleContentColor = VeloxColors.OnBackground,
            textContentColor = VeloxColors.OnSurfaceVariant,
        )
    }
}

@Composable
private fun SectionHeader(icon: ImageVector, title: String, modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(VeloxSpacing.sm),
        modifier = modifier.padding(top = VeloxSpacing.sm),
    ) {
        Icon(icon, contentDescription = null, tint = accentColor(), modifier = Modifier.size(20.dp))
        Text(title, style = VeloxTheme.typography.titleLarge, color = VeloxColors.OnBackground)
    }
}

/** Preference row pattern (§10): title + optional subtitle + trailing control. */
@Composable
private fun SwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = modifier.fillMaxWidth().padding(vertical = VeloxSpacing.xs),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = VeloxTheme.typography.titleMedium, color = VeloxColors.OnSurface)
            if (subtitle != null) {
                Text(subtitle, style = VeloxTheme.typography.bodyMedium, color = VeloxColors.OnSurfaceVariant)
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = VeloxColors.currentBackground,
                checkedTrackColor = accentColor(),
            ),
        )
    }
}

@Composable
private fun ChoiceRow(title: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clip(VeloxShapes.sm)
            .clickable(onClick = onClick)
            .padding(vertical = VeloxSpacing.xs),
    ) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .border(2.dp, if (selected) accentColor() else glassOutlineColor(strong = true), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(accentColor()),
                )
            }
        }
        Text(
            text = title,
            style = VeloxTheme.typography.titleMedium,
            color = if (selected) VeloxColors.OnBackground else VeloxColors.OnSurface,
            modifier = Modifier.padding(start = VeloxSpacing.md),
        )
    }
}

@Composable
private fun LanguageRow(title: String, selected: Boolean, onClick: () -> Unit) =
    ChoiceRow(title = title, selected = selected, onClick = onClick)

@Composable
private fun ChoiceChip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(VeloxShapes.full)
            .background(if (selected) accentColor() else glassSurfaceColor())
            .clickable(onClick = onClick)
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
private fun AccentSwatch(color: Color, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(color)
            .border(
                width = if (selected) 3.dp else 1.dp,
                color = if (selected) VeloxColors.OnBackground else glassOutlineColor(strong = true),
                shape = CircleShape,
            )
            .clickable(onClick = onClick),
    )
}

@Composable
private fun subtitleSliderColors() = SliderDefaults.colors(
    thumbColor = accentColor(),
    activeTrackColor = accentColor(),
    inactiveTrackColor = VeloxColors.OnSurfaceVariant.copy(alpha = 0.24f),
)
