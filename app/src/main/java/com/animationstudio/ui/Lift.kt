package com.animationstudio.ui

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.animationstudio.domain.Affine
import com.animationstudio.domain.BrushStroke
import com.animationstudio.domain.FillRegion
import com.animationstudio.domain.ImageData
import com.animationstudio.domain.ImageSource
import com.animationstudio.domain.Lasso
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
 * Laço pixel a pixel, sem perda de qualidade.
 *
 * O desenho da camada é vetorial, por isso a seleção é renderizada a [Lasso.supersample] píxeis por unidade da folha
 * (2 a 4×, mais quanto maior o [zoom] do editor), e não a 1×: ao mover, escalar ou rodar a seleção continua nítida.
 *
 * A máscara do polígono ([Lasso.rasterize]) é calculada UMA vez, na mesma grelha, e usada nos dois lados:
 *  - a imagem levantada fica só com os píxeis dentro da máscara;
 *  - a borracha que retira a região original apaga exatamente essas mesmas células.
 * Assim a imagem e o buraco são complementares: sem seleção movida, o resultado é igual ao desenho original
 * (sem fio claro nem rebordo duplicado nas bordas).
 *
 * Devolve null se a região está vazia. Chamar fora da thread principal.
 */
internal fun liftSelection(
    project: Project,
    layerId: Long,
    sw: Int,
    sh: Int,
    polygon: List<Point>,
    zoom: Float = StudioRender.view.scale,
): LiftResult? {
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

    val k = Lasso.supersample(bw, bh, zoom)
    val kf = k.toFloat()
    val mw = bw * k
    val mh = bh * k

    val bmp = ImageBitmap(mw, mh)
    ImageScale.with(kf) {
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, GfxCanvas(bmp), Size(mw.toFloat(), mh.toFloat())) {
            // p' = k · (p − origem): primeiro desloca, depois amplia (a última chamada aplica-se primeiro).
            withTransform({ scale(kf, kf, Offset.Zero); translate(-x0.toFloat(), -y0.toFloat()) }) {
                drawContext.canvas.saveLayer(Rect(0f, 0f, sw.toFloat(), sh.toFloat()), Paint())
                drawElements(strokes, null, false)
                drawContext.canvas.restore()
            }
        }
    }

    val mask = Lasso.rasterize(polygon, x0, y0, bw, bh, k)
    val abmp: Bitmap = bmp.asAndroidBitmap()
    val px = IntArray(mw * mh)
    abmp.getPixels(px, 0, mw, 0, 0, mw, mh)
    var any = false
    for (i in px.indices) {
        if (mask[i]) {
            if ((px[i] ushr 24) != 0) any = true
        } else {
            px[i] = 0
        }
    }
    if (!any) return null
    abmp.setPixels(px, 0, mw, 0, 0, mw, mh)

    val png = ByteArrayOutputStream()
    abmp.compress(Bitmap.CompressFormat.PNG, 100, png)
    val source = ImageSource(png.toByteArray(), mw, mh)
    // O bitmap já está pronto: evita descodificar o PNG logo a seguir (a seleção aparece mais depressa).
    source.cache = bmp

    val region = FillRegion.encode(mask, mw, mh, x0 * k, y0 * k, 1f / kf)
    return LiftResult(
        ImageData(source, Affine(1f / kf, 0f, 0f, 1f / kf, x0.toFloat(), y0.toFloat())),
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
