package com.animationstudio.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.animationstudio.domain.BrushKind
import com.animationstudio.domain.BrushLibrary
import com.animationstudio.domain.BrushPreset
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Pincéis que o utilizador importa de uma imagem sua (PNG, JPG, com ou sem transparência). A imagem vira a ponta do
 * pincel: se tem transparência, o que é opaco é tinta; se não tem, a tinta é a cor menos frequente (traço escuro sobre
 * fundo claro ou o contrário), com contraste normalizado. A ponta é guardada só como máscara de transparência em
 * 192×192, na pasta privada da app, e o pincel ganha a cor que o utilizador escolher. As imagens ficam só no
 * telemóvel do utilizador: a app não traz nenhuma.
 */
internal object CustomBrushes {
    internal class Entry(val id: String, val name: String, val spacing: Float)

    private const val TIP_SIDE = 192
    private const val DEFAULT_SPACING = 0.2f

    @Volatile private var entries: List<Entry> = emptyList()
    @Volatile private var loaded = false
    private val tips = ConcurrentHashMap<String, ImageBitmap>()
    private var dir: File? = null

    private fun toPreset(e: Entry) = BrushPreset(
        e.id, e.name, BrushLibrary.CUSTOM, BrushKind.BRUSH, 40f, 1f, 0f, 1f, keywords = "importado png imagem ponta",
    )

    private fun publish() {
        BrushLibrary.setCustom(entries.map { toPreset(it) })
    }

    /** Lê os pincéis guardados (uma vez por arranque). */
    fun load(context: Context) {
        if (loaded) return
        synchronized(this) {
            if (loaded) return
            val d = File(context.applicationContext.filesDir, "brushes")
            d.mkdirs()
            dir = d
            val index = File(d, "index.txt")
            val list = ArrayList<Entry>()
            if (index.exists()) {
                for (line in index.readLines()) {
                    val p = line.split('\t')
                    if (p.size >= 3 && File(d, p[0] + ".png").exists()) list.add(Entry(p[0], p[1], p[2].toFloatOrNull() ?: DEFAULT_SPACING))
                }
            }
            entries = list
            publish()
            loaded = true
        }
    }

    fun find(id: String): Entry? = if (id.startsWith("my_")) entries.firstOrNull { it.id == id } else null

    /** Ponta do pincel (máscara branca com transparência), carregada do ficheiro na primeira vez. */
    fun tip(id: String): ImageBitmap? {
        tips[id]?.let { return it }
        val d = dir ?: return null
        val bmp = BitmapFactory.decodeFile(File(d, "$id.png").absolutePath) ?: return null
        val img = bmp.asImageBitmap()
        tips[id] = img
        return img
    }

    private fun displayName(context: Context, uri: Uri): String? = try {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        }?.substringBeforeLast('.')?.trim()?.take(32)?.ifBlank { null }
    } catch (e: Exception) {
        null
    }

    private fun decode(context: Context, uri: Uri): Bitmap? = try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (bounds.outWidth / sample > 1024 || bounds.outHeight / sample > 1024) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample; inPreferredConfig = Bitmap.Config.ARGB_8888 }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
    } catch (e: Exception) {
        null
    }

    /** Converte uma imagem qualquer numa máscara de ponta de pincel (branco com transparência), 192×192, centrada. */
    internal fun toTip(src: Bitmap): Bitmap {
        val side = TIP_SIDE
        val k = min(side.toFloat() / src.width, side.toFloat() / src.height)
        val tw = max(1, (src.width * k).roundToInt())
        val th = max(1, (src.height * k).roundToInt())
        val scaled = Bitmap.createScaledBitmap(src, tw, th, true)
        val px = IntArray(tw * th)
        scaled.getPixels(px, 0, tw, 0, 0, tw, th)
        var hasAlpha = false
        var lumSum = 0.0
        var counted = 0
        for (p in px) {
            val a = p ushr 24
            if (a < 250) hasAlpha = true
            if (a > 20) {
                lumSum += (0.299 * ((p shr 16) and 0xFF) + 0.587 * ((p shr 8) and 0xFF) + 0.114 * (p and 0xFF)) / 255.0
                counted++
            }
        }
        val inkIsDark = counted == 0 || lumSum / counted >= 0.5
        // Quantidade de tinta (0–1) de cada pixel.
        val ink = FloatArray(px.size)
        var lo = Float.MAX_VALUE
        var hi = -Float.MAX_VALUE
        for (i in px.indices) {
            val p = px[i]
            val a = (p ushr 24) / 255f
            val v = if (hasAlpha) {
                a
            } else {
                val lum = ((0.299f * ((p shr 16) and 0xFF) + 0.587f * ((p shr 8) and 0xFF) + 0.114f * (p and 0xFF)) / 255f)
                if (inkIsDark) 1f - lum else lum
            }
            ink[i] = v
            if (v < lo) lo = v
            if (v > hi) hi = v
        }
        val span = max(1e-3f, hi - lo)
        val out = IntArray(side * side)
        val ox = (side - tw) / 2
        val oy = (side - th) / 2
        for (y in 0 until th) {
            for (x in 0 until tw) {
                val raw = ink[y * tw + x]
                val v = if (hasAlpha) raw else (raw - lo) / span
                val a = (v.coerceIn(0f, 1f) * 255f).roundToInt()
                out[(oy + y) * side + (ox + x)] = (a shl 24) or 0xFFFFFF
            }
        }
        val bmp = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
        bmp.setPixels(out, 0, side, 0, 0, side, side)
        return bmp
    }

    /** Importa a imagem [uri] como pincel novo. Devolve o nome dado ao pincel, ou null se não foi possível. */
    fun importFrom(context: Context, uri: Uri): String? {
        load(context)
        val d = dir ?: return null
        val src = decode(context, uri) ?: return null
        val tip = toTip(src)
        val id = "my_" + System.currentTimeMillis().toString(36)
        val name = displayName(context, uri) ?: "Pincel importado ${entries.size + 1}"
        return try {
            File(d, "$id.png").outputStream().use { tip.compress(Bitmap.CompressFormat.PNG, 100, it) }
            File(d, "index.txt").appendText("$id\t${name.replace('\t', ' ')}\t$DEFAULT_SPACING\n")
            entries = entries + Entry(id, name, DEFAULT_SPACING)
            publish()
            name
        } catch (e: Exception) {
            null
        }
    }
}
