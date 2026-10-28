package com.animationstudio.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PaintingStyle
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.DrawTransform
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.animationstudio.domain.BrushKind
import com.animationstudio.domain.BrushLibrary
import com.animationstudio.domain.BrushPreset
import com.animationstudio.domain.BrushStroke
import com.animationstudio.domain.FillRegion
import com.animationstudio.domain.ImageData
import com.animationstudio.domain.LayerKind
import com.animationstudio.domain.Point
import com.animationstudio.domain.Project
import com.animationstudio.domain.Stamp
import com.animationstudio.domain.StampGeometry
import com.animationstudio.domain.StampShape
import com.animationstudio.domain.StrokeGeometry
import com.animationstudio.domain.bounds
import java.util.concurrent.ConcurrentHashMap
import androidx.compose.ui.graphics.Canvas as GfxCanvas
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

// ---------------------------------------------------------------------------------------------
// Traços. Motores: contínuo (espessura uniforme, pontas redondas), toques suaves (aerógrafo/aguarela),
// linhas paralelas (cerdas), carimbos (plantas, luz) e grão de papel aplicado por cima (lápis, carvão).
// ---------------------------------------------------------------------------------------------

private class InkPass(val width: Float, val alpha: Float, val body: Path?, val polys: List<Path>, val tips: List<Pair<Offset, Float>>)

private class LineRender(val path: Path, val width: Float, val alpha: Float)

private class InkRender(
    val passes: List<InkPass>,
    val stampBuckets: List<Pair<Float, Path>>?,
    val dabs: List<Stamp>?,
    val dabHardness: Float,
    val dabFlow: Float,
    val lines: List<LineRender>?,
    val grain: Float,
)

private val dabCache = ConcurrentHashMap<Int, ImageBitmap>()
private val grainCache = ConcurrentHashMap<Int, ImageBitmap>()

private fun polyPath(poly: List<Point>): Path {
    val p = Path()
    p.moveTo(poly[0].x, poly[0].y)
    for (i in 1 until poly.size) p.lineTo(poly[i].x, poly[i].y)
    p.close()
    return p
}

private fun polyline(pts: List<Point>): Path {
    val p = Path()
    if (pts.isEmpty()) return p
    p.moveTo(pts[0].x, pts[0].y)
    if (pts.size == 1) p.lineTo(pts[0].x, pts[0].y)
    for (i in 1 until pts.size) p.lineTo(pts[i].x, pts[i].y)
    return p
}

