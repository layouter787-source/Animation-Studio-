package com.animationstudio.domain

/** Pixels de uma imagem em PNG. A cache (bitmap decodificado) é partilhada por todas as cópias transformadas. */
class ImageSource(val png: ByteArray, val w: Int, val h: Int) {
    var cache: Any? = null

    override fun equals(other: Any?): Boolean =
        other is ImageSource && w == other.w && h == other.h && png.contentEquals(other.png)

    override fun hashCode(): Int = w * 31 + h
}

/** Imagem colocada na folha: [m] leva as coordenadas da imagem (0..w, 0..h) para a folha. */
class ImageData(val source: ImageSource, val m: Affine) {
    fun transformed(t: Affine): ImageData = ImageData(source, t.times(m))

    fun corners(): List<Point> {
        val w = source.w.toFloat()
        val h = source.h.toFloat()
        return listOf(Point(0f, 0f), Point(w, 0f), Point(w, h), Point(0f, h)).map { m.map(it) }
    }

    fun bounds(): Bounds {
        val c = corners()
        return Bounds(c.minOf { it.x }, c.minOf { it.y }, c.maxOf { it.x }, c.maxOf { it.y })
    }

    override fun equals(other: Any?): Boolean = other is ImageData && source == other.source && m == other.m

    override fun hashCode(): Int = source.hashCode() * 31 + m.hashCode()
}
