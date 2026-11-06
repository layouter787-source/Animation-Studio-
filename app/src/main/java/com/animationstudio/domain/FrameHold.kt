package com.animationstudio.domain

import kotlin.math.min

/**
 * Duração de um frame (exposição): vários frames seguidos que apontam para o mesmo desenho em todas as camadas.
 * Estender a duração acrescenta frames que partilham o desenho (não copia nada); encurtar tira o último.
 * Frames em branco não contam como exposição: só há exposição onde há desenho numa camada de desenho.
 */

/** Dois frames seguidos mostram exatamente o mesmo desenho (e há desenho numa camada de desenho). */
fun Project.sameExposure(a: Int, b: Int): Boolean {
    if (a == b || a !in frameIds.indices || b !in frameIds.indices) return false
    val fa = frameIds[a]
    val fb = frameIds[b]
    var any = false
    for (l in layers) {
        val ca = l.cels[fa]
        val cb = l.cels[fb]
        if (ca != cb) return false
        if (ca != null && l.kind == LayerKind.DRAWING) any = true
    }
    return any
}

/** Primeiro frame da exposição a que [index] pertence. */
fun Project.holdStart(index: Int): Int {
    var j = index.coerceIn(0, frameCount - 1)
    while (j > 0 && sameExposure(j - 1, j)) j--
    return j
}

/** Último frame da exposição a que [index] pertence. */
fun Project.holdEnd(index: Int): Int {
    var j = index.coerceIn(0, frameCount - 1)
    while (j + 1 < frameCount && sameExposure(j, j + 1)) j++
    return j
}

/** Estende a exposição do frame [index] em [count] frames, logo a seguir ao fim dela. O frame atual não muda. */
fun Project.extendHold(index: Int, count: Int = 1): Project {
    var p = this
    repeat(count.coerceIn(0, 500)) {
        val e = p.holdEnd(min(index, p.frameCount - 1))
        val src = p.frameIds[e]
        val newId = p.nextId
        val ids = p.frameIds.toMutableList().apply { add(e + 1, newId) }
        val newLayers = p.layers.map { l ->
            val d = l.cels[src]
            if (d != null) l.copy(cels = l.cels + (newId to d)) else l
        }
        p = Project(ids, newLayers, if (p.currentFrame > e) p.currentFrame + 1 else p.currentFrame, p.nextId + 1)
    }
    return p
}

/** Encurta a exposição do frame [index] em [count] frames (tira o último de cada vez; nunca o primeiro). */
fun Project.shrinkHold(index: Int, count: Int = 1): Project {
    var p = this
    var idx = index
    repeat(count.coerceIn(0, 500)) {
        idx = idx.coerceIn(0, p.frameCount - 1)
        val s = p.holdStart(idx)
        val e = p.holdEnd(idx)
        if (e > s) {
            val fid = p.frameIds[e]
            val ids = p.frameIds.toMutableList().apply { removeAt(e) }
            val newLayers = p.layers.map { l ->
                val remaining = l.cels - fid
                val used = remaining.values.toSet()
                l.copy(cels = remaining, drawings = l.drawings.filterKeys { it in used })
            }
            val cur = when {
                p.currentFrame == e -> e - 1
                p.currentFrame > e -> p.currentFrame - 1
                else -> p.currentFrame
            }
            if (idx == e) idx = e - 1
            p = Project(ids, newLayers, cur, p.nextId)
        }
    }
    return p
}
