package com.animationstudio.domain

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * Região preenchida pelo balde: máscara binária em grelha, comprimida por corridas alternadas
 * (começa por "desligado"). [x], [y] são a origem em células; [unit] = unidades do projeto por célula.
 */
class FillRegion(val x: Int, val y: Int, val w: Int, val h: Int, val unit: Float, val runs: List<Int>) {
    /** Cache opcional da UI (bitmap). Não participa em equals/hashCode. */
    var cache: Any? = null

    fun decode(): BooleanArray {
        val out = BooleanArray(w * h)
        var pos = 0
        var on = false
        for (r in runs) {
            if (on) {
                val end = min(pos + r, out.size)
                for (i in pos until end) out[i] = true
            }
            pos += r
            on = !on
        }
        return out
    }

    fun transformed(m: Affine): FillRegion? {
        val inv = m.inverse() ?: return null
        val corners = listOf(
            m.map(Point(x * unit, y * unit)),
            m.map(Point((x + w) * unit, y * unit)),
            m.map(Point(x * unit, (y + h) * unit)),
            m.map(Point((x + w) * unit, (y + h) * unit)),
        )
        val nx = floor(corners.minOf { it.x } / unit).toInt()
        val ny = floor(corners.minOf { it.y } / unit).toInt()
        val mx = ceil(corners.maxOf { it.x } / unit).toInt()
        val my = ceil(corners.maxOf { it.y } / unit).toInt()
        val nw = max(1, mx - nx)
        val nh = max(1, my - ny)
        if (nw.toLong() * nh > 16_000_000L) return null
        val src = decode()
        val out = BooleanArray(nw * nh)
        for (j in 0 until nh) {
            for (i in 0 until nw) {
                val s = inv.map(Point((nx + i + 0.5f) * unit, (ny + j + 0.5f) * unit))
                val sx = floor(s.x / unit).toInt() - x
                val sy = floor(s.y / unit).toInt() - y
                if (sx in 0 until w && sy in 0 until h && src[sy * w + sx]) out[j * nw + i] = true
            }
        }
        return encode(out, nw, nh, nx, ny, unit)
    }

    override fun equals(other: Any?): Boolean =
        other is FillRegion && x == other.x && y == other.y && w == other.w && h == other.h && unit == other.unit && runs == other.runs

    override fun hashCode(): Int = (((x * 31 + y) * 31 + w) * 31 + h) * 31 + runs.size

    companion object {
        fun encode(mask: BooleanArray, w: Int, h: Int, x: Int, y: Int, unit: Float): FillRegion {
            val runs = ArrayList<Int>()
            var state = false
            var count = 0
            for (v in mask) {
                if (v == state) {
                    count += 1
                } else {
                    runs.add(count)
                    state = v
                    count = 1
                }
            }
            runs.add(count)
            return FillRegion(x, y, w, h, unit, runs)
        }
    }
}
