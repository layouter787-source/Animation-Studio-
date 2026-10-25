package com.animationstudio.domain

import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** Polígono de uma ponta afilada: contorno + círculo na ponta. */
class RibbonShape(val polygon: List<Point>, val tip: Point, val tipRadius: Float)

/** Corpo uniforme (polilinha densa) + pontas afiladas opcionais. [radius] = metade da espessura. */
class StrokeShape(val body: List<Point>, val start: RibbonShape?, val end: RibbonShape?, val radius: Float)

/** Linhas paralelas ao longo do traço (pincéis de linhas múltiplas e de cerdas). */
class ParallelLines(val lineWidth: Float, val lines: List<List<Point>>)

object StrokeGeometry {
    /** Amostras densas (a cada ~[step]) da curva suave que passa pelos pontos (Béziers com alças limitadas a 1/3). */
    fun sample(pts: List<Point>, step: Float): List<Point> {
        if (pts.size < 2) return pts
        val out = ArrayList<Point>()
        out.add(pts[0])
        val last = pts.size - 1
        for (i in 0 until last) {
            val p0 = pts[if (i > 0) i - 1 else 0]
            val p1 = pts[i]
            val p2 = pts[i + 1]
            val p3 = pts[if (i + 2 <= last) i + 2 else last]
            val seg = hypot(p2.x - p1.x, p2.y - p1.y)
            if (seg == 0f) continue
            val d0x = p2.x - p0.x
            val d0y = p2.y - p0.y
            val l0 = hypot(d0x, d0y)
            val d3x = p3.x - p1.x
            val d3y = p3.y - p1.y
            val l3 = hypot(d3x, d3y)
            val c1x = if (l0 > 0f) p1.x + d0x / l0 * seg / 3f else p1.x
            val c1y = if (l0 > 0f) p1.y + d0y / l0 * seg / 3f else p1.y
            val c2x = if (l3 > 0f) p2.x - d3x / l3 * seg / 3f else p2.x
            val c2y = if (l3 > 0f) p2.y - d3y / l3 * seg / 3f else p2.y
            val n = ceil(seg / step).toInt().coerceIn(1, 64)
            for (k in 1..n) {
                val t = k.toFloat() / n
                val u = 1f - t
                val b0 = u * u * u
                val b1 = 3f * u * u * t
                val b2 = 3f * u * t * t
                val b3 = t * t * t
                out.add(Point(b0 * p1.x + b1 * c1x + b2 * c2x + b3 * p2.x, b0 * p1.y + b1 * c1y + b2 * c2y + b3 * p2.y))
            }
        }
        return out
    }

    private class Slice(val pts: List<Point>, val cums: List<Float>)

    private fun slice(s: List<Point>, cum: FloatArray, from: Float, to: Float): Slice {
        val pts = ArrayList<Point>()
        val cs = ArrayList<Float>()
        fun interp(i: Int, target: Float): Point {
            val seg = cum[i] - cum[i - 1]
            val t = if (seg > 0f) (target - cum[i - 1]) / seg else 0f
            return Point(s[i - 1].x + (s[i].x - s[i - 1].x) * t, s[i - 1].y + (s[i].y - s[i - 1].y) * t)
        }
        for (i in s.indices) {
            val c = cum[i]
            if (c < from) continue
            if (pts.isEmpty() && i > 0 && cum[i - 1] < from && c > from) {
                pts.add(interp(i, from))
                cs.add(from)
            }
            if (c > to) {
                if (i > 0 && cum[i - 1] <= to && (cs.isEmpty() || cs.last() < to)) {
                    pts.add(interp(i, to))
                    cs.add(to)
                }
                break
            }
            pts.add(s[i])
            cs.add(c)
        }
        return Slice(pts, cs)
    }

    private fun smooth(t: Float): Float {
        val x = t.coerceIn(0f, 1f)
        return x * x * (3f - 2f * x)
    }

