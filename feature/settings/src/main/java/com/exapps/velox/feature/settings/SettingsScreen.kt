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
// `minimumInteractiveComponentSize` lives in `androidx.compose.material`
// (not `androidx.compose.foundation.layout`). Pulled in transitively
// via `material-icons-extended`.
import androidx.compose.material.minimumInteractiveComponentSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.FileOpen
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.exapps.velox.core.data.preferences.AppLanguage
import com.exapps.velox.core.domain.theme.ThemeDefinition
import com.exapps.velox.core.ui.components.GlassCard
import kotlinx.coroutines.launch
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
    onShareCrashLog: (String) -> Unit = {},
    onOpenStatistics: () -> Unit = {},
    onReplayIntro: () -> Unit,
    onOpenPlugins: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    // L12: lastCrashSummary is now a StateFlow (loaded in the VM's init
    // on IO). Read it the same way as settings instead of dereferencing
    // a lazy property.
    val lastCrashSummary by viewModel.lastCrashSummary.collectAsStateWithLifecycle()
    // L13: history-cleared one-shot event from the VM; the snackbar
    // host below consumes the true pulse and acks it back to false.
    val historyCleared by viewModel.historyCleared.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current
    // L18 (features review): the dialog/backupMessage flags were held
    // in `remember`, which is destroyed on configuration change (rotation,
    // dark/light mode toggle). rememberSaveable persists them through
    // the SavedStateHandle so the user doesn't lose a pending dialog.
    var showClearHistoryDialog by rememberSaveable { mutableStateOf(false) }
    var showResetRecommendationsDialog by rememberSaveable { mutableStateOf(false) }
    var backupMessage by rememberSaveable { mutableStateOf<String?>(null) }

    // Phase 2 "Backup / restore" — SAF pickers, no storage permissions.
    val exportLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri != null) {
            viewModel.exportBackup(uri) { backupMessage = it }
        }
    }
    val importLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            viewModel.restoreBackup(uri) { backupMessage = it }
        }
    }

    // Phase 3 / Milestone 2 — Theme engine. SAF launcher for importing
    // a .json theme file. The user picks a file; we hand the URI to
    // the VM, which parses, copies, and refreshes the available list.
    val coroutineScope = rememberCoroutineScope()
    val themeImportError = stringResource(R.string.settings_theme_import_failed)
    val importThemeLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            coroutineScope.launch {
                val newId = viewModel.importTheme(uri)
                if (newId != null) {
                    viewModel.selectTheme(newId)
                } else {
                    backupMessage = themeImportError
                }
            }
        }
    }
    val availableThemes by viewModel.availableThemes.collectAsStateWithLifecycle()
    val activeTheme by viewModel.activeTheme.collectAsStateWithLifecycle()

    // L13: Snackbar host layered behind the LazyColumn so the
    // history-cleared confirmation and the backupMessage popup don't
    // hijack the screen.
    val snackbarHostState = remember { androidx.compose.material3.SnackbarHostState() }
    val historyClearedMessage = stringResource(R.string.settings_history_cleared)
    androidx.compose.runtime.LaunchedEffect(historyCleared) {
        if (historyCleared) {
            snackbarHostState.showSnackbar(historyClearedMessage)
            viewModel.ackHistoryCleared()
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
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
                    // Phase 3 / Milestone 2 — Theme engine. The picker
                    // lists every bundled + imported theme as a ChoiceRow
                    // (radio-style). The active theme is highlighted; the
                    // AMOLED toggle below is the runtime override
                    // (Dark Glass + AMOLED = pure black, regardless of
                    // the theme's own background token).
                    val currentLocale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0].language
                    Text(
                        text = stringResource(R.string.settings_theme_picker),
                        style = VeloxTheme.typography.titleMedium,
                        color = VeloxColors.OnSurface,
                    )
                    Text(
                        text = stringResource(R.string.settings_theme_picker_hint),
                        style = VeloxTheme.typography.bodySmall,
                        color = VeloxColors.OnSurfaceVariant,
                        modifier = Modifier.padding(top = VeloxSpacing.xxs, bottom = VeloxSpacing.sm),
                    )
                    availableThemes.forEach { theme ->
                        ChoiceRow(
                            title = theme.name.forLocale(currentLocale),
                            selected = activeTheme.id == theme.id,
                            onClick = { viewModel.selectTheme(theme.id) },
                        )
                    }
                    androidx.compose.material3.TextButton(
                        onClick = {
                            importThemeLauncher.launch(arrayOf("application/json", "text/plain"))
                        },
                    ) {
                        Icon(
                            imageVector = Icons.Filled.FileOpen,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.size(VeloxSpacing.xs))
                        Text(text = stringResource(R.string.settings_theme_import))
                    }
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
                    // Phase 3 / Wave 3 / Round 2 — smart silence skip.
                    // The detector runs once per track the first time
                    // it plays; the player seeks past the intro on
                    // the second listen. Default ON; the per-track
                    // skip-intro button on Now Playing is the manual
                    // override and is unaffected.
                    SwitchRow(
                        title = stringResource(R.string.settings_intelligent_silence),
                        subtitle = stringResource(R.string.settings_intelligent_silence_hint),
                        checked = settings.intelligentSilenceEnabled,
                        onCheckedChange = viewModel::setIntelligentSilenceEnabled,
                    )
                    // Phase 3 / Wave 3 / Round 3.5c — auto chapter
                    // generation. Default OFF; the chapter detector
                    // is heuristic and noisy on mixed-content
                    // libraries. When on, the Markers sheet
                    // surfaces auto chapters with an "auto" badge
                    // so they can be distinguished from sidecar /
                    // embedded chapters.
                    SwitchRow(
                        title = stringResource(R.string.settings_auto_chapter_generation),
                        subtitle = stringResource(R.string.settings_auto_chapter_generation_hint),
                        checked = settings.autoChapterGenerationEnabled,
                        onCheckedChange = viewModel::setAutoChapterGenerationEnabled,
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

        // Phase 3 / Wave 3 / Round 3 — Milestone 7: recommendation
        // data reset. The matrix is recomputed from play history
        // on the next call to forYou/upNext/becauseYouListened; this
        // entry just throws away the in-memory cache.
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
                                text = stringResource(R.string.settings_reset_recommendations),
                                style = VeloxTheme.typography.titleMedium,
                                color = VeloxColors.OnSurface,
                            )
                            Text(
                                text = stringResource(R.string.settings_reset_recommendations_hint),
                                style = VeloxTheme.typography.bodyMedium,
                                color = VeloxColors.OnSurfaceVariant,
                            )
                        }
                        VeloxDestructiveButton(
                            text = stringResource(R.string.settings_reset_action),
                            onClick = { showResetRecommendationsDialog = true },
                        )
                    }
                }
            }
        }

        // ---- Phase 2: Playback & video processing ----
        item { SectionHeader(Icons.Filled.Tune, stringResource(R.string.settings_section_processing)) }
        item {
            GlassCard {
                Column {
                    ChoiceRow(
                        title = stringResource(R.string.settings_decoder_auto),
                        selected = settings.decoderPreference == com.exapps.velox.core.data.preferences.DecoderPreference.AUTO,
                        onClick = { viewModel.setDecoderPreference(com.exapps.velox.core.data.preferences.DecoderPreference.AUTO) },
                    )
                    ChoiceRow(
                        title = stringResource(R.string.settings_decoder_software),
                        selected = settings.decoderPreference == com.exapps.velox.core.data.preferences.DecoderPreference.SOFTWARE,
                        onClick = { viewModel.setDecoderPreference(com.exapps.velox.core.data.preferences.DecoderPreference.SOFTWARE) },
                    )
                }
            }
        }

        // ---- Phase 2: Custom gesture configuration ----
        item { SectionHeader(Icons.Filled.TouchApp, stringResource(R.string.settings_section_gestures)) }
        item {
            GlassCard {
                Column {
                    SwitchRow(
                        title = stringResource(R.string.settings_gesture_long_press),
                        checked = settings.gestureLongPressSpeedBoost,
                        onCheckedChange = viewModel::setGestureLongPressSpeedBoost,
                    )
                    SwitchRow(
                        title = stringResource(R.string.settings_gesture_h_seek),
                        checked = settings.gestureHorizontalSeekDrag,
                        onCheckedChange = viewModel::setGestureHorizontalSeekDrag,
                    )
                    ChoiceRow(
                        title = stringResource(R.string.settings_gesture_v_default),
                        selected = settings.gestureVerticalDragMapping ==
                            com.exapps.velox.core.data.preferences.VerticalDragMapping.BRIGHTNESS_LEFT_VOLUME_RIGHT,
                        onClick = {
                            viewModel.setGestureVerticalDragMapping(
                                com.exapps.velox.core.data.preferences.VerticalDragMapping.BRIGHTNESS_LEFT_VOLUME_RIGHT,
                            )
                        },
                    )
                    ChoiceRow(
                        title = stringResource(R.string.settings_gesture_v_swapped),
                        selected = settings.gestureVerticalDragMapping ==
                            com.exapps.velox.core.data.preferences.VerticalDragMapping.VOLUME_LEFT_BRIGHTNESS_RIGHT,
                        onClick = {
                            viewModel.setGestureVerticalDragMapping(
                                com.exapps.velox.core.data.preferences.VerticalDragMapping.VOLUME_LEFT_BRIGHTNESS_RIGHT,
                            )
                        },
                    )
                }
            }
        }

        // ---- Phase 2: Backup / restore + statistics ----
        item { SectionHeader(Icons.Filled.Backup, stringResource(R.string.settings_section_data)) }
        item {
            GlassCard {
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onOpenStatistics),
                    ) {
                        Text(stringResource(R.string.statistics_title), style = VeloxTheme.typography.titleMedium, color = VeloxColors.OnSurface)
                        Icon(Icons.Filled.BarChart, contentDescription = null, tint = VeloxColors.OnSurfaceVariant)
                    }
                    Spacer(Modifier.height(VeloxSpacing.md))
                    Row(horizontalArrangement = Arrangement.spacedBy(VeloxSpacing.md)) {
                        androidx.compose.material3.TextButton(onClick = { exportLauncher.launch("velox-backup.json") }) {
                            Text(stringResource(R.string.settings_backup_export), color = accentColor())
                        }
                        androidx.compose.material3.TextButton(onClick = { importLauncher.launch(arrayOf("application/json", "application/octet-stream", "text/plain")) }) {
                            Text(stringResource(R.string.settings_backup_import), color = accentColor())
                        }
                    }
                    backupMessage?.let {
                        Text(it, style = VeloxTheme.typography.bodyMedium, color = VeloxColors.OnSurfaceVariant)
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
                    if (lastCrashSummary != null) {
                        Spacer(Modifier.height(VeloxSpacing.md))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onShareCrashLog(viewModel.lastCrashFullText().orEmpty())
                                    },
                        ) {
                            Text(
                                text = stringResource(R.string.settings_last_crash),
                                style = VeloxTheme.typography.titleMedium,
                                color = VeloxColors.OnSurface,
                            )
                            Text(
                                text = lastCrashSummary.orEmpty(),
                                style = VeloxTheme.typography.bodyMedium,
                                color = VeloxColors.Error,
                            )
                        }
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
                    // Phase 3 / Milestone 4 — link into the plugin
                    // registry surface. Round 1 is read-only; the
                    // entry is intentionally a clickable row, not a
                    // button, so it sits in the same Rhythm as the
                    // other About rows.
                    Spacer(Modifier.height(VeloxSpacing.md))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(VeloxShapes.sm)
                            .clickable(onClick = onOpenPlugins)
                            .padding(VeloxSpacing.sm),
                    ) {
                        Text(
                            text = stringResource(R.string.settings_plugins_title),
                            style = VeloxTheme.typography.titleMedium,
                            color = VeloxColors.OnSurface,
                        )
                        Text(
                            text = "›",
                            style = VeloxTheme.typography.titleLarge,
                            color = VeloxColors.OnSurfaceVariant,
                        )
                    }
                    // Phase 3 / Wave 3 / Round 3 — Milestone 7. The
                    // recommendations engine is on-device only;
                    // surface that explicitly so the user knows
                    // their play history never leaves the phone.
                    Spacer(Modifier.height(VeloxSpacing.md))
                    Text(
                        text = stringResource(R.string.settings_about_recommendations_privacy),
                        style = VeloxTheme.typography.bodySmall,
                        color = VeloxColors.OnSurfaceVariant,
                    )
                }
            }
        }
    }

    // L13: snackbar host layered over the LazyColumn.
    androidx.compose.material3.SnackbarHost(
        hostState = snackbarHostState,
        modifier = Modifier
            .align(androidx.compose.ui.Alignment.BottomCenter)
            .padding(VeloxSpacing.lg),
    )
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

    if (showResetRecommendationsDialog) {
        AlertDialog(
            onDismissRequest = { showResetRecommendationsDialog = false },
            title = { Text(stringResource(R.string.settings_reset_recommendations_confirm_title)) },
            text = { Text(stringResource(R.string.settings_reset_recommendations_confirm_body)) },
            confirmButton = {
                VeloxDestructiveButton(
                    text = stringResource(R.string.settings_reset_action),
                    onClick = {
                        viewModel.resetRecommendations()
                        showResetRecommendationsDialog = false
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
                        .clickable { showResetRecommendationsDialog = false }
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
        // M13: vertical padding bumped to `sm` so the row meets
        // Material's 40dp minimum touch target. L15: Role.Switch so
        // TalkBack announces the row as a switch toggle.
        modifier = modifier.fillMaxWidth()
            .padding(vertical = VeloxSpacing.sm)
            .semantics(mergeDescendants = true) { role = androidx.compose.ui.semantics.Role.Switch },
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
    // M13 (features review): padding used to be 4dp vertical, which on
    // the small `titleMedium` text produced a row under Material's
    // 40dp minimum touch target. Bump the vertical padding to 12dp so
    // the row is always tall enough.
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clip(VeloxShapes.sm)
            .clickable(onClick = onClick)
            // L15: Role.RadioButton so TalkBack announces the row as a
            // radio selection.
            .semantics(mergeDescendants = true) {
                role = androidx.compose.ui.semantics.Role.RadioButton
            }
            .padding(vertical = VeloxSpacing.sm, horizontal = VeloxSpacing.xs),
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
            // M13 (features review): the 36dp swatch was under Material's
            // 40dp minimum touch target. minimumInteractiveComponentSize()
            // pads the touchable area while the visual swatch stays 36dp.
            .minimumInteractiveComponentSize()
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
