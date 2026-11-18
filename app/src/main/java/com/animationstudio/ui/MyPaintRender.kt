package com.animationstudio.ui

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.toArgb
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max
import kotlin.math.roundToInt

private const val DAB_SIDE = 64
private const val DAB_STRIDE = 11

private val maskCache = ConcurrentHashMap<Int, ImageBitmap>()

/**
 * Máscara de um toque do libmypaint (branca, com alfa): o mesmo perfil de dureza do libmypaint. Dentro do raio de
 * dureza a opacidade desce de 1 até à dureza; fora dele desce linearmente até 0 na borda.
 */
private fun dabMask(hardness: Float): ImageBitmap {
    val key = (hardness.coerceIn(0.03f, 1f) * 32f).roundToInt().coerceIn(1, 32)
    return maskCache.getOrPut(key) {
        val h = key / 32f
        val px = IntArray(DAB_SIDE * DAB_SIDE)
        val c = (DAB_SIDE - 1) / 2f
        val half = DAB_SIDE / 2f
        for (y in 0 until DAB_SIDE) {
            for (x in 0 until DAB_SIDE) {
                val dx = (x - c) / half
                val dy = (y - c) / half
                val rr = dx * dx + dy * dy
                val a = when {
                    rr >= 1f -> 0f
                    rr <= h -> rr + 1f - rr / h
                    else -> h / (1f - h) * (1f - rr)
                }
                px[y * DAB_SIDE + x] = ((a.coerceIn(0f, 1f) * 255f).roundToInt() shl 24) or 0xFFFFFF
            }
        }
        val bmp = Bitmap.createBitmap(DAB_SIDE, DAB_SIDE, Bitmap.Config.ARGB_8888)
        bmp.setPixels(px, 0, DAB_SIDE, 0, 0, DAB_SIDE, DAB_SIDE)
        bmp.asImageBitmap()
    }
}

/**
 * Desenha os toques calculados pelo libmypaint ([dabs]: 11 valores por toque, ver [MyPaintNative.computeDabs]).
 * Cada toque tem a sua cor, opacidade, dureza, razão de aspeto e ângulo. Com [mono] (onion skin ou borracha) todos
 * os toques usam [rgb].
 */
internal fun DrawScope.drawMyPaintDabs(dabs: FloatArray, rgb: Color, mono: Boolean, blend: BlendMode) {
    val n = dabs.size / DAB_STRIDE
    var lastKey = 0
    var filter: ColorFilter? = null
    for (k in 0 until n) {
        val o = k * DAB_STRIDE
        val radius = dabs[o + 2]
        val opaque = dabs[o + 6]
        if (radius < 0.05f || opaque < 0.002f) continue
        val color = if (mono) rgb else Color(dabs[o + 3].coerceIn(0f, 1f), dabs[o + 4].coerceIn(0f, 1f), dabs[o + 5].coerceIn(0f, 1f))
        val key = color.toArgb()
        if (filter == null || key != lastKey) {
            filter = ColorFilter.tint(color, BlendMode.SrcIn)
            lastKey = key
        }
        val aspect = max(1f, dabs[o + 9])
        val sx = 2f * radius / DAB_SIDE
        val sy = 2f * radius / aspect / DAB_SIDE
        val x = dabs[o]
        val y = dabs[o + 1]
        val angle = dabs[o + 10]
        val mask = dabMask(dabs[o + 7])
        withTransform({
            translate(x, y)
            rotate(angle, Offset.Zero)
            scale(sx, sy, Offset.Zero)
            translate(-DAB_SIDE / 2f, -DAB_SIDE / 2f)
        }) {
            drawImage(mask, alpha = opaque.coerceIn(0f, 1f), colorFilter = filter, blendMode = blend)
        }
    }
}
