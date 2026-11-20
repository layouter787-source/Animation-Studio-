package com.animationstudio.ui

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import com.animationstudio.domain.BrushKind
import com.animationstudio.domain.BrushLibrary
import com.animationstudio.domain.BrushPreset
import com.animationstudio.domain.BrushStroke
import org.json.JSONObject
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlin.math.min

/** Ponte JNI para a AnimBrush (ver app/src/main/cpp/animbrush_jni.cpp). */
internal object AnimBrushNative {
    external fun nativeAvailable(): Boolean
    external fun nativeInit(pack: ByteArray?): Boolean
    external fun nativeCatalog(): ByteArray
    external fun nativeRender(
        id: String, pts: FloatArray, radius: Float, r: Float, g: Float, b: Float, seed: Int, eraser: Boolean,
    ): IntArray?

    fun available(): Boolean = try {
        nativeAvailable()
    } catch (e: UnsatisfiedLinkError) {
        false
    }
}

/** Traço da AnimBrush já rasterizado: [image] cobre a folha a partir de ([x], [y]) com [scale] píxeis por unidade. */
internal class AnimBrushImage(val image: ImageBitmap, val x: Float, val y: Float, val scale: Float)

/** Pincel do catálogo da AnimBrush. */
private class AbInfo(val id: String, val name: String, val category: String, val eraser: Boolean, val radius: Float, val tags: String)

/**
 * Biblioteca de pincéis AnimBrush (third_party/AnimBrush) na biblioteca da app.
 *
 * O catálogo vem do motor nativo; os pincéis aparecem com o prefixo "ab:" no id. Cada traço é rasterizado pelo
 * motor (superfície de referência da AnimBrush, em CPU) numa imagem justa à volta do traço, que fica em cache e é
 * desenhada pelo Compose. Se a biblioteca não estiver no build, esta lista fica vazia e a app usa só os pincéis
 * incluídos.
 */
internal object AnimBrushes {
    private const val PACK = "stamps.abpack"
    private const val PREFIX = "ab:"
    private const val MAX_POINTS = 2500

    @Volatile
    private var known: Set<String> = emptySet()

    @Volatile
    private var started = false

    /** Aberto quando o catálogo ficou pronto (ou falhou): o desenho espera por ele, para nunca guardar em cache um traço provisório. */
    private val loaded = CountDownLatch(1)

    private val categoryNames = mapOf(
        "acrilica" to "Acrílica", "aerografo" to "Aerógrafo", "agua" to "Água", "aquarela" to "Aquarela",
        "arvores" to "Árvores", "borracha" to "Borrachas", "carvao" to "Carvão", "ceu" to "Céu", "clima" to "Clima",
        "decor" to "Decoração", "efeitos-anim" to "Efeitos animados", "fogo" to "Fogo", "fumaca" to "Fumaça",
        "giz" to "Giz", "lapis" to "Lápis", "luz" to "Luz", "manga" to "Mangá", "marcador" to "Marcadores",
        "mistura" to "Mistura", "oleo" to "Óleo", "pastel" to "Pastel", "pelos" to "Pelos", "pixel" to "Pixel",
        "plantas" to "Plantas", "raios" to "Raios", "terreno" to "Terreno", "tinta" to "Tinta", "zip" to "Carimbos",
    )

    private fun categoryLabel(id: String): String =
        "AB · " + (categoryNames[id] ?: id.replaceFirstChar { it.uppercase() })

    /** Lê as texturas e o catálogo (uma vez por arranque, fora da thread principal) e junta os pincéis à biblioteca. */
    fun load(context: Context) {
        if (started) return
        started = true
        val app = context.applicationContext
        Thread {
            try {
                if (!AnimBrushNative.available()) return@Thread // o finally marca como pronto
                val pack = try {
                    app.assets.open(PACK).use { it.readBytes() }
                } catch (e: Exception) {
                    null
                }
                AnimBrushNative.nativeInit(pack)
                val list = parse(String(AnimBrushNative.nativeCatalog(), Charsets.UTF_8))
                known = list.map { it.id }.toSet()
                BrushLibrary.setExtra(list.map { toPreset(it) })
            } catch (e: Throwable) {
                // sem a AnimBrush a app continua com os pincéis incluídos
            } finally {
                loaded.countDown()
            }
        }.start()
    }

