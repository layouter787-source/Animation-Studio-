package com.animationstudio.ui

import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.animationstudio.domain.Point

// Nomes usados pelo Render.kt para os pincéis importados de PNG. A ponta passou a ser CARIMBADA ao longo do traço, no
// seu tamanho, sem esticar (ver PngTipStamps.kt); estas funções só ligam os nomes antigos à implementação nova.

/** Carimbos da ponta de um pincel PNG ao longo de um traço. */
internal typealias TexStrip = TipStamps

private const val DEFAULT_TIP_SPACING = 0.25f

internal fun buildTexStrip(points: List<Point>, width: Float, tip: ImageBitmap): TexStrip? =
    buildTipStamps(points, width, tip, DEFAULT_TIP_SPACING)

/** Carimba a ponta com a cor [rgb]. Devolve sempre true (carimbar não depende do aparelho). */
internal fun DrawScope.drawTexStrip(strip: TexStrip, rgb: Color): Boolean {
    drawTipStamps(strip, rgb, BlendMode.SrcOver)
    return true
}
