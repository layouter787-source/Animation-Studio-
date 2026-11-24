package com.animationstudio.ui

import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope

// Desenho dos traços da AnimBrush chamado pelo Render.kt (ver MyPaintBrushes.kt).
//
// O traço vem sempre do motor C++ da AnimBrush: já calculado ao vivo enquanto se desenhava, ou rasterizado aqui no
// primeiro desenho (por exemplo, ao abrir um projeto). Não há linha provisória nem espera por zoom.

internal fun DrawScope.drawMyPaintDabs(dabs: FloatArray, rgb: Color, mono: Boolean, blend: BlendMode) {
    val lz = MyPaintBrushes.lazy[dabs] ?: return
    val img = MyPaintBrushes.image(lz) ?: return // o motor não pintou nada (por exemplo, borracha fora de qualquer tinta)
    drawAnimBrush(img, rgb, mono, blend)
}
