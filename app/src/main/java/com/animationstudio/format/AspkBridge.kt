package com.animationstudio.format

import com.animationstudio.domain.Affine
import com.animationstudio.domain.BrushStroke
import com.animationstudio.domain.Drawing
import com.animationstudio.domain.FillRegion
import com.animationstudio.domain.ImageData
import com.animationstudio.domain.ImageSource
import com.animationstudio.domain.Layer
import com.animationstudio.domain.LayerKind
import com.animationstudio.domain.Point
import com.animationstudio.domain.Project
import org.json.JSONArray
import org.json.JSONObject
import java.util.Base64

/** Folha do projeto: dados que o ASPK guarda e o domínio [Project] não tem. */
data class AspkSheet(val name: String, val width: Int, val height: Int, val fps: Int, val paperArgb: Int)

/** Propriedades de camada que o domínio ainda não modela; preservadas para o round-trip. */
data class LayerExtras(val blend: String = "NORMAL", val locked: Boolean = false)

data class RasterKey(val layerId: Long, val drawingId: Long)

/**
 * Projeto do Animation Studio + tudo o que veio no ASPK e o domínio ainda não representa
 * (rasters PNG, blend/bloqueio, entradas desconhecidas). Guardar o documento devolve estes dados intactos.
 */
class AspkDocument(
    val sheet: AspkSheet,
    val project: Project,
    val layerExtras: Map<Long, LayerExtras> = emptyMap(),
    val rasters: Map<RasterKey, ByteArray> = emptyMap(),
    val extras: Map<String, ByteArray> = emptyMap(),
)

/** Resultado com avisos de perda, para o utilizador saber o que não foi convertido. */
class AspkImport(val document: AspkDocument, val warnings: List<String>)

class AspkExport(val project: AspkProject, val warnings: List<String>)

object AspkBridge {
    private val KNOWN_KINDS = setOf("DRAWING", "SCENERY")

    fun import(a: AspkProject): AspkImport {
        if (a.width <= 0 || a.height <= 0) throw AspkException("Dimensões da folha inválidas")
        if (a.layers.isEmpty()) throw AspkException("Projeto sem camadas")
        if (a.frameIds.toSet().size != a.frameIds.size) throw AspkException("Ids de frame duplicados")
        if (a.layers.map { it.meta.id }.toSet().size != a.layers.size) throw AspkException("Ids de camada duplicados")

        val frames = a.frameIds.toHashSet()
        val warnings = ArrayList<String>()
        val layerExtras = HashMap<Long, LayerExtras>()
        val rasters = HashMap<RasterKey, ByteArray>()
        var maxId = a.frameIds.maxOrNull() ?: 0L
        var pressureLost = false
        var extBroken = 0

        fun stroke(s: AspkStroke): BrushStroke {
            if (s.points.any { it.pressure != 1f }) pressureLost = true
            var fill: FillRegion? = null
            var image: ImageData? = null
            try {
                (s.ext["f"] as? JSONObject)?.let { fill = decodeFill(it) }
                (s.ext["i"] as? JSONObject)?.let { image = decodeImage(it) }
            } catch (e: Exception) {
                extBroken += 1
                fill = null
                image = null
            }
            return BrushStroke(
                points = s.points.map { Point(it.x, it.y) },
                colorArgb = s.colorArgb,
                width = s.width,
                erase = s.erase,
                fill = fill,
                taper = (s.ext["t"] as? Number)?.toFloat() ?: 0.6f,
                hardness = (s.ext["k"] as? Number)?.toFloat() ?: 1f,
                brush = s.brushId ?: "",
                mode = (s.ext["m"] as? Number)?.toInt() ?: 0,
                image = image,
            )
        }

        val layers = a.layers.map { al ->
            val m = al.meta
            maxId = maxOf(maxId, m.id)
            val kind = if (m.kind in KNOWN_KINDS) {
                LayerKind.valueOf(m.kind)
            } else {
                warnings += "Camada '${m.name}': tipo '${m.kind}' desconhecido; tratada como desenho."
                LayerKind.DRAWING
            }
            if (m.blend != "NORMAL") {
                warnings += "Camada '${m.name}': modo de mistura ${m.blend} ainda não é suportado; fica guardado no ficheiro."
            }
            layerExtras[m.id] = LayerExtras(m.blend, m.locked)

            var rasterCount = 0
            var strokesIgnored = 0
            val drawings = HashMap<Long, Drawing>()
            for (d in al.drawings.values) {
                maxId = maxOf(maxId, d.id)
                val png = d.rasterPng
                if (png != null) {
                    // Regra 1 do INTERCHANGE: raster prevalece sobre vetor.
                    rasters[RasterKey(m.id, d.id)] = png
                    rasterCount += 1
                    if (d.strokes.isNotEmpty()) strokesIgnored += 1
                    drawings[d.id] = Drawing(d.id)
                } else {
                    drawings[d.id] = Drawing(d.id, d.strokes.map { stroke(it) })
                }
            }
            if (rasterCount > 0) {
                warnings += "Camada '${m.name}': $rasterCount desenho(s) raster; ficam preservados mas ainda não são editáveis nem desenhados pelo domínio."
            }
            if (strokesIgnored > 0) {
                warnings += "Camada '${m.name}': traços vetoriais ignorados em $strokesIgnored desenho(s) que têm raster."
            }

            val validCels = al.cels.filter { (f, did) -> f in frames && did in drawings.keys }
            val dropped = al.cels.size - validCels.size
            if (dropped > 0) warnings += "Camada '${m.name}': $dropped referência(s) a frames/desenhos inexistentes removida(s)."

            Layer(m.id, m.name, kind, m.opacityPercent.coerceIn(0, 100), m.visible, drawings, validCels)
        }
        if (pressureLost) warnings += "A pressão dos pontos não é guardada no modelo atual (traços importados com pressão uniforme)."
        if (extBroken > 0) warnings += "$extBroken traço(s) com preenchimento/imagem inválidos foram ignorados."

        val project = try {
            Project(a.frameIds, layers, a.currentFrame, maxOf(a.nextId, maxId + 1))
        } catch (e: IllegalArgumentException) {
            throw AspkException("Projeto inválido: ${e.message}")
        }
        val doc = AspkDocument(
            sheet = AspkSheet(a.name, a.width, a.height, a.fps.coerceIn(1, 120), a.paperArgb),
            project = project,
            layerExtras = layerExtras,
            rasters = rasters,
            extras = a.extras,
        )
        return AspkImport(doc, warnings)
    }

