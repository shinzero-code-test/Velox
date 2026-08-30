package com.exapps.velox.core.audioanalysis

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Phase 3 / Wave 3 / Round 2 — PCM decoder for the audio
 * analyzer. Decodes the audio track of a media file to 16-bit
 * signed little-endian PCM at 22 050 Hz mono, which is what
 * the [SilenceDetector] / [ChapterDetector] expect.
 *
 * Uses [MediaExtractor] + [MediaCodec] synchronously on the
 * calling thread (the analyzer's host coroutine hops to
 * `Dispatchers.Default` before calling this). Synchronous
 * MediaCodec is fine for the analyzer's use case — the file
 * is read once, processed, and the result discarded.
 *
 * The output is a `ShortArray` instead of `ByteArray` for
 * precision: the analyzers work in dBFS, which is computed
 * from sample values, and shorts map 1:1 to those values.
 */
@Singleton
class AndroidPcmDecoder @Inject constructor() {

    /**
     * @return Decoded PCM plus the sample rate and total
     *   duration in milliseconds. Returns null if the file
     *   has no decodable audio track (e.g. a malformed file).
     */
    suspend fun decode(
        uri: String,
        targetSampleRate: Int = TARGET_SAMPLE_RATE,
    ): DecodedAudio? = withContext(Dispatchers.Default) {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(uri)
        } catch (e: Exception) {
            extractor.release()
            return@withContext null
        }

        try {
            val trackIndex = findAudioTrack(extractor)
            if (trackIndex < 0) {
                extractor.release()
                return@withContext null
            }
            extractor.selectTrack(trackIndex)
            val format = extractor.getTrackFormat(trackIndex)
            val sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            val durationUs = if (format.containsKey(MediaFormat.KEY_DURATION)) {
                format.getLong(MediaFormat.KEY_DURATION)
            } else 0L

            val mime = format.getString(MediaFormat.KEY_MIME) ?: return@withContext null
            val codec = MediaCodec.createDecoderByType(mime)
            codec.configure(format, null, null, 0)
            codec.start()

            val pcm = decodeAll(extractor, codec, targetSampleRate, sampleRate)
            codec.stop()
            codec.release()
            extractor.release()

            DecodedAudio(
                pcm = pcm,
                sampleRate = targetSampleRate,
                durationMs = durationUs / 1000L,
            )
        } catch (e: Exception) {
            extractor.release()
            null
        }
    }

    private fun findAudioTrack(extractor: MediaExtractor): Int {
        for (i in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(i)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
            if (mime.startsWith("audio/")) return i
        }
        return -1
    }

    private fun decodeAll(
        extractor: MediaExtractor,
        codec: MediaCodec,
        targetSampleRate: Int,
        sourceSampleRate: Int,
    ): ShortArray {
        val out = mutableListOf<Short>()
        val bufferInfo = MediaCodec.BufferInfo()
        var inputDone = false
        var outputDone = false
        val timeoutUs = 10_000L

        while (!outputDone) {
            if (!inputDone) {
                val inputIndex = codec.dequeueInputBuffer(timeoutUs)
                if (inputIndex >= 0) {
                    val inputBuffer = codec.getInputBuffer(inputIndex)
                        ?: break
                    val sampleSize = extractor.readSampleData(inputBuffer, 0)
                    if (sampleSize < 0) {
                        codec.queueInputBuffer(
                            inputIndex,
                            0, 0, 0,
                            MediaCodec.BUFFER_FLAG_END_OF_STREAM,
                        )
                        inputDone = true
                    } else {
                        codec.queueInputBuffer(
                            inputIndex,
                            0, sampleSize, extractor.sampleTime, 0,
                        )
                        extractor.advance()
                    }
                }
            }
            val outputIndex = codec.dequeueOutputBuffer(bufferInfo, timeoutUs)
            if (outputIndex >= 0) {
                val outputBuffer = codec.getOutputBuffer(outputIndex)
                if (outputBuffer != null && bufferInfo.size > 0) {
                    outputBuffer.position(bufferInfo.offset)
                    outputBuffer.limit(bufferInfo.offset + bufferInfo.size)
                    val shortBuffer = outputBuffer.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                    val frameCount = shortBuffer.remaining()
                    if (sourceSampleRate == targetSampleRate) {
                        for (i in 0 until frameCount) out += shortBuffer.get()
                    } else {
                        // Cheap decimation: pick every Nth sample.
                        // A proper resampler (windowed sinc) is out
                        // of scope for Round 2; the analyzers only
                        // need the envelope, not the bit-exact
                        // waveform.
                        val step = sourceSampleRate.toDouble() / targetSampleRate.toDouble()
                        var pos = 0.0
                        while (pos < frameCount) {
                            out += shortBuffer.get(pos.toInt())
                            pos += step
                        }
                    }
                }
                codec.releaseOutputBuffer(outputIndex, false)
                if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                    outputDone = true
                }
            }
        }
        return out.toShortArray()
    }

    private companion object {
        const val TARGET_SAMPLE_RATE = 22_050
    }
}

data class DecodedAudio(
    val pcm: ShortArray,
    val sampleRate: Int,
    val durationMs: Long,
) {
    fun toBytes(): ByteArray {
        val bytes = ByteArray(pcm.size * 2)
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        for (sample in pcm) buffer.putShort(sample)
        return bytes
    }
}
