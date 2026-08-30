package com.exapps.velox.core.domain.audio

import kotlinx.coroutines.flow.Flow

/**
 * Phase 3 / Wave 3 / Round 2 — Service port for the audio analysis
 * pipeline. The plan says "Detection runs once per track the
 * first time it's loaded". [TrackAnalysisService] is the seam
 * that the rest of the app talks to: the player stack calls
 * [scheduleFirstListenAnalysis] after the first
 * `STATE_READY` for a new `mediaId`; the analysis runs in the
 * background and writes its results to the Room store.
 *
 * The actual PCM decode (using `MediaCodec` to read the file's
 * audio track and downsample to 16-bit PCM) lives behind the
 * port — the analyzer takes raw `ByteArray` + `sampleRate` +
 * `durationMs`, and the implementation figures out how to
 * produce that from the file URI.
 */
interface TrackAnalysisService {

    /**
     * Hot, conflated stream of [IntroOutro] rows for [mediaItemId].
     * The first emission is whatever's in the Room store (or
     * empty when the analysis hasn't run yet). Re-emits on
     * every write.
     */
    fun observeIntros(mediaItemId: Long): Flow<List<IntroOutro>>

    fun observeChapters(mediaItemId: Long): Flow<List<Chapter>>

    /**
     * Schedule a one-shot analysis for [mediaItemId]. The call
     * is non-blocking: the actual analysis happens on a
     * background dispatcher. Calling this twice for the same
     * track is idempotent (the analyzer's REPLACE-on-conflict
     * upsert makes a re-run a refresh, not a duplicate).
     */
    fun scheduleFirstListenAnalysis(mediaItemId: Long, mediaUri: String)

    /**
     * Phase 3 / Wave 3 / Round 3.5c — chapter-only analysis. The
     * PCM-decode + silence detection is skipped (it's a separate
     * call); only the [com.exapps.velox.core.audioanalysis.ChapterDetector]
     * runs. Used by the player controller when the
     * `autoChapterGenerationEnabled` Settings toggle is on but
     * the user has smart-silence disabled.
     */
    fun scheduleChapterOnlyAnalysis(mediaItemId: Long, mediaUri: String)

    /**
     * One-shot read of the intro row for the given kind. Returns
     * `null` if no analysis has been persisted for this track.
     * The player stack calls this on `play()` to decide whether
     * to skip the intro on this listen.
     */
    suspend fun getIntroOutro(mediaItemId: Long, kind: IntroOutroKind): IntroOutro?

    /**
     * Phase 3 / Wave 3 / Round 3.5e — bulk-delete every
     * auto-detected chapter across all tracks. Sidecar
     * `.chapters.txt` and embedded chapters are unaffected (they
     * aren't stored in the auto-chapter table). Returns the
     * number of rows removed so the Settings UI can show a
     * confirmation.
     */
    suspend fun clearAllAutoChapters(): Int
}
