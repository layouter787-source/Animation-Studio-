package com.animationstudio.domain

import kotlin.math.hypot

/**
 * Estabilizador de traço: o ponto desenhado segue o dedo com atraso (média exponencial).
 * [strength] 0 = sem suavização; 1 = máxima. Em [finish] o ponto alcança o dedo sem salto.
 */
class StrokeStabilizer(strength: Float) {
    private val k: Float = (1f - 0.94f * strength.coerceIn(0f, 1f)).coerceIn(0.05f, 1f)
    private var sx = 0f
    private var sy = 0f

    fun start(p: Point): Point {
        sx = p.x
        sy = p.y
        return p
    }

    fun next(raw: Point): Point {
        sx += (raw.x - sx) * k
        sy += (raw.y - sy) * k
        return Point(sx, sy)
    }

    fun finish(raw: Point): List<Point> {
        val out = ArrayList<Point>()
        for (i in 0 until 40) {
            out.add(next(raw))
            if (hypot(raw.x - sx, raw.y - sy) < 0.25f) break
        }
        out.add(raw)
        return out
    }
}
