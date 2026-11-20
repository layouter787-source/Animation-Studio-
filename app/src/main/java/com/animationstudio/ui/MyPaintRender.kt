package com.animationstudio.ui

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.max

// Desenho dos traços da AnimBrush chamado pelo Render.kt (ver MyPaintBrushes.kt).
//
// O motor rasteriza o traço inteiro de uma vez (7 a 150 ms por traço, conforme o pincel). Enquanto o dedo desenha, o
// traço em curso é pedido a cada imagem do ecrã; para não travar, só se rasteriza se já passou tempo suficiente desde
// o último cálculo (3× o custo dele, no mínimo 120 ms). Nos outros quadros mostra-se uma linha simples e agenda-se um
// redesenho; ao levantar o dedo o traço final é sempre calculado.

private val tick = mutableIntStateOf(0)
private val handler = Handler(Looper.getMainLooper())

@Volatile private var tickScheduled = false

@Volatile private var lastDoneMs = 0L

@Volatile private var lastCostMs = 0L

private fun scheduleRedraw(delayMs: Long) {
    if (tickScheduled) return
    tickScheduled = true
    handler.postDelayed({
        tickScheduled = false
        tick.intValue = tick.intValue + 1
    }, max(16L, delayMs))
}

internal fun DrawScope.drawMyPaintDabs(dabs: FloatArray, rgb: Color, mono: Boolean, blend: BlendMode) {
    val lz = MyPaintBrushes.lazy[dabs] ?: return
    tick.intValue // lido para que o desenho se repita quando o redesenho agendado chegar
    var img = lz.image
    if (img == null && !lz.failed) {
        val now = SystemClock.uptimeMillis()
        val wait = max(120L, lastCostMs * 3) - (now - lastDoneMs)
        if (wait <= 0L) {
            img = AnimBrushes.render(lz.abId, lz.stroke)
            val done = SystemClock.uptimeMillis()
            lastCostMs = done - now
            lastDoneMs = done
            if (img == null) lz.failed = true else lz.image = img
        } else {
            scheduleRedraw(wait)
        }
    }
    when {
        img != null -> drawAnimBrush(img, rgb, mono, blend)
        lz.failed -> Unit // o motor não pintou nada (por exemplo, borracha fora de qualquer tinta)
        else -> drawPlaceholder(lz, rgb, blend)
    }
}

/** Linha simples com o tamanho do pincel, enquanto o traço da AnimBrush não está pronto. */
private fun DrawScope.drawPlaceholder(lz: LazyAnimBrush, rgb: Color, blend: BlendMode) {
    val pts = lz.stroke.points
    val w = max(1f, lz.stroke.width)
    if (pts.size == 1) {
        drawCircle(rgb, radius = w / 2f, center = Offset(pts[0].x, pts[0].y), blendMode = blend)
        return
    }
    val path = Path()
    path.moveTo(pts[0].x, pts[0].y)
    for (i in 1 until pts.size) path.lineTo(pts[i].x, pts[i].y)
    drawPath(path, rgb, style = Stroke(width = w, cap = StrokeCap.Round, join = StrokeJoin.Round), blendMode = blend)
}
