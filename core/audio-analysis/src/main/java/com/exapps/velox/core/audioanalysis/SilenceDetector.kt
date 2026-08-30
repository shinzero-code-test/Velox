package com.exapps.velox.core.audioanalysis

import com.exapps.velox.core.domain.audio.IntroOutro
import com.exapps.velox.core.domain.audio.IntroOutroKind
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.sqrt

/**
 * Phase 3 / Wave 3 / Round 2 — Milestone 5: smart silence / intro detection.
 *
 * Pure-Kotlin RMS-based silence detector. Reads a stream of
 * 16-bit signed PCM samples, slides a 100 ms window across the
 * first 60 s (intro search) and the last 30 s (outro search),
 * and emits an [IntroOutro] when a contiguous run of "silent"
 * windows long enough to be an intro/outro appears.
 *
 * Thresholds (per the Phase 3 plan):
 *  - A window is "silent" if its RMS < -50 dBFS.
 *  - A "silence run" is a contiguous run of silent windows ≥ 2 s.
 *  - The intro skip candidate is the first silence run that
 *    begins within 30 s of t = 0. The end of the run is the
 *    resume point.
 *  - The outro skip candidate is the last silence run that ends
 *    within 30 s of `trackDurationMs`.
 *
 * The class is deliberately [Singleton] + stateless: a single
 * instance can be called concurrently for different tracks; the
 * input [pcm] array is the only mutable state and it lives on the
 * caller side.
 */
@Singleton
class SilenceDetector @Inject constructor() {

    /**
     * @param pcm 16-bit signed little-endian PCM samples
     *   (`ByteArray` so the caller can hand us a `MediaCodec`
     *   output buffer without copying).
     * @param sampleRate Hz (e.g. 44_100).
     * @param trackDurationMs total track length. Required for
     *   outro detection; pass `0` if unknown and outro detection
     *   is skipped.
     */
    fun detect(
        mediaItemId: Long,
        pcm: ByteArray,
        sampleRate: Int,
        trackDurationMs: Long,
    ): List<IntroOutro> {
        if (pcm.isEmpty() || sampleRate <= 0) return emptyList()

        val samples = pcm.toShortLE()
        val windowSizeMs = 100
        val windowSizeSamples = (sampleRate * windowSizeMs / 1000).coerceAtLeast(1)
        val silenceDbThreshold = -50.0
        val minRunMs = 2_000
        val introSearchEndMs = 60_000L
        val outroSearchStartMs = (trackDurationMs - 30_000L).coerceAtLeast(0L)
        val candidateWindowMs = 30_000L

        val windows: List<Window> = computeWindows(samples, windowSizeSamples, sampleRate)
        val silentIndices: Set<Int> = windows.indices
            .filter { windows[it].db < silenceDbThreshold }
            .toSet()

        val intro = findSilenceRunStartingInRange(
            windows = windows,
            silentIndices = silentIndices,
            minRunMs = minRunMs,
            candidateEndMs = candidateWindowMs,
        )?.let { run ->
            IntroOutro(
                mediaItemId = mediaItemId,
                kind = IntroOutroKind.INTRO,
                startMs = run.startMs,
                endMs = run.endMs,
            )
        }?.takeIf { it.endMs < introSearchEndMs }

        val outro = if (trackDurationMs > 0) {
            findSilenceRunEndingInRange(
                windows = windows,
                silentIndices = silentIndices,
                minRunMs = minRunMs,
                candidateStartMs = outroSearchStartMs,
            )?.let { run ->
                IntroOutro(
                    mediaItemId = mediaItemId,
                    kind = IntroOutroKind.OUTRO,
                    startMs = run.startMs,
                    endMs = run.endMs,
                )
            }?.takeIf { it.startMs >= outroSearchStartMs }
        } else null

        return listOfNotNull(intro, outro)
    }

    private data class Window(val startSample: Int, val rmsDb: Double, val sampleRate: Int) {
        val startMs: Long get() = (startSample.toLong() * 1000L / sampleRate)
        val endMs: Long get() = startMs + 100
    }

    private data class Run(val startMs: Long, val endMs: Long)

    private fun computeWindows(samples: ShortArray, windowSize: Int, sampleRate: Int): List<Window> {
        val windows = mutableListOf<Window>()
        var i = 0
        while (i < samples.size) {
            val end = (i + windowSize).coerceAtMost(samples.size)
            var sumSquares = 0.0
            for (j in i until end) {
                val v = samples[j].toDouble()
                sumSquares += v * v
            }
            val count = (end - i).coerceAtLeast(1)
            val meanSquares = sumSquares / count
            val rms = sqrt(meanSquares)
            val db = if (rms <= 0.0) Double.NEGATIVE_INFINITY else 20.0 * log10(rms / Short.MAX_VALUE)
            windows += Window(startSample = i, rmsDb = db, sampleRate = sampleRate)
            i += windowSize
        }
        return windows
    }

    private fun findSilenceRunStartingInRange(
        windows: List<Window>,
        silentIndices: Set<Int>,
        minRunMs: Long,
        candidateEndMs: Long,
    ): Run? {
        var i = 0
        while (i < windows.size) {
            if (i in silentIndices && windows[i].startMs < candidateEndMs) {
                // Walk to the end of the silent run.
                var j = i
                while (j < windows.size && j in silentIndices) j++
                val startMs = windows[i].startMs
                // The run ends at the *end* of the last silent window
                // (window.startMs + windowSizeMs), not at the start
                // of the next one. Use the Window.endMs helper.
                val endMs = if (j - 1 in windows.indices) windows[j - 1].endMs else windows[i].endMs
                if (endMs - startMs >= minRunMs) {
                    return Run(startMs, endMs)
                }
                i = j
            } else {
                i++
            }
        }
        return null
    }

    private fun findSilenceRunEndingInRange(
        windows: List<Window>,
        silentIndices: Set<Int>,
        minRunMs: Long,
        candidateStartMs: Long,
    ): Run? {
        // Walk runs right-to-left: find any silent run that ends
        // at or after candidateStartMs.
        var i = windows.size - 1
        var bestRun: Run? = null
        while (i >= 0) {
            if (i in silentIndices) {
                var j = i
                while (j >= 0 && j in silentIndices) j--
                val startMs = if (j + 1 in windows.indices) windows[j + 1].startMs else windows[i].startMs
                val endMs = windows[i].endMs
                if (endMs - startMs >= minRunMs && endMs >= candidateStartMs) {
                    if (bestRun == null || endMs > bestRun.endMs) {
                        bestRun = Run(startMs, endMs)
                    }
                    // Keep scanning; we want the LAST such run.
                }
                i = j
            } else {
                i--
            }
        }
        return bestRun
    }
}

/**
 * Convert a 16-bit signed little-endian PCM byte buffer to a
 * `ShortArray`. Exposed for testing — the analyzer is decoupled
 * from `java.nio.ByteBuffer` so unit tests can hand it plain
 * `ByteArray`s.
 */
internal fun ByteArray.toShortLE(): ShortArray {
    val out = ShortArray(size / 2)
    var i = 0
    var j = 0
    while (i + 1 < size) {
        val lo = this[i].toInt() and 0xff
        val hi = this[i + 1].toInt() and 0xff
        out[j] = ((hi shl 8) or lo).toShort()
        i += 2
        j++
    }
    return out
}
