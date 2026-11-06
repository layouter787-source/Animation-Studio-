package com.animationstudio.domain

/**
 * Dados de tempo do projeto, por id de frame (por isso inserir ou apagar frames não os desloca):
 * [holds] = duração de cada frame em tempos (1 por defeito), definida ao arrastar a ponta direita do frame.
 */
data class Timing(val holds: Map<Long, Int> = emptyMap()) {
    fun hold(frameId: Long): Int = (holds[frameId] ?: 1).coerceIn(1, MAX_HOLD)

    fun withHold(frameId: Long, ticks: Int): Timing {
        val t = ticks.coerceIn(1, MAX_HOLD)
        return copy(holds = if (t == 1) holds - frameId else holds + (frameId to t))
    }

    companion object {
        const val MAX_HOLD = 48
    }
}

object TimingMath {
    /** Tempo (em tempos) em que cada frame começa. */
    fun startTicks(frameIds: List<Long>, t: Timing): IntArray {
        val out = IntArray(frameIds.size)
        var acc = 0
        for (i in frameIds.indices) {
            out[i] = acc
            acc += t.hold(frameIds[i])
        }
        return out
    }

    fun totalTicks(frameIds: List<Long>, t: Timing): Int = frameIds.sumOf { t.hold(it) }

    /** Índice do frame que está visível no tempo [tick]. */
    fun frameAtTick(frameIds: List<Long>, t: Timing, tick: Int): Int {
        var acc = 0
        for (i in frameIds.indices) {
            acc += t.hold(frameIds[i])
            if (tick < acc) return i
        }
        return frameIds.lastIndex
    }
}
