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
import com.animationstudio.domain.BrushLibrary
import com.animationstudio.domain.BrushPreset
import com.animationstudio.domain.BrushStroke
import com.animationstudio.domain.FillRegion
import com.animationstudio.domain.LayerKind
import com.animationstudio.domain.Point
import com.animationstudio.domain.Project
import com.animationstudio.domain.Stamp
import com.animationstudio.domain.StampGeometry
import com.animationstudio.domain.StampShape
import com.animationstudio.domain.StrokeGeometry
import com.animationstudio.domain.bounds
import androidx.compose.ui.graphics.Canvas as GfxCanvas
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

// ---------------------------------------------------------------------------------------------
// Traços: contínuos (corpo + pontas afiladas + bordas suaves) ou por carimbos (textura, plantas, luz).
// ---------------------------------------------------------------------------------------------

private class InkPass(val width: Float, val alpha: Float, val body: Path?, val polys: List<Path>, val tips: List<Pair<Offset, Float>>)

private class InkRender(val passes: List<InkPass>, val stampBuckets: List<Pair<Float, Path>>?)

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

private fun addStamp(path: Path, shape: StampShape, st: Stamp) {
    val c = cos(st.rotation)
    val sn = sin(st.rotation)
    fun wx(lx: Float, ly: Float) = st.x + lx * c - ly * sn
    fun wy(lx: Float, ly: Float) = st.y + lx * sn + ly * c
    val r = st.size / 2f
    when (shape) {
        StampShape.GRAIN, StampShape.DOT -> path.addOval(Rect(st.x - r, st.y - r, st.x + r, st.y + r))
        StampShape.STAR -> {
            for (i in 0 until 8) {
                val ang = i * (PI.toFloat() / 4f)
                val rad = if (i % 2 == 0) r else r * 0.3f
                val lx = cos(ang) * rad
                val ly = sin(ang) * rad
                if (i == 0) path.moveTo(wx(lx, ly), wy(lx, ly)) else path.lineTo(wx(lx, ly), wy(lx, ly))
            }
            path.close()
        }
        StampShape.LEAF -> {
            val len = st.size
            val wid = st.size * 0.28f
            path.moveTo(wx(0f, 0f), wy(0f, 0f))
            path.quadraticTo(wx(len * 0.5f, wid), wy(len * 0.5f, wid), wx(len, 0f), wy(len, 0f))
            path.quadraticTo(wx(len * 0.5f, -wid), wy(len * 0.5f, -wid), wx(0f, 0f), wy(0f, 0f))
            path.close()
        }
        StampShape.GRASS -> {
            val hgt = st.size
            val base = max(1f, st.size * 0.07f)
            val bend = hgt * 0.25f
            path.moveTo(wx(0f, -base), wy(0f, -base))
            path.quadraticTo(wx(hgt * 0.55f, -base * 0.4f + bend), wy(hgt * 0.55f, -base * 0.4f + bend), wx(hgt, bend * 1.4f), wy(hgt, bend * 1.4f))
            path.quadraticTo(wx(hgt * 0.55f, base * 0.4f + bend), wy(hgt * 0.55f, base * 0.4f + bend), wx(0f, base), wy(0f, base))
            path.close()
        }
        StampShape.NONE -> {}
    }
}

private fun buildStamps(s: BrushStroke, preset: BrushPreset): List<Pair<Float, Path>> {
    val first = s.points.firstOrNull()
    val seed = if (first == null) 0 else (first.x * 131f).toInt() * 31 + (first.y * 131f).toInt()
    val low = Path()
    val mid = Path()
    val high = Path()
    for (st in StampGeometry.generate(s.points, s.width, preset, seed)) {
        val target = if (st.alpha < 0.5f) low else if (st.alpha < 0.8f) mid else high
        addStamp(target, preset.stamp, st)
    }
    return listOf(0.4f to low, 0.7f to mid, 1f to high)
}

private fun buildInk(s: BrushStroke): InkRender {
    val preset = if (s.brush.isNotEmpty()) BrushLibrary.byIdOrNull(s.brush) else null
    if (preset != null && preset.stamp != StampShape.NONE) return InkRender(emptyList(), buildStamps(s, preset))
    val h = s.hardness.coerceIn(0f, 1f)
    if (h > 0.95f) return InkRender(listOf(buildPass(s, s.width, 1f)), null)
    val core = s.width * (0.4f + 0.6f * h)
    val span = s.width - core
    return InkRender(
        listOf(
            buildPass(s, s.width, 0.14f),
            buildPass(s, core + span * 0.66f, 0.2f),
            buildPass(s, core + span * 0.33f, 0.3f),
            buildPass(s, core, 1f),
        ),
        null,
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

private fun DrawScope.drawRender(r: InkRender, rgb: Color, blend: BlendMode) {
    val buckets = r.stampBuckets
    if (buckets != null) {
        for (b in buckets) drawPath(b.second, rgb.copy(alpha = b.first), blendMode = blend)
    } else {
        for (p in r.passes) drawPass(p, rgb, blend)
    }
}

/**
 * Desenha um traço. A opacidade vem do alfa da cor; a borracha usa DstOut (apaga proporcionalmente ao alfa).
 * Com opacidade < 100% ou modo "dentro"/"por trás" as peças são desenhadas opacas numa camada e a camada
 * é composta com o alfa e o modo, para não escurecer onde as peças se sobrepõem.
 */
internal fun DrawScope.drawInk(s: BrushStroke, tint: Color?, useCache: Boolean) {
    if (s.points.isEmpty()) return
    val cached = s.renderCache as? InkRender
    val render = cached ?: buildInk(s).also { if (useCache) s.renderCache = it }
    val source = tint ?: Color(s.colorArgb)
    val alpha = source.alpha
    val rgb = if (s.erase) Color.Black else source.copy(alpha = 1f)
    val layerBlend = when {
        s.erase -> BlendMode.DstOut
        s.mode == 1 -> BlendMode.SrcAtop
        s.mode == 2 -> BlendMode.DstOver
        else -> BlendMode.SrcOver
    }
    val needLayer = alpha < 0.999f || (!s.erase && s.mode != 0)
    if (!needLayer) {
        drawRender(render, rgb, if (s.erase) BlendMode.DstOut else BlendMode.SrcOver)
        return
    }
    val b = s.bounds() ?: return
    val pad = s.width + 2f
    val paint = Paint()
    paint.alpha = alpha
    paint.blendMode = layerBlend
    drawContext.canvas.saveLayer(Rect(b.minX - pad, b.minY - pad, b.maxX + pad, b.maxY + pad), paint)
    drawRender(render, rgb, BlendMode.SrcOver)
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

/** Pré-visualização de um pincel/borracha: uma onda desenhada com a predefinição indicada. */
internal fun DrawScope.drawBrushSample(preset: BrushPreset, width: Float, opacity: Float, ink: Color) {
    val w = size.width
    val h = size.height
    val pad = h * 0.3f
    val pts = List(48) { i ->
        val t = i / 47f
        Point(pad + t * (w - 2f * pad), h / 2f + sin(t * 2f * PI.toFloat()) * h * 0.2f)
    }
    val wd = width.coerceIn(1f, h * 0.5f)
    val erase = preset.kind == BrushKind.ERASER
    if (erase) drawRect(StudioColors.Accent.copy(alpha = 0.85f))
    val argb = if (erase) Color.Black.copy(alpha = opacity).toArgb() else ink.copy(alpha = opacity).toArgb()
    drawInk(BrushStroke(pts, argb, wd, erase, null, preset.taper, preset.hardness, preset.id, 0), null, false)
}
