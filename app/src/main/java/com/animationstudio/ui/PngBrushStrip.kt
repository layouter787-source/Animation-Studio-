package com.animationstudio.ui

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.Shader
import android.os.Build
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import com.animationstudio.domain.Point
import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Faixa de triângulos que segue o traço, com a imagem da ponta (PNG importado) mapeada UMA SÓ VEZ ao longo dele:
 * o eixo horizontal da imagem corre do início ao fim do traço e o vertical atravessa a largura. A ponta não é
 * carimbada nem repetida; a sua textura é que desenha a linha, como num pincel procedural.
 * [verts] e [texs] têm pares (x, y): posições na folha e coordenadas (em píxeis) na imagem [bitmap].
 */
internal class TexStrip(val verts: FloatArray, val texs: FloatArray, val bitmap: Bitmap)

private const val MAX_SAMPLES = 4000

/** Constrói a faixa do traço [points] com a largura [width]; null se não há nada para desenhar. */
internal fun buildTexStrip(points: List<Point>, width: Float, tip: ImageBitmap): TexStrip? {
    if (points.isEmpty()) return null
    val bmp = tip.asAndroidBitmap()
    val tw = bmp.width.toFloat()
    val th = bmp.height.toFloat()
    val half = max(0.5f, width) / 2f

    // Pontos distintos e comprimento acumulado.
    val xs = ArrayList<Float>(points.size)
    val ys = ArrayList<Float>(points.size)
    for (p in points) {
        if (xs.isEmpty() || hypot(p.x - xs.last(), p.y - ys.last()) > 0.01f) {
            xs.add(p.x)
            ys.add(p.y)
        }
    }
    val cum = FloatArray(xs.size)
    var total = 0f
    for (i in 1 until xs.size) {
        total += hypot(xs[i] - xs[i - 1], ys[i] - ys[i - 1])
        cum[i] = total
    }

    // Toque simples ou traço mais curto que meia largura: a ponta inteira, uma vez, no seu tamanho.
    if (xs.size < 2 || total < half * 0.5f) {
        val cx = xs[0]
        val cy = ys[0]
        return TexStrip(
            floatArrayOf(cx - half, cy - half, cx - half, cy + half, cx + half, cy - half, cx + half, cy + half),
            floatArrayOf(0f, 0f, 0f, th, tw, 0f, tw, th),
            bmp,
        )
    }

    // Reamostragem a passo uniforme ao longo do traço.
    val step = (width * 0.15f).coerceIn(0.75f, 6f)
    val m = min(MAX_SAMPLES, max(2, ceil(total / step).toInt() + 1))
    val px = FloatArray(m)
    val py = FloatArray(m)
    var seg = 1
    for (j in 0 until m) {
        val t = total * j / (m - 1)
        while (seg < xs.size - 1 && cum[seg] < t) seg++
        val len = cum[seg] - cum[seg - 1]
        val f = if (len > 1e-6f) ((t - cum[seg - 1]) / len).coerceIn(0f, 1f) else 0f
        px[j] = xs[seg - 1] + (xs[seg] - xs[seg - 1]) * f
        py[j] = ys[seg - 1] + (ys[seg] - ys[seg - 1]) * f
    }

    // Tangente suavizada numa janela (evita que a faixa se dobre nas curvas apertadas).
    val spacing = total / (m - 1)
    val win = ((half * 0.6f) / spacing).roundToInt().coerceIn(1, 8)
    val verts = FloatArray(m * 4)
    val texs = FloatArray(m * 4)
    var tx = 1f
    var ty = 0f
    for (j in 0 until m) {
        val a = max(0, j - win)
        val b = min(m - 1, j + win)
        val dx = px[b] - px[a]
        val dy = py[b] - py[a]
        val len = hypot(dx, dy)
        if (len > 1e-6f) {
            tx = dx / len
            ty = dy / len
        }
        val nx = -ty
        val ny = tx
        val u = tw * j / (m - 1)
        val o = j * 4
        verts[o] = px[j] + nx * half
        verts[o + 1] = py[j] + ny * half
        verts[o + 2] = px[j] - nx * half
        verts[o + 3] = py[j] - ny * half
        texs[o] = u
        texs[o + 1] = 0f
        texs[o + 2] = u
        texs[o + 3] = th
    }
    return TexStrip(verts, texs, bmp)
}

/**
 * Desenha a faixa com a cor [rgb] (a imagem dá só a opacidade). Devolve false se o aparelho não consegue
 * (Android 9 com aceleração por hardware não desenha malhas); nesse caso quem chama desenha uma linha simples.
 */
internal fun DrawScope.drawTexStrip(strip: TexStrip, rgb: Color): Boolean {
    val nc = drawContext.canvas.nativeCanvas
    if (Build.VERSION.SDK_INT < 29 && nc.isHardwareAccelerated) return false
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    paint.isFilterBitmap = true
    paint.shader = BitmapShader(strip.bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
    paint.colorFilter = PorterDuffColorFilter(rgb.copy(alpha = 1f).toArgb(), PorterDuff.Mode.SRC_IN)
    nc.drawVertices(
        android.graphics.Canvas.VertexMode.TRIANGLE_STRIP, strip.verts.size, strip.verts, 0, strip.texs, 0,
        null, 0, null, 0, 0, paint,
    )
    return true
}
