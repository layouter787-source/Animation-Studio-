package com.animationstudio.ui

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.DrawTransform
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import com.animationstudio.domain.BrushKind
import com.animationstudio.domain.BrushStroke
import com.animationstudio.domain.FillRegion
import com.animationstudio.domain.LayerKind
import com.animationstudio.domain.Point
import com.animationstudio.domain.Project
import com.animationstudio.domain.StrokeGeometry
import com.animationstudio.domain.bounds
import androidx.compose.ui.graphics.Canvas as GfxCanvas
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

// ---------------------------------------------------------------------------------------------
// Traços de tinta: corpo uniforme + pontas afiladas; bordas suaves por passagens empilhadas.
// ---------------------------------------------------------------------------------------------

private class InkPass(val width: Float, val alpha: Float, val body: Path?, val polys: List<Path>, val tips: List<Pair<Offset, Float>>)

private class InkRender(val passes: List<InkPass>)

private fun polyPath(poly: List<Point>): Path {
    val p = Path()
    p.moveTo(poly[0].x, poly[0].y)
    for (i in 1 until poly.size) p.lineTo(poly[i].x, poly[i].y)
    p.close()
    return p
}

private fun buildPass(s: BrushStroke, width: Float, alpha: Float): InkPass {
    val pts = s.points
    if (pts.size == 1) return InkPass(width, alpha, null, emptyList(), listOf(Offset(pts[0].x, pts[0].y) to width / 2f))
    val shape = StrokeGeometry.build(pts, width, s.taper)
    var body: Path? = null
    if (shape.body.size >= 2) {
        val b = Path()
        b.moveTo(shape.body[0].x, shape.body[0].y)
        for (i in 1 until shape.body.size) b.lineTo(shape.body[i].x, shape.body[i].y)
        body = b
    }
    val polys = ArrayList<Path>()
    val tips = ArrayList<Pair<Offset, Float>>()
    val st = shape.start
    if (st != null) {
        polys.add(polyPath(st.polygon))
        tips.add(Offset(st.tip.x, st.tip.y) to st.tipRadius)
    }
    val en = shape.end
    if (en != null) {
        polys.add(polyPath(en.polygon))
        tips.add(Offset(en.tip.x, en.tip.y) to en.tipRadius)
    }
    if (body == null && polys.isEmpty() && shape.body.isNotEmpty()) {
        tips.add(Offset(shape.body[0].x, shape.body[0].y) to width / 2f)
    }
    return InkPass(width, alpha, body, polys, tips)
}

private fun buildInk(s: BrushStroke): InkRender {
    val h = s.hardness.coerceIn(0f, 1f)
    if (h > 0.95f) return InkRender(listOf(buildPass(s, s.width, 1f)))
    val core = s.width * (0.4f + 0.6f * h)
    val span = s.width - core
    return InkRender(
        listOf(
            buildPass(s, s.width, 0.14f),
            buildPass(s, core + span * 0.66f, 0.2f),
            buildPass(s, core + span * 0.33f, 0.3f),
            buildPass(s, core, 1f),
        )
    )
}

private fun DrawScope.drawPass(p: InkPass, rgb: Color, blend: BlendMode) {
    val c = rgb.copy(alpha = p.alpha)
    val body = p.body
    if (body != null) {
        drawPath(body, c, style = Stroke(width = p.width, cap = StrokeCap.Round, join = StrokeJoin.Round), blendMode = blend)
    }
    for (poly in p.polys) drawPath(poly, c, blendMode = blend)
    for (t in p.tips) drawCircle(c, t.second, t.first, blendMode = blend)
}

/**
 * Desenha um traço. A opacidade vem do alfa da cor; a borracha usa DstOut (apaga proporcionalmente ao alfa).
 * Com opacidade < 100% as peças são desenhadas opacas numa camada e a camada recebe o alfa, para não
 * escurecer onde as peças se sobrepõem.
 */
internal fun DrawScope.drawInk(s: BrushStroke, tint: Color?, useCache: Boolean) {
    if (s.points.isEmpty()) return
    val cached = s.renderCache as? InkRender
    val render = cached ?: buildInk(s).also { if (useCache) s.renderCache = it }
    val source = tint ?: Color(s.colorArgb)
    val alpha = source.alpha
    val rgb = if (s.erase) Color.Black else source.copy(alpha = 1f)
    val blend = if (s.erase) BlendMode.DstOut else BlendMode.SrcOver
    if (alpha >= 0.999f) {
        for (p in render.passes) drawPass(p, rgb, blend)
        return
    }
    val b = s.bounds() ?: return
    val pad = s.width + 2f
    val paint = Paint()
    paint.alpha = alpha
    paint.blendMode = blend
    drawContext.canvas.saveLayer(Rect(b.minX - pad, b.minY - pad, b.maxX + pad, b.maxY + pad), paint)
    for (p in render.passes) drawPass(p, rgb, BlendMode.SrcOver)
    drawContext.canvas.restore()
}

// ---------------------------------------------------------------------------------------------
// Preenchimentos (balde)
// ---------------------------------------------------------------------------------------------

private fun fillBitmap(f: FillRegion, argb: Int): ImageBitmap {
    val mask = f.decode()
    val px = IntArray(f.w * f.h)
    for (i in px.indices) if (mask[i]) px[i] = argb
    val bmp = Bitmap.createBitmap(f.w, f.h, Bitmap.Config.ARGB_8888)
    bmp.setPixels(px, 0, f.w, 0, 0, f.w, f.h)
    return bmp.asImageBitmap()
}

