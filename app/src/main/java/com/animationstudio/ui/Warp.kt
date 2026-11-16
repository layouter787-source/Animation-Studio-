package com.animationstudio.ui

import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Matrix as AndroidMatrix
import android.graphics.Paint as AndroidPaint
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import com.animationstudio.domain.Affine
import com.animationstudio.domain.ImageData
import com.animationstudio.domain.ImageSource
import com.animationstudio.domain.Lasso
import com.animationstudio.domain.Point
import com.animationstudio.domain.QuadTransform
import java.io.ByteArrayOutputStream
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

private val warpPaint = AndroidPaint().apply {
    isAntiAlias = true
    isFilterBitmap = true
    isDither = true
}

private fun quadMatrix(w: Float, h: Float, dst: FloatArray): AndroidMatrix {
    val m = AndroidMatrix()
    m.setPolyToPoly(floatArrayOf(0f, 0f, w, 0f, w, h, 0f, h), 0, dst, 0, 4)
    return m
}

/** Píxeis de saída por píxel da imagem, para uma imagem deformada para [quad] (unidades da folha) e vista a [scale]. */
private fun devicePerSource(f: ImageData, quad: List<Point>, scale: Float): Float {
    val area = max(QuadTransform.area(quad), 1e-3f)
    return sqrt(area / (f.source.w.toFloat() * f.source.h.toFloat())) * scale
}

/** Desenha a imagem flutuante deformada para o quadrilátero [quad] (perspetiva real, com anti-aliasing). */
internal fun DrawScope.drawWarped(f: ImageData, quad: List<Point>) {
    if (quad.size != 4) return
    // Ao reduzir muito a imagem usa-se uma versão já reduzida dela: sem serrilhado.
    val level = ImageMips.levelFor(devicePerSource(f, quad, ImageScale.current()))
    val bmp = ImageMips.get(f.source, level) ?: return
    val dst = floatArrayOf(quad[0].x, quad[0].y, quad[1].x, quad[1].y, quad[2].x, quad[2].y, quad[3].x, quad[3].y)
    drawContext.canvas.nativeCanvas.drawBitmap(bmp, quadMatrix(bmp.width.toFloat(), bmp.height.toFloat(), dst), warpPaint)
}

/**
 * Fixa a deformação: desenha a imagem no quadrilátero para um novo bitmap e devolve uma imagem normal, com matriz
 * afim, pronta a ser guardada no projeto.
 *
 * A resolução do resultado acompanha a da imagem de origem (píxeis da imagem por unidade da folha), e não fica
 * limitada a 1 píxel por unidade como antes: assim fixar a perspetiva não reduz a qualidade de uma seleção
 * super-amostrada. Limites: 4096 px no maior lado e [Lasso.MAX_LIFT_PIXELS] píxeis no total.
 */
internal fun bakeQuad(f: ImageData, quad: List<Point>): ImageData {
    if (quad.size != 4) return f
    val minX = quad.minOf { it.x }
    val minY = quad.minOf { it.y }
    val maxX = quad.maxOf { it.x }
    val maxY = quad.maxOf { it.y }
    val w = max(1f, maxX - minX)
    val h = max(1f, maxY - minY)
    val area = max(1f, QuadTransform.area(quad))
    val density = sqrt(f.source.w.toFloat() * f.source.h.toFloat() / area)
    val capSide = 4096f / max(w, h)
    val capPixels = sqrt(Lasso.MAX_LIFT_PIXELS.toFloat() / (w * h))
    val k = min(density, min(capSide, capPixels)).coerceAtLeast(0.05f)
    val bw = ceil(w * k).toInt().coerceIn(1, 4096)
    val bh = ceil(h * k).toInt().coerceIn(1, 4096)
    // Se foi preciso limitar a resolução, desenha a partir de uma versão reduzida da origem.
    val level = ImageMips.levelFor(k / density)
    val src = ImageMips.get(f.source, level) ?: return f
    val dst = FloatArray(8)
    for (i in 0 until 4) {
        dst[i * 2] = (quad[i].x - minX) * k
        dst[i * 2 + 1] = (quad[i].y - minY) * k
    }
    val out = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
    AndroidCanvas(out).drawBitmap(src, quadMatrix(src.width.toFloat(), src.height.toFloat(), dst), warpPaint)
    val bos = ByteArrayOutputStream()
    out.compress(Bitmap.CompressFormat.PNG, 100, bos)
    val source = ImageSource(bos.toByteArray(), bw, bh)
    source.cache = out.asImageBitmap()
    return ImageData(source, Affine(1f / k, 0f, 0f, 1f / k, minX, minY))
}
