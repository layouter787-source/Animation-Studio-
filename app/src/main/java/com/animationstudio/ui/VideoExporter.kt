package com.animationstudio.ui

import android.content.Context
import android.media.Image
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.compose.ui.graphics.asAndroidBitmap
import com.animationstudio.domain.CameraMath
import com.animationstudio.domain.CameraTrack
import com.animationstudio.domain.Project
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min

/**
 * Exporta MP4 (H.264): cada frame da timeline (a duração é feita de frames repetidos) é renderizado — visto pela
 * câmara quando há keyframes — e entregue ao codificador em buffers YUV com marca de tempo exata.
 */
internal object VideoExporter {
    private fun fillYuv(image: Image, px: IntArray, w: Int, h: Int) {
        val yP = image.planes[0]
        val uP = image.planes[1]
        val vP = image.planes[2]
        val yBuf = yP.buffer
        val uBuf = uP.buffer
        val vBuf = vP.buffer
        for (y in 0 until h) {
            for (x in 0 until w) {
                val c = px[y * w + x]
                val r = (c shr 16) and 0xFF
                val g = (c shr 8) and 0xFF
                val b = c and 0xFF
                val yy = ((66 * r + 129 * g + 25 * b + 128) shr 8) + 16
                yBuf.put(y * yP.rowStride + x * yP.pixelStride, yy.coerceIn(0, 255).toByte())
                if (y % 2 == 0 && x % 2 == 0) {
                    val u = ((-38 * r - 74 * g + 112 * b + 128) shr 8) + 128
                    val v = ((112 * r - 94 * g - 18 * b + 128) shr 8) + 128
                    uBuf.put((y / 2) * uP.rowStride + (x / 2) * uP.pixelStride, u.coerceIn(0, 255).toByte())
                    vBuf.put((y / 2) * vP.rowStride + (x / 2) * vP.pixelStride, v.coerceIn(0, 255).toByte())
                }
            }
        }
    }

    suspend fun export(context: Context, uri: Uri, project: Project, camera: CameraTrack, fps: Int, sw: Int, sh: Int): Boolean =
        withContext(Dispatchers.Default) {
            var codec: MediaCodec? = null
            var muxer: MediaMuxer? = null
            var pfd: ParcelFileDescriptor? = null
            var muxerStarted = false
            try {
                val k = min(1f, 1920f / max(sw, sh))
                val w = max(16, ((sw * k).toInt() / 2) * 2)
                val h = max(16, ((sh * k).toInt() / 2) * 2)
                val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, w, h)
                format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible)
                format.setInteger(MediaFormat.KEY_BIT_RATE, (w * h * fps * 0.15f).toInt().coerceIn(1_500_000, 20_000_000))
                format.setInteger(MediaFormat.KEY_FRAME_RATE, fps)
                format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
                val enc = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
                codec = enc
                enc.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                enc.start()
                val fd = context.contentResolver.openFileDescriptor(uri, "w") ?: return@withContext false
                pfd = fd
                val mux = MediaMuxer(fd.fileDescriptor, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
                muxer = mux
                val info = MediaCodec.BufferInfo()
                var track = -1

                fun drain(end: Boolean) {
                    var waits = 0
                    while (true) {
                        val idx = enc.dequeueOutputBuffer(info, if (end) 10_000L else 0L)
                        if (idx == MediaCodec.INFO_TRY_AGAIN_LATER) {
                            if (!end) return
                            waits += 1
                            if (waits > 500) return
                        } else if (idx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                            track = mux.addTrack(enc.outputFormat)
                            mux.start()
                            muxerStarted = true
                        } else if (idx >= 0) {
                            val buf = enc.getOutputBuffer(idx)
                            if ((info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) info.size = 0
                            if (info.size > 0 && buf != null && muxerStarted) {
                                buf.position(info.offset)
                                buf.limit(info.offset + info.size)
                                mux.writeSampleData(track, buf, info)
                            }
                            enc.releaseOutputBuffer(idx, false)
                            if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) return
                        }
                    }
                }

                fun nextInput(): Int {
                    var idx = enc.dequeueInputBuffer(10_000L)
                    var tries = 0
                    while (idx < 0 && tries < 500) {
                        drain(false)
                        idx = enc.dequeueInputBuffer(10_000L)
                        tries += 1
                    }
                    return idx
                }

                val ids = project.frameIds
                val total = project.frameCount
                val frameUs = 1_000_000L / fps
                for (i in 0 until total) {
                    val cam = if (!camera.isEmpty) {
                        CameraMath.at(ids, camera, i.toFloat(), sw.toFloat(), sh.toFloat()).outputAffine(sw.toFloat(), sh.toFloat())
                    } else null
                    val bmp = renderCompositeWithCamera(project, i, sw, sh, w, h, cam).asAndroidBitmap()
                    val px = IntArray(w * h)
                    bmp.getPixels(px, 0, w, 0, 0, w, h)
                    val inIdx = nextInput()
                    if (inIdx < 0) return@withContext false
                    val image = enc.getInputImage(inIdx) ?: return@withContext false
                    fillYuv(image, px, w, h)
                    enc.queueInputBuffer(inIdx, 0, w * h * 3 / 2, i * frameUs, 0)
                    drain(false)
                }
                val eos = nextInput()
                if (eos >= 0) enc.queueInputBuffer(eos, 0, 0, total * frameUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                drain(true)
                muxerStarted
            } catch (e: Exception) {
                false
            } finally {
                try { codec?.stop() } catch (e: Exception) { }
                try { codec?.release() } catch (e: Exception) { }
                try { if (muxerStarted) muxer?.stop() } catch (e: Exception) { }
                try { muxer?.release() } catch (e: Exception) { }
                try { pfd?.close() } catch (e: Exception) { }
            }
        }
}
