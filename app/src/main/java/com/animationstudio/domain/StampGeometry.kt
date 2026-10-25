package com.animationstudio.domain

import kotlin.math.PI
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/** Um carimbo: centro, tamanho (diâmetro/comprimento), rotação do eixo da forma (rad) e opacidade relativa. */
class Stamp(val x: Float, val y: Float, val size: Float, val rotation: Float, val alpha: Float)

/** Gera, de forma determinística (mesma semente = mesmo resultado), os carimbos ao longo de um traço. */
object StampGeometry {
    const val MAX_STAMPS = 20000

    fun generate(points: List<Point>, width: Float, preset: BrushPreset, seed: Int): List<Stamp> {
        val out = ArrayList<Stamp>()
        if (points.isEmpty() || preset.stamp == StampShape.NONE) return out
        val rnd = java.util.Random(seed.toLong())
        val r = width / 2f
        val spacingDist = max(0.5f, width * preset.spacing)

        fun emit(x: Float, y: Float, tx: Float, ty: Float) {
            for (k in 0 until preset.density) {
                if (out.size >= MAX_STAMPS) return
                val lat = (rnd.nextFloat() * 2f - 1f) * preset.scatter * r
                val along = (rnd.nextFloat() * 2f - 1f) * preset.scatter * r * 0.5f
                val px = x - ty * lat + tx * along
                val py = y + tx * lat + ty * along
                val jit = 1f - preset.sizeJitter * rnd.nextFloat()
                when (preset.stamp) {
                    StampShape.GRAIN -> out.add(Stamp(px, py, max(0.5f, width * 0.09f * jit), 0f, 0.35f + 0.65f * rnd.nextFloat()))
                    StampShape.DOT -> out.add(Stamp(px, py, max(0.5f, width * 0.28f * jit), 0f, 1f))
                    StampShape.LEAF -> out.add(Stamp(px, py, max(1f, width * 0.55f * jit), rnd.nextFloat() * 2f * PI.toFloat(), 1f))
                    StampShape.GRASS -> out.add(Stamp(px, py, max(1f, width * (0.55f + 0.45f * rnd.nextFloat()) * jit), -PI.toFloat() / 2f + (rnd.nextFloat() - 0.5f) * 0.9f, 1f))
                    StampShape.STAR -> out.add(Stamp(px, py, max(1f, width * 0.45f * jit), rnd.nextFloat() * PI.toFloat(), 0.6f + 0.4f * rnd.nextFloat()))
                    StampShape.NONE -> {}
                }
            }
        }

        val s = StrokeGeometry.sample(points, max(0.4f, spacingDist / 3f))
        if (s.size < 2) {
            emit(points[0].x, points[0].y, 1f, 0f)
            return out
        }
        var next = 0f
        var travelled = 0f
        for (i in 1 until s.size) {
            val a = s[i - 1]
            val b = s[i]
            val seg = hypot(b.x - a.x, b.y - a.y)
            if (seg == 0f) continue
            val tx = (b.x - a.x) / seg
            val ty = (b.y - a.y) / seg
            while (next <= travelled + seg) {
                val t = next - travelled
                emit(a.x + tx * t, a.y + ty * t, tx, ty)
                next += spacingDist
                if (out.size >= MAX_STAMPS) return out
            }
            travelled += seg
        }
        return out
    }

    private fun smooth(t: Float): Float {
        val x = t.coerceIn(0f, 1f)
        return x * x * (3f - 2f * x)
    }

    /**
     * Toques redondos e suaves ao longo do traço (aerógrafo, aguarela, brilho). [spacingFrac] é a distância entre
     * toques como fração do diâmetro; com [taper] > 0 o tamanho diminui nas pontas.
     */
    fun dabs(points: List<Point>, width: Float, taper: Float, spacingFrac: Float): List<Stamp> {
        val out = ArrayList<Stamp>()
        if (points.isEmpty()) return out
        if (points.size < 2) {
            out.add(Stamp(points[0].x, points[0].y, width, 0f, 1f))
            return out
        }
        val spacing = max(0.5f, width * spacingFrac)
        val s = StrokeGeometry.sample(points, max(0.4f, spacing / 2f))
        var total = 0f
        for (i in 1 until s.size) total += hypot(s[i].x - s[i - 1].x, s[i].y - s[i - 1].y)
        val taperLen = min(total / 2.4f, width * 3f)
        val t = taper.coerceIn(0f, 1f)
        var next = 0f
        var travelled = 0f
        for (i in 1 until s.size) {
            val a = s[i - 1]
            val b = s[i]
            val seg = hypot(b.x - a.x, b.y - a.y)
            if (seg == 0f) continue
            val tx = (b.x - a.x) / seg
            val ty = (b.y - a.y) / seg
            while (next <= travelled + seg) {
                val d = next - travelled
                val pos = next
                val edge = min(pos, total - pos)
                val factor = if (t < 0.05f || taperLen <= 0f) 1f else 1f - t * 0.9f * (1f - smooth(edge / taperLen))
                out.add(Stamp(a.x + tx * d, a.y + ty * d, max(0.5f, width * factor), 0f, 1f))
                next += spacing
                if (out.size >= MAX_STAMPS) return out
            }
            travelled += seg
        }
        return out
    }
}
