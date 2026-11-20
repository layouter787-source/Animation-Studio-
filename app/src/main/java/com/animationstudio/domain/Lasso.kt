package com.animationstudio.domain

import kotlin.math.ceil
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
    // Os pincéis da AnimBrush pintam fora do caminho (ramos, chamas, brilho): a caixa tem de os cobrir.
    val r = width / 2f + (if (brush.startsWith("ab:")) max(48f, width * 2f) else 0f)
    return Bounds(
        points.minOf { it.x } - r, points.minOf { it.y } - r,
        points.maxOf { it.x } + r, points.maxOf { it.y } + r,
    )
}

/** Geometria do laço: ponto dentro de polígono e polígonos dos modos retângulo e elipse. */
object Lasso {
    /** Máximo de píxeis da imagem levantada pelo laço (memória): ~9 milhões = 36 MB em ARGB_8888. */
    const val MAX_LIFT_PIXELS = 9_000_000L

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

    /**
     * Fator de super-amostragem da seleção (1..4): quantos píxeis da imagem levantada por unidade da folha.
     * Sobe com o zoom do editor (para ficar nítida quando se trabalha ampliado) e desce nas seleções grandes
     * para respeitar [MAX_LIFT_PIXELS].
     */
    fun supersample(w: Int, h: Int, zoom: Float): Int {
        val area = max(1L, w.toLong() * h.toLong())
        var k = ceil(zoom * 1.5f).toInt().coerceIn(2, 4)
        while (k > 1 && area * k * k > MAX_LIFT_PIXELS) k--
        return k
    }

    /**
     * Máscara do polígono sobre a grelha da caixa (x0, y0, w, h) em unidades da folha, com [k] células por unidade.
     * Regra do enrolamento (como o preenchimento do canvas): o interior de um laço feito à mão que se cruza
     * a si próprio fica todo selecionado. Uma célula está dentro se o seu centro está dentro.
     * Tamanho do resultado: (w·k)·(h·k), por linhas.
     */
    fun rasterize(polygon: List<Point>, x0: Int, y0: Int, w: Int, h: Int, k: Int): BooleanArray {
        val mw = w * k
        val mh = h * k
        val out = BooleanArray(max(0, mw) * max(0, mh))
        val n = polygon.size
        if (n < 3 || mw <= 0 || mh <= 0) return out
        val xs = FloatArray(n)
        val dirs = IntArray(n)
        for (row in 0 until mh) {
            val y = y0 + (row + 0.5f) / k
            var cnt = 0
            for (i in 0 until n) {
                val a = polygon[i]
                val b = polygon[if (i + 1 == n) 0 else i + 1]
                val up = a.y <= y && b.y > y
                val down = b.y <= y && a.y > y
                if (up || down) {
                    val t = (y - a.y) / (b.y - a.y)
                    xs[cnt] = a.x + t * (b.x - a.x)
                    dirs[cnt] = if (up) 1 else -1
                    cnt++
                }
            }
            if (cnt < 2) continue
            for (i in 1 until cnt) {
                val xv = xs[i]
                val dv = dirs[i]
                var j = i - 1
                while (j >= 0 && xs[j] > xv) {
                    xs[j + 1] = xs[j]
                    dirs[j + 1] = dirs[j]
                    j--
                }
                xs[j + 1] = xv
                dirs[j + 1] = dv
            }
            var winding = 0
            for (i in 0 until cnt - 1) {
                winding += dirs[i]
                if (winding != 0) {
                    val c0 = ceil((xs[i] - x0) * k - 0.5f).toInt().coerceIn(0, mw)
                    val c1 = ceil((xs[i + 1] - x0) * k - 0.5f).toInt().coerceIn(0, mw)
                    for (c in c0 until c1) out[row * mw + c] = true
                }
            }
        }
        return out
    }
}
