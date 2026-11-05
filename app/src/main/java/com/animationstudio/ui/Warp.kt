package com.animationstudio.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Matrix as AndroidMatrix
import android.graphics.Paint as AndroidPaint
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import com.animationstudio.domain.Affine
import com.animationstudio.domain.ImageData
import com.animationstudio.domain.ImageSource
import com.animationstudio.domain.Point
import java.io.ByteArrayOutputStream
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

private val warpPaint = AndroidPaint().apply {
    isAntiAlias = true
    isFilterBitmap = true
    isDither = true
}

private fun sourceBitmap(f: ImageData): Bitmap? {
    val cached = f.source.cache as? ImageBitmap
    if (cached != null) return cached.asAndroidBitmap()
    val bmp = BitmapFactory.decodeByteArray(f.source.png, 0, f.source.png.size) ?: return null
    f.source.cache = bmp.asImageBitmap()
    return bmp
}

private fun quadMatrix(f: ImageData, dst: FloatArray): AndroidMatrix {
    val w = f.source.w.toFloat()
    val h = f.source.h.toFloat()
    val m = AndroidMatrix()
    m.setPolyToPoly(floatArrayOf(0f, 0f, w, 0f, w, h, 0f, h), 0, dst, 0, 4)
    return m
}

/** Desenha a imagem flutuante deformada para o quadrilátero [quad] (perspetiva real, com anti-aliasing). */
internal fun DrawScope.drawWarped(f: ImageData, quad: List<Point>) {
    if (quad.size != 4) return
    val bmp = sourceBitmap(f) ?: return
    val dst = floatArrayOf(quad[0].x, quad[0].y, quad[1].x, quad[1].y, quad[2].x, quad[2].y, quad[3].x, quad[3].y)
    drawContext.canvas.nativeCanvas.drawBitmap(bmp, quadMatrix(f, dst), warpPaint)
}

/**
 * Fixa a deformação: desenha a imagem no quadrilátero para um novo bitmap (até 4096 px no maior lado) e devolve
 * uma imagem normal, com matriz afim, pronta a ser guardada no projeto.
 */
internal fun bakeQuad(f: ImageData, quad: List<Point>): ImageData {
    if (quad.size != 4) return f
    val minX = quad.minOf { it.x }
    val minY = quad.minOf { it.y }
    val maxX = quad.maxOf { it.x }
    val maxY = quad.maxOf { it.y }
    val w = max(1f, maxX - minX)
    val h = max(1f, maxY - minY)
    val k = min(1f, 4096f / max(w, h))
    val bw = ceil(w * k).toInt().coerceIn(1, 4096)
    val bh = ceil(h * k).toInt().coerceIn(1, 4096)
    val src = sourceBitmap(f) ?: return f
    val dst = FloatArray(8)
    for (i in 0 until 4) {
        dst[i * 2] = (quad[i].x - minX) * k
        dst[i * 2 + 1] = (quad[i].y - minY) * k
    }
    val out = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
    AndroidCanvas(out).drawBitmap(src, quadMatrix(f, dst), warpPaint)
    val bos = ByteArrayOutputStream()
    out.compress(Bitmap.CompressFormat.PNG, 100, bos)
    return ImageData(ImageSource(bos.toByteArray(), bw, bh), Affine(1f / k, 0f, 0f, 1f / k, minX, minY))
}
