package com.animationstudio.domain

import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Suavização do traço por percentagem (0 = sem suavização, 1 = máxima).
 *  1) média móvel ponderada (tira o tremor da mão e dos sensores do ecrã);
 *  2) corte de cantos de Chaikin (transforma segmentos retos em curvas contínuas, sem "escadas").
 * Os extremos do traço ficam sempre onde o utilizador começou e acabou. O desenho final usa anti-aliasing do canvas.
 */
object StrokeSmoothing {
    const val MAX_POINTS = 6000

    fun smooth(points: List<Point>, amount: Float): List<Point> {
        val a = amount.coerceIn(0f, 1f)
        if (a < 0.01f || points.size < 3) return points
        var pts = points
        val radius = (a * 4f).roundToInt()
        if (radius > 0) pts = movingAverage(pts, radius)
        val iterations = if (a < 0.25f) 1 else if (a < 0.6f) 2 else 3
        repeat(iterations) {
            if (pts.size >= 3 && pts.size * 2 <= MAX_POINTS) pts = chaikin(pts)
        }
        return pts
    }

    private fun movingAverage(p: List<Point>, radius: Int): List<Point> {
        val n = p.size
        val out = ArrayList<Point>(n)
        for (i in 0 until n) {
            val r = min(radius, min(i, n - 1 - i))
            if (r == 0) { out.add(p[i]); continue }
            var sx = 0f
            var sy = 0f
            var sw = 0f
            for (j in -r..r) {
                val w = (r + 1 - kotlin.math.abs(j)).toFloat()
                sx += p[i + j].x * w
                sy += p[i + j].y * w
                sw += w
            }
            out.add(Point(sx / sw, sy / sw))
        }
        return out
    }

    private fun chaikin(p: List<Point>): List<Point> {
        val out = ArrayList<Point>(p.size * 2)
        out.add(p.first())
        for (i in 0 until p.size - 1) {
            val a = p[i]
            val b = p[i + 1]
            out.add(Point(a.x * 0.75f + b.x * 0.25f, a.y * 0.75f + b.y * 0.25f))
            out.add(Point(a.x * 0.25f + b.x * 0.75f, a.y * 0.25f + b.y * 0.75f))
        }
        out.add(p.last())
        return out
    }
}
