package com.exapps.velox.feature.player

import com.exapps.velox.core.common.util.LrcParser
import com.exapps.velox.core.domain.model.MediaItem
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Phase 1.1 "Lyrics display (basic)": sidecar lookup next to the audio file —
 * `TrackName.lrc` first (synced), then `TrackName.txt` (plain). Embedded tags are
 * not read yet; see PROGRESS.md for what's deferred.
 */
@Singleton
class LyricsLoader @Inject constructor() {

    data class Lyrics(
        /** Non-null when a synced .lrc was found (sorted by timestamp, no nulls). */
        val syncedLines: List<LrcParser.LyricLine>,
        /** Plain-text fallback (.txt sidecar or an .lrc with no timestamps). */
        val plainText: String?,
    ) {
        val isEmpty: Boolean get() = syncedLines.isEmpty() && plainText.isNullOrBlank()
    }

    suspend fun load(item: MediaItem): Lyrics = runCatching { loadUnsafe(item) }.getOrDefault(Lyrics(emptyList(), null))

    private fun loadUnsafe(item: MediaItem): Lyrics {
        val folder = item.folderPath ?: return empty()
        val name = item.fileName ?: return empty()
        val base = File(name).nameWithoutExtension

        val lrc = File(folder, "$base.lrc")
        if (lrc.isFile) {
            val parsed = LrcParser.parse(runCatching { lrc.readText() }.getOrDefault(""))
            if (parsed.any { it.timeMs != null }) {
                return Lyrics(syncedLines = parsed.filter { it.timeMs != null }, plainText = null)
            }
            // An .lrc without usable timestamps degrades to plain text.
            return Lyrics(emptyList(), parsed.joinToString("\n") { it.text }.takeIf { it.isNotBlank() })
        }

        val txt = File(folder, "$base.txt")
        if (txt.isFile) {
            val text = runCatching { txt.readText() }.getOrDefault("")
            if (text.isNotBlank()) return Lyrics(emptyList(), text)
        }

        return empty()
    }

    private fun empty() = Lyrics(emptyList(), null)
}
