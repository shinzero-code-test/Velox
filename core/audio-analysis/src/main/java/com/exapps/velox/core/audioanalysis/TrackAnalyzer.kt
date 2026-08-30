package com.exapps.velox.core.audioanalysis

import com.exapps.velox.core.domain.audio.TrackAnalysisResult
import com.exapps.velox.core.domain.audio.TrackAnalyzer
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Phase 3 / Wave 3 / Round 2 — Default implementation of the
 * [TrackAnalyzer] port. Bundles [SilenceDetector] and
 * [ChapterDetector] into a single call so the player stack and
 * the analysis service only inject one type.
 */
@Singleton
class DefaultTrackAnalyzer @Inject constructor(
    private val silenceDetector: SilenceDetector,
    private val chapterDetector: ChapterDetector,
) : TrackAnalyzer {
    override suspend fun analyze(
        mediaItemId: Long,
        pcm: ByteArray,
        sampleRate: Int,
        trackDurationMs: Long,
    ): TrackAnalysisResult = TrackAnalysisResult(
        intros = silenceDetector.detect(mediaItemId, pcm, sampleRate, trackDurationMs),
        chapters = chapterDetector.detect(mediaItemId, pcm, sampleRate, trackDurationMs),
    )
}
