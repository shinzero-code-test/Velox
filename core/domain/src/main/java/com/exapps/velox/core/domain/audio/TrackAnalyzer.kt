package com.exapps.velox.core.domain.audio

/**
 * Phase 3 / Wave 3 / Round 2 — Port for the audio-analysis
 * pipeline. The player stack (and any future caller) talks to
 * this; the implementation lives in `:core:audio-analysis`.
 */
interface TrackAnalyzer {
    suspend fun analyze(
        mediaItemId: Long,
        pcm: ByteArray,
        sampleRate: Int,
        trackDurationMs: Long,
    ): TrackAnalysisResult
}

data class TrackAnalysisResult(
    val intros: List<IntroOutro>,
    val chapters: List<Chapter>,
)