private fun buildPass(s: BrushStroke, width: Float, alpha: Float): InkPass {
    val pts = s.points
    if (pts.size == 1) return InkPass(width, alpha, null, emptyList(), listOf(Offset(pts[0].x, pts[0].y) to width / 2f))
    // Espessura uniforme e pontas redondas. Só afina as pontas se o utilizador ativar essa opção.
    val shape = StrokeGeometry.buildNatural(pts, width, if (StudioRender.taperEnds) s.taper else 0f)
    var body: Path? = null
    if (shape.body.size >= 2) body = polyline(shape.body)
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
        StampShape.HAIR -> {
            val len = st.size
            val w = max(0.5f, st.size * 0.025f)
            val bend = len * 0.18f
            path.moveTo(wx(0f, -w), wy(0f, -w))
            path.quadraticTo(wx(len * 0.5f, -w * 0.5f + bend), wy(len * 0.5f, -w * 0.5f + bend), wx(len, bend * 1.5f), wy(len, bend * 1.5f))
            path.quadraticTo(wx(len * 0.5f, w * 0.5f + bend), wy(len * 0.5f, w * 0.5f + bend), wx(0f, w), wy(0f, w))
            path.close()
        }
        StampShape.NEEDLE -> {
            val len = st.size
            val w = max(0.5f, st.size * 0.04f)
            path.moveTo(wx(0f, -w), wy(0f, -w))
            path.lineTo(wx(len, 0f), wy(len, 0f))
            path.lineTo(wx(0f, w), wy(0f, w))
            path.close()
        }
        StampShape.PETAL -> {
            val len = st.size
            val wid = st.size * 0.45f
            path.moveTo(wx(0f, 0f), wy(0f, 0f))
            path.cubicTo(wx(len * 0.3f, -wid), wy(len * 0.3f, -wid), wx(len * 0.9f, -wid), wy(len * 0.9f, -wid), wx(len, 0f), wy(len, 0f))
            path.cubicTo(wx(len * 0.9f, wid), wy(len * 0.9f, wid), wx(len * 0.3f, wid), wy(len * 0.3f, wid), wx(0f, 0f), wy(0f, 0f))
            path.close()
        }
        StampShape.FLAKE -> {
            val w = max(0.4f, r * 0.1f)
            for (i in 0 until 6) {
                val ang = i * (PI.toFloat() / 3f)
                val ax = cos(ang) * r
                val ay = sin(ang) * r
                val qx = -sin(ang) * w
                val qy = cos(ang) * w
                path.moveTo(wx(qx, qy), wy(qx, qy))
                path.lineTo(wx(ax, ay), wy(ax, ay))
                path.lineTo(wx(-qx, -qy), wy(-qx, -qy))
                path.close()
            }
        }
        StampShape.STREAK, StampShape.RAIN -> {
            val len = st.size
            val w = max(0.5f, st.size * 0.02f)
            path.moveTo(wx(-len / 2f, -w), wy(-len / 2f, -w))
            path.lineTo(wx(len / 2f, -w * 0.3f), wy(len / 2f, -w * 0.3f))
            path.lineTo(wx(len / 2f, w * 0.3f), wy(len / 2f, w * 0.3f))
            path.lineTo(wx(-len / 2f, w), wy(-len / 2f, w))
            path.close()
        }
        StampShape.DASH -> {
            val len = st.size
            val w = max(0.8f, st.size * 0.15f)
            path.moveTo(wx(-len / 2f, -w), wy(-len / 2f, -w))
            path.lineTo(wx(len / 2f, -w), wy(len / 2f, -w))
            path.lineTo(wx(len / 2f, w), wy(len / 2f, w))
            path.lineTo(wx(-len / 2f, w), wy(-len / 2f, w))
            path.close()
        }
        StampShape.BOLT -> {
            val len = st.size
            val w = max(0.7f, st.size * 0.035f)
            val xs = floatArrayOf(0f, 0.2f, 0.4f, 0.6f, 0.8f, 1f)
            val ys = floatArrayOf(0f, -0.16f, 0.12f, -0.18f, 0.1f, 0f)
            for (i in xs.indices) {
                val lx = xs[i] * len
                val ly = ys[i] * len - w
                if (i == 0) path.moveTo(wx(lx, ly), wy(lx, ly)) else path.lineTo(wx(lx, ly), wy(lx, ly))
            }
            for (i in xs.indices.reversed()) {
                val lx = xs[i] * len
                val ly = ys[i] * len + w
                path.lineTo(wx(lx, ly), wy(lx, ly))
            }
            path.close()
        }
        StampShape.TWIG -> {
            val len = st.size
            val w = max(0.6f, st.size * 0.035f)
            fun spike(bx: Float, by: Float, tx: Float, ty: Float, ww: Float) {
                val dx = tx - bx
                val dy = ty - by
                val l = max(0.001f, hypot(dx, dy))
                val nx = -dy / l * ww
                val ny = dx / l * ww
                path.moveTo(wx(bx + nx, by + ny), wy(bx + nx, by + ny))
                path.lineTo(wx(tx, ty), wy(tx, ty))
                path.lineTo(wx(bx - nx, by - ny), wy(bx - nx, by - ny))
                path.close()
            }
            spike(0f, 0f, len, 0f, w)
            spike(len * 0.35f, 0f, len * 0.7f, -len * 0.32f, w * 0.8f)
            spike(len * 0.55f, 0f, len * 0.88f, len * 0.3f, w * 0.8f)
        }
        StampShape.NONE -> {}
    }
}

private fun seedOf(s: BrushStroke): Long {
    val first = s.points.firstOrNull() ?: return 0L
    return ((first.x * 131f).toInt() * 31 + (first.y * 131f).toInt()).toLong()
}

private fun buildStamps(s: BrushStroke, preset: BrushPreset): List<Pair<Float, Path>> {
    val low = Path()
    val mid = Path()
    val high = Path()
    for (st in StampGeometry.generate(s.points, s.width, preset, seedOf(s).toInt())) {
        val target = if (st.alpha < 0.5f) low else if (st.alpha < 0.8f) mid else high
        addStamp(target, preset.stamp, st)
    }
    return listOf(0.4f to low, 0.7f to mid, 1f to high)
}

