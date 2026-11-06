package com.animationstudio.domain

import kotlin.math.ceil
import kotlin.math.hypot

/**
 * Smoothing "por corda" (como o Rough Animator): a ponta do pincel é puxada pelo dedo ou caneta por uma corda de
 * comprimento fixo. Enquanto o dedo estiver dentro do comprimento da corda, a ponta não se mexe; quando se afasta,
 * a corda estica e arrasta a ponta pelo caminho mais curto. Resultado: o tremor da mão (menor do que a corda)
 * desaparece e as linhas saem limpas e continuas. Quanto maior a [strength] (0–1), mais comprida a corda.
 *
 * A corda mede-se em pixels de ecrã, por isso ao ampliar a folha o traço fica mais fino e preciso, como nas
 * apps de animação. No fim do traço ([finish]) a ponta junta-se ao dedo num segmento reto em passos pequenos.
 * Com [strength] = 0 devolve sempre o ponto bruto.
 */
class StrokeStabilizer(strength: Float) {
    private val ropePx = strength.coerceIn(0f, 1f) * ROPE_MAX_PX
    private var px = 0f
    private var py = 0f

    fun start(raw: Point, timeMs: Long): Point {
        px = raw.x
        py = raw.y
        return raw
    }

    fun next(raw: Point, timeMs: Long): Point {
        if (ropePx < MIN_ROPE_PX) {
            px = raw.x
            py = raw.y
            return raw
        }
        val dx = raw.x - px
        val dy = raw.y - py
        val d = hypot(dx, dy)
        if (d > ropePx) {
            val k = (d - ropePx) / d
            px += dx * k
            py += dy * k
        }
        return Point(px, py)
    }

    /** Pontos que levam a ponta até [raw], sem salto; o último é exatamente [raw]. */
    fun finish(raw: Point): List<Point> {
        if (ropePx < MIN_ROPE_PX) return listOf(raw)
        val dx = raw.x - px
        val dy = raw.y - py
        val d = hypot(dx, dy)
        val steps = ceil(d / STEP_PX).toInt().coerceIn(1, MAX_STEPS)
        val out = ArrayList<Point>(steps)
        for (i in 1 until steps) {
            val t = i.toFloat() / steps
            out.add(Point(px + dx * t, py + dy * t))
        }
        out.add(raw)
        px = raw.x
        py = raw.y
        return out
    }

    private companion object {
        const val ROPE_MAX_PX = 40f
        const val MIN_ROPE_PX = 0.5f
        const val STEP_PX = 3f
        const val MAX_STEPS = 40
    }
}
