package com.animationstudio.domain

/** Move uma camada [delta] posições na pilha (positivo = para cima, negativo = para baixo). */
fun Project.moveLayer(layerId: Long, delta: Int): Project = moveLayerImpl(layerId, delta)
