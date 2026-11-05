package com.animationstudio.domain

import kotlin.math.pow

/** Interpolação entre keyframes: LINEAR ou SMOOTH (começa e acaba devagar). */
enum class Ease { LINEAR, SMOOTH }

/**
 * Estado da câmara num keyframe. [cx], [cy] = centro (coordenadas da folha), [zoom] = ampliação (1 = vê a folha
 * inteira; 2 = vê metade da largura), [rotation] = rotação do enquadramento em radianos (horária).
 * [ease] aplica-se ao troco que começa neste keyframe.
 */
data class CameraKey(val cx: Float, val cy: Float, val zoom: Float, val rotation: Float, val ease: Ease = Ease.SMOOTH) {
    /** Transformação folha → imagem de saída (do tamanho da folha): centro da câmara vai para o meio da saída. */
    fun outputAffine(sw: Float, sh: Float): Affine {
        val toOrigin = Affine.translate(-cx, -cy)
        val rot = Affine.around(Point(0f, 0f), 1f, -rotation, 0f, 0f)
        val scale = Affine(zoom, 0f, 0f, zoom, 0f, 0f)
        val toCenter = Affine.translate(sw / 2f, sh / 2f)
        return toCenter.times(scale).times(rot).times(toOrigin)
    }

    /** Os quatro cantos do enquadramento na folha (para desenhar o retângulo da câmara). */
    fun rect(sw: Float, sh: Float): List<Point> {
        val inv = outputAffine(sw, sh).inverse() ?: return emptyList()
        return listOf(Point(0f, 0f), Point(sw, 0f), Point(sw, sh), Point(0f, sh)).map { inv.map(it) }
    }

    companion object {
        fun default(sw: Float, sh: Float) = CameraKey(sw / 2f, sh / 2f, 1f, 0f)
    }
}

/**
 * Dados de tempo do projeto, por id de frame (por isso inserir ou apagar frames não os desloca):
 *  - [holds]: duração de cada frame em tempos (1 por defeito) — arrastar a ponta direita do frame;
 *  - [camera]: keyframes da câmara.
 */
data class Timing(val holds: Map<Long, Int> = emptyMap(), val camera: Map<Long, CameraKey> = emptyMap()) {
    fun hold(frameId: Long): Int = (holds[frameId] ?: 1).coerceIn(1, MAX_HOLD)

    fun withHold(frameId: Long, ticks: Int): Timing {
        val t = ticks.coerceIn(1, MAX_HOLD)
        return copy(holds = if (t == 1) holds - frameId else holds + (frameId to t))
    }

    fun withKey(frameId: Long, key: CameraKey): Timing = copy(camera = camera + (frameId to key))

    fun withoutKey(frameId: Long): Timing = copy(camera = camera - frameId)

    val hasCamera: Boolean get() = camera.isNotEmpty()

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

    fun frameAtTick(frameIds: List<Long>, t: Timing, tick: Int): Int {
        var acc = 0
        for (i in frameIds.indices) {
            acc += t.hold(frameIds[i])
            if (tick < acc) return i
        }
        return frameIds.lastIndex
    }

    private fun lerp(a: Float, b: Float, u: Float) = a + (b - a) * u

    /** Câmara no instante [tick] (pode ser fracionário): interpola entre keyframes; antes/depois mantém o mais próximo. */
    fun cameraAt(frameIds: List<Long>, t: Timing, tick: Float, sw: Float, sh: Float): CameraKey {
        if (t.camera.isEmpty()) return CameraKey.default(sw, sh)
        val starts = startTicks(frameIds, t)
        val keys = ArrayList<Pair<Int, CameraKey>>()
        for (i in frameIds.indices) {
            val k = t.camera[frameIds[i]]
            if (k != null) keys.add(starts[i] to k)
        }
        if (keys.isEmpty()) return CameraKey.default(sw, sh)
        if (tick <= keys.first().first) return keys.first().second
        if (tick >= keys.last().first) return keys.last().second
        for (i in 0 until keys.size - 1) {
            val (t0, k0) = keys[i]
            val (t1, k1) = keys[i + 1]
            if (tick >= t0 && tick <= t1) {
                val u = if (t1 == t0) 1f else (tick - t0) / (t1 - t0).toFloat()
                val e = if (k0.ease == Ease.SMOOTH) u * u * (3f - 2f * u) else u
                return CameraKey(
                    lerp(k0.cx, k1.cx, e), lerp(k0.cy, k1.cy, e),
                    k0.zoom * (k1.zoom / k0.zoom).pow(e), lerp(k0.rotation, k1.rotation, e), k0.ease,
                )
            }
        }
        return keys.last().second
    }
}
