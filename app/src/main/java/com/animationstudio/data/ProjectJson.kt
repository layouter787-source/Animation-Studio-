package com.animationstudio.data

import com.animationstudio.domain.BrushStroke
import com.animationstudio.domain.Drawing
import com.animationstudio.domain.FillRegion
import com.animationstudio.domain.Layer
import com.animationstudio.domain.LayerKind
import com.animationstudio.domain.Point
import com.animationstudio.domain.Project
import org.json.JSONArray
import org.json.JSONObject

data class ProjectMeta(
    val id: String,
    val name: String,
    val fps: Int,
    val width: Int,
    val height: Int,
    val createdAt: Long,
    val modifiedAt: Long,
)

/** Formato de ficheiro v1 (JSON). Um ficheiro corrompido devolve null em vez de rebentar. */
object ProjectJson {
    private const val VERSION = 1

    fun encode(meta: ProjectMeta, p: Project): String {
        val root = JSONObject()
        root.put("v", VERSION)
        root.put("id", meta.id)
        root.put("name", meta.name)
        root.put("fps", meta.fps)
        root.put("w", meta.width)
        root.put("h", meta.height)
        root.put("created", meta.createdAt)
        root.put("modified", meta.modifiedAt)
        root.put("nextId", p.nextId)
        root.put("currentFrame", p.currentFrame)
        val frames = JSONArray()
        for (f in p.frameIds) frames.put(f)
        root.put("frameIds", frames)
        val layers = JSONArray()
        for (l in p.layers) {
            val lo = JSONObject()
            lo.put("id", l.id)
            lo.put("name", l.name)
            lo.put("kind", l.kind.name)
            lo.put("opacity", l.opacityPercent)
            lo.put("visible", l.visible)
            val ds = JSONArray()
            for (d in l.drawings.values) {
                val dobj = JSONObject()
                dobj.put("id", d.id)
                val ss = JSONArray()
                for (s in d.strokes) ss.put(encodeStroke(s))
                dobj.put("strokes", ss)
                ds.put(dobj)
            }
            lo.put("drawings", ds)
            val cels = JSONArray()
            for ((frameId, drawingId) in l.cels) {
                val pair = JSONArray()
                pair.put(frameId)
                pair.put(drawingId)
                cels.put(pair)
            }
            lo.put("cels", cels)
            layers.put(lo)
        }
        root.put("layers", layers)
        return root.toString()
    }

    private fun encodeStroke(s: BrushStroke): JSONObject {
        val so = JSONObject()
        so.put("c", s.colorArgb)
        so.put("w", s.width.toDouble())
        so.put("e", s.erase)
        so.put("t", s.taper.toDouble())
        so.put("k", s.hardness.toDouble())
        val pts = JSONArray()
        for (pt in s.points) {
            pts.put(pt.x.toDouble())
            pts.put(pt.y.toDouble())
        }
        so.put("p", pts)
        val f = s.fill
        if (f != null) {
            val fo = JSONObject()
            fo.put("x", f.x)
            fo.put("y", f.y)
            fo.put("w", f.w)
            fo.put("h", f.h)
            fo.put("u", f.unit.toDouble())
            val ra = JSONArray()
            for (r in f.runs) ra.put(r)
            fo.put("r", ra)
            so.put("f", fo)
        }
        return so
    }

    private fun decodeStroke(so: JSONObject): BrushStroke {
        val pa = so.getJSONArray("p")
        val pts = List(pa.length() / 2) { k -> Point(pa.getDouble(2 * k).toFloat(), pa.getDouble(2 * k + 1).toFloat()) }
        var fill: FillRegion? = null
        if (so.has("f")) {
            val fo = so.getJSONObject("f")
            val ra = fo.getJSONArray("r")
            val runs = List(ra.length()) { ra.getInt(it) }
            fill = FillRegion(fo.getInt("x"), fo.getInt("y"), fo.getInt("w"), fo.getInt("h"), fo.getDouble("u").toFloat(), runs)
        }
        return BrushStroke(
            points = pts,
            colorArgb = so.getInt("c"),
            width = so.getDouble("w").toFloat(),
            erase = so.getBoolean("e"),
            fill = fill,
            taper = if (so.has("t")) so.getDouble("t").toFloat() else 0.6f,
            hardness = if (so.has("k")) so.getDouble("k").toFloat() else 1f,
        )
    }

    fun decodeMeta(text: String): ProjectMeta? = try {
        readMeta(JSONObject(text))
    } catch (e: Exception) {
        null
    }

    fun decode(text: String): Pair<ProjectMeta, Project>? = try {
        val r = JSONObject(text)
        val meta = readMeta(r)
        val fa = r.getJSONArray("frameIds")
        val frameIds = List(fa.length()) { fa.getLong(it) }
        val la = r.getJSONArray("layers")
        val layers = List(la.length()) { readLayer(la.getJSONObject(it)) }
        val cur = r.getInt("currentFrame").coerceIn(0, frameIds.lastIndex)
        meta to Project(frameIds, layers, cur, r.getLong("nextId"))
    } catch (e: Exception) {
        null
    }

    private fun readMeta(r: JSONObject): ProjectMeta {
        if (r.getInt("v") != VERSION) throw IllegalArgumentException("Versão não suportada")
        return ProjectMeta(
            id = r.getString("id"),
            name = r.getString("name"),
            fps = r.getInt("fps"),
            width = r.getInt("w"),
            height = r.getInt("h"),
            createdAt = r.getLong("created"),
            modifiedAt = r.getLong("modified"),
        )
    }

    private fun readLayer(lo: JSONObject): Layer {
        val da = lo.getJSONArray("drawings")
        val drawings = LinkedHashMap<Long, Drawing>()
        for (i in 0 until da.length()) {
            val dobj = da.getJSONObject(i)
            val sa = dobj.getJSONArray("strokes")
            val strokes = List(sa.length()) { si -> decodeStroke(sa.getJSONObject(si)) }
            val id = dobj.getLong("id")
            drawings[id] = Drawing(id, strokes)
        }
        val ca = lo.getJSONArray("cels")
        val cels = LinkedHashMap<Long, Long>()
        for (i in 0 until ca.length()) {
            val pair = ca.getJSONArray(i)
            cels[pair.getLong(0)] = pair.getLong(1)
        }
        return Layer(
            id = lo.getLong("id"),
            name = lo.getString("name"),
            kind = LayerKind.valueOf(lo.getString("kind")),
            opacityPercent = lo.getInt("opacity"),
            visible = lo.getBoolean("visible"),
            drawings = drawings,
            cels = cels,
        )
    }
}