private fun buildInk(s: BrushStroke): InkRender {
    val preset = if (s.brush.isNotEmpty()) BrushLibrary.byIdOrNull(s.brush) else null
    val grain = preset?.grain ?: 0f
    if (preset != null && preset.stamp != StampShape.NONE) {
        return InkRender(emptyList(), buildStamps(s, preset), null, 0f, 0f, null, grain)
    }
    if (preset != null && preset.soft) {
        val spacing = 0.08f
        val flow = 1f - Math.pow(0.05, spacing.toDouble()).toFloat()
        val dabTaper = if (StudioRender.taperEnds) s.taper * 0.4f else 0f
        return InkRender(emptyList(), null, StampGeometry.dabs(s.points, s.width, dabTaper, spacing), s.hardness.coerceIn(0f, 1f), flow, null, grain)
    }
    if (preset != null && preset.lines > 1) {
        val pl = StrokeGeometry.parallel(s.points, s.width, preset.lines)
        val rnd = java.util.Random(seedOf(s))
        val lines = pl.lines.map { pts -> LineRender(polyline(pts), pl.lineWidth, 1f - preset.lineJitter * rnd.nextFloat()) }
        return InkRender(emptyList(), null, null, 0f, 0f, lines, grain)
    }
    val h = s.hardness.coerceIn(0f, 1f)
    if (h > 0.95f) return InkRender(listOf(buildPass(s, s.width, 1f)), null, null, 0f, 0f, null, grain)
    // Traços antigos com borda suave: passagens empilhadas.
    val core = s.width * (0.4f + 0.6f * h)
    val span = s.width - core
    return InkRender(
        listOf(
            buildPass(s, s.width, 0.14f),
            buildPass(s, core + span * 0.66f, 0.2f),
            buildPass(s, core + span * 0.33f, 0.3f),
            buildPass(s, core, 1f),
        ),
        null, null, 0f, 0f, null, grain,
    )
}

private fun dabImage(hardness: Float): ImageBitmap {
    val key = (hardness.coerceIn(0f, 1f) * 20f).roundToInt()
    return dabCache.getOrPut(key) {
        val h = key / 20f
        val n = 64
        val c = (n - 1) / 2f
        val px = IntArray(n * n)
        for (y in 0 until n) {
            for (x in 0 until n) {
                val d = hypot(x - c, y - c) / (n / 2f)
                val a = if (d >= 1f) 0f else if (d <= h) 1f else {
                    val t = (1f - d) / (1f - h)
                    t * t * (3f - 2f * t)
                }
                px[y * n + x] = ((a * 255f).roundToInt() shl 24) or 0xFFFFFF
            }
        }
        val bmp = Bitmap.createBitmap(n, n, Bitmap.Config.ARGB_8888)
        bmp.setPixels(px, 0, n, 0, 0, n, n)
        bmp.asImageBitmap()
    }
}

/**
 * Papel com fibra: ruído de valor com quatro oitavas que repete sem costuras (256 px). Em vez de ruído branco
 * (pontinhos soltos), o grão forma manchas finas e contínuas, como a textura de um lápis em papel.
 */
private val paperNoiseData: FloatArray by lazy { paperNoise(256) }

private fun paperNoise(n: Int): FloatArray {
    val rnd = java.util.Random(7L)
    val out = FloatArray(n * n)
    val octaves = intArrayOf(16, 32, 64, 128)
    val weights = floatArrayOf(0.18f, 0.30f, 0.32f, 0.20f)
    for (o in octaves.indices) {
        val cells = octaves[o]
        val grid = FloatArray(cells * cells) { rnd.nextFloat() }
        val cs = n.toFloat() / cells
        for (y in 0 until n) {
            val fy = y / cs
            val y0 = fy.toInt() % cells
            val y1 = (y0 + 1) % cells
            val ty0 = fy - fy.toInt()
            val ty = ty0 * ty0 * (3f - 2f * ty0)
            for (x in 0 until n) {
                val fx = x / cs
                val x0 = fx.toInt() % cells
                val x1 = (x0 + 1) % cells
                val tx0 = fx - fx.toInt()
                val tx = tx0 * tx0 * (3f - 2f * tx0)
                val a = grid[y0 * cells + x0] + (grid[y0 * cells + x1] - grid[y0 * cells + x0]) * tx
                val b = grid[y1 * cells + x0] + (grid[y1 * cells + x1] - grid[y1 * cells + x0]) * tx
                out[y * n + x] += weights[o] * (a + (b - a) * ty)
            }
        }
    }
    var lo = Float.MAX_VALUE
    var hi = -Float.MAX_VALUE
    for (v in out) { if (v < lo) lo = v; if (v > hi) hi = v }
    val span = max(1e-4f, hi - lo)
    for (i in out.indices) out[i] = (out[i] - lo) / span
    return out
}

