package com.animationstudio.domain

import kotlin.math.PI
import kotlin.math.hypot

/**
 * Estabilizador de traço com filtro One Euro: remove o tremor quando o dedo anda devagar (corte baixo) e
 * deixa de atrasar quando anda depressa (o corte sobe com a velocidade). Trabalha em qualquer unidade;
 * a app aplica-o em pixels de ecrã. [strength] 0 = sem filtro; 1 = máximo.
 */
class StrokeStabilizer(strength: Float) {
    private val s = strength.coerceIn(0f, 1f)
    private val minCutoff = 10f - 9.5f * s
    private val beta = 0.012f
    private val dCutoff = 1f
    private var x = 0f
    private var y = 0f
    private var dx = 0f
    private var dy = 0f
    private var lastT = -1L

    private fun alpha(cutoff: Float, dt: Float): Float {
        val tau = 1f / (2f * PI.toFloat() * cutoff)
        return 1f / (1f + tau / dt)
    }

    fun start(p: Point, tMs: Long = -1L): Point {
        x = p.x
        y = p.y
        dx = 0f
        dy = 0f
        lastT = tMs
        return p
    }

    fun next(raw: Point, tMs: Long = -1L): Point {
        if (s <= 0.001f) return raw
        val dt = if (tMs >= 0L && lastT >= 0L && tMs > lastT) ((tMs - lastT) / 1000f).coerceIn(0.002f, 0.1f) else 1f / 120f
        if (tMs >= 0L) lastT = tMs
        val ad = alpha(dCutoff, dt)
        dx += ((raw.x - x) / dt - dx) * ad
        dy += ((raw.y - y) / dt - dy) * ad
        val a = alpha(minCutoff + beta * hypot(dx, dy), dt)
        x += (raw.x - x) * a
        y += (raw.y - y) * a
        return Point(x, y)
    }

    /** Pontos para o traço alcançar o dedo no fim, sem salto. */
    fun finish(raw: Point): List<Point> {
        val out = ArrayList<Point>()
        for (i in 0 until 80) {
            out.add(next(raw))
            if (hypot(raw.x - x, raw.y - y) < 0.5f) break
        }
        out.add(raw)
        return out
    }
}
