package com.animationstudio.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import com.animationstudio.domain.Point
import kotlin.math.hypot
import kotlin.math.max

/**
 * Pincel importado de um PNG: a imagem é a ponta do pincel e é carimbada ao longo do traço, no seu tamanho
 * (largura do traço = lado maior da imagem), sem esticar nem deformar. É assim que funcionam os pincéis nativos de
 * carimbo: cada toque deposita a textura inteira e o espaçamento ([spacingFrac] da largura) decide se o traço sai
 * contínuo (espaço pequeno) ou como uma fila de carimbos (espaço grande).
 * [xy] tem pares (x, y): centros dos carimbos na folha. [scale] é o fator da imagem para a folha.
 */
internal class TipStamps(val xy: FloatArray, val image: ImageBitmap, val scale: Float)

private const val MAX_STAMPS = 8000

/** Centros dos carimbos ao longo de [points], a cada [spacingFrac] × [width]; null se não há pontos. */
internal fun buildTipStamps(points: List<Point>, width: Float, tip: ImageBitmap, spacingFrac: Float): TipStamps? {
    if (points.isEmpty()) return null
    val side = max(tip.width, tip.height).toFloat()
    if (side <= 0f) return null
    val w = max(0.5f, width)
    var step = max(0.4f, w * spacingFrac.coerceIn(0.03f, 1.5f))

    // Comprimento total para limitar o número de carimbos (traços muito longos com espaçamento mínimo).
    var total = 0f
    for (i in 1 until points.size) total += hypot(points[i].x - points[i - 1].x, points[i].y - points[i - 1].y)
    if (total / step > MAX_STAMPS) step = total / MAX_STAMPS

    var buf = FloatArray(256)
    var n = 0
    fun emit(x: Float, y: Float) {
        if (n + 2 > buf.size) buf = buf.copyOf(buf.size * 2)
        buf[n++] = x
        buf[n++] = y
    }

    emit(points[0].x, points[0].y)
    var next = step // distância, a partir do ponto atual, ao próximo carimbo
    for (i in 1 until points.size) {
        val a = points[i - 1]
        val b = points[i]
        val len = hypot(b.x - a.x, b.y - a.y)
        if (len < 1e-4f) continue
        var pos = next
        while (pos <= len) {
            val t = pos / len
            emit(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t)
            pos += step
        }
        next = pos - len
    }
    // O fim do traço fica sempre marcado.
    val last = points.last()
    if (points.size > 1 && step - next > step * 0.5f) emit(last.x, last.y)
    return TipStamps(buf.copyOf(n), tip, w / side)
}

/** Carimba a ponta em cada centro, com a cor [rgb] (a imagem só dá a opacidade). */
internal fun DrawScope.drawTipStamps(t: TipStamps, rgb: Color, blend: BlendMode) {
    val filter = ColorFilter.tint(rgb, BlendMode.SrcIn)
    val tw = t.image.width.toFloat()
    val th = t.image.height.toFloat()
    var i = 0
    while (i + 1 < t.xy.size) {
        val x = t.xy[i]
        val y = t.xy[i + 1]
        withTransform({
            translate(x, y)
            scale(t.scale, t.scale, Offset.Zero)
            translate(-tw / 2f, -th / 2f)
        }) {
            drawImage(t.image, colorFilter = filter, blendMode = blend)
        }
        i += 2
    }
}
