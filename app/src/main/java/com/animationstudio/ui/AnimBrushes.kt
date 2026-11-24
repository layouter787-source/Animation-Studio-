package com.animationstudio.ui

import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.toArgb
import com.animationstudio.domain.BrushKind
import com.animationstudio.domain.BrushLibrary
import com.animationstudio.domain.BrushPreset
import com.animationstudio.domain.BrushStroke
import com.animationstudio.domain.Point
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** Ponte JNI para a AnimBrush (ver app/src/main/cpp/animbrush_jni.cpp). */
internal object AnimBrushNative {
    external fun nativeAvailable(): Boolean
    external fun nativeInit(pack: ByteArray?): Boolean
    external fun nativeCatalog(): ByteArray
    external fun nativeRender(
        id: String, pts: FloatArray, radius: Float, r: Float, g: Float, b: Float, seed: Int, eraser: Boolean,
    ): IntArray?

    // Traço ao vivo (uma sessão de cada vez): cada chamada devolve só a área alterada, no formato de nativeRender.
    external fun nativeLiveBegin(
        id: String, sheetW: Float, sheetH: Float, radius: Float, r: Float, g: Float, b: Float, seed: Int, eraser: Boolean,
        x: Float, y: Float, pressure: Float,
    ): IntArray?
    external fun nativeLiveMove(x: Float, y: Float, pressure: Float, dtSeconds: Float): IntArray?
    external fun nativeLiveEnd(): IntArray?
    external fun nativeLiveCancel()

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
     * Rasteriza o traço [s] com o pincel [abId], com a pressão real guardada em cada ponto (Point.p); nada é
     * sintetizado. O tamanho do pincel da app (diâmetro) vira o raio do motor.
     */
    fun render(abId: String, s: BrushStroke): AnimBrushImage? {
        val src = s.points
        if (src.isEmpty()) return null
        val step = max(1, (src.size + MAX_POINTS - 1) / MAX_POINTS)
        val kept = ArrayList<Point>()
        var i = 0
        while (i < src.size) {
            kept.add(src[i])
            i += step
        }
        val last = src.last()
        if (kept.last() != last) kept.add(last)
        val n = kept.size
        val flat = FloatArray(n * 3)
        for (k in 0 until n) {
            flat[3 * k] = kept[k].x
            flat[3 * k + 1] = kept[k].y
            flat[3 * k + 2] = kept[k].p.coerceIn(0f, 1f)
        }
        val c = s.colorArgb
        val r = ((c shr 16) and 0xFF) / 255f
        val g = ((c shr 8) and 0xFF) / 255f
        val b = (c and 0xFF) / 255f
        // A semente só depende do ponto inicial: o traço em curso não muda de aspeto a cada ponto novo.
        val seed = src[0].x.toBits() * 31 + src[0].y.toBits()
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

/**
 * Traço ao vivo: o motor C++ recebe cada ponto com a pressão e o tempo reais da caneta e devolve só a área que mudou;
 * aqui essa área é copiada para uma imagem do tamanho da folha, que o Render.kt mostra enquanto se desenha.
 * Ao levantar a caneta, [end] devolve o desenho completo, que passa a ser o desenho final do traço (fica igual ao que
 * se viu). Tudo corre na thread principal.
 */
internal object AnimBrushLive {
    private var bmp: Bitmap? = null
    private var img: ImageBitmap? = null
    private var scale = 1f
    private var lastT = 0L
    private var ux0 = 0
    private var uy0 = 0
    private var ux1 = 0
    private var uy1 = 0

    var isActive = false
        private set

    fun begin(
        abId: String, sheetW: Float, sheetH: Float, radius: Float, argb: Int, seed: Int, erase: Boolean,
        x: Float, y: Float, pressure: Float, tMs: Long,
    ): Boolean {
        cancel()
        if (sheetW < 1f || sheetH < 1f) return false
        val area = sheetW.toDouble() * sheetH
        scale = if (area > 4_500_000.0) sqrt(4_500_000.0 / area).toFloat() else 1f
        val w = max(2, ceil(sheetW * scale).toInt())
        val h = max(2, ceil(sheetH * scale).toInt())
        bmp = try {
            Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        } catch (e: OutOfMemoryError) {
            return false
        }
        img = null
        ux0 = Int.MAX_VALUE
        uy0 = Int.MAX_VALUE
        ux1 = -1
        uy1 = -1
        val r = ((argb shr 16) and 0xFF) / 255f
        val g = ((argb shr 8) and 0xFF) / 255f
        val b = (argb and 0xFF) / 255f
        val data = try {
            AnimBrushNative.nativeLiveBegin(abId, sheetW, sheetH, radius, r, g, b, seed, erase, x, y, pressure)
        } catch (e: UnsatisfiedLinkError) {
            bmp = null
            return false
        }
        isActive = true
        lastT = tMs
        if (data != null) apply(data)
        return true
    }

    fun move(x: Float, y: Float, pressure: Float, tMs: Long) {
        if (!isActive) return
        val dt = max(0L, tMs - lastT) / 1000f
        lastT = max(lastT, tMs)
        val data = try {
            AnimBrushNative.nativeLiveMove(x, y, pressure, dt)
        } catch (e: UnsatisfiedLinkError) {
            null
        }
        if (data != null) apply(data)
    }

    /** O desenho do traço ao vivo, na posição da folha, para mostrar no ecrã; null se não há sessão. */
    fun image(): AnimBrushImage? {
        val b = bmp ?: return null
        val i = img ?: b.asImageBitmap().also { img = it }
        return AnimBrushImage(i, 0f, 0f, scale)
    }

    /** Termina a sessão e devolve o traço completo (caixa justa da área pintada), ou null se nada foi pintado. */
    fun end(): AnimBrushImage? {
        if (!isActive) return null
        val data = try {
            AnimBrushNative.nativeLiveEnd()
        } catch (e: UnsatisfiedLinkError) {
            null
        }
        if (data != null) apply(data)
        isActive = false
        val b = bmp
        bmp = null
        img = null
        if (b == null) return null
        val out = if (ux1 > ux0 && uy1 > uy0) {
            try {
                AnimBrushImage(
                    Bitmap.createBitmap(b, ux0, uy0, ux1 - ux0, uy1 - uy0).asImageBitmap(), ux0 / scale, uy0 / scale, scale,
                )
            } catch (e: OutOfMemoryError) {
                null
            }
        } else {
            null
        }
        b.recycle()
        return out
    }

    fun cancel() {
        if (isActive) {
            try {
                AnimBrushNative.nativeLiveCancel()
            } catch (e: UnsatisfiedLinkError) {
                // sem motor, nada a cancelar
            }
        }
        isActive = false
        bmp?.recycle()
        bmp = null
        img = null
    }

    private fun apply(data: IntArray) {
        val w = data[0]
        val h = data[1]
        val b = bmp ?: return
        if (w <= 0 || h <= 0 || data.size < 5 + w * h) return
        val ix = Math.round(Float.fromBits(data[2]) * scale)
        val iy = Math.round(Float.fromBits(data[3]) * scale)
        if (ix < 0 || iy < 0 || ix + w > b.width || iy + h > b.height) return
        b.setPixels(data, 5, w, ix, iy, w, h)
        ux0 = min(ux0, ix)
        uy0 = min(uy0, iy)
        ux1 = max(ux1, ix + w)
        uy1 = max(uy1, iy + h)
    }
}

/** Mostra o traço ao vivo do motor: mesma camada (opacidade e modo) que o desenho final do traço. */
internal fun DrawScope.drawAnimBrushLive(s: BrushStroke, tint: Color?, sw: Int, sh: Int) {
    val img = AnimBrushLive.image() ?: return
    val source = tint ?: Color(s.colorArgb)
    val rgb = if (s.erase) Color.Black else source.copy(alpha = 1f)
    val mono = tint != null || s.erase
    val layerBlend = when {
        s.erase -> BlendMode.DstOut
        s.mode == 1 -> BlendMode.SrcAtop
        s.mode == 2 -> BlendMode.DstOver
        else -> BlendMode.SrcOver
    }
    val paint = Paint()
    paint.alpha = source.alpha
    paint.blendMode = layerBlend
    drawContext.canvas.saveLayer(Rect(0f, 0f, sw.toFloat(), sh.toFloat()), paint)
    drawAnimBrush(img, rgb, mono, BlendMode.SrcOver)
    drawContext.canvas.restore()
}

private class PreviewResult(val image: AnimBrushImage?)

/** Pré-visualizações dos pincéis (painel de pincéis): calculadas pelo motor fora da thread principal e guardadas. */
internal object AnimBrushPreviews {
    private val results = ConcurrentHashMap<String, PreviewResult>()
    private val asked = ConcurrentHashMap.newKeySet<String>()
    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    /** Lido ao desenhar, para o desenho se repetir quando chega uma pré-visualização nova. */
    val tick = mutableIntStateOf(0)

    /** A onda de amostra desenhada pelo motor, em coordenadas de uma área [w] x [h]; null enquanto não está pronta. */
    fun get(abId: String, w: Float, h: Float, width: Float, argb: Int, erase: Boolean): AnimBrushImage? {
        tick.intValue
        val key = "$abId|${w.toInt()}|${h.toInt()}|${(width * 10).toInt()}|$argb|$erase"
        results[key]?.let { return it.image }
        if (asked.add(key)) {
            worker.execute {
                val pad = h * 0.3f
                val pts = List(48) { i ->
                    val t = i / 47f
                    Point(pad + t * (w - 2f * pad), h / 2f + sin(t * 2f * PI.toFloat()) * h * 0.2f, 0.7f)
                }
                val img = try {
                    AnimBrushes.render(abId, BrushStroke(pts, argb, width, erase))
                } catch (e: Throwable) {
                    null
                }
                results[key] = PreviewResult(img)
                main.post { tick.intValue = tick.intValue + 1 }
            }
        }
        return null
    }
}

/**
 * Pré-visualização de um pincel da AnimBrush: o próprio motor desenha a onda e a imagem completa (sem cortes) é
 * mostrada; só se for maior do que a área é reduzida para caber.
 */
internal fun DrawScope.drawAnimBrushSample(abId: String, preset: BrushPreset, width: Float, opacity: Float, ink: Color) {
    val w = size.width
    val h = size.height
    val erase = preset.kind == BrushKind.ERASER
    val wd = width.coerceIn(1f, h * 0.5f)
    val argb = if (erase) 0xFF000000.toInt() else ink.toArgb()
    val img = AnimBrushPreviews.get(abId, w, h, wd, argb, erase) ?: return
    val iw = img.image.width / img.scale
    val ih = img.image.height / img.scale
    val fit = min(1f, min(w / iw, h / ih))
    val cx = img.x + iw / 2f
    val cy = img.y + ih / 2f
    val paint = Paint()
    paint.alpha = opacity.coerceIn(0f, 1f)
    paint.blendMode = if (erase) BlendMode.DstOut else BlendMode.SrcOver
    drawContext.canvas.saveLayer(Rect(0f, 0f, w, h), paint)
    withTransform({
        if (fit < 1f) {
            translate(w / 2f, h / 2f)
            scale(fit, fit, Offset.Zero)
            translate(-cx, -cy)
        }
    }) {
        drawAnimBrush(img, Color.Black, erase, BlendMode.SrcOver)
    }
    drawContext.canvas.restore()
}
