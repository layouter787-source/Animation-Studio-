package com.animationstudio.domain

import org.json.JSONObject

/** Parâmetro editável de um pincel AnimBrush (slider). */
data class AbParam(val key: String, val label: String, val def: Float, val min: Float, val max: Float)

/** Um pincel do catálogo do AnimBrush, tal como o motor nativo o descreve (`catalogJson`). Tamanhos em RAIO, em px. */
data class AbBrushInfo(
    val id: String,
    val name: String,
    val category: String,
    val eraser: Boolean,
    val animated: Boolean,
    val additive: Boolean,
    val defaultRadius: Float,
    val minRadius: Float,
    val maxRadius: Float,
    val description: String,
    val tags: List<String>,
    val params: List<AbParam>,
)

/**
 * Catálogo do AnimBrush (a biblioteca de pincéis em C++): lê o JSON do motor e converte cada pincel numa
 * [BrushPreset] da biblioteca do app. Os ids ficam com o prefixo [ID_PREFIX] ("ab:") para o editor saber que o traço
 * é desenhado pelo motor nativo; as categorias aparecem como "AnimBrush · Tinta", "AnimBrush · Natureza"…
 */
object AnimBrushCatalog {
    const val ID_PREFIX = "ab:"
    const val CATEGORY_PREFIX = "AnimBrush · "

    fun isAnimBrushId(id: String): Boolean = id.startsWith(ID_PREFIX)

    fun nativeId(id: String): String = id.removePrefix(ID_PREFIX)

    fun parse(json: String): List<AbBrushInfo> {
        val arr = JSONObject(json).optJSONArray("brushes") ?: return emptyList()
        val out = ArrayList<AbBrushInfo>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val id = o.optString("id")
            if (id.isEmpty()) continue
            val tagsJson = o.optJSONArray("tags")
            val tags = if (tagsJson == null) emptyList() else List(tagsJson.length()) { tagsJson.optString(it) }
            val paramsJson = o.optJSONArray("params")
            val params = if (paramsJson == null) emptyList() else (0 until paramsJson.length()).mapNotNull { k ->
                val p = paramsJson.optJSONObject(k) ?: return@mapNotNull null
                val key = p.optString("key")
                if (key.isEmpty()) null
                else AbParam(key, p.optString("label", key), p.optDouble("def", 0.0).toFloat(), p.optDouble("min", 0.0).toFloat(), p.optDouble("max", 1.0).toFloat())
            }
            out.add(
                AbBrushInfo(
                    id = id,
                    name = o.optString("name", id),
                    category = o.optString("category", "outros"),
                    eraser = o.optBoolean("eraser", false),
                    animated = o.optBoolean("animated", false),
                    additive = o.optBoolean("additive", false),
                    defaultRadius = o.optDouble("defaultSize", 12.0).toFloat(),
                    minRadius = o.optDouble("minSize", 1.0).toFloat(),
                    maxRadius = o.optDouble("maxSize", 200.0).toFloat(),
                    description = o.optString("description"),
                    tags = tags,
                    params = params,
                ),
            )
        }
        return out
    }

    private fun capitalized(s: String): String = s.replaceFirstChar { it.uppercase() }

    /** O tamanho do app é o diâmetro do traço; o do AnimBrush é o raio. */
    fun toPreset(i: AbBrushInfo): BrushPreset = BrushPreset(
        id = ID_PREFIX + i.id,
        name = i.name,
        category = CATEGORY_PREFIX + capitalized(i.category),
        kind = if (i.eraser) BrushKind.ERASER else BrushKind.BRUSH,
        defaultSize = (i.defaultRadius * 2f).coerceIn(BrushLimits.MIN_SIZE, BrushLimits.MAX_SIZE),
        defaultOpacity = 1f,
        taper = 0f,
        hardness = 1f,
        keywords = (i.tags + i.description.take(120)).joinToString(" "),
    )
}
