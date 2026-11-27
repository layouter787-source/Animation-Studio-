package com.animationstudio.domain

/**
 * Duração de um frame (exposição): vários frames seguidos que apontam para o mesmo desenho em todas as camadas.
 * Estender a duração acrescenta frames que partilham o desenho (não copia nada); encurtar tira o último.
 * Frames em branco não contam como exposição: só há exposição onde há desenho numa camada de desenho.
 * As regras vivem no C++ (studio_hold.hpp).
 */

/** Dois frames seguidos mostram exatamente o mesmo desenho (e há desenho numa camada de desenho). */
fun Project.sameExposure(a: Int, b: Int): Boolean = fenced { NativeStudio.nSameExposure(handle, a, b) }

/** Primeiro frame da exposição a que [index] pertence. */
fun Project.holdStart(index: Int): Int = fenced { NativeStudio.nHoldStart(handle, index) }

/** Último frame da exposição a que [index] pertence. */
fun Project.holdEnd(index: Int): Int = fenced { NativeStudio.nHoldEnd(handle, index) }

/** Estende a exposição do frame [index] em [count] frames, logo a seguir ao fim dela. O frame atual não muda. */
fun Project.extendHold(index: Int, count: Int = 1): Project {
    val n = count.coerceIn(0, 500)
    return if (n == 0) this else fenced { Project.of(NativeStudio.nExtendHold(handle, index, n)) }
}

/** Encurta a exposição do frame [index] em [count] frames (tira o último de cada vez; nunca o primeiro). */
fun Project.shrinkHold(index: Int, count: Int = 1): Project {
    val n = count.coerceIn(0, 500)
    if (n == 0) return this
    val next = fenced { Project.of(NativeStudio.nShrinkHold(handle, index, n)) }
    return if (next.frameCount == frameCount) this else next
}
