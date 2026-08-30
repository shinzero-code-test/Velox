package com.exapps.velox.core.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.exapps.velox.core.domain.theme.SCHEMA_VERSION
import com.exapps.velox.core.domain.theme.ThemeDefinition
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Phase 3 / Milestone 2 — Theme engine. The persistence side: stores the
 * user's currently-selected theme id in DataStore and enumerates the
 * bundled themes from `assets/themes/`.
 *
 * The bundled enumeration is loaded once (lazily on first read of
 * [bundled]) and cached in memory; it never changes at runtime. User-
 * imported themes are read on demand from `filesDir/themes/` and are
 * not cached across process restarts (a re-scan on cold start is
 * cheap — at most a few small JSON files).
 *
 * Schema versioning: a stored theme that doesn't match [SCHEMA_VERSION]
 * is dropped on read, and the active selection falls back to the
 * bundled default. There is no in-place migration for theme JSON yet —
 * the schema is at v1 and a v2 would add fields with defaults, not
 * remove or rename.
 */
@Singleton
class ThemePreferences @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dataStore: DataStore<Preferences>,
) {

    /**
     * Hot, conflated view of the active theme id. Reads the persisted
     * value on first collection; falls back to [DEFAULT_THEME_ID] when
     * nothing has been selected (fresh install).
     */
    val activeThemeId: Flow<String> = dataStore.data.map { prefs ->
        prefs[ACTIVE_THEME_KEY] ?: DEFAULT_THEME_ID
    }

    /**
     * The bundled themes, shipped in `assets/themes/`. Cached after the
     * first read; the set is fixed at build time and never changes.
     */
    private var cachedBundled: List<ThemeDefinition>? = null
    suspend fun bundled(): List<ThemeDefinition> {
        cachedBundled?.let { return it }
        val loaded = loadBundledFromAssets()
        cachedBundled = loaded
        return loaded
    }

    /**
     * User-imported themes from `filesDir/themes/*.veloxtheme.json`. Not
     * cached — a re-scan is fast and reflects any SAF import that
     * happened while the process was alive.
     */
    suspend fun imported(): List<ThemeDefinition> = loadImportedFromFilesDir()

    suspend fun current(): String = activeThemeId.first()

    suspend fun setActive(themeId: String) {
        dataStore.edit { prefs -> prefs[ACTIVE_THEME_KEY] = themeId }
    }

    private fun loadBundledFromAssets(): List<ThemeDefinition> {
        val json = Json {
            ignoreUnknownKeys = true
            isLenient = true
        }
        val manager = context.assets
        // Walk every file under assets/themes/ and parse anything that
        // looks like a theme JSON. This is a small directory (the
        // shipped set is Dark Glass + AMOLED Dark); an AssetManager
        // list() is the right tool here.
        val themes = mutableListOf<ThemeDefinition>()
        val root = "themes"
        val files = runCatching { manager.list(root) }.getOrNull().orEmpty()
        for (name in files) {
            if (!name.endsWith(".json")) continue
            val text = runCatching { manager.open("$root/$name").bufferedReader().use { it.readText() } }
                .getOrNull() ?: continue
            runCatching { json.decodeFromString(ThemeDefinition.serializer(), text) }
                .getOrNull()
                ?.takeIf { it.schemaVersion == SCHEMA_VERSION }
                ?.let { themes += it }
        }
        return themes.sortedBy { it.name.default }
    }

    private fun loadImportedFromFilesDir(): List<ThemeDefinition> {
        val json = Json {
            ignoreUnknownKeys = true
            isLenient = true
        }
        val dir = java.io.File(context.filesDir, "themes")
        if (!dir.isDirectory) return emptyList()
        val themes = mutableListOf<ThemeDefinition>()
        dir.listFiles { f -> f.isFile && f.name.endsWith(".json") }?.forEach { file ->
            runCatching { file.readText() }
                .getOrNull()
                ?.let { runCatching { json.decodeFromString(ThemeDefinition.serializer(), it) }.getOrNull() }
                ?.takeIf { it.schemaVersion == SCHEMA_VERSION }
                ?.let { themes += it }
        }
        return themes.sortedBy { it.name.default }
    }

    /**
     * SAF-import a theme JSON. The file is copied into
     * `filesDir/themes/` and its filename is normalised to
     * `{themeId}.json` for stable lookup. Returns the new file size in
     * bytes, or throws on parse failure.
     */
    suspend fun importFromUri(uri: android.net.Uri): Long {
        val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            ?: throw java.io.IOException("Could not open theme URI: $uri")
        val parsed = Json { ignoreUnknownKeys = true; isLenient = true }
            .decodeFromString(ThemeDefinition.serializer(), text)
        check(parsed.schemaVersion == SCHEMA_VERSION) {
            "Unsupported theme schema version: ${parsed.schemaVersion} (this build supports $SCHEMA_VERSION)"
        }
        val dir = java.io.File(context.filesDir, "themes").apply { mkdirs() }
        val out = java.io.File(dir, "${parsed.id}.json")
        out.writeText(text)
        return out.length()
    }

    private companion object {
        val ACTIVE_THEME_KEY = stringPreferencesKey("active_theme_id")
        const val DEFAULT_THEME_ID = "velox-dark-glass"
    }
}