    private fun parse(json: String): List<AbInfo> {
        val arr = JSONObject(json).getJSONArray("brushes")
        val out = ArrayList<AbInfo>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val tags = o.optJSONArray("tags")
            val tagText = if (tags == null) "" else (0 until tags.length()).joinToString(" ") { tags.getString(it) }
            out.add(
                AbInfo(
                    o.getString("id"), o.optString("name", o.getString("id")), o.optString("category", ""),
                    o.optBoolean("eraser", false), o.optDouble("defaultSize", 6.0).toFloat().coerceIn(0.5f, 500f), tagText,
                )
            )
        }
        return out
    }

    private fun toPreset(d: AbInfo) = BrushPreset(
        PREFIX + d.id, d.name, categoryLabel(d.category), if (d.eraser) BrushKind.ERASER else BrushKind.BRUSH,
        d.radius * 2f, 1f, 0f, 1f, keywords = "animbrush ${d.id} ${d.tags}",
    )

    /** O id do pincel no motor, se [brushId] é um pincel da AnimBrush disponível. Espera (até 5 s) pelo catálogo. */
    fun find(brushId: String): String? {
        if (!brushId.startsWith(PREFIX) || !started) return null
        try {
            loaded.await(5, TimeUnit.SECONDS)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
        }
        val id = brushId.substring(PREFIX.length)
        return if (id in known) id else null
    }

    /**
     * Rasteriza o traço [s] com o pincel [abId]. O traço guardado só tem posições: a pressão é sintetizada (sobe no
     * início e desce no fim). O tamanho do pincel da app (diâmetro) vira o raio do motor.
     */
    fun render(abId: String, s: BrushStroke): AnimBrushImage? {
        val src = s.points
        if (src.isEmpty()) return null
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
        val n = xs.size
        val ramp = min(10, n / 3)
        val flat = FloatArray(n * 3)
        for (k in 0 until n) {
            var p = 0.7f
            if (ramp > 0) {
                if (k < ramp) p *= 0.4f + 0.6f * k / ramp
                if (k >= n - ramp) p *= 0.4f + 0.6f * (n - 1 - k) / ramp
            }
            flat[3 * k] = xs[k]
            flat[3 * k + 1] = ys[k]
            flat[3 * k + 2] = p
        }
        val c = s.colorArgb
        val r = ((c shr 16) and 0xFF) / 255f
        val g = ((c shr 8) and 0xFF) / 255f
        val b = (c and 0xFF) / 255f
        val seed = (src[0].x.toBits() * 31 + src[0].y.toBits()) xor (n * 7919)
        val data = try {
            AnimBrushNative.nativeRender(abId, flat, max(0.3f, s.width / 2f), r, g, b, seed, s.erase)
        } catch (e: UnsatisfiedLinkError) {
            null
        } ?: return null
        val w = data[0]
        val h = data[1]
        if (w <= 0 || h <= 0 || data.size < 5 + w * h) return null
        val bmp = Bitmap.createBitmap(data, 5, w, w, h, Bitmap.Config.ARGB_8888)
        return AnimBrushImage(bmp.asImageBitmap(), Float.fromBits(data[2]), Float.fromBits(data[3]), Float.fromBits(data[4]))
    }
}

/** Desenha o traço da AnimBrush. Com [mono] (borracha ou onion skin) só conta o alfa, na cor [rgb]. */
internal fun DrawScope.drawAnimBrush(img: AnimBrushImage, rgb: Color, mono: Boolean, blend: BlendMode) {
    val filter = if (mono) ColorFilter.tint(rgb, BlendMode.SrcIn) else null
    val k = 1f / img.scale
    withTransform({
        translate(img.x, img.y)
        scale(k, k, Offset.Zero)
    }) {
        drawImage(img.image, colorFilter = filter, blendMode = blend)
    }
}
