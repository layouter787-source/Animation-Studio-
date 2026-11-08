package com.animationstudio.format

import com.animationstudio.domain.BrushStroke
import com.animationstudio.domain.Drawing
import com.animationstudio.domain.Layer
import com.animationstudio.domain.LayerKind
import com.animationstudio.domain.Point
import com.animationstudio.domain.Project

/** Folha do projeto: dados que o ASPK guarda e o domínio [Project] ainda não tem. */
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
        var brushIdLost = false

        val layers = a.layers.map { al ->
            val m = al.meta
            maxId = maxOf(maxId, m.id)
            val kind = if (m.kind in KNOWN_KINDS) {
                LayerKind.valueOf(m.kind)
            } else {
                warnings += "Camada \"${m.name}\": tipo \"${m.kind}\" desconhecido; tratada como desenho."
                LayerKind.DRAWING
            }
            if (m.blend != "NORMAL") {
                warnings += "Camada \"${m.name}\": modo de mistura ${m.blend} ainda não é suportado; fica guardado no ficheiro."
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
                    for (s in d.strokes) {
                        if (s.brushId != null) brushIdLost = true
                        if (s.points.any { it.pressure != 1f }) pressureLost = true
                    }
                    drawings[d.id] = Drawing(
                        d.id,
                        d.strokes.map { s -> BrushStroke(s.points.map { Point(it.x, it.y) }, s.colorArgb, s.width, s.erase) },
                    )
                }
            }
            if (rasterCount > 0) {
                warnings += "Camada \"${m.name}\": $rasterCount desenho(s) raster; o domínio ainda não tem raster, ficam preservados mas não editáveis."
            }
            if (strokesIgnored > 0) {
                warnings += "Camada \"${m.name}\": traços vetoriais ignorados em $strokesIgnored desenho(s) que têm raster."
            }

            val validCels = al.cels.filter { (f, did) -> f in frames && did in drawings.keys }
            val dropped = al.cels.size - validCels.size
            if (dropped > 0) warnings += "Camada \"${m.name}\": $dropped referência(s) a frames/desenhos inexistentes removida(s)."

            Layer(m.id, m.name, kind, m.opacityPercent.coerceIn(0, 100), m.visible, drawings, validCels)
        }
        if (pressureLost) warnings += "A pressão dos pontos não é guardada no modelo atual (traços importados com pressão uniforme)."
        if (brushIdLost) warnings += "Identificadores de pincel descartados (o Animation Studio ainda não tem motor de pincéis partilhado)."

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
                AspkDrawing(
                    id,
                    d.strokes.map { s -> AspkStroke(s.points.map { AspkPoint(it.x, it.y, 1f) }, s.colorArgb, s.width, s.erase) },
                    png,
                )
            }
            if (shadowed > 0) {
                warnings += "Camada \"${l.name}\": $shadowed desenho(s) com raster e traços novos; o Studio Paint mostra só o raster."
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
}
