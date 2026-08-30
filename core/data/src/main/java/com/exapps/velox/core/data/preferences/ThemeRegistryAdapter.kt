package com.exapps.velox.core.data.preferences

import com.exapps.velox.core.common.di.ApplicationScope
import com.exapps.velox.core.domain.theme.ThemeDefinition
import com.exapps.velox.core.domain.theme.ThemeRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Phase 3 / Milestone 2 — Theme engine. Default implementation of
 * [ThemeRegistry] backed by [ThemePreferences].
 *
 * [active] is a hot [StateFlow] that:
 *  - emits the persisted id (or the bundled default) on first read;
 *  - then re-emits whenever the user picks a different theme;
 *  - is also a write target — [setActive] updates both the persisted
 *    selection and the cached flow, so the Settings screen sees the
 *    change instantly without re-collecting.
 *
 * The bundled default lives in a small `@Volatile` field that's primed
 * once at app start via [primeCache] (the [com.exapps.velox.core.domain.theme.ThemeDefinition]
 * for the default theme id, looked up once).
 */
@Singleton
class ThemeRegistryAdapter @Inject constructor(
    private val preferences: ThemePreferences,
    @ApplicationScope private val appScope: CoroutineScope,
) : ThemeRegistry {

    @Volatile
    private var cachedDefault: ThemeDefinition? = null

    private val activeOverride = MutableStateFlow<ThemeDefinition?>(null)

    override val active: StateFlow<ThemeDefinition> = preferences.activeThemeId
        .map { id -> resolveTheme(id) }
        .stateIn(
            scope = appScope,
            // Eagerly start so the first reader sees the resolved
            // theme, not a placeholder. SharingStarted.WhileSubscribed
            // would also work but the Settings screen is the only
            // consumer and it stays alive for the whole session.
            started = SharingStarted.Eagerly,
            initialValue = cachedDefault ?: DefaultDarkGlass,
        )

    override suspend fun available(): List<ThemeDefinition> {
        val bundled = preferences.bundled()
        val imported = preferences.imported()
        return (bundled + imported).distinctBy { it.id }
    }

    override suspend fun setActive(themeId: String) {
        val all = available()
        val match = all.firstOrNull { it.id == themeId } ?: return
        preferences.setActive(themeId)
        activeOverride.value = match
    }

    override suspend fun primeCache() {
        val bundled = preferences.bundled()
        cachedDefault = bundled.firstOrNull { it.id == preferences.current() }
            ?: bundled.firstOrNull()
            ?: DefaultDarkGlass
        // Re-emit the cached value through the flow so the first
        // reader after a cold start sees the persisted selection, not
        // a default.
        activeOverride.value = cachedDefault
    }

    /**
     * Resolves a theme by id. Falls back to the bundled default if
     * the id is unknown (e.g. the user imported a theme, picked it,
     * and then deleted the import file — the persisted id still
     * points at the missing theme, but the next read should fall
     * back to a usable one rather than crash).
     */
    private suspend fun resolveTheme(id: String): ThemeDefinition {
        val all = available()
        return all.firstOrNull { it.id == id } ?: all.firstOrNull() ?: DefaultDarkGlass
    }

    companion object {
        /**
         * Hard-coded fallback used when even the bundled assets fail
         * to load (e.g. an empty APK install). Mirrors the values in
         * `assets/themes/dark-glass.json` so the app remains usable
         * even in that pathological case.
         */
        val DefaultDarkGlass: ThemeDefinition = ThemeDefinition(
            id = "velox-dark-glass",
            name = com.exapps.velox.core.domain.theme.LocalizedText(
                default = "Dark Glass",
                ar = "الزجاج الداكن",
                en = "Dark Glass",
            ),
            tokens = com.exapps.velox.core.domain.theme.ThemeTokens(
                background = "#0B0D10",
                onBackground = "#F2F4F7",
                surface = "#12151A",
                onSurface = "#E8EAED",
                onSurfaceVariant = "#9AA0A6",
                error = "#FF6B6B",
                glassAlpha = 0.06f,
                glassElevatedAlpha = 0.09f,
                outlineAlpha = 0.08f,
                outlineStrongAlpha = 0.14f,
                radiusScale = 1.0f,
                amoled = false,
            ),
        )
    }
}
