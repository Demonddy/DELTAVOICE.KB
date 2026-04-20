package com.deltavoice

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Helper to extract audio from video, process it, and mux processed audio back with the original video
 * so users get a full video output (not just audio).
 */
object VideoProcessingHelper {

    /**
     * Extract audio track from video file for processing.
     */
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

    /**
     * Mux original video (visuals) with processed audio into a final MP4.
     * Returns the output video file, or null if muxing fails.
     */
    suspend fun muxVideoWithProcessedAudio(
        originalVideoFile: File,
        processedAudioFile: File,
        cacheDir: File
    ): File? = withContext(Dispatchers.IO) {
        try {
            val outputFile = File(cacheDir, "final_video_${System.currentTimeMillis()}.mp4")

            val videoExtractor = android.media.MediaExtractor()
            videoExtractor.setDataSource(originalVideoFile.absolutePath)

            var videoTrackIndex = -1
            var videoFormat: android.media.MediaFormat? = null
            for (i in 0 until videoExtractor.trackCount) {
                val fmt = videoExtractor.getTrackFormat(i)
                if (fmt.getString(android.media.MediaFormat.KEY_MIME)?.startsWith("video/") == true) {
                    videoTrackIndex = i
                    videoFormat = fmt
                    break
                }
            }
            if (videoTrackIndex == -1 || videoFormat == null) {
                videoExtractor.release()
                return@withContext null
            }
            videoExtractor.selectTrack(videoTrackIndex)

            val audioExtractor = android.media.MediaExtractor()
            audioExtractor.setDataSource(processedAudioFile.absolutePath)

            var audioTrackIndex = -1
            var audioFormat: android.media.MediaFormat? = null
            for (i in 0 until audioExtractor.trackCount) {
                val fmt = audioExtractor.getTrackFormat(i)
                if (fmt.getString(android.media.MediaFormat.KEY_MIME)?.startsWith("audio/") == true) {
                    audioTrackIndex = i
                    audioFormat = fmt
                    break
                }
            }
            if (audioTrackIndex == -1 || audioFormat == null) {
                videoExtractor.release()
                audioExtractor.release()
                return@withContext null
            }
            val audioMime = audioFormat.getString(android.media.MediaFormat.KEY_MIME) ?: ""
            if (audioMime.contains("mpeg", ignoreCase = true) && !audioMime.contains("mp4a", ignoreCase = true)) {
                videoExtractor.release()
                audioExtractor.release()
                return@withContext null
            }
            audioExtractor.selectTrack(audioTrackIndex)

            val muxer = android.media.MediaMuxer(
                outputFile.absolutePath,
                android.media.MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4
            )
            val muxVideoTrack = muxer.addTrack(videoFormat)
            val muxAudioTrack = muxer.addTrack(audioFormat)
            muxer.start()

            val bufferSize = 1024 * 1024
            val videoBuffer = java.nio.ByteBuffer.allocate(bufferSize)
            val audioBuffer = java.nio.ByteBuffer.allocate(bufferSize)
            val bufferInfo = android.media.MediaCodec.BufferInfo()

            // MediaMuxer requires samples in presentation-time order across all tracks (interleaved).
            var videoSize = videoExtractor.readSampleData(videoBuffer, 0)
            var audioSize = audioExtractor.readSampleData(audioBuffer, 0)
            var videoEOS = videoSize < 0
            var audioEOS = audioSize < 0

            while (!videoEOS || !audioEOS) {
                when {
                    videoEOS && audioEOS -> break
                    videoEOS -> {
                        bufferInfo.offset = 0
                        bufferInfo.size = audioSize
                        bufferInfo.presentationTimeUs = audioExtractor.sampleTime
                        bufferInfo.flags = audioExtractor.sampleFlags
                        muxer.writeSampleData(muxAudioTrack, audioBuffer, bufferInfo)
                        audioExtractor.advance()
                        audioSize = audioExtractor.readSampleData(audioBuffer, 0)
                        audioEOS = audioSize < 0
                    }
                    audioEOS -> {
                        bufferInfo.offset = 0
                        bufferInfo.size = videoSize
                        bufferInfo.presentationTimeUs = videoExtractor.sampleTime
                        bufferInfo.flags = videoExtractor.sampleFlags
                        muxer.writeSampleData(muxVideoTrack, videoBuffer, bufferInfo)
                        videoExtractor.advance()
                        videoSize = videoExtractor.readSampleData(videoBuffer, 0)
                        videoEOS = videoSize < 0
                    }
                    else -> {
                        val vPts = videoExtractor.sampleTime
                        val aPts = audioExtractor.sampleTime
                        if (vPts <= aPts) {
                            bufferInfo.offset = 0
                            bufferInfo.size = videoSize
                            bufferInfo.presentationTimeUs = vPts
                            bufferInfo.flags = videoExtractor.sampleFlags
                            muxer.writeSampleData(muxVideoTrack, videoBuffer, bufferInfo)
                            videoExtractor.advance()
                            videoSize = videoExtractor.readSampleData(videoBuffer, 0)
                            videoEOS = videoSize < 0
                        } else {
                            bufferInfo.offset = 0
                            bufferInfo.size = audioSize
                            bufferInfo.presentationTimeUs = aPts
                            bufferInfo.flags = audioExtractor.sampleFlags
                            muxer.writeSampleData(muxAudioTrack, audioBuffer, bufferInfo)
                            audioExtractor.advance()
                            audioSize = audioExtractor.readSampleData(audioBuffer, 0)
                            audioEOS = audioSize < 0
                        }
                    }
                }
            }

            muxer.stop()
            muxer.release()
            videoExtractor.release()
            audioExtractor.release()

            outputFile
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Decode any supported audio file (MP3, WAV, etc.) to AAC in an M4A container for [muxVideoWithProcessedAudio].
     * Returns null if conversion fails. If the source is already AAC in MP4, returns [sourceFile].
     */
    suspend fun convertAudioToAacForMux(sourceFile: File, cacheDir: File): File? = withContext(Dispatchers.IO) {
        try {
            val aacFile = File(cacheDir, "converted_audio_${System.currentTimeMillis()}.m4a")

            val extractor = android.media.MediaExtractor()
            extractor.setDataSource(sourceFile.absolutePath)

            var trackIndex = -1
            var inputFormat: android.media.MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val fmt = extractor.getTrackFormat(i)
                if (fmt.getString(android.media.MediaFormat.KEY_MIME)?.startsWith("audio/") == true) {
                    trackIndex = i
                    inputFormat = fmt
                    break
                }
            }
            if (trackIndex == -1 || inputFormat == null) {
                extractor.release()
                return@withContext null
            }
            extractor.selectTrack(trackIndex)

            val sampleRate = try {
                inputFormat.getInteger(android.media.MediaFormat.KEY_SAMPLE_RATE)
            } catch (_: Exception) { 44100 }
            val channelCount = try {
                inputFormat.getInteger(android.media.MediaFormat.KEY_CHANNEL_COUNT)
            } catch (_: Exception) { 1 }

            val inputMime = inputFormat.getString(android.media.MediaFormat.KEY_MIME) ?: "audio/mpeg"
            if (inputMime.contains("mp4a", ignoreCase = true) || inputMime == "audio/mp4a-latm") {
                extractor.release()
                return@withContext sourceFile
            }
            val decoder = android.media.MediaCodec.createDecoderByType(inputMime)
            decoder.configure(inputFormat, null, null, 0)
            decoder.start()

            val outputFormat = android.media.MediaFormat.createAudioFormat("audio/mp4a-latm", sampleRate, channelCount)
            outputFormat.setInteger(android.media.MediaFormat.KEY_BIT_RATE, 128000)
            outputFormat.setInteger(android.media.MediaFormat.KEY_AAC_PROFILE, android.media.MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            val encoder = android.media.MediaCodec.createEncoderByType("audio/mp4a-latm")
            encoder.configure(outputFormat, null, null, android.media.MediaCodec.CONFIGURE_FLAG_ENCODE)
            encoder.start()

            val muxer = android.media.MediaMuxer(aacFile.absolutePath, android.media.MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            var muxerTrackIndex = -1
            var muxerStarted = false

            val timeoutUs = 10000L
            val bufferInfo = android.media.MediaCodec.BufferInfo()
            var inputDone = false
            var decoderDone = false
            var allDone = false

            while (!allDone) {
                if (!inputDone) {
                    val inIdx = decoder.dequeueInputBuffer(timeoutUs)
                    if (inIdx >= 0) {
                        val inBuf = decoder.getInputBuffer(inIdx)!!
                        val sampleSize = extractor.readSampleData(inBuf, 0)
                        if (sampleSize < 0) {
                            decoder.queueInputBuffer(inIdx, 0, 0, 0, android.media.MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            decoder.queueInputBuffer(inIdx, 0, sampleSize, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }

                if (!decoderDone) {
                    val outIdx = decoder.dequeueOutputBuffer(bufferInfo, timeoutUs)
                    if (outIdx >= 0) {
                        val decoded = decoder.getOutputBuffer(outIdx)!!
                        val isEos = bufferInfo.flags and android.media.MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0

                        if (bufferInfo.size > 0) {
                            decoded.position(bufferInfo.offset)
                            decoded.limit(bufferInfo.offset + bufferInfo.size)
                            var pending = bufferInfo.size
                            var feedOffset = 0
                            val bytesPerSample = (channelCount * 2).coerceAtLeast(1)

                            while (pending > 0) {
                                val encInIdx = encoder.dequeueInputBuffer(timeoutUs)
                                if (encInIdx < 0) break
                                val encBuf = encoder.getInputBuffer(encInIdx)!!
                                encBuf.clear()
                                val toCopy = minOf(pending, encBuf.capacity())
                                decoded.limit(decoded.position() + toCopy)
                                encBuf.put(decoded)
                                val pts = if (sampleRate > 0) bufferInfo.presentationTimeUs + (feedOffset * 1_000_000L / (sampleRate * bytesPerSample))
                                    else bufferInfo.presentationTimeUs
                                encoder.queueInputBuffer(encInIdx, 0, toCopy, pts, 0)
                                feedOffset += toCopy
                                pending -= toCopy
                            }
                        }
                        decoder.releaseOutputBuffer(outIdx, false)
                        if (isEos) {
                            val encInIdx = encoder.dequeueInputBuffer(timeoutUs)
                            if (encInIdx >= 0) {
                                encoder.queueInputBuffer(encInIdx, 0, 0, 0, android.media.MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            }
                            decoderDone = true
                        }
                    }
                }

                val encOutIdx = encoder.dequeueOutputBuffer(bufferInfo, timeoutUs)
                if (encOutIdx == android.media.MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    if (!muxerStarted) {
                        muxerTrackIndex = muxer.addTrack(encoder.outputFormat)
                        muxer.start()
                        muxerStarted = true
                    }
                } else if (encOutIdx >= 0) {
                    val encData = encoder.getOutputBuffer(encOutIdx)!!
                    if (muxerStarted && bufferInfo.size > 0) {
                        muxer.writeSampleData(muxerTrackIndex, encData, bufferInfo)
                    }
                    val isEos = bufferInfo.flags and android.media.MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                    encoder.releaseOutputBuffer(encOutIdx, false)
                    if (isEos) allDone = true
                }
            }

            decoder.stop(); decoder.release()
            encoder.stop(); encoder.release()
            if (muxerStarted) { muxer.stop(); muxer.release() }
            extractor.release()

            aacFile
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Convert MP3 to AAC for MediaMuxer compatibility.
     * MediaMuxer's MPEG-4 output only accepts AAC; raw MP3 cannot be muxed directly.
     * Falls back to original file if conversion fails.
     */
    suspend fun convertMp3ToAac(mp3File: File, cacheDir: File): File =
        convertAudioToAacForMux(mp3File, cacheDir) ?: mp3File
}
