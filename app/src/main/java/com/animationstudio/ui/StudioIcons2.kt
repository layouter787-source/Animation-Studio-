package com.animationstudio.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/** Ícone do menu: painel arredondado com a metade esquerda preenchida. */
@Composable
internal fun PanelGlyph(tint: Color, modifier: Modifier = Modifier.size(18.dp)) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        drawRoundRect(tint, Offset(w * 0.08f, h * 0.2f), Size(w * 0.84f, h * 0.6f), CornerRadius(w * 0.14f), style = Stroke(width = w * 0.09f))
        drawRoundRect(tint, Offset(w * 0.08f, h * 0.2f), Size(w * 0.38f, h * 0.6f), CornerRadius(w * 0.14f))
    }
}

/** Seis pontos (2×3): pega para mover uma barra. */
@Composable
internal fun GripGlyph(tint: Color, modifier: Modifier = Modifier.size(14.dp)) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        for (c in 0 until 2) {
            for (r in 0 until 3) {
                drawCircle(tint, w * 0.09f, Offset(w * (0.32f + 0.36f * c), h * (0.2f + 0.3f * r)))
            }
        }
    }
}

/** Três pontos na vertical (mais opções). */
@Composable
internal fun DotsGlyph(tint: Color, modifier: Modifier = Modifier.size(14.dp)) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        for (r in 0 until 3) drawCircle(tint, w * 0.09f, Offset(w * 0.5f, h * (0.2f + 0.3f * r)))
    }
}

/** Quadrado tracejado de frame com "+" (novo) ou prancheta (colar) e seta para o lado. */
@Composable
internal fun FrameActionGlyph(tint: Color, toRight: Boolean, paste: Boolean, modifier: Modifier = Modifier.size(22.dp)) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val dash = PathEffect.dashPathEffect(floatArrayOf(w * 0.12f, w * 0.08f))
        drawRoundRect(tint, Offset(w * 0.2f, h * 0.12f), Size(w * 0.6f, h * 0.6f), CornerRadius(w * 0.08f), style = Stroke(width = w * 0.07f, pathEffect = dash))
        val cx = w * 0.5f
        val cy = h * 0.42f
        if (!paste) {
            drawLine(tint, Offset(cx - w * 0.12f, cy), Offset(cx + w * 0.12f, cy), strokeWidth = w * 0.07f, cap = StrokeCap.Round)
            drawLine(tint, Offset(cx, cy - w * 0.12f), Offset(cx, cy + w * 0.12f), strokeWidth = w * 0.07f, cap = StrokeCap.Round)
        } else {
            drawRoundRect(tint, Offset(cx - w * 0.12f, cy - w * 0.14f), Size(w * 0.24f, w * 0.28f), CornerRadius(w * 0.04f), style = Stroke(width = w * 0.06f))
        }
        val y = h * 0.92f
        val x0 = w * 0.2f
        val x1 = w * 0.8f
        drawLine(tint, Offset(x0, y), Offset(x1, y), strokeWidth = w * 0.07f, cap = StrokeCap.Round)
        val tip = if (toRight) x1 else x0
        val dir = if (toRight) -1f else 1f
        drawLine(tint, Offset(tip, y), Offset(tip + dir * w * 0.14f, y - w * 0.12f), strokeWidth = w * 0.07f, cap = StrokeCap.Round)
        drawLine(tint, Offset(tip, y), Offset(tip + dir * w * 0.14f, y + w * 0.12f), strokeWidth = w * 0.07f, cap = StrokeCap.Round)
    }
}

// ---------------------------------------------------------------------------------------------
// Grelhas de ajuda ao desenho (não fazem parte do desenho nem da exportação)
// ---------------------------------------------------------------------------------------------

internal enum class GridType(val label: String) {
    NONE("Nenhuma"), GRID("Grade"), ONE("Perspetiva 1 ponto de fuga"), TWO("Perspetiva 2 pontos de fuga"),
    THREE("Perspetiva 3 pontos de fuga"), FISHEYE("Olho de peixe"),
}

private fun DrawScope.rays(vp: Offset, n: Int, color: Color, lineW: Float, far: Float) {
    for (i in 0 until n) {
        val a = 2.0 * PI * i / n
        drawLine(color, vp, Offset(vp.x + (cos(a) * far).toFloat(), vp.y + (sin(a) * far).toFloat()), lineW)
    }
}

/** Desenha a grelha em coordenadas da folha (chamar dentro de clipRect da folha). */
internal fun DrawScope.drawGridOverlay(type: GridType, cell: Float, sw: Float, sh: Float, color: Color, lineW: Float) {
    val far = 3f * max(sw, sh)
    when (type) {
        GridType.NONE -> {}
        GridType.GRID -> {
            var x = cell
            while (x < sw) { drawLine(color, Offset(x, 0f), Offset(x, sh), lineW); x += cell }
            var y = cell
            while (y < sh) { drawLine(color, Offset(0f, y), Offset(sw, y), lineW); y += cell }
        }
        GridType.ONE -> {
            val vp = Offset(sw / 2f, sh * 0.42f)
            drawLine(color, Offset(0f, vp.y), Offset(sw, vp.y), lineW * 1.5f)
            rays(vp, 28, color, lineW, far)
        }
        GridType.TWO -> {
            val y = sh * 0.5f
            drawLine(color, Offset(0f, y), Offset(sw, y), lineW * 1.5f)
            rays(Offset(-sw * 0.35f, y), 18, color, lineW, far)
            rays(Offset(sw * 1.35f, y), 18, color, lineW, far)
            var x = cell
            while (x < sw) { drawLine(color.copy(alpha = color.alpha * 0.6f), Offset(x, 0f), Offset(x, sh), lineW); x += cell }
        }
        GridType.THREE -> {
            val y = sh * 0.55f
            drawLine(color, Offset(0f, y), Offset(sw, y), lineW * 1.5f)
            rays(Offset(-sw * 0.3f, y), 14, color, lineW, far)
            rays(Offset(sw * 1.3f, y), 14, color, lineW, far)
            rays(Offset(sw / 2f, -sh * 1.1f), 14, color, lineW, far)
        }
        GridType.FISHEYE -> {
            val c = Offset(sw / 2f, sh / 2f)
            rays(c, 24, color, lineW, far)
            val step = sh / 6f
            for (k in 1..8) drawCircle(color, step * k, c, style = Stroke(width = lineW))
        }
    }
}
