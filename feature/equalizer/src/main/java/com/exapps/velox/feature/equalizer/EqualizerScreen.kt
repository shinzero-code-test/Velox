package com.exapps.velox.feature.equalizer

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.exapps.velox.core.domain.player.EqualizerBand
import com.exapps.velox.core.domain.player.EqualizerPreset
import com.exapps.velox.core.ui.components.GlassCard
import com.exapps.velox.core.ui.components.VeloxGlassIconButton
import com.exapps.velox.core.ui.theme.VeloxColors
import com.exapps.velox.core.ui.theme.VeloxShapes
import com.exapps.velox.core.ui.theme.VeloxSpacing
import com.exapps.velox.core.ui.theme.VeloxTheme
import com.exapps.velox.core.ui.theme.accentColor
import com.exapps.velox.core.ui.theme.glassSurfaceColor

/**
 * SCREEN_EQUALIZER.md — preset chips, the band field (kept LTR per §9's technical
 * convention), and the additional-effects card. While no audio session exists yet
 * the screen renders a disabled placeholder field rather than an error.
 */
@Composable
fun EqualizerScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: EqualizerViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = VeloxSpacing.xxl),
    ) {
        // Header: back, title, reset (§2)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = VeloxSpacing.md, vertical = VeloxSpacing.sm),
        ) {
            VeloxGlassIconButton(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.cd_back),
                onClick = onBack,
            )
            Text(
                text = stringResource(R.string.equalizer_title),
                style = VeloxTheme.typography.headlineLarge,
                color = VeloxColors.OnBackground,
                modifier = Modifier.weight(1f).padding(horizontal = VeloxSpacing.md),
            )
            Text(
                text = stringResource(R.string.equalizer_reset),
                style = VeloxTheme.typography.labelLarge,
                color = accentColor(),
                modifier = Modifier
                    .clip(VeloxShapes.sm)
                    .clickable(onClick = viewModel::onReset)
                    .padding(horizontal = VeloxSpacing.md, vertical = VeloxSpacing.sm),
            )
            Switch(
                checked = state?.enabled == true,
                onCheckedChange = viewModel::onEnabledChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = VeloxColors.currentBackground,
                    checkedTrackColor = accentColor(),
                ),
            )
        }

        if (state == null) {
            Text(
                text = stringResource(R.string.equalizer_standby_body),
                style = VeloxTheme.typography.bodyMedium,
                color = VeloxColors.OnSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(VeloxSpacing.lg),
            )
        }

        // Preset chips (§4)
        LazyRow(
            contentPadding = PaddingValues(horizontal = VeloxSpacing.lg),
            horizontalArrangement = Arrangement.spacedBy(VeloxSpacing.xs),
            modifier = Modifier.padding(vertical = VeloxSpacing.sm),
        ) {
            items(EqualizerPreset.entries) { preset ->
                val selected = state?.activePresetId == preset.name
                PresetChip(
                    label = presetLabel(preset),
                    selected = selected,
                    enabled = state != null,
                    onClick = { viewModel.onPresetSelected(preset) },
                )
            }
            // §4 "مستخدم (User)" — shown once the user has dragged past a preset.
            if (state?.activePresetId == null) {
                item {
                    PresetChip(
                        label = stringResource(R.string.equalizer_preset_user),
                        selected = true,
                        enabled = false,
                        onClick = {},
                    )
                }
            }
        }

        // Band field (§3) — LTR: low → high frequencies stay in the technical order (§9).
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            val bands = state?.bands.orEmpty()
            Row(
                horizontalArrangement = Arrangement.spacedBy(VeloxSpacing.xxs),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = VeloxSpacing.md)
                    .height(220.dp),
            ) {
                if (bands.isEmpty()) {
                    // Placeholder field while waiting for an audio session.
                    EqualizerPreset.NORMAL.frequenciesHz.forEach { _ ->
                        Box(modifier = Modifier.weight(1f).fillMaxSize().padding(VeloxSpacing.xxs)) {
                            VerticalBandSlider(
                                levelMillibel = 0,
                                minLevelMillibel = -1500,
                                maxLevelMillibel = 1500,
                                enabled = false,
                                onLevelChange = {},
                                onDragFinished = {},
                            )
                        }
                    }
                } else {
                    bands.forEach { band ->
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.weight(1f),
                        ) {
                            VerticalBandSlider(
                                levelMillibel = band.levelMillibel,
                                minLevelMillibel = band.minLevelMillibel,
                                maxLevelMillibel = band.maxLevelMillibel,
                                enabled = state?.enabled == true,
                                onLevelChange = { viewModel.onBandLevelChange(band.index, it) },
                                onDragFinished = viewModel::onBandLevelChangeFinished,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                text = formatBandFrequency(band.centerFrequencyMilliHz),
                                style = VeloxTheme.typography.labelSmall,
                                color = VeloxColors.OnSurfaceVariant,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(VeloxSpacing.lg))

        // Additional effects (§5)
        GlassCard(modifier = Modifier.fillMaxWidth().padding(horizontal = VeloxSpacing.lg)) {
            Column(verticalArrangement = Arrangement.spacedBy(VeloxSpacing.md)) {
                EffectSliderRow(
                    label = stringResource(R.string.equalizer_bass_boost),
                    value = (state?.bassBoostStrength ?: 0) / 10f,
                    enabled = state?.enabled == true,
                    onChange = { viewModel.onBassBoostChange((it * 10).toInt()) },
                )
                EffectSliderRow(
                    label = stringResource(R.string.equalizer_virtualizer),
                    value = (state?.virtualizerStrength ?: 0) / 10f,
                    enabled = state?.enabled == true,
                    onChange = { viewModel.onVirtualizerChange((it * 10).toInt()) },
                )
            }
        }
    }
}

@Composable
private fun PresetChip(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(VeloxShapes.full)
            .background(
                when {
                    selected -> accentColor()
                    else -> glassSurfaceColor()
                },
            )
            .clickable(enabled = enabled, onClick = onClick)
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
private fun EffectSliderRow(
    label: String,
    value: Float,
    enabled: Boolean,
    onChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(label, style = VeloxTheme.typography.titleMedium, color = VeloxColors.OnSurface)
            Text("${(value * 100).toInt()}%", style = VeloxTheme.typography.labelMedium, color = VeloxColors.OnSurfaceVariant)
        }
        Slider(
            value = value.coerceIn(0f, 100f),
            onValueChange = { if (enabled) onChange(it) },
            valueRange = 0f..100f,
            enabled = enabled,
            colors = SliderDefaults.colors(
                thumbColor = if (enabled) accentColor() else VeloxColors.OnSurfaceVariant,
                activeTrackColor = if (enabled) accentColor() else VeloxColors.OnSurfaceVariant,
                inactiveTrackColor = VeloxColors.OnSurfaceVariant.copy(alpha = 0.24f),
            ),
        )
    }
}

@Composable
private fun presetLabel(preset: EqualizerPreset): String = stringResource(
    when (preset) {
        EqualizerPreset.NORMAL -> R.string.equalizer_preset_normal
        EqualizerPreset.POP -> R.string.equalizer_preset_pop
        EqualizerPreset.ROCK -> R.string.equalizer_preset_rock
        EqualizerPreset.JAZZ -> R.string.equalizer_preset_jazz
        EqualizerPreset.CLASSICAL -> R.string.equalizer_preset_classical
        EqualizerPreset.BASS -> R.string.equalizer_preset_bass
        EqualizerPreset.VOCAL -> R.string.equalizer_preset_vocal
        EqualizerPreset.ELECTRONIC -> R.string.equalizer_preset_electronic
    },
)
