package com.animationstudio.domain

/**
 * Contorno do traço de tinta, calculado no C++ (studio_ink.hpp). O traço é desenhado com a espessura cheia e termina em pontas
 * que dependem da velocidade: rápido = ponta longa e fina; lento = ponta curta e mais grossa.
 */
object InkOutline {
    /** Tempo (ms) que mede uma ponta de tamanho 1: o caminho andado nesse tempo antes de levantar a caneta. */
    private const val TAPER_MS = 100f

    /**
     * Polígonos [n, x0, y0, ..., x(n-1), y(n-1)]* do traço; preencher com a regra não-zero.
     * [taper] = 0 desliga as pontas (espessura cheia, pontas redondas); 1 = pontas normais.
     */
    fun polygons(points: List<Point>, width: Float, taper: Float): FloatArray {
        val n = points.size
        val xy = FloatArray(n * 2)
        val p = FloatArray(n)
        val t = FloatArray(n)
        for (i in 0 until n) {
            val q = points[i]
            xy[2 * i] = q.x
            xy[2 * i + 1] = q.y
            p[i] = q.p
            t[i] = q.t
        }
        val tp = taper.coerceIn(0f, 1.5f)
        // largura, fração mínima, pressão, velocidade, ponta inicial, ponta final, tamanho das pontas, ms, alisamento, escala
        val params = floatArrayOf(width, 0.01f, 0f, 0f, tp, tp, 1f, TAPER_MS, 0.5f, 1f)
        return NativeStudio.nInkOutline(xy, p, t, params)
    }
}
