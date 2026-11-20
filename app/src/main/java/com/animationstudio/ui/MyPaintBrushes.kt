package com.animationstudio.ui

import com.animationstudio.domain.BrushStroke
import java.util.Collections
import java.util.WeakHashMap

/**
 * Ligação entre o Render.kt e a biblioteca AnimBrush (o libmypaint foi removido; o nome ficou para o Render.kt não
 * mudar). O Render.kt guarda um vetor de floats por traço; aqui esse vetor é só uma chave para o traço da AnimBrush,
 * que se rasteriza de forma preguiçosa no primeiro desenho (ver MyPaintRender.kt).
 */
internal class LazyAnimBrush(val abId: String, val stroke: BrushStroke) {
    @Volatile var image: AnimBrushImage? = null
    @Volatile var failed = false
}

internal object MyPaintBrushes {
    /** Chaves fracas: quando o traço sai da cache, a entrada desaparece. */
    internal val lazy: MutableMap<FloatArray, LazyAnimBrush> = Collections.synchronizedMap(WeakHashMap())

    /** O id do pincel na AnimBrush, ou null se [id] não é um pincel dela. */
    fun find(id: String): String? = AnimBrushes.find(id)

    /** Chave do traço [s] com o pincel [abId]; a imagem só se calcula ao desenhar. */
    fun dabs(abId: String, s: BrushStroke): FloatArray? {
        if (s.points.isEmpty()) return null
        val key = FloatArray(1)
        lazy[key] = LazyAnimBrush(abId, s)
        return key
    }
}