    private fun ribbon(sl: Slice, distFromTip: (Float) -> Float, tipAtStart: Boolean, r: Float, taperLen: Float, taper: Float): RibbonShape? {
        val q = sl.pts
        if (q.size < 2) return null
        val last = q.size - 1
        // Ponta quase em ponto (3% do raio) e curva suave até à largura total.
        val radii = FloatArray(q.size) { i -> r * (1f - taper * 0.97f * (1f - smooth(distFromTip(sl.cums[i]) / taperLen))) }
        val left = ArrayList<Point>()
        val right = ArrayList<Point>()
        for (i in q.indices) {
            val a = q[if (i > 0) i - 1 else 0]
            val b = q[if (i < last) i + 1 else last]
            var tx = b.x - a.x
            var ty = b.y - a.y
            val len = sqrt(tx * tx + ty * ty)
            if (len > 0f) { tx /= len; ty /= len }
            val nx = -ty
            val ny = tx
            left.add(Point(q[i].x + nx * radii[i], q[i].y + ny * radii[i]))
            right.add(Point(q[i].x - nx * radii[i], q[i].y - ny * radii[i]))
        }
        val polygon = ArrayList<Point>(left)
        polygon.addAll(right.asReversed())
        val tipIdx = if (tipAtStart) 0 else last
        return RibbonShape(polygon, q[tipIdx], radii[tipIdx])
    }

    fun build(points: List<Point>, width: Float, taper: Float): StrokeShape {
        val r = width / 2f
        if (points.size < 2) return StrokeShape(points, null, null, r)
        val s = sample(points, (width * 0.2f).coerceIn(0.4f, 3f))
        if (s.size < 2) return StrokeShape(s, null, null, r)
        val cum = FloatArray(s.size)
        for (i in 1 until s.size) cum[i] = cum[i - 1] + hypot(s[i].x - s[i - 1].x, s[i].y - s[i - 1].y)
        val total = cum[s.size - 1]
        val t = taper.coerceIn(0f, 1f)
        // Canetas finas têm pontas mais compridas (até ~5,7× a espessura); pincéis grossos, mais curtas.
        val taperLen = min(total / 2.4f, width * (2.2f + 3.5f * (1f - min(width / 12f, 1f))))
        if (t < 0.05f || taperLen < width * 0.6f) return StrokeShape(s, null, null, r)
        val body = slice(s, cum, taperLen, total - taperLen).pts
        val start = ribbon(slice(s, cum, 0f, taperLen), { d -> d }, true, r, taperLen, t)
        val end = ribbon(slice(s, cum, total - taperLen, total), { d -> total - d }, false, r, taperLen, t)
        return StrokeShape(body, start, end, r)
    }

    /** [lines] polilinhas paralelas distribuídas pela espessura (para linha dupla, tripla, cerdas…). */
    fun parallel(points: List<Point>, width: Float, lines: Int): ParallelLines {
        val n = max(1, lines)
        val lw = if (n <= 5) max(1f, width * 0.1f) else max(0.8f, width / n * 0.85f)
        if (points.size < 2) return ParallelLines(lw, List(n) { points })
        val s = sample(points, (lw * 1.5f).coerceIn(0.5f, 4f))
        val span = max(0f, width - lw)
        val out = List(n) { ArrayList<Point>(s.size) }
        for (i in s.indices) {
            val a = s[max(i - 1, 0)]
            val b = s[min(i + 1, s.size - 1)]
            var tx = b.x - a.x
            var ty = b.y - a.y
            val len = hypot(tx, ty)
            if (len > 0f) { tx /= len; ty /= len }
            val nx = -ty
            val ny = tx
            for (k in 0 until n) {
                val off = if (n == 1) 0f else (k / (n - 1f) - 0.5f) * span
                out[k].add(Point(s[i].x + nx * off, s[i].y + ny * off))
            }
        }
        return ParallelLines(lw, out)
    }
}
