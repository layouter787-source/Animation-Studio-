package com.animationstudio.ui

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.animationstudio.domain.Affine
import com.animationstudio.domain.BrushStroke
import com.animationstudio.domain.FillRegion
import com.animationstudio.domain.ImageData
import com.animationstudio.domain.ImageSource
import com.animationstudio.domain.Point
import com.animationstudio.domain.Project
import java.io.ByteArrayOutputStream
import androidx.compose.ui.graphics.Canvas as GfxCanvas
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/** Resultado de levantar uma seleção: a imagem flutuante e o elemento que apaga a região original. */
internal class LiftResult(val image: ImageData, val eraser: BrushStroke)

/**
 * Laço pixel a pixel: renderiza o que a camada tem dentro do polígono (com bordas suavizadas), devolve-o como
 * imagem transformável e uma máscara de borracha (a 2×) para retirar esse conteúdo da camada.
 * Devolve null se a região está vazia. Chamar fora da thread principal.
 */
internal fun liftSelection(project: Project, layerId: Long, sw: Int, sh: Int, polygon: List<Point>): LiftResult? {
    if (polygon.size < 3) return null
    val x0 = max(0, floor(polygon.minOf { it.x }).toInt())
    val y0 = max(0, floor(polygon.minOf { it.y }).toInt())
    val x1 = min(sw, ceil(polygon.maxOf { it.x }).toInt())
    val y1 = min(sh, ceil(polygon.maxOf { it.y }).toInt())
    val bw = x1 - x0
    val bh = y1 - y0
    if (bw < 2 || bh < 2) return null
    val layer = project.layers.firstOrNull { it.id == layerId } ?: return null
    val strokes = layer.drawingAt(project.currentFrameId)?.strokes ?: return null

    fun polyPath(scale: Float): Path {
        val p = Path()
        p.moveTo((polygon[0].x - x0) * scale, (polygon[0].y - y0) * scale)
        for (i in 1 until polygon.size) p.lineTo((polygon[i].x - x0) * scale, (polygon[i].y - y0) * scale)
        p.close()
        return p
    }

    val bmp = ImageBitmap(bw, bh)
    CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, GfxCanvas(bmp), Size(bw.toFloat(), bh.toFloat())) {
        clipPath(polyPath(1f)) {
            withTransform({ translate(-x0.toFloat(), -y0.toFloat()) }) {
                drawContext.canvas.saveLayer(Rect(0f, 0f, sw.toFloat(), sh.toFloat()), Paint())
                drawElements(strokes, null, false)
                drawContext.canvas.restore()
            }
        }
    }
    val abmp: Bitmap = bmp.asAndroidBitmap()
    val px = IntArray(bw * bh)
    abmp.getPixels(px, 0, bw, 0, 0, bw, bh)
    if (px.none { (it ushr 24) != 0 }) return null
    val png = ByteArrayOutputStream()
    abmp.compress(Bitmap.CompressFormat.PNG, 100, png)

    val mw = bw * 2
    val mh = bh * 2
    val mbmp = ImageBitmap(mw, mh)
    CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, GfxCanvas(mbmp), Size(mw.toFloat(), mh.toFloat())) {
        drawPath(polyPath(2f), Color.Black)
    }
    val mpx = IntArray(mw * mh)
    mbmp.asAndroidBitmap().getPixels(mpx, 0, mw, 0, 0, mw, mh)
    val mask = BooleanArray(mw * mh) { (mpx[it] ushr 24) > 127 }
    val region = FillRegion.encode(mask, mw, mh, x0 * 2, y0 * 2, 0.5f)
    return LiftResult(
        ImageData(ImageSource(png.toByteArray(), bw, bh), Affine.translate(x0.toFloat(), y0.toFloat())),
        BrushStroke(emptyList(), 0xFF000000.toInt(), 0f, erase = true, fill = region),
    )
}

/** Fração (0..1) da folha ocupada por um preenchimento. */
internal fun fillCoverage(s: BrushStroke, sw: Int, sh: Int): Float {
    val f = s.fill ?: return 0f
    var on = 0L
    var state = false
    for (r in f.runs) {
        if (state) on += r
        state = !state
    }
    val area = on.toFloat() * f.unit * f.unit
    return area / (sw.toFloat() * sh.toFloat())
}
