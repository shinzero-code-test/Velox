package com.exapps.velox.core.audioanalysis

import com.exapps.velox.core.domain.audio.IntroOutroKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sin

/**
 * Phase 3 / Wave 3 / Round 2 — Silence detector contract tests.
 * The detector is pure-Kotlin; the tests synthesize PCM in memory
 * and feed it through [SilenceDetector.detect].
 *
 * Test PCM shape: 44 100 Hz mono, 16-bit signed little-endian.
 * A 4-second silence at t=2s (silence = all-zero samples) is
 * the canonical "intro" case from the plan.
 */
class SilenceDetectorTest {

    @Test
    fun `detect returns empty for empty PCM`() {
        val result = SilenceDetector().detect(
            mediaItemId = 1L,
            pcm = ByteArray(0),
            sampleRate = 44_100,
            trackDurationMs = 60_000L,
        )
        assertTrue(result.isEmpty())
    }

    @Test
    fun `detect returns empty for all-loud PCM`() {
        // 60 seconds of 440 Hz sine wave at -10 dBFS amplitude.
        val pcm = synthesizeSine(
            durationSeconds = 60,
            frequencyHz = 440.0,
            amplitudeDb = -10.0,
            sampleRate = 44_100,
        )
        val result = SilenceDetector().detect(
            mediaItemId = 1L,
            pcm = pcm,
            sampleRate = 44_100,
            trackDurationMs = 60_000L,
        )
        assertTrue("loud PCM should not produce intros; got $result", result.isEmpty())
    }

    @Test
    fun `detect finds an intro after 2 seconds of audio and 4 seconds of silence`() {
        // 2s of 440 Hz @ -10 dBFS, then 4s of silence, then 4s of audio.
        val sampleRate = 44_100
        val intro = synthesizeSine(2.0, 440.0, -10.0, sampleRate)
        val silence = ByteArray(2 * 4 * sampleRate) // 4s of zeros
        val outro = synthesizeSine(4.0, 880.0, -10.0, sampleRate)
        val pcm = intro + silence + outro

        val result = SilenceDetector().detect(
            mediaItemId = 42L,
            pcm = pcm,
            sampleRate = sampleRate,
            trackDurationMs = 10_000L,
        )

        val introResult = result.firstOrNull { it.kind == IntroOutroKind.INTRO }
        assertNotNull("expected an INTRO detection", introResult)
        assertEquals(42L, introResult!!.mediaItemId)
        // The intro run starts at ~2.0s and ends at ~6.0s; allow
        // ±200 ms jitter from the 100 ms windowing.
        assertTrue(
            "intro start should be ~2000 ms, was ${introResult.startMs}",
            introResult.startMs in 1_900L..2_200L,
        )
        assertTrue(
            "intro end should be ~6000 ms, was ${introResult.endMs}",
            introResult.endMs in 5_800L..6_200L,
        )
    }

    @Test
    fun `detect returns null intros when the silence is shorter than the 2 s minimum`() {
        // 1.5 s of silence is below the [minRunMs] threshold.
        val sampleRate = 44_100
        val intro = synthesizeSine(2.0, 440.0, -10.0, sampleRate)
        val silence = ByteArray((1.5 * sampleRate * 2).toInt())
        val outro = synthesizeSine(4.0, 880.0, -10.0, sampleRate)
        val pcm = intro + silence + outro

        val result = SilenceDetector().detect(
            mediaItemId = 1L,
            pcm = pcm,
            sampleRate = sampleRate,
            trackDurationMs = 8_000L,
        )
        assertNull("expected no intro for a 1.5s silence; got $result", result.firstOrNull { it.kind == IntroOutroKind.INTRO })
    }

    @Test
    fun `detect returns no intro when the silence run starts after the 30 s candidate window`() {
        // 35s of audio, then 4s of silence. The silence run starts
        // past the 30s candidate window; the detector rejects it.
        val sampleRate = 44_100
        val pcm = synthesizeSine(35.0, 440.0, -10.0, sampleRate) +
            ByteArray(4 * 2 * sampleRate)
        val result = SilenceDetector().detect(
            mediaItemId = 1L,
            pcm = pcm,
            sampleRate = sampleRate,
            trackDurationMs = 39_000L,
        )
        assertNull(
            "expected no intro for a late silence run; got $result",
            result.firstOrNull { it.kind == IntroOutroKind.INTRO },
        )
    }

    /**
     * Synthesize a sine wave PCM buffer of [durationSeconds] at
     * [frequencyHz] with RMS amplitude calibrated to [amplitudeDb]
     * dBFS.
     */
    private fun synthesizeSine(
        durationSeconds: Double,
        frequencyHz: Double,
        amplitudeDb: Double,
        sampleRate: Int,
    ): ByteArray {
        val totalSamples = (durationSeconds * sampleRate).toInt()
        val bytes = ByteArray(totalSamples * 2)
        val amplitudeLinear = Math.pow(10.0, amplitudeDb / 20.0).toFloat()
        val peak = Short.MAX_VALUE.toFloat() * amplitudeLinear
        val phaseStep = 2.0 * Math.PI * frequencyHz / sampleRate
        var phase = 0.0
        var i = 0
        while (i < totalSamples) {
            val sample = (peak * sin(phase)).toInt().toShort()
            bytes[i * 2] = (sample.toInt() and 0xff).toByte()
            bytes[i * 2 + 1] = ((sample.toInt() shr 8) and 0xff).toByte()
            phase += phaseStep
            i++
        }
        return bytes
    }
}