private fun grainImage(strength: Float): ImageBitmap {
    val key = (strength.coerceIn(0f, 1f) * 20f).roundToInt()
    return grainCache.getOrPut(key) {
        val s = key / 20f
        val n = 256
        val noise = paperNoiseData
        val px = IntArray(n * n)
        for (i in px.indices) {
            // Quanto maior o grão, mais o "vale" do papel deixa de receber grãfite.
            val a = (255f * (1f - s * noise[i])).roundToInt().coerceIn(0, 255)
            px[i] = (a shl 24) or 0xFFFFFF
        }
        val bmp = Bitmap.createBitmap(n, n, Bitmap.Config.ARGB_8888)
        bmp.setPixels(px, 0, n, 0, 0, n, n)
        bmp.asImageBitmap()
    }
}

// Desenho com anti-aliasing (normal) ou sem ele (modo pixelado escolhido pelo utilizador).

private fun pixelPaint(c: Color, blend: BlendMode): Paint {
    val p = Paint()
    p.isAntiAlias = false
    p.color = c
    p.blendMode = blend
    return p
}

private fun DrawScope.strokePath(path: Path, c: Color, width: Float, blend: BlendMode) {
    if (!StudioRender.pixelated) {
        drawPath(path, c, style = Stroke(width = width, cap = StrokeCap.Round, join = StrokeJoin.Round), blendMode = blend)
        return
    }
    val p = pixelPaint(c, blend)
    p.style = PaintingStyle.Stroke
    p.strokeWidth = width
    p.strokeCap = StrokeCap.Round
    p.strokeJoin = StrokeJoin.Round
    drawContext.canvas.drawPath(path, p)
}

private fun DrawScope.fillPath(path: Path, c: Color, blend: BlendMode) {
    if (!StudioRender.pixelated) {
        drawPath(path, c, blendMode = blend)
        return
    }
    drawContext.canvas.drawPath(path, pixelPaint(c, blend))
}

private fun DrawScope.dot(c: Color, radius: Float, center: Offset, blend: BlendMode) {
    if (!StudioRender.pixelated) {
        drawCircle(c, radius, center, blendMode = blend)
        return
    }
    drawContext.canvas.drawCircle(center, radius, pixelPaint(c, blend))
}

private fun DrawScope.drawPass(p: InkPass, rgb: Color, blend: BlendMode) {
    val c = rgb.copy(alpha = p.alpha)
    val body = p.body
    if (body != null) strokePath(body, c, p.width, blend)
    for (poly in p.polys) fillPath(poly, c, blend)
    for (t in p.tips) dot(c, t.second, t.first, blend)
}

private fun DrawScope.drawDabs(dabs: List<Stamp>, hardness: Float, flow: Float, rgb: Color, blend: BlendMode) {
    val img = dabImage(hardness)
    val filter = ColorFilter.tint(rgb, BlendMode.SrcIn)
    for (d in dabs) {
        val k = d.size / 64f
        withTransform({ translate(d.x - d.size / 2f, d.y - d.size / 2f); scale(k, k, Offset.Zero) }) {
            drawImage(img, alpha = flow, colorFilter = filter, blendMode = blend)
        }
    }
}

private fun DrawScope.drawRender(r: InkRender, rgb: Color, blend: BlendMode) {
    val dabs = r.dabs
    val lines = r.lines
    val buckets = r.stampBuckets
    if (dabs != null) {
        drawDabs(dabs, r.dabHardness, r.dabFlow, rgb, blend)
    } else if (lines != null) {
        for (l in lines) strokePath(l.path, rgb.copy(alpha = l.alpha), l.width, blend)
    } else if (buckets != null) {
        for (b in buckets) fillPath(b.second, rgb.copy(alpha = b.first), blend)
    } else {
        for (p in r.passes) drawPass(p, rgb, blend)
    }
}

/** Grão do papel: mantém o traço só onde a textura (fixa na folha) tem opacidade. */
private fun DrawScope.drawGrain(strength: Float, r: Rect) {
    val shader = ImageShader(grainImage(strength), TileMode.Repeated, TileMode.Repeated)
    drawRect(ShaderBrush(shader), r.topLeft, r.size, blendMode = BlendMode.DstIn)
}

