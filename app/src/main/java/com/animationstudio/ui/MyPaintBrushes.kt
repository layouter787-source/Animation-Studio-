package com.animationstudio.ui

import android.content.Context
import com.animationstudio.domain.BrushKind
import com.animationstudio.domain.BrushLibrary
import com.animationstudio.domain.BrushPreset
import com.animationstudio.domain.BrushStroke
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min

/** Definição de um pincel libmypaint: valores base e curvas (entrada -> valor) de cada definição. */
internal class MyPaintDef(
    val id: String,
    val name: String,
    val keywords: String,
    val names: Array<String>,
    val base: FloatArray,
    val mapSetting: IntArray,
    val mapInput: Array<String>,
    val mapPoints: Array<FloatArray>,
    val radiusIdx: Int,
    val hueIdx: Int,
    val satIdx: Int,
    val valIdx: Int,
    val defaultSize: Float,
)

/**
 * Pincéis do libmypaint na biblioteca da app.
 *
 * Os pincéis vêm de assets/mypaint_brushes.txt, gerado a partir dos ficheiros .myb do projeto mypaint-brushes
 * (licença CC0 / domínio público; ver docs/LIBMYPAINT.md). Formato, por pincel:
 *   B <tab> id <tab> nome <tab> palavras-chave
 *   S <tab> definição <tab> valor base
 *   M <tab> definição <tab> entrada <tab> x y x y ...   (curva da entrada para a definição)
 * Os ficheiros são lidos aqui e aplicados ao libmypaint com mypaint_brush_set_base_value / set_mapping_*.
 */
internal object MyPaintBrushes {
    private const val ASSET = "mypaint_brushes.txt"
    private const val MAX_POINTS = 3000
    private const val DTIME = 0.008f

    @Volatile
    private var defs: Map<String, MyPaintDef> = emptyMap()

    @Volatile
    private var loaded = false

    private class Builder(val id: String, val name: String, val keywords: String) {
        val names = ArrayList<String>()
        val base = ArrayList<Float>()
        val mapSetting = ArrayList<Int>()
        val mapInput = ArrayList<String>()
        val mapPoints = ArrayList<FloatArray>()

        fun indexOf(cname: String, initial: Float = 0f): Int {
            val i = names.indexOf(cname)
            if (i >= 0) return i
            names.add(cname)
            base.add(initial)
            return names.size - 1
        }

        fun build(): MyPaintDef {
            val hasRadius = names.contains("radius_logarithmic")
            val radius = indexOf("radius_logarithmic", 1f)
            val h = indexOf("color_h")
            val s = indexOf("color_s")
            val v = indexOf("color_v")
            val size = if (hasRadius) (2.0 * exp(base[radius].toDouble())).toFloat() else 6f
            return MyPaintDef(
                id, name, keywords, names.toTypedArray(), base.toFloatArray(),
                mapSetting.toIntArray(), mapInput.toTypedArray(), mapPoints.toTypedArray(),
                radius, h, s, v, size.coerceIn(1f, 200f),
            )
        }
    }

    internal fun parse(lines: List<String>): List<MyPaintDef> {
        val out = ArrayList<MyPaintDef>()
        var cur: Builder? = null
        for (line in lines) {
            if (line.isEmpty()) continue
            val p = line.split('\t')
            when (p[0]) {
                "B" -> {
                    cur?.let { out.add(it.build()) }
                    cur = if (p.size >= 2) Builder(p[1], p.getOrElse(2) { p[1] }, p.getOrElse(3) { "" }) else null
                }
                "S" -> {
                    val b = cur
                    val v = p.getOrNull(2)?.toFloatOrNull()
                    if (b != null && p.size >= 3 && v != null) b.base[b.indexOf(p[1])] = v
                }
                "M" -> {
                    val b = cur
                    if (b != null && p.size >= 4) {
                        val nums = p[3].trim().split(' ').mapNotNull { it.toFloatOrNull() }
                        if (nums.size >= 4 && nums.size % 2 == 0) {
                            b.mapSetting.add(b.indexOf(p[1]))
                            b.mapInput.add(p[2])
                            b.mapPoints.add(nums.toFloatArray())
                        }
                    }
                }
            }
        }
        cur?.let { out.add(it.build()) }
        return out
    }

    private fun toPreset(d: MyPaintDef) = BrushPreset(
        d.id, d.name, BrushLibrary.MYPAINT, BrushKind.BRUSH, d.defaultSize, 1f, 0f, 1f,
        keywords = "libmypaint mypaint " + d.keywords,
    )

    /** Lê o catálogo (uma vez por arranque) e junta os pincéis à biblioteca, se o motor nativo estiver disponível. */
    fun load(context: Context) {
        if (loaded) return
        synchronized(this) {
            if (loaded) return
            val list = try {
                context.applicationContext.assets.open(ASSET).bufferedReader(Charsets.UTF_8).use { parse(it.readLines()) }
            } catch (e: Exception) {
                emptyList()
            }
            val ready = list.isNotEmpty() && MyPaintNative.available()
            defs = if (ready) list.associateBy { it.id } else emptyMap()
            BrushLibrary.setExtra(if (ready) list.map { toPreset(it) } else emptyList())
            loaded = true
        }
    }

    fun find(id: String): MyPaintDef? = if (id.startsWith("mp_")) defs[id] else null

    /**
     * Toques do traço [s] com o pincel [d]. O tamanho do pincel da app define o raio base; a cor vem do traço.
     * O traço guardado só tem posições, por isso a pressão é sintetizada: sobe no início e desce no fim do traço
     * (as dinâmicas de velocidade e de ruído do libmypaint aplicam-se normalmente).
     */
    fun dabs(d: MyPaintDef, s: BrushStroke): FloatArray? {
        if (!MyPaintNative.available() || s.points.isEmpty()) return null
        val src = s.points
        val step = max(1, (src.size + MAX_POINTS - 1) / MAX_POINTS)
        val xs = ArrayList<Float>()
        val ys = ArrayList<Float>()
        var i = 0
        while (i < src.size) {
            xs.add(src[i].x)
            ys.add(src[i].y)
            i += step
        }
        val last = src.last()
        if (xs.last() != last.x || ys.last() != last.y) {
            xs.add(last.x)
            ys.add(last.y)
        }
        if (xs.size == 1) {
            // Toque simples: um segundo ponto muito próximo faz o libmypaint assentar um toque.
            xs.add(xs[0] + 0.6f)
            ys.add(ys[0])
        }
        val n = xs.size
        val ramp = min(10, n / 3)
        val flat = FloatArray(n * 3)
        for (k in 0 until n) {
            var p = 0.6f
            if (ramp > 0) {
                if (k < ramp) p *= 0.35f + 0.65f * k / ramp
                if (k >= n - ramp) p *= 0.35f + 0.65f * (n - 1 - k) / ramp
            }
            flat[3 * k] = xs[k]
            flat[3 * k + 1] = ys[k]
            flat[3 * k + 2] = p
        }
        val base = d.base.copyOf()
        base[d.radiusIdx] = ln(max(0.1f, s.width / 2f))
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(s.colorArgb, hsv)
        base[d.hueIdx] = hsv[0] / 360f
        base[d.satIdx] = hsv[1]
        base[d.valIdx] = hsv[2]
        return MyPaintNative.computeDabs(d.names, base, d.mapSetting, d.mapInput, d.mapPoints, flat, DTIME)
    }
}
