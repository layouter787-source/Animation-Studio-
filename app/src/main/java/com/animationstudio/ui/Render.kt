package com.animationstudio.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
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
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import com.animationstudio.domain.BrushKind
import com.animationstudio.domain.BrushLibrary
import com.animationstudio.domain.BrushPreset
import com.animationstudio.domain.BrushStroke
import com.animationstudio.domain.FillRegion
import com.animationstudio.domain.GrainKind
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
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

// ---------------------------------------------------------------------------------------------
// Traços. Motores: contínuo (espessura uniforme, pontas redondas), toques suaves (aerógrafo/aguarela),
// linhas paralelas (cerdas), carimbos (plantas, luz, formas) e textura procedural aplicada por cima
// (papel, tela, madeira, pedra, nuvem, couro, areia, grunge, metal, concreto).
// A borracha NUNCA pinta de branco: usa composição DstOut (remove a opacidade do que existe na camada).
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
    val grainKind: GrainKind,
)

private val dabCache = ConcurrentHashMap<Int, ImageBitmap>()
private val grainCache = ConcurrentHashMap<Int, ImageBitmap>()
private val textureCache = ConcurrentHashMap<GrainKind, FloatArray>()

/** A borracha é um pouco maior que o traço: come o fio de anti-aliasing que ficava como "marca" da área apagada. */
private const val ERASER_BLEED = 1f

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
    val twoPi = 2f * PI.toFloat()

    /** Polígono fechado a partir de pares x,y locais. */
    fun poly(vararg v: Float) {
        var i = 0
        while (i + 1 < v.size) {
            if (i == 0) path.moveTo(wx(v[0], v[1]), wy(v[0], v[1])) else path.lineTo(wx(v[i], v[i + 1]), wy(v[i], v[i + 1]))
            i += 2
        }
        path.close()
    }

    /** Disco em coordenadas locais. */
    fun disc(cx: Float, cy: Float, rad: Float) {
        val x = wx(cx, cy)
        val y = wy(cx, cy)
        path.addOval(Rect(x - rad, y - rad, x + rad, y + rad))
    }

    /** Fita de espessura 2w ao longo de uma polilinha local. */
    fun ribbon(xs: FloatArray, ys: FloatArray, w: Float) {
        for (i in xs.indices) {
            if (i == 0) path.moveTo(wx(xs[i], ys[i] - w), wy(xs[i], ys[i] - w)) else path.lineTo(wx(xs[i], ys[i] - w), wy(xs[i], ys[i] - w))
        }
        for (i in xs.indices.reversed()) path.lineTo(wx(xs[i], ys[i] + w), wy(xs[i], ys[i] + w))
        path.close()
    }

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
        // ---- Formas geométricas ----
        StampShape.SQUARE -> poly(-r, -r, r, -r, r, r, -r, r)
        StampShape.TRIANGLE -> poly(0f, -r, r, r * 0.8f, -r, r * 0.8f)
        StampShape.DIAMOND -> poly(0f, -r, r, 0f, 0f, r, -r, 0f)
        StampShape.HEXAGON -> {
            for (i in 0 until 6) {
                val a = i * (PI.toFloat() / 3f)
                val lx = cos(a) * r
                val ly = sin(a) * r
                if (i == 0) path.moveTo(wx(lx, ly), wy(lx, ly)) else path.lineTo(wx(lx, ly), wy(lx, ly))
            }
            path.close()
        }
        StampShape.RING, StampShape.BUBBLE -> {
            val n = 20
            for (i in 0..n) {
                val a = i * twoPi / n
                val lx = cos(a) * r
                val ly = sin(a) * r
                if (i == 0) path.moveTo(wx(lx, ly), wy(lx, ly)) else path.lineTo(wx(lx, ly), wy(lx, ly))
            }
            path.close()
            // Anel interior com sentido contrário: faz o buraco.
            for (i in 0..n) {
                val a = -i * twoPi / n
                val lx = cos(a) * r * 0.62f
                val ly = sin(a) * r * 0.62f
                if (i == 0) path.moveTo(wx(lx, ly), wy(lx, ly)) else path.lineTo(wx(lx, ly), wy(lx, ly))
            }
            path.close()
            if (shape == StampShape.BUBBLE) disc(-r * 0.3f, -r * 0.3f, r * 0.13f)
        }
        StampShape.HEART -> {
            path.moveTo(wx(0f, r * 0.9f), wy(0f, r * 0.9f))
            path.cubicTo(wx(-r * 1.3f, r * 0.1f), wy(-r * 1.3f, r * 0.1f), wx(-r * 0.7f, -r * 0.9f), wy(-r * 0.7f, -r * 0.9f), wx(0f, -r * 0.35f), wy(0f, -r * 0.35f))
            path.cubicTo(wx(r * 0.7f, -r * 0.9f), wy(r * 0.7f, -r * 0.9f), wx(r * 1.3f, r * 0.1f), wy(r * 1.3f, r * 0.1f), wx(0f, r * 0.9f), wy(0f, r * 0.9f))
            path.close()
        }
        StampShape.CROWN -> poly(-r, r * 0.7f, -r, -r * 0.4f, -r * 0.5f, r * 0.1f, 0f, -r * 0.8f, r * 0.5f, r * 0.1f, r, -r * 0.4f, r, r * 0.7f)
        StampShape.SPIRAL -> {
            val n = 48
            val w = max(0.5f, r * 0.08f)
            val sx = FloatArray(n + 1)
            val sy = FloatArray(n + 1)
            for (i in 0..n) {
                val t = i / n.toFloat()
                val a = t * 2.5f * twoPi
                sx[i] = cos(a) * r * t
                sy[i] = sin(a) * r * t
            }
            val nx = FloatArray(n + 1)
            val ny = FloatArray(n + 1)
            for (i in 0..n) {
                val j0 = max(i - 1, 0)
                val j1 = min(i + 1, n)
                val tx = sx[j1] - sx[j0]
                val ty = sy[j1] - sy[j0]
                val l = max(1e-4f, hypot(tx, ty))
                nx[i] = -ty / l * w
                ny[i] = tx / l * w
            }
            for (i in 0..n) {
                val lx = sx[i] + nx[i]
                val ly = sy[i] + ny[i]
                if (i == 0) path.moveTo(wx(lx, ly), wy(lx, ly)) else path.lineTo(wx(lx, ly), wy(lx, ly))
            }
            for (i in n downTo 0) path.lineTo(wx(sx[i] - nx[i], sy[i] - ny[i]), wy(sx[i] - nx[i], sy[i] - ny[i]))
            path.close()
        }
        StampShape.ARROW -> poly(-r, -r * 0.2f, r * 0.3f, -r * 0.2f, r * 0.3f, -r * 0.6f, r, 0f, r * 0.3f, r * 0.6f, r * 0.3f, r * 0.2f, -r, r * 0.2f)
        StampShape.CROSS -> poly(
            -r * 0.3f, -r, r * 0.3f, -r, r * 0.3f, -r * 0.3f, r, -r * 0.3f, r, r * 0.3f, r * 0.3f, r * 0.3f,
            r * 0.3f, r, -r * 0.3f, r, -r * 0.3f, r * 0.3f, -r, r * 0.3f, -r, -r * 0.3f, -r * 0.3f, -r * 0.3f,
        )
        StampShape.DROPLET -> {
            path.moveTo(wx(0f, -r), wy(0f, -r))
            path.cubicTo(wx(r * 0.9f, r * 0.1f), wy(r * 0.9f, r * 0.1f), wx(r * 0.8f, r), wy(r * 0.8f, r), wx(0f, r), wy(0f, r))
            path.cubicTo(wx(-r * 0.8f, r), wy(-r * 0.8f, r), wx(-r * 0.9f, r * 0.1f), wy(-r * 0.9f, r * 0.1f), wx(0f, -r), wy(0f, -r))
            path.close()
        }
        StampShape.SPLAT -> {
            val n = 14
            for (i in 0 until n) {
                val a = i * twoPi / n
                val rad = r * (0.55f + 0.45f * abs(sin(i * 1.7f + st.rotation * 4f)))
                val lx = cos(a) * rad
                val ly = sin(a) * rad
                if (i == 0) path.moveTo(wx(lx, ly), wy(lx, ly)) else path.lineTo(wx(lx, ly), wy(lx, ly))
            }
            path.close()
            disc(cos(3f) * r * 1.05f, sin(3f) * r * 1.05f, r * 0.12f)
            disc(cos(5.2f) * r * 1.1f, sin(5.2f) * r * 1.1f, r * 0.08f)
        }
        StampShape.BRICK -> poly(-r, -r * 0.5f, r, -r * 0.5f, r, r * 0.5f, -r, r * 0.5f)
        StampShape.FLOWER -> {
            for (i in 0 until 5) {
                val a = i * twoPi / 5f
                disc(cos(a) * r * 0.5f, sin(a) * r * 0.5f, r * 0.36f)
            }
            disc(0f, 0f, r * 0.22f)
        }
        StampShape.CONIFER -> {
            poly(0f, -r, r * 0.6f, -r * 0.2f, -r * 0.6f, -r * 0.2f)
            poly(0f, -r * 0.5f, r * 0.8f, r * 0.4f, -r * 0.8f, r * 0.4f)
            poly(0f, r * 0.1f, r, r * 0.9f, -r, r * 0.9f)
            poly(-r * 0.12f, r * 0.9f, r * 0.12f, r * 0.9f, r * 0.12f, r * 1.1f, -r * 0.12f, r * 1.1f)
        }
        StampShape.ROUNDTREE -> {
            disc(0f, -r * 0.25f, r * 0.65f)
            disc(-r * 0.45f, -r * 0.05f, r * 0.45f)
            disc(r * 0.45f, -r * 0.05f, r * 0.45f)
            poly(-r * 0.1f, r * 0.3f, r * 0.1f, r * 0.3f, r * 0.1f, r, -r * 0.1f, r)
        }
        StampShape.BAMBOO -> {
            poly(-r * 0.1f, -r, r * 0.1f, -r, r * 0.1f, r, -r * 0.1f, r)
            poly(-r * 0.16f, -r * 0.35f, r * 0.16f, -r * 0.35f, r * 0.16f, -r * 0.28f, -r * 0.16f, -r * 0.28f)
            poly(-r * 0.16f, r * 0.4f, r * 0.16f, r * 0.4f, r * 0.16f, r * 0.47f, -r * 0.16f, r * 0.47f)
            poly(r * 0.1f, -r * 0.3f, r * 0.85f, -r * 0.55f, r * 0.1f, -r * 0.12f)
        }
        StampShape.MOUNTAIN -> poly(-r, r * 0.7f, -r * 0.3f, -r * 0.6f, 0f, -r * 0.1f, r * 0.3f, -r, r, r * 0.7f)
        StampShape.PUFF -> {
            disc(0f, 0f, r * 0.6f)
            disc(-r * 0.5f, r * 0.15f, r * 0.42f)
            disc(r * 0.5f, r * 0.15f, r * 0.42f)
            disc(0f, -r * 0.3f, r * 0.45f)
        }
        StampShape.WAVE -> {
            val n = 16
            val xs = FloatArray(n + 1) { -r + 2f * r * it / n }
            val ys = FloatArray(n + 1) { sin(it / n.toFloat() * twoPi) * r * 0.35f }
            ribbon(xs, ys, max(0.8f, r * 0.1f))
        }
        StampShape.ZIGZAG -> {
            val xs = FloatArray(5) { -r + 2f * r * it / 4f }
            val ys = floatArrayOf(0f, -0.3f * r, 0.3f * r, -0.3f * r, 0f)
            ribbon(xs, ys, max(0.8f, r * 0.08f))
        }
        StampShape.SCRATCH -> {
            val w = max(0.4f, r * 0.05f)
            poly(-r, 0f, -r * 0.2f, -w, r * 0.5f, -w * 0.5f, r, 0f, r * 0.4f, w, -r * 0.3f, w * 0.4f)
        }
        StampShape.BURST -> {
            val w = max(0.4f, r * 0.06f)
            for (i in 0 until 12) {
                val ang = i * (PI.toFloat() / 6f)
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
        StampShape.LINE -> {
            val w = max(0.6f, r * 0.05f)
            poly(-r, -w, r, -w, r, w, -r, w)
        }
        StampShape.CACTUS -> {
            poly(-r * 0.18f, -r, r * 0.18f, -r, r * 0.18f, r, -r * 0.18f, r)
            poly(-r * 0.18f, r * 0.1f, -r * 0.7f, r * 0.1f, -r * 0.7f, -r * 0.5f, -r * 0.5f, -r * 0.5f, -r * 0.5f, -r * 0.1f, -r * 0.18f, -r * 0.1f)
            poly(r * 0.18f, r * 0.2f, r * 0.7f, r * 0.2f, r * 0.7f, -r * 0.4f, r * 0.5f, -r * 0.4f, r * 0.5f, 0f, r * 0.18f, 0f)
        }
        StampShape.ROCK -> {
            val n = 9
            for (i in 0 until n) {
                val a = i * twoPi / n
                val rad = r * (0.7f + 0.3f * sin(i * 2.3f + st.rotation * 5f))
                val lx = cos(a) * rad
                val ly = sin(a) * rad * 0.8f
                if (i == 0) path.moveTo(wx(lx, ly), wy(lx, ly)) else path.lineTo(wx(lx, ly), wy(lx, ly))
            }
            path.close()
        }
        StampShape.FERN -> {
            poly(-r * 0.04f, -r, r * 0.04f, -r, r * 0.04f, r, -r * 0.04f, r)
            for (k in 0..5) {
                val y = r * 0.8f - k * r * 0.32f
                val len = r * (0.7f - k * 0.09f)
                poly(0f, y, len, y - r * 0.2f, 0f, y - r * 0.08f)
                poly(0f, y, -len, y - r * 0.2f, 0f, y - r * 0.08f)
            }
        }
        StampShape.STAR5 -> {
            for (i in 0 until 10) {
                val a = -PI.toFloat() / 2f + i * (PI.toFloat() / 5f)
                val rad = if (i % 2 == 0) r else r * 0.42f
                val lx = cos(a) * rad
                val ly = sin(a) * rad
                if (i == 0) path.moveTo(wx(lx, ly), wy(lx, ly)) else path.lineTo(wx(lx, ly), wy(lx, ly))
            }
            path.close()
        }
        StampShape.FLAME -> {
            path.moveTo(wx(r * 0.1f, -r), wy(r * 0.1f, -r))
            path.cubicTo(wx(r * 1.0f, -r * 0.1f), wy(r * 1.0f, -r * 0.1f), wx(r * 0.6f, r), wy(r * 0.6f, r), wx(0f, r), wy(0f, r))
            path.cubicTo(wx(-r * 0.7f, r), wy(-r * 0.7f, r), wx(-r * 0.8f, -r * 0.1f), wy(-r * 0.8f, -r * 0.1f), wx(-r * 0.1f, -r * 0.4f), wy(-r * 0.1f, -r * 0.4f))
            path.cubicTo(wx(0f, -r * 0.5f), wy(0f, -r * 0.5f), wx(r * 0.1f, -r * 0.7f), wy(r * 0.1f, -r * 0.7f), wx(r * 0.1f, -r), wy(r * 0.1f, -r))
            path.close()
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
    val gk = preset?.texture ?: GrainKind.PAPER
    if (preset != null && preset.stamp != StampShape.NONE) {
        return InkRender(emptyList(), buildStamps(s, preset), null, 0f, 0f, null, grain, gk)
    }
    if (preset != null && preset.soft) {
        val spacing = 0.08f
        val flow = 1f - Math.pow(0.05, spacing.toDouble()).toFloat()
        val dabTaper = if (StudioRender.taperEnds) s.taper * 0.4f else 0f
        return InkRender(emptyList(), null, StampGeometry.dabs(s.points, s.width, dabTaper, spacing), s.hardness.coerceIn(0f, 1f), flow, null, grain, gk)
    }
    if (preset != null && preset.lines > 1) {
        val pl = StrokeGeometry.parallel(s.points, s.width, preset.lines)
        val rnd = java.util.Random(seedOf(s))
        val lines = pl.lines.map { pts -> LineRender(polyline(pts), pl.lineWidth, 1f - preset.lineJitter * rnd.nextFloat()) }
        return InkRender(emptyList(), null, null, 0f, 0f, lines, grain, gk)
    }
    // A borracha de traço contínuo é ligeiramente mais larga (sangra ERASER_BLEED) para não deixar fio de cor.
    val w = if (s.erase) s.width + ERASER_BLEED else s.width
    val h = s.hardness.coerceIn(0f, 1f)
    if (h > 0.95f) return InkRender(listOf(buildPass(s, w, 1f)), null, null, 0f, 0f, null, grain, gk)
    // Traços antigos com borda suave: passagens empilhadas.
    val core = w * (0.4f + 0.6f * h)
    val span = w - core
    return InkRender(
        listOf(
            buildPass(s, w, 0.14f),
            buildPass(s, core + span * 0.66f, 0.2f),
            buildPass(s, core + span * 0.33f, 0.3f),
            buildPass(s, core, 1f),
        ),
        null, null, 0f, 0f, null, grain, gk,
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

// ---------------------------------------------------------------------------------------------
// Texturas procedurais (256×256, repetem sem costuras). Tudo gerado por código.
// ---------------------------------------------------------------------------------------------

/** Ruído de valor com [cx]×[cy] células por azulejo, interpolado com suavização; repete sem costuras. */
private fun valueNoise(n: Int, cx: Int, cy: Int, rnd: java.util.Random): FloatArray {
    val grid = FloatArray(cx * cy) { rnd.nextFloat() }
    val out = FloatArray(n * n)
    val sx = n.toFloat() / cx
    val sy = n.toFloat() / cy
    for (y in 0 until n) {
        val fy = y / sy
        val y0 = fy.toInt() % cy
        val y1 = (y0 + 1) % cy
        val t0 = fy - fy.toInt()
        val ty = t0 * t0 * (3f - 2f * t0)
        for (x in 0 until n) {
            val fx = x / sx
            val x0 = fx.toInt() % cx
            val x1 = (x0 + 1) % cx
            val u0 = fx - fx.toInt()
            val tx = u0 * u0 * (3f - 2f * u0)
            val a = grid[y0 * cx + x0] + (grid[y0 * cx + x1] - grid[y0 * cx + x0]) * tx
            val b = grid[y1 * cx + x0] + (grid[y1 * cx + x1] - grid[y1 * cx + x0]) * tx
            out[y * n + x] = a + (b - a) * ty
        }
    }
    return out
}

private fun normalized(a: FloatArray): FloatArray {
    var lo = Float.MAX_VALUE
    var hi = -Float.MAX_VALUE
    for (v in a) { if (v < lo) lo = v; if (v > hi) hi = v }
    val span = max(1e-4f, hi - lo)
    return FloatArray(a.size) { (a[it] - lo) / span }
}

private fun blendNoise(n: Int, parts: List<Pair<FloatArray, Float>>): FloatArray {
    val out = FloatArray(n * n)
    for ((arr, w) in parts) for (i in out.indices) out[i] += arr[i] * w
    return normalized(out)
}

/** Ruído celular (Worley): distância ao ponto mais próximo (f1) e ao segundo (f2), repete sem costuras. */
private fun worley(n: Int, cells: Int, rnd: java.util.Random): Pair<FloatArray, FloatArray> {
    val ptx = FloatArray(cells * cells) { rnd.nextFloat() }
    val pty = FloatArray(cells * cells) { rnd.nextFloat() }
    val f1 = FloatArray(n * n)
    val f2 = FloatArray(n * n)
    val k = cells.toFloat() / n
    for (y in 0 until n) {
        val v = y * k
        val cy0 = floor(v).toInt()
        for (x in 0 until n) {
            val u = x * k
            val cx0 = floor(u).toInt()
            var m1 = 9f
            var m2 = 9f
            for (dy in -1..1) {
                for (dx in -1..1) {
                    val ncx = cx0 + dx
                    val ncy = cy0 + dy
                    val idx = (((ncy % cells) + cells) % cells) * cells + (((ncx % cells) + cells) % cells)
                    val ddx = ncx + ptx[idx] - u
                    val ddy = ncy + pty[idx] - v
                    val d = hypot(ddx, ddy)
                    if (d < m1) { m2 = m1; m1 = d } else if (d < m2) m2 = d
                }
            }
            f1[y * n + x] = (m1 / 1.1f).coerceIn(0f, 1f)
            f2[y * n + x] = (m2 / 1.4f).coerceIn(0f, 1f)
        }
    }
    return f1 to f2
}

private fun generateTexture(kind: GrainKind, n: Int): FloatArray {
    val rnd = java.util.Random(7L + kind.ordinal)
    val twoPi = 2f * PI.toFloat()
    return when (kind) {
        GrainKind.PAPER -> blendNoise(
            n, listOf(valueNoise(n, 16, 16, rnd) to 0.18f, valueNoise(n, 32, 32, rnd) to 0.30f, valueNoise(n, 64, 64, rnd) to 0.32f, valueNoise(n, 128, 128, rnd) to 0.20f),
        )
        GrainKind.CANVAS -> {
            val weave = FloatArray(n * n) { i ->
                val x = i % n
                val y = i / n
                (sin(twoPi * x / 8f) * 0.5f + 0.5f) * (sin(twoPi * y / 8f) * 0.5f + 0.5f)
            }
            blendNoise(n, listOf(weave to 0.65f, valueNoise(n, 64, 64, rnd) to 0.35f))
        }
        GrainKind.WOOD -> blendNoise(n, listOf(valueNoise(n, 3, 96, rnd) to 0.6f, valueNoise(n, 6, 160, rnd) to 0.4f))
        GrainKind.STONE -> {
            val f1 = worley(n, 10, rnd).first
            val inv = FloatArray(n * n) { 1f - f1[it] }
            blendNoise(n, listOf(inv to 0.5f, valueNoise(n, 16, 16, rnd) to 0.25f, valueNoise(n, 48, 48, rnd) to 0.25f))
        }
        GrainKind.CLOUD -> blendNoise(
            n, listOf(valueNoise(n, 4, 4, rnd) to 0.45f, valueNoise(n, 8, 8, rnd) to 0.30f, valueNoise(n, 16, 16, rnd) to 0.15f, valueNoise(n, 32, 32, rnd) to 0.10f),
        )
        GrainKind.LEATHER -> {
            val (f1, f2) = worley(n, 24, rnd)
            val edge = FloatArray(n * n) { f2[it] - f1[it] }
            blendNoise(n, listOf(edge to 0.8f, valueNoise(n, 64, 64, rnd) to 0.2f))
        }
        GrainKind.SAND -> {
            val white = FloatArray(n * n) { rnd.nextFloat() }
            blendNoise(n, listOf(valueNoise(n, 128, 128, rnd) to 0.5f, white to 0.5f))
        }
        GrainKind.GRUNGE -> {
            val base = blendNoise(
                n, listOf(valueNoise(n, 6, 6, rnd) to 0.3f, valueNoise(n, 12, 12, rnd) to 0.25f, valueNoise(n, 24, 24, rnd) to 0.2f, valueNoise(n, 48, 48, rnd) to 0.15f, valueNoise(n, 96, 96, rnd) to 0.1f),
            )
            FloatArray(n * n) { i ->
                val t = ((base[i] - 0.35f) / 0.3f).coerceIn(0f, 1f)
                t * t * (3f - 2f * t)
            }
        }
        GrainKind.METAL -> blendNoise(n, listOf(valueNoise(n, 2, 160, rnd) to 0.7f, valueNoise(n, 4, 200, rnd) to 0.3f))
        GrainKind.CONCRETE -> {
            val base = blendNoise(
                n, listOf(valueNoise(n, 8, 8, rnd) to 0.3f, valueNoise(n, 16, 16, rnd) to 0.3f, valueNoise(n, 32, 32, rnd) to 0.25f, valueNoise(n, 64, 64, rnd) to 0.15f),
            )
            repeat(500) { base[rnd.nextInt(base.size)] = 1f }
            base
        }
    }
}

private fun textureData(kind: GrainKind): FloatArray = textureCache.getOrPut(kind) { generateTexture(kind, 256) }

private fun grainImage(kind: GrainKind, strength: Float): ImageBitmap {
    val key = (strength.coerceIn(0f, 1f) * 20f).roundToInt()
    return grainCache.getOrPut(kind.ordinal * 100 + key) {
        val s = key / 20f
        val n = 256
        val noise = textureData(kind)
        val px = IntArray(n * n)
        for (i in px.indices) {
            // Quanto maior o grão, mais os "vales" da textura deixam de receber tinta.
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

/** Textura do material: mantém o traço só onde a textura (fixa na folha) tem opacidade. */
private fun DrawScope.drawGrain(strength: Float, kind: GrainKind, r: Rect) {
    val shader = ImageShader(grainImage(kind, strength), TileMode.Repeated, TileMode.Repeated)
    drawRect(ShaderBrush(shader), r.topLeft, r.size, blendMode = BlendMode.DstIn)
}

/**
 * Desenha um traço. A opacidade vem do alfa da cor; a borracha usa DstOut (apaga a opacidade, não pinta de branco).
 * Com opacidade < 100%, modo "dentro"/"por trás", toques suaves ou textura, as peças são desenhadas numa camada que
 * depois recebe o alfa, o modo e a textura, para não escurecer onde as peças se sobrepõem.
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
        // Borracha total: duas passagens DstOut tiram o fio de anti-aliasing que sobrava nas bordas ((1-c)² em vez de (1-c)).
        val passes = if (s.erase) 2 else 1
        repeat(passes) { drawRender(render, rgb, if (s.erase) BlendMode.DstOut else BlendMode.SrcOver) }
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
    if (render.grain > 0f) drawGrain(render.grain, render.grainKind, rect)
    drawContext.canvas.restore()
}

// ---------------------------------------------------------------------------------------------
// Preenchimentos (balde), máscaras de borracha e imagens (seleção do laço, importação)
// ---------------------------------------------------------------------------------------------

/** Bitmap do preenchimento: células inteiras (píxeis nítidos, sem desfocar). */
private fun fillBitmap(f: FillRegion, argb: Int): ImageBitmap {
    val mask = f.decode()
    val px = IntArray(f.w * f.h)
    for (i in px.indices) if (mask[i]) px[i] = argb
    val bmp = Bitmap.createBitmap(f.w, f.h, Bitmap.Config.ARGB_8888)
    bmp.setPixels(px, 0, f.w, 0, 0, f.w, f.h)
    return bmp.asImageBitmap()
}

/**
 * Desenha a máscara com transformação exata, em píxeis nítidos (filtro "nenhum", sem suavização ao ampliar).
 * Respeita o modo: borracha (DstOut), por trás (DstOver, o do balde), dentro (SrcAtop) ou normal.
 */
internal fun DrawScope.drawFillElement(s: BrushStroke, tint: Color?, useCache: Boolean) {
    val f = s.fill ?: return
    val cached = f.cache as? ImageBitmap
    val img = cached ?: fillBitmap(f, s.colorArgb).also { if (useCache) f.cache = it }
    val filter = if (tint != null) ColorFilter.tint(tint, BlendMode.SrcIn) else null
    val blend = when {
        s.erase -> BlendMode.DstOut
        s.mode == 2 -> BlendMode.DstOver
        s.mode == 1 -> BlendMode.SrcAtop
        else -> BlendMode.SrcOver
    }
    val size = IntSize(img.width, img.height)
    withTransform({ translate(f.x * f.unit, f.y * f.unit); scale(f.unit, f.unit, Offset.Zero) }) {
        drawImage(
            img, srcOffset = IntOffset.Zero, srcSize = size, dstOffset = IntOffset.Zero, dstSize = size,
            colorFilter = filter, blendMode = blend, filterQuality = FilterQuality.None,
        )
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
