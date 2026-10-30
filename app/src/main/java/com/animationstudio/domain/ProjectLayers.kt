package com.animationstudio.domain

/** Move uma camada [delta] posições na pilha (positivo = para cima, negativo = para baixo). */
fun Project.moveLayer(layerId: Long, delta: Int): Project {
    val i = layers.indexOfFirst { it.id == layerId }
    if (i < 0) return this
    val j = (i + delta).coerceIn(0, layers.lastIndex)
    if (i == j) return this
    val list = layers.toMutableList()
    val l = list.removeAt(i)
    list.add(j, l)
    return copy(layers = list)
}
