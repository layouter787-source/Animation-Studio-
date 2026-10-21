package com.animationstudio.ui

import androidx.compose.foundation.Canvas
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp

/** Ícones desenhados à mão para as ferramentas que não existem no conjunto Material. */
@Composable
internal fun EraserGlyph(tint: Color, modifier: Modifier = Modifier.then(Modifier)) {
    Canvas(modifier.sizeIcon()) {
        val w = size.width
        val h = size.height
        rotate(-45f, Offset(w / 2f, h / 2f)) {
            val left = w * 0.08f
            val top = h * 0.30f
            val bw = w * 0.84f
            val bh = h * 0.38f
            drawRoundRect(tint, Offset(left, top), Size(bw, bh), CornerRadius(w * 0.08f), style = Stroke(width = w * 0.08f))
            drawRoundRect(tint, Offset(left + bw * 0.55f, top), Size(bw * 0.45f, bh), CornerRadius(w * 0.08f))
        }
        drawLine(tint, Offset(w * 0.14f, h * 0.9f), Offset(w * 0.86f, h * 0.9f), strokeWidth = w * 0.07f, cap = StrokeCap.Round)
    }
}

@Composable
internal fun LassoGlyph(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.sizeIcon()) {
        val w = size.width
        val h = size.height
        drawOval(
            tint, Offset(w * 0.08f, h * 0.10f), Size(w * 0.84f, h * 0.55f),
            style = Stroke(width = w * 0.08f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(w * 0.14f, w * 0.09f)))
        )
        drawCircle(tint, w * 0.08f, Offset(w * 0.30f, h * 0.84f), style = Stroke(width = w * 0.06f))
        drawLine(tint, Offset(w * 0.36f, h * 0.77f), Offset(w * 0.46f, h * 0.64f), strokeWidth = w * 0.06f, cap = StrokeCap.Round)
    }
}

@Composable
internal fun TriangleGlyph(tint: Color, pointsLeft: Boolean, modifier: Modifier = Modifier) {
    Canvas(modifier.sizeIcon()) {
        val w = size.width
        val h = size.height
        val path = Path()
        if (pointsLeft) {
            path.moveTo(w * 0.72f, h * 0.15f); path.lineTo(w * 0.24f, h * 0.5f); path.lineTo(w * 0.72f, h * 0.85f)
        } else {
            path.moveTo(w * 0.28f, h * 0.15f); path.lineTo(w * 0.76f, h * 0.5f); path.lineTo(w * 0.28f, h * 0.85f)
        }
        path.close()
        drawPath(path, tint)
    }
}

@Composable
internal fun RgbGlyph(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.sizeIcon()) {
        val w = size.width
        val r = w * 0.24f
        val st = Stroke(width = w * 0.07f)
        drawCircle(tint, r, Offset(w * 0.50f, h(0.34f)), style = st)
        drawCircle(tint, r, Offset(w * 0.34f, h(0.64f)), style = st)
        drawCircle(tint, r, Offset(w * 0.66f, h(0.64f)), style = st)
    }
}

@Composable
internal fun HsbGlyph(modifier: Modifier = Modifier) {
    Canvas(modifier.sizeIcon()) {
        val w = size.width
        val colors = listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red)
        drawCircle(
            brush = Brush.sweepGradient(colors, Offset(w / 2f, size.height / 2f)),
            radius = w * 0.34f, center = Offset(w / 2f, size.height / 2f), style = Stroke(width = w * 0.18f)
        )
    }
}

@Composable
internal fun PaletteGlyph(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.sizeIcon()) {
        val w = size.width
        val cell = w * 0.24f
        val gap = w * 0.09f
        val start = (w - (3 * cell + 2 * gap)) / 2f
        for (r in 0 until 3) for (c in 0 until 3) {
            drawRoundRect(tint, Offset(start + c * (cell + gap), start + r * (cell + gap)), Size(cell, cell), CornerRadius(cell * 0.2f))
        }
    }
}

@Composable
internal fun BrushGlyph(tint: Color, modifier: Modifier = Modifier) {
    Icon(Icons.Default.Brush, contentDescription = null, tint = tint, modifier = modifier.sizeIcon())
}

private fun Modifier.sizeIcon(): Modifier = this.then(Modifier.androidxSize())

private fun Modifier.androidxSize(): Modifier = androidx.compose.foundation.layout.run { Modifier.size(22.dp) }

private fun androidx.compose.ui.graphics.drawscope.DrawScope.h(f: Float): Float = size.height * f
