package com.animationstudio.domain

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Transformação afim 2D: x' = a·x + c·y + tx ; y' = b·x + d·y + ty. */
data class Affine(val a: Float, val b: Float, val c: Float, val d: Float, val tx: Float, val ty: Float) {
    fun map(p: Point): Point = Point(a * p.x + c * p.y + tx, b * p.x + d * p.y + ty)

    val scaleFactor: Float get() = sqrt(abs(a * d - b * c))

    /** Composição: aplica primeiro [o] e depois esta transformação. */
    fun times(o: Affine): Affine = Affine(
        a * o.a + c * o.b,
        b * o.a + d * o.b,
        a * o.c + c * o.d,
        b * o.c + d * o.d,
        a * o.tx + c * o.ty + tx,
        b * o.tx + d * o.ty + ty,
    )

    fun inverse(): Affine? {
        val det = a * d - b * c
        if (abs(det) < 1e-9f) return null
        val ia = d / det
        val ib = -b / det
        val ic = -c / det
        val id = a / det
        return Affine(ia, ib, ic, id, -(ia * tx + ic * ty), -(ib * tx + id * ty))
    }

    companion object {
        val IDENTITY = Affine(1f, 0f, 0f, 1f, 0f, 0f)

        fun translate(dx: Float, dy: Float) = Affine(1f, 0f, 0f, 1f, dx, dy)

        /** Espelho horizontal em torno da reta vertical x = [px]. */
        fun flipH(px: Float) = Affine(-1f, 0f, 0f, 1f, 2f * px, 0f)

        /** Espelho vertical em torno da reta horizontal y = [py]. */
        fun flipV(py: Float) = Affine(1f, 0f, 0f, -1f, 0f, 2f * py)

        /** p' = R(rot)·S(scale)·(p − pivot) + pivot + (dx, dy). Rotação positiva = horária no ecrã (y para baixo). */
        fun around(pivot: Point, scale: Float, rotationRad: Float, dx: Float, dy: Float): Affine {
            val cs = cos(rotationRad) * scale
            val sn = sin(rotationRad) * scale
            val a = cs
            val b = sn
            val c = -sn
            val d = cs
            return Affine(a, b, c, d, pivot.x + dx - (a * pivot.x + c * pivot.y), pivot.y + dy - (b * pivot.x + d * pivot.y))
        }
    }
}
