package com.exapps.velox.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.exapps.velox.core.domain.plugin.MediaSourceProvider
import com.exapps.velox.core.domain.plugin.PluginRegistry
import com.exapps.velox.core.ui.components.GlassCard
import com.exapps.velox.core.ui.components.VeloxGlassIconButton
import com.exapps.velox.core.ui.theme.VeloxColors
import com.exapps.velox.core.ui.theme.VeloxSpacing
import com.exapps.velox.core.ui.theme.VeloxTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Phase 3 / Milestone 4 — Plugins surface. The Settings → About →
 * Plugins entry. Lists every provider the [PluginRegistry] knows
 * about, with the id, display name (localised), and supported
 * protocols. Built-in providers (SMB/FTP/WebDAV) and first-party
 * plugins (HttpUrlProvider) all show up here.
 *
 * Round 1 of this surface is read-only — there's no enable/disable
 * toggle yet because the engine's router consults the registry
 * eagerly. Phase 3b in the plan adds a Settings toggle per plugin;
 * for v1.5.0 the list is a diagnostic / about-screen addition.
 */
@Composable
fun PluginsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PluginsViewModel = hiltViewModel(),
) {
    val providers by viewModel.providers.collectAsStateWithLifecycle()

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(VeloxSpacing.sm),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = VeloxSpacing.lg, vertical = VeloxSpacing.sm),
        ) {
            VeloxGlassIconButton(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.cd_back),
                onClick = onBack,
            )
            Text(
                text = stringResource(R.string.settings_plugins_title),
                style = VeloxTheme.typography.headlineMedium,
                color = VeloxColors.OnBackground,
            )
        }
        LazyColumn(
            contentPadding = PaddingValues(horizontal = VeloxSpacing.lg, vertical = VeloxSpacing.xs),
            verticalArrangement = Arrangement.spacedBy(VeloxSpacing.sm),
        ) {
            items(providers, key = { it.id }) { provider ->
                GlassCard {
                    Column {
                        Text(
                            text = provider.displayName.forLocale(currentLocale()),
                            style = VeloxTheme.typography.titleMedium,
                            color = VeloxColors.OnSurface,
                        )
                        Text(
                            text = provider.id,
                            style = VeloxTheme.typography.bodySmall,
                            color = VeloxColors.OnSurfaceVariant,
                        )
                        Text(
                            text = stringResource(
                                R.string.settings_plugins_protocols,
                                provider.supportedProtocols.joinToString(", "),
                            ),
                            style = VeloxTheme.typography.bodyMedium,
                            color = VeloxColors.OnSurfaceVariant,
                        )
                    }
                }
            }
            // Phase 3 / Wave 3 / Round 3.5e — when the registry has
            // only the four first-party providers, show a
            // one-liner pointing at the plugin contract. Real
            // third-party plugins are loaded via
            // [com.exapps.velox.core.data.plugin.PackageManagerPluginDiscovery]
            // when the user installs an APK signed with the same
            // key that holds `com.exapps.velox.permission.PLUGIN_HOST`.
            if (providers.size <= 4) {
                item("install-hint") {
                    Column(modifier = Modifier.fillMaxWidth().padding(top = VeloxSpacing.md)) {
                        Text(
                            text = stringResource(R.string.settings_plugins_install_hint_title),
                            style = VeloxTheme.typography.labelLarge,
                            color = VeloxColors.OnSurfaceVariant,
                        )
                        Text(
                            text = stringResource(R.string.settings_plugins_install_hint_body),
                            style = VeloxTheme.typography.bodySmall,
                            color = VeloxColors.OnSurfaceVariant,
                            modifier = Modifier.padding(top = VeloxSpacing.xs),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun currentLocale(): String =
    LocalConfiguration.current.locales[0].language

@HiltViewModel
class PluginsViewModel @Inject constructor(
    private val registry: PluginRegistry,
) : ViewModel() {

    private val _providers = MutableStateFlow<List<MediaSourceProvider>>(emptyList())
    val providers: StateFlow<List<MediaSourceProvider>> = _providers.asStateFlow()

    init {
        viewModelScope.launch {
            _providers.value = registry.available()
        }
    }
}
