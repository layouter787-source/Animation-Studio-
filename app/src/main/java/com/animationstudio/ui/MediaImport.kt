package com.animationstudio.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.animationstudio.domain.Affine
import com.animationstudio.domain.ImageData
import com.animationstudio.domain.ImageSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.min

/** Importação de imagens e de frames de vídeo, ajustados à folha (sem aumentar, centrados). */
internal object MediaImport {
    private fun fit(w: Int, h: Int, sw: Int, sh: Int): Affine {
        val k = min(1f, min(sw.toFloat() / w, sh.toFloat() / h))
        return Affine(k, 0f, 0f, k, (sw - w * k) / 2f, (sh - h * k) / 2f)
    }

    private fun encode(bmp: Bitmap, jpeg: Boolean): ByteArray {
        val out = ByteArrayOutputStream()
        if (jpeg) bmp.compress(Bitmap.CompressFormat.JPEG, 85, out) else bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
        return out.toByteArray()
    }

    /** Imagem da galeria (PNG com transparência preservada), reduzida a no máximo ~2048 px. */
    suspend fun loadImage(context: Context, uri: Uri, sw: Int, sh: Int): ImageData? = withContext(Dispatchers.IO) {
        try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (bounds.outWidth / sample > 2048 || bounds.outHeight / sample > 2048) sample *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            val bmp = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) } ?: return@withContext null
            ImageData(ImageSource(encode(bmp, false), bmp.width, bmp.height), fit(bmp.width, bmp.height, sw, sh))
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Extrai frames de um vídeo ao ritmo do projeto ([fps]), até [maxFrames], reduzidos a ~960 px e guardados em JPEG
     * (para o ficheiro do projeto não crescer demais).
     */
    suspend fun loadVideoFrames(context: Context, uri: Uri, fps: Int, sw: Int, sh: Int, maxFrames: Int): List<ImageData> =
        withContext(Dispatchers.IO) {
            val out = ArrayList<ImageData>()
            val r = MediaMetadataRetriever()
            try {
                r.setDataSource(context, uri)
                val durMs = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                val n = min(maxFrames, max(1, (durMs / 1000.0 * fps).toInt()))
                for (i in 0 until n) {
                    val tUs = i * 1_000_000L / fps
                    var bmp = r.getFrameAtTime(tUs, MediaMetadataRetriever.OPTION_CLOSEST) ?: continue
                    if (bmp.width > 960) {
                        val k = 960f / bmp.width
                        bmp = Bitmap.createScaledBitmap(bmp, 960, max(1, (bmp.height * k).toInt()), true)
                    }
                    out.add(ImageData(ImageSource(encode(bmp, true), bmp.width, bmp.height), fit(bmp.width, bmp.height, sw, sh)))
                }
            } catch (e: Exception) {
                // devolve o que já foi extraído
            } finally {
                try { r.release() } catch (e: Exception) { }
            }
            out
        }
}