    fun export(doc: AspkDocument, producer: String = "Animation-Studio"): AspkExport {
        val warnings = ArrayList<String>()
        val layers = doc.project.layers.map { l ->
            val ex = doc.layerExtras[l.id] ?: LayerExtras()
            var shadowed = 0
            val drawings = l.drawings.mapValues { (id, d) ->
                val png = doc.rasters[RasterKey(l.id, id)]
                if (png != null && d.strokes.isNotEmpty()) shadowed += 1
                AspkDrawing(id, d.strokes.map { toAspk(it) }, png)
            }
            if (shadowed > 0) {
                warnings += "Camada '${l.name}': $shadowed desenho(s) com raster e traços novos; o Studio Paint mostra só o raster."
            }
            AspkLayer(
                AspkLayerMeta(l.id, l.name, l.kind.name, l.opacityPercent, l.visible, ex.blend, ex.locked),
                drawings,
                l.cels,
            )
        }
        val p = doc.project
        val out = AspkProject(
            name = doc.sheet.name, width = doc.sheet.width, height = doc.sheet.height, fps = doc.sheet.fps,
            paperArgb = doc.sheet.paperArgb, frameIds = p.frameIds, currentFrame = p.currentFrame,
            layers = layers, nextId = p.nextId, producer = producer, extras = doc.extras,
        )
        return AspkExport(out, warnings)
    }

    private fun toAspk(s: BrushStroke): AspkStroke {
        val ext = HashMap<String, Any>()
        ext["t"] = s.taper.toDouble()
        ext["k"] = s.hardness.toDouble()
        if (s.mode != 0) ext["m"] = s.mode
        s.fill?.let { ext["f"] = encodeFill(it) }
        s.image?.let { ext["i"] = encodeImage(it) }
        return AspkStroke(
            points = s.points.map { AspkPoint(it.x, it.y, 1f) },
            colorArgb = s.colorArgb,
            width = s.width,
            erase = s.erase,
            brushId = if (s.brush.isEmpty()) null else s.brush,
            ext = ext,
        )
    }

    // Mesma codificação de preenchimento/imagem que data/ProjectJson.kt, para o ASPK e o ficheiro nativo concordarem.
    private fun encodeFill(f: FillRegion) = JSONObject().apply {
        put("x", f.x); put("y", f.y); put("w", f.w); put("h", f.h); put("u", f.unit.toDouble())
        put("r", JSONArray().also { a -> f.runs.forEach { a.put(it) } })
    }

    private fun decodeFill(o: JSONObject): FillRegion {
        val ra = o.getJSONArray("r")
        return FillRegion(o.getInt("x"), o.getInt("y"), o.getInt("w"), o.getInt("h"), o.getDouble("u").toFloat(), List(ra.length()) { ra.getInt(it) })
    }

    private fun encodeImage(im: ImageData) = JSONObject().apply {
        put("w", im.source.w); put("h", im.source.h)
        put("png", Base64.getEncoder().encodeToString(im.source.png))
        put("m", JSONArray().also { a -> listOf(im.m.a, im.m.b, im.m.c, im.m.d, im.m.tx, im.m.ty).forEach { a.put(it.toDouble()) } })
    }

    private fun decodeImage(o: JSONObject): ImageData {
        val m = o.getJSONArray("m")
        return ImageData(
            ImageSource(Base64.getDecoder().decode(o.getString("png")), o.getInt("w"), o.getInt("h")),
            Affine(
                m.getDouble(0).toFloat(), m.getDouble(1).toFloat(), m.getDouble(2).toFloat(),
                m.getDouble(3).toFloat(), m.getDouble(4).toFloat(), m.getDouble(5).toFloat(),
            ),
        )
    }
}
