package com.deltavoice

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Helper to extract audio from video for processing.
 * Note: Video+audio muxing (FFmpeg) was removed due to dependency unavailability.
 * Processed output is audio-only (MP3).
 */
object VideoProcessingHelper {

    suspend fun extractAudioFromVideo(videoFile: File, cacheDir: File): File? = withContext(Dispatchers.IO) {
        try {
            val audioFile = File(cacheDir, "extracted_audio_${System.currentTimeMillis()}.m4a")
            val extractor = android.media.MediaExtractor()
            extractor.setDataSource(videoFile.absolutePath)

            var audioTrackIndex = -1
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(android.media.MediaFormat.KEY_MIME)
                if (mime?.startsWith("audio/") == true) {
                    audioTrackIndex = i
                    break
                }
            }

            if (audioTrackIndex == -1) {
                extractor.release()
                return@withContext null
            }

            extractor.selectTrack(audioTrackIndex)
            val format = extractor.getTrackFormat(audioTrackIndex)
            val muxer = android.media.MediaMuxer(audioFile.absolutePath, android.media.MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val audioMuxerTrack = muxer.addTrack(format)
            muxer.start()

            val bufferSize = 1024 * 1024
            val buffer = java.nio.ByteBuffer.allocate(bufferSize)
            val bufferInfo = android.media.MediaCodec.BufferInfo()

            while (true) {
                val sampleSize = extractor.readSampleData(buffer, 0)
                if (sampleSize < 0) break
                bufferInfo.offset = 0
                bufferInfo.size = sampleSize
                bufferInfo.presentationTimeUs = extractor.sampleTime
                bufferInfo.flags = extractor.sampleFlags
                muxer.writeSampleData(audioMuxerTrack, buffer, bufferInfo)
                extractor.advance()
            }

            muxer.stop()
            muxer.release()
            extractor.release()
            audioFile
        } catch (e: Exception) {
            null
        }
    }
}
