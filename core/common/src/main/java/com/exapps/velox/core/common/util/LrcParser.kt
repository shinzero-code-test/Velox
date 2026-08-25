package com.exapps.velox.core.common.util

/**
 * Phase 1.1 "Lyrics display (basic)": a minimal LRC parser. Supports the common
 * `[mm:ss.xx]` / `[mm:ss.xxx]` line format, multiple timestamps per line, and the
 * optional `[offset:±ms]` header tag (added to every timestamp; positive shifts
 * lyrics later per the de-facto LRC spec). Everything else (metadata tags like
 * `[ar:]`, plain text) is kept as unsynced content.
 */
object LrcParser {

    data class LyricLine(val timeMs: Long?, val text: String)

    private val timestampRegex = Regex("""\[(\d{1,2}):(\d{1,2})(?:[.:](\d{1,3}))?]""")
    private val offsetRegex = Regex("""\[offset:\s*([+-]?\d+)]""", RegexOption.IGNORE_CASE)

    fun parse(content: String): List<LyricLine> {
        val offset = offsetRegex.find(content)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
        val lines = mutableListOf<LyricLine>()

        content.lineSequence().forEach { raw ->
            val timestamps = timestampRegex.findAll(raw).toList()
            if (timestamps.isEmpty()) return@forEach

            val text = raw.replace(timestampRegex, "").trim()
            timestamps.forEach { match ->
                val minutes = match.groupValues[1].toLong()
                val seconds = match.groupValues[2].toLong()
                val fraction = when (match.groupValues[3].length) {
                    0 -> 0L
                    1 -> match.groupValues[3].toLong() * 100
                    2 -> match.groupValues[3].toLong() * 10
                    else -> match.groupValues[3].take(3).toLong()
                }
                lines += LyricLine(
                    timeMs = minutes * 60_000 + seconds * 1_000 + fraction - offset,
                    text = text,
                )
            }
        }

        return lines.sortedBy { it.timeMs ?: Long.MAX_VALUE }
    }
}
