package com.animationstudio.domain

/**
 * Smoothing "por corda" (como o Rough Animator): a ponta do pincel é puxada pelo dedo ou caneta por uma corda de
 * comprimento fixo. Enquanto o dedo estiver dentro do comprimento da corda, a ponta não se mexe; quando se afasta,
 * a corda estica e arrasta a ponta pelo caminho mais curto. Resultado: o tremor da mão (menor do que a corda)
 * desaparece e as linhas saem limpas e contínuas. Quanto maior a [strength] (0–1), mais comprida a corda.
 *
 * A corda mede-se em píxeis de ecrã, por isso ao ampliar a folha o traço fica mais fino e preciso. No fim do traço
 * ([finish]) a ponta junta-se ao dedo por uma curva que sai na direção em que a corda andava (não por um segmento reto).
 * A conta vive no C++ (studio_ink.hpp). Com [strength] = 0 devolve sempre o ponto bruto.
 */
class StrokeStabilizer(strength: Float) {
    private val ropePx = strength.coerceIn(0f, 1f) * ROPE_MAX_PX
    private val state = FloatArray(4)
    private var t0 = 0L
    private var lastT = 0f

    /** [timeMs] = instante real do evento: os pontos devolvidos levam o tempo desde o início (a velocidade decide as pontas). */
    fun start(raw: Point, timeMs: Long): Point {
        t0 = timeMs
        lastT = 0f
        NativeStudio.nRopeStart(state, raw.x, raw.y)
        return raw.copy(t = 0f)
    }

    fun next(raw: Point, timeMs: Long): Point {
        lastT = (timeMs - t0).toFloat().coerceAtLeast(lastT)
        NativeStudio.nRopeNext(state, raw.x, raw.y, ropePx)
        return Point(state[0], state[1], 1f, lastT)
    }

    /** Pontos que levam a ponta até [raw], sem salto; o último é exatamente [raw]. */
    fun finish(raw: Point): List<Point> {
        val xy = NativeStudio.nRopeFinish(state, raw.x, raw.y, ropePx)
        return List(xy.size / 2) { Point(xy[2 * it], xy[2 * it + 1], 1f, lastT) }
    }

    private companion object {
        const val ROPE_MAX_PX = 40f
    }
}
