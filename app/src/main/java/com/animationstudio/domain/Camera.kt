package com.animationstudio.domain

import kotlin.math.pow

/** Interpolação entre keyframes da câmara: LINEAR ou SMOOTH (começa e acaba devagar). */
enum class Ease { LINEAR, SMOOTH }

/**
 * Estado da câmara num keyframe. [cx], [cy] = centro (coordenadas da folha), [zoom] = ampliação (1 = vê a folha
 * inteira; 1,35 = 135%), [rotation] = rotação do enquadramento em radianos (horária).
 * [ease] aplica-se ao troco que começa neste keyframe.
 */
data class CameraKey(val cx: Float, val cy: Float, val zoom: Float, val rotation: Float, val ease: Ease = Ease.SMOOTH) {
    /** Transformação folha → imagem de saída (do tamanho da folha): o centro da câmara vai para o meio da saída. */
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

/** Keyframes da câmara, por id de frame (inserir ou apagar frames não os desloca). */
data class CameraTrack(val keys: Map<Long, CameraKey> = emptyMap()) {
    val isEmpty: Boolean get() = keys.isEmpty()

    fun withKey(frameId: Long, key: CameraKey): CameraTrack = CameraTrack(keys + (frameId to key))

    fun withoutKey(frameId: Long): CameraTrack = CameraTrack(keys - frameId)
}

object CameraMath {
    private fun lerp(a: Float, b: Float, u: Float) = a + (b - a) * u

    /**
     * Câmara na posição [position] da timeline (índice de frame, pode ser fracionário): interpola entre os dois
     * keyframes vizinhos; antes do primeiro e depois do último mantém o keyframe mais próximo. Sem keyframes,
     * a câmara vê a folha inteira.
     */
    fun at(frameIds: List<Long>, track: CameraTrack, position: Float, sw: Float, sh: Float): CameraKey {
        if (track.isEmpty) return CameraKey.default(sw, sh)
        val keys = ArrayList<Pair<Int, CameraKey>>()
        for (i in frameIds.indices) {
            val k = track.keys[frameIds[i]]
            if (k != null) keys.add(i to k)
        }
        if (keys.isEmpty()) return CameraKey.default(sw, sh)
        if (position <= keys.first().first) return keys.first().second
        if (position >= keys.last().first) return keys.last().second
        for (i in 0 until keys.size - 1) {
            val (t0, k0) = keys[i]
            val (t1, k1) = keys[i + 1]
            if (position >= t0 && position <= t1) {
                val u = if (t1 == t0) 1f else (position - t0) / (t1 - t0).toFloat()
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
