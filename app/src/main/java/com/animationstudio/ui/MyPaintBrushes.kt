package com.animationstudio.ui

import androidx.compose.ui.geometry.Rect
import com.animationstudio.domain.BrushStroke
import java.util.Collections
import java.util.WeakHashMap

/**
 * Ligação entre o Render.kt e a biblioteca AnimBrush (o libmypaint foi removido; o nome ficou para o Render.kt não
 * mudar). O Render.kt guarda um vetor de floats por traço; aqui esse vetor é só uma chave para o traço da AnimBrush.
 * O desenho vem do motor C++: ou já calculado ao vivo (ver AnimBrushLive), ou rasterizado no primeiro desenho.
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

    /** Chave do traço [s] cuja imagem [img] já foi calculada (ao vivo). */
    fun dabsWithImage(abId: String, s: BrushStroke, img: AnimBrushImage): FloatArray {
        val key = FloatArray(1)
        lazy[key] = LazyAnimBrush(abId, s).also { it.image = img }
        return key
    }

    /** A imagem do traço; se ainda não existe, o motor rasteriza-a agora (sem linhas provisórias). */
    fun image(lz: LazyAnimBrush): AnimBrushImage? {
        lz.image?.let { return it }
        if (lz.failed) return null
        val img = AnimBrushes.render(lz.abId, lz.stroke)
        if (img == null) lz.failed = true else lz.image = img
        return img
    }

    /** O retângulo (unidades da folha) que a imagem do traço ocupa, ou null se o motor não pintou nada. */
    fun inkRect(key: FloatArray): Rect? {
        val lz = lazy[key] ?: return null
        val img = image(lz) ?: return null
        val w = img.image.width / img.scale
        val h = img.image.height / img.scale
        return Rect(img.x, img.y, img.x + w, img.y + h)
    }
}