internal fun DrawScope.drawFillElement(s: BrushStroke, tint: Color?, useCache: Boolean) {
    val f = s.fill ?: return
    val cached = f.cache as? ImageBitmap
    val img = cached ?: fillBitmap(f, s.colorArgb).also { if (useCache) f.cache = it }
    val filter = if (tint != null) ColorFilter.tint(tint, BlendMode.SrcIn) else null
    drawImage(
        img,
        srcOffset = IntOffset.Zero,
        srcSize = IntSize(f.w, f.h),
        dstOffset = IntOffset((f.x * f.unit).roundToInt(), (f.y * f.unit).roundToInt()),
        dstSize = IntSize((f.w * f.unit).roundToInt().coerceAtLeast(1), (f.h * f.unit).roundToInt().coerceAtLeast(1)),
        colorFilter = filter,
    )
}

internal fun DrawScope.drawElement(s: BrushStroke, tint: Color?, useCache: Boolean) {
    if (s.fill != null) drawFillElement(s, tint, useCache) else drawInk(s, tint, useCache)
}

/** Elementos de um desenho; os selecionados (laço) saem com a transformação de pré-visualização. */
internal fun DrawScope.drawElements(
    strokes: List<BrushStroke>,
    tint: Color?,
    useCache: Boolean,
    selected: Set<Int>? = null,
    selTransform: ((DrawTransform) -> Unit)? = null,
) {
    for (i in strokes.indices) {
        val s = strokes[i]
        if (selected != null && selTransform != null && i in selected) {
            withTransform({ selTransform(this) }) { drawElement(s, tint, useCache) }
        } else {
            drawElement(s, tint, useCache)
        }
    }
}

/**
 * Camadas de um frame, de baixo para cima, cada uma isolada (opacidade da camada e borracha só afetam a própria).
 * [kind] filtra por tipo de camada (null = todas). [live] é o traço em curso, desenhado na camada [liveLayerId].
 */
internal fun DrawScope.drawFrameLayers(
    project: Project,
    frameId: Long,
    sw: Float,
    sh: Float,
    kind: LayerKind?,
    tint: Color?,
    useCache: Boolean,
    live: BrushStroke? = null,
    liveLayerId: Long = -1L,
    selLayerId: Long = -1L,
    selected: Set<Int>? = null,
    selTransform: ((DrawTransform) -> Unit)? = null,
) {
    for (l in project.layers) {
        if (!l.visible) continue
        if (kind != null && l.kind != kind) continue
        val strokes = l.drawingAt(frameId)?.strokes ?: emptyList()
        val hasLive = live != null && l.id == liveLayerId
        if (strokes.isEmpty() && !hasLive) continue
        val paint = Paint()
        paint.alpha = l.opacityPercent / 100f
        drawContext.canvas.saveLayer(Rect(0f, 0f, sw, sh), paint)
        drawElements(strokes, tint, useCache, if (l.id == selLayerId) selected else null, selTransform)
        if (live != null && hasLive) drawElement(live, tint, false)
        drawContext.canvas.restore()
    }
}

/** Miniatura de um desenho (para as células da timeline). O DrawScope deve ter o tamanho da miniatura. */
internal fun DrawScope.drawThumbnail(strokes: List<BrushStroke>, sw: Float, sh: Float) {
    val k = size.width / sw
    withTransform({ scale(k, k, Offset.Zero) }) {
        clipRect(0f, 0f, sw, sh) {
            drawContext.canvas.saveLayer(Rect(0f, 0f, sw, sh), Paint())
            drawElements(strokes, null, true)
            drawContext.canvas.restore()
        }
    }
}

/** Renderiza o frame (todas as camadas visíveis) para um bitmap. Seguro fora da thread principal com useCache=false. */
internal fun renderComposite(
    project: Project,
    frameIndex: Int,
    sw: Int,
    sh: Int,
    outW: Int,
    outH: Int,
    background: Color?,
    useCache: Boolean,
): ImageBitmap {
    val bmp = ImageBitmap(outW, outH)
    val canvas = GfxCanvas(bmp)
    CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, canvas, Size(outW.toFloat(), outH.toFloat())) {
        if (background != null) drawRect(background)
        withTransform({ scale(outW / sw.toFloat(), outH / sh.toFloat(), Offset.Zero) }) {
            clipRect(0f, 0f, sw.toFloat(), sh.toFloat()) {
                drawFrameLayers(project, project.frameIds[frameIndex], sw.toFloat(), sh.toFloat(), null, null, useCache)
            }
        }
    }
    return bmp
}

/** Pré-visualização de um pincel/borracha: uma onda desenhada com os parâmetros indicados. */
internal fun DrawScope.drawBrushSample(kind: BrushKind, width: Float, opacity: Float, taper: Float, hardness: Float, ink: Color) {
    val w = size.width
    val h = size.height
    val pad = h * 0.32f
    val pts = List(48) { i ->
        val t = i / 47f
        Point(pad + t * (w - 2f * pad), h / 2f + sin(t * 2f * PI.toFloat()) * h * 0.2f)
    }
    val wd = width.coerceIn(1f, h * 0.5f)
    if (kind == BrushKind.ERASER) {
        drawRect(StudioColors.Accent.copy(alpha = 0.85f))
        drawInk(BrushStroke(pts, Color.Black.copy(alpha = opacity).toArgb(), wd, true, null, taper, hardness), null, false)
    } else {
        drawInk(BrushStroke(pts, ink.copy(alpha = opacity).toArgb(), wd, false, null, taper, hardness), null, false)
    }
}
