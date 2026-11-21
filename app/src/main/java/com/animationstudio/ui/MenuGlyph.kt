package com.animationstudio.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap

/** Ícone do menu: duas barras de pontas redondas, a de cima mais comprida (desenho do autor). */
@Composable
internal fun MenuGlyph(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val t = size.height * 0.15f
        val x0 = t / 2f
        val x1 = size.width - t / 2f
        val x2 = x0 + (x1 - x0) * 0.66f
        drawLine(tint, Offset(x0, size.height * 0.36f), Offset(x1, size.height * 0.36f), strokeWidth = t, cap = StrokeCap.Round)
        drawLine(tint, Offset(x0, size.height * 0.64f), Offset(x2, size.height * 0.64f), strokeWidth = t, cap = StrokeCap.Round)
    }
}
