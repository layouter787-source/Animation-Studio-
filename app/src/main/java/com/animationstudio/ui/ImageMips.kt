package com.animationstudio.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.animationstudio.domain.ImageSource
import kotlin.math.max
import kotlin.math.min

/**
 * Versões reduzidas (metade, quarto…) das imagens da folha. Uma imagem levantada pelo laço tem 2 a 4 píxeis por unidade
 * da folha; ao desenhá-la reduzida com filtro bilinear simples haveria serrilhado. Em vez disso escolhe-se o nível
 * cuja resolução fica mais perto do que o ecrã (ou a exportação) precisa. Cada nível é a média 2×2 do anterior.
 */
internal object ImageMips {
    const val MAX_LEVEL = 5

    /** Bitmap do nível [level] (0 = original). [remember] guarda o resultado na própria imagem. Devolve null se o PNG falhar. */
    fun get(src: ImageSource, level: Int, remember: Boolean = true): Bitmap? = synchronized(src) {
        val base = (src.cache as? ImageBitmap)?.asAndroidBitmap()
            ?: BitmapFactory.decodeByteArray(src.png, 0, src.png.size)?.also { if (remember) src.cache = it.asImageBitmap() }
            ?: return null
        if (level <= 0) return base
        @Suppress("UNCHECKED_CAST")
        val levels = (src.mips as? Array<Bitmap?>)
            ?: arrayOfNulls<Bitmap>(MAX_LEVEL + 1).also { if (remember) src.mips = it }
        var prev = base
        for (l in 1..min(level, MAX_LEVEL)) {
            var b = levels[l]
            if (b == null) {
                b = Bitmap.createScaledBitmap(prev, max(1, prev.width / 2), max(1, prev.height / 2), true)
                levels[l] = b
            }
            prev = b
        }
        prev
    }

    /** Nível a usar para uma imagem desenhada com [devicePerSource] píxeis de saída por píxel da imagem. */
    fun levelFor(devicePerSource: Float): Int {
        if (devicePerSource >= 1f || devicePerSource <= 0f) return 0
        var level = 0
        var d = devicePerSource
        while (d < 0.5f && level < MAX_LEVEL) {
            d *= 2f
            level++
        }
        return level
    }
}

/**
 * Escala (píxeis de saída por unidade da folha) com que se está a desenhar neste momento. O editor usa o zoom da vista;
 * a cache de camadas, as miniaturas e as exportações indicam a sua própria escala com [with].
 */
internal object ImageScale {
    private val local = ThreadLocal<Float?>()

    fun current(): Float = local.get() ?: StudioRender.view.scale

    inline fun <T> with(scale: Float, block: () -> T): T {
        val previous = peek()
        set(scale)
        try {
            return block()
        } finally {
            set(previous)
        }
    }

    fun peek(): Float? = local.get()

    fun set(v: Float?) = local.set(v)
}