/**
 * Desenha um traço. A opacidade vem do alfa da cor; a borracha usa DstOut. Com opacidade < 100%, modo
 * "dentro"/"por trás", toques suaves ou grão, as peças são desenhadas numa camada que depois recebe o alfa, o
 * modo e o grão, para não escurecer onde as peças se sobrepõem.
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
    val needLayer = alpha < 0.999f || (!s.erase && s.mode != 0) || render.dabs != null || render.grain > 0f
    if (!needLayer) {
        drawRender(render, rgb, if (s.erase) BlendMode.DstOut else BlendMode.SrcOver)
        return
    }
    val b = s.bounds() ?: return
    val pad = s.width + 2f
    val rect = Rect(b.minX - pad, b.minY - pad, b.maxX + pad, b.maxY + pad)
    val paint = Paint()
    paint.alpha = alpha
    paint.blendMode = layerBlend
    drawContext.canvas.saveLayer(rect, paint)
    drawRender(render, rgb, BlendMode.SrcOver)
    if (render.grain > 0f) drawGrain(render.grain, rect)
    drawContext.canvas.restore()
}

// ---------------------------------------------------------------------------------------------
// Preenchimentos (balde), máscaras de borracha e imagens (seleção do laço, importação)
// ---------------------------------------------------------------------------------------------

/**
 * Bitmap do preenchimento com borda suavizada (anti-aliasing): a cobertura de cada célula é a média da vizinhança 3×3,
 * com uma curva que mantém a borda firme. Nas escalas grandes o bitmap é ampliado com filtro bilinear.
 */
private fun fillBitmap(f: FillRegion, argb: Int): ImageBitmap {
    val mask = f.decode()
    val w = f.w
    val h = f.h
    val baseA = argb ushr 24
    val rgb = argb and 0xFFFFFF
    val px = IntArray(w * h)
    for (y in 0 until h) {
        for (x in 0 until w) {
            var c = 0
            for (dy in -1..1) {
                val yy = y + dy
                if (yy < 0 || yy >= h) continue
                for (dx in -1..1) {
                    val xx = x + dx
                    if (xx < 0 || xx >= w) continue
                    if (mask[yy * w + xx]) c++
                }
            }
            if (c == 0) continue
            val cov = ((c / 9f - 0.15f) / 0.7f).coerceIn(0f, 1f)
            val a = (baseA * cov).roundToInt().coerceIn(0, 255)
            if (a > 0) px[y * w + x] = (a shl 24) or rgb
        }
    }
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    bmp.setPixels(px, 0, w, 0, 0, w, h)
    return bmp.asImageBitmap()
}

/** Desenha a máscara com transformação exata (sem arredondar para pixels), como cor ou como borracha. */
internal fun DrawScope.drawFillElement(s: BrushStroke, tint: Color?, useCache: Boolean) {
    val f = s.fill ?: return
    val cached = f.cache as? ImageBitmap
    val img = cached ?: fillBitmap(f, s.colorArgb).also { if (useCache) f.cache = it }
    val filter = if (tint != null) ColorFilter.tint(tint, BlendMode.SrcIn) else null
    val blend = if (s.erase) BlendMode.DstOut else BlendMode.SrcOver
    withTransform({ translate(f.x * f.unit, f.y * f.unit); scale(f.unit, f.unit, Offset.Zero) }) {
        drawImage(img, colorFilter = filter, blendMode = blend)
    }
}

private fun decodePng(png: ByteArray): ImageBitmap? = BitmapFactory.decodeByteArray(png, 0, png.size)?.asImageBitmap()

internal fun DrawScope.drawImageData(im: ImageData, tint: Color?, useCache: Boolean) {
    val cached = im.source.cache as? ImageBitmap
    val img = cached ?: (decodePng(im.source.png)?.also { if (useCache) im.source.cache = it }) ?: return
    val m = im.m
    val matrix = Matrix(floatArrayOf(m.a, m.b, 0f, 0f, m.c, m.d, 0f, 0f, 0f, 0f, 1f, 0f, m.tx, m.ty, 0f, 1f))
    val filter = if (tint != null) ColorFilter.tint(tint, BlendMode.SrcIn) else null
    withTransform({ transform(matrix) }) { drawImage(img, colorFilter = filter) }
}

internal fun DrawScope.drawElement(s: BrushStroke, tint: Color?, useCache: Boolean) {
    val im = s.image
    if (im != null) drawImageData(im, tint, useCache)
    else if (s.fill != null) drawFillElement(s, tint, useCache)
    else drawInk(s, tint, useCache)
}

/** Elementos de um desenho, pela ordem em que foram feitos. */
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

/** Onion skin: o frame vizinho desenhado numa só camada colorida com opacidade [alpha] (uma composição por frame). */
internal fun DrawScope.drawOnionFrame(project: Project, frameId: Long, sw: Float, sh: Float, tint: Color, alpha: Float) {
    val paint = Paint()
    paint.alpha = alpha
    drawContext.canvas.saveLayer(Rect(0f, 0f, sw, sh), paint)
    drawFrameLayers(project, frameId, sw, sh, LayerKind.DRAWING, tint.copy(alpha = 1f), true)
    drawContext.canvas.restore()
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
