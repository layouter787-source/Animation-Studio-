package com.animationstudio.domain

import kotlin.math.max
import kotlin.math.min

data class Bounds(val minX: Float, val minY: Float, val maxX: Float, val maxY: Float) {
    val cx: Float get() = (minX + maxX) / 2f
    val cy: Float get() = (minY + maxY) / 2f
    fun union(o: Bounds) = Bounds(min(minX, o.minX), min(minY, o.minY), max(maxX, o.maxX), max(maxY, o.maxY))
}

fun BrushStroke.bounds(): Bounds? {
    val im = image
    if (im != null) return im.bounds()
    val f = fill
    if (f != null) return Bounds(f.x * f.unit, f.y * f.unit, (f.x + f.w) * f.unit, (f.y + f.h) * f.unit)
    if (points.isEmpty()) return null
    val r = width / 2f
    return Bounds(
        points.minOf { it.x } - r, points.minOf { it.y } - r,
        points.maxOf { it.x } + r, points.maxOf { it.y } + r,
    )
}

/** Geometria do laço: ponto dentro de polígono e polígonos dos modos retângulo e elipse. */
object Lasso {
    fun pointInPolygon(p: Point, poly: List<Point>): Boolean {
        var inside = false
        var j = poly.size - 1
        for (i in poly.indices) {
            val a = poly[i]
            val b = poly[j]
            if ((a.y > p.y) != (b.y > p.y) && p.x < (b.x - a.x) * (p.y - a.y) / (b.y - a.y) + a.x) inside = !inside
            j = i
        }
        return inside
    }

    fun rectangle(a: Point, b: Point): List<Point> =
        listOf(Point(a.x, a.y), Point(b.x, a.y), Point(b.x, b.y), Point(a.x, b.y))

    fun ellipse(a: Point, b: Point, steps: Int = 48): List<Point> {
        val cx = (a.x + b.x) / 2f
        val cy = (a.y + b.y) / 2f
        val rx = kotlin.math.abs(b.x - a.x) / 2f
        val ry = kotlin.math.abs(b.y - a.y) / 2f
        return List(steps) { i ->
            val t = 2.0 * Math.PI * i / steps
            Point(cx + (kotlin.math.cos(t) * rx).toFloat(), cy + (kotlin.math.sin(t) * ry).toFloat())
        }
    }

    fun select(strokes: List<BrushStroke>, polygon: List<Point>): Set<Int> {
        if (polygon.size < 3) return emptySet()
        val out = LinkedHashSet<Int>()
        strokes.forEachIndexed { i, s ->
            if (s.fill != null || s.image != null) {
                val b = s.bounds()
                if (b != null && pointInPolygon(Point(b.cx, b.cy), polygon)) out.add(i)
            } else if (s.points.isNotEmpty()) {
                val inside = s.points.count { pointInPolygon(it, polygon) }
                if (inside * 2 >= s.points.size) out.add(i)
            }
        }
        return out
    }

    fun boundsOf(strokes: List<BrushStroke>, indices: Set<Int>): Bounds? {
        var acc: Bounds? = null
        for (i in indices) {
            val b = strokes.getOrNull(i)?.bounds() ?: continue
            acc = if (acc == null) b else acc.union(b)
        }
        return acc
    }
}
