package com.animationstudio.domain

import java.text.Normalizer

enum class BrushKind { BRUSH, ERASER }

/** Forma do carimbo desenhado ao longo do traço (gerada por código, sem imagens nem licenças externas). */
enum class StampShape { NONE, GRAIN, DOT, LEAF, GRASS, STAR }

/**
 * Predefinição de pincel ou borracha. Com [stamp] = NONE o traço é contínuo (espessura, pontas, borda);
 * caso contrário é composto por carimbos com [spacing] (fração do tamanho), [scatter] (dispersão lateral),
 * [sizeJitter] e [density] (carimbos por posição).
 */
data class BrushPreset(
    val id: String,
    val name: String,
    val category: String,
    val kind: BrushKind,
    val defaultSize: Float,
    val defaultOpacity: Float,
    val taper: Float,
    val hardness: Float,
    val stamp: StampShape = StampShape.NONE,
    val spacing: Float = 0.25f,
    val scatter: Float = 0f,
    val sizeJitter: Float = 0f,
    val density: Int = 1,
    val keywords: String = "",
)

object BrushLimits {
    const val MIN_SIZE = 1f
    const val MAX_SIZE = 2000f
}

object BrushLibrary {
    const val SKETCH = "Esboço"
    const val INK = "Tinta"
    const val MARKER = "Marcadores"
    const val AIR = "Aerógrafo"
    const val LIGHT = "Luz"
    const val PLANTS = "Plantas"
    const val DOTS = "Pontilhismo"
    const val ERASERS = "Borrachas"

    val brushCategories = listOf(SKETCH, INK, MARKER, AIR, LIGHT, PLANTS, DOTS)
    val eraserCategories = listOf(ERASERS)

    val all: List<BrushPreset> = listOf(
        // Esboço: lápis e traços de rascunho / line art
        BrushPreset("pencil_h", "Lápis H", SKETCH, BrushKind.BRUSH, 3f, 0.7f, 0.3f, 0.9f, keywords = "line art rascunho"),
        BrushPreset("pencil_hb", "Lápis HB", SKETCH, BrushKind.BRUSH, 4f, 0.9f, 0.35f, 0.85f, keywords = "line art rascunho"),
        BrushPreset("pencil_2b", "Lápis 2B", SKETCH, BrushKind.BRUSH, 6f, 0.9f, 0.3f, 0.7f, keywords = "line art"),
        BrushPreset("pencil_mid", "Lápis médio", SKETCH, BrushKind.BRUSH, 9f, 0.8f, 0f, 1f, StampShape.GRAIN, 0.12f, 0.55f, 0.3f, 3, "textura"),
        BrushPreset("pencil_thick", "Lápis grosso", SKETCH, BrushKind.BRUSH, 16f, 0.75f, 0f, 1f, StampShape.GRAIN, 0.1f, 0.7f, 0.3f, 4, "textura"),
        BrushPreset("graphite", "Grafite", SKETCH, BrushKind.BRUSH, 5f, 0.8f, 0.4f, 0.8f, keywords = "line art"),
        BrushPreset("sketch_light", "Esboço claro", SKETCH, BrushKind.BRUSH, 8f, 0.45f, 0.5f, 0.7f, keywords = "rascunho"),
        BrushPreset("charcoal", "Carvão", SKETCH, BrushKind.BRUSH, 22f, 0.65f, 0f, 1f, StampShape.GRAIN, 0.08f, 0.9f, 0.4f, 6, "textura"),
        // Tinta
        BrushPreset("ink_fine", "Caneta fina", INK, BrushKind.BRUSH, 2.5f, 1f, 0.9f, 1f, keywords = "line art"),
        BrushPreset("ink_g", "G-pen", INK, BrushKind.BRUSH, 6f, 1f, 0.75f, 1f, keywords = "line art manga"),
        BrushPreset("ink_brush", "Pincel de tinta", INK, BrushKind.BRUSH, 14f, 1f, 1f, 1f),
        BrushPreset("ink_line", "Line art", INK, BrushKind.BRUSH, 3f, 1f, 0.6f, 1f, keywords = "contorno"),
        BrushPreset("ink_tech", "Caneta técnica", INK, BrushKind.BRUSH, 3f, 1f, 0f, 1f, keywords = "uniforme"),
        BrushPreset("ink_dry", "Pincel seco", INK, BrushKind.BRUSH, 12f, 1f, 0f, 1f, StampShape.GRAIN, 0.09f, 0.6f, 0.4f, 4, "textura"),
        // Marcadores
        BrushPreset("marker", "Marcador", MARKER, BrushKind.BRUSH, 16f, 1f, 0f, 1f),
        BrushPreset("highlighter", "Realce", MARKER, BrushKind.BRUSH, 40f, 0.4f, 0f, 1f),
        BrushPreset("flat", "Chato", MARKER, BrushKind.BRUSH, 28f, 0.85f, 0f, 0.9f),
        BrushPreset("felt", "Feltro", MARKER, BrushKind.BRUSH, 8f, 1f, 0f, 1f),
        // Aerógrafo
        BrushPreset("airbrush", "Aerógrafo", AIR, BrushKind.BRUSH, 60f, 0.5f, 0f, 0.15f),
        BrushPreset("soft", "Suave", AIR, BrushKind.BRUSH, 30f, 0.7f, 0.2f, 0.35f),
        BrushPreset("spray", "Spray", AIR, BrushKind.BRUSH, 80f, 0.8f, 0f, 1f, StampShape.GRAIN, 0.06f, 1f, 0.6f, 8, "partículas"),
        // Luz
        BrushPreset("glow", "Brilho", LIGHT, BrushKind.BRUSH, 16f, 0.9f, 0f, 0.25f, keywords = "neon luz"),
        BrushPreset("neon", "Neon", LIGHT, BrushKind.BRUSH, 14f, 1f, 0.2f, 0.3f, keywords = "luz"),
        BrushPreset("sparks", "Faíscas", LIGHT, BrushKind.BRUSH, 40f, 1f, 0f, 1f, StampShape.STAR, 0.9f, 0.9f, 0.6f, 1, "estrelas brilho"),
        // Plantas
        BrushPreset("grass", "Relva", PLANTS, BrushKind.BRUSH, 60f, 1f, 0f, 1f, StampShape.GRASS, 0.15f, 0.5f, 0.5f, 2, "erva árvore paisagem"),
        BrushPreset("leaves", "Folhas", PLANTS, BrushKind.BRUSH, 40f, 1f, 0f, 1f, StampShape.LEAF, 0.8f, 0.8f, 0.5f, 1, "árvore paisagem"),
        BrushPreset("foliage", "Folhagem", PLANTS, BrushKind.BRUSH, 80f, 1f, 0f, 1f, StampShape.LEAF, 0.5f, 0.9f, 0.7f, 3, "árvore copa paisagem"),
        // Pontilhismo
        BrushPreset("dots", "Pontos", DOTS, BrushKind.BRUSH, 30f, 1f, 0f, 1f, StampShape.DOT, 0.6f, 0.9f, 0.6f, 2),
        BrushPreset("dots_fine", "Pontos finos", DOTS, BrushKind.BRUSH, 12f, 1f, 0f, 1f, StampShape.DOT, 0.4f, 0.9f, 0.5f, 2),
        // Borrachas
        BrushPreset("eraser_hard", "Borracha dura", ERASERS, BrushKind.ERASER, 24f, 1f, 0f, 1f),
        BrushPreset("eraser_soft", "Borracha suave", ERASERS, BrushKind.ERASER, 48f, 0.8f, 0f, 0.25f),
        BrushPreset("eraser_pencil", "Borracha de lápis", ERASERS, BrushKind.ERASER, 6f, 1f, 0.3f, 0.9f),
        BrushPreset("eraser_texture", "Borracha de textura", ERASERS, BrushKind.ERASER, 30f, 1f, 0f, 1f, StampShape.GRAIN, 0.08f, 0.8f, 0.4f, 6, "textura"),
    )

    fun byIdOrNull(id: String): BrushPreset? = all.firstOrNull { it.id == id }
    fun byId(id: String): BrushPreset = byIdOrNull(id) ?: all.first()
    fun ofKind(kind: BrushKind): List<BrushPreset> = all.filter { it.kind == kind }
    fun categoriesOf(kind: BrushKind): List<String> = if (kind == BrushKind.BRUSH) brushCategories else eraserCategories

    private fun norm(s: String): String =
        Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")

    /** Pesquisa por nome, categoria ou palavras-chave, sem diferenciar acentos nem maiúsculas. */
    fun search(kind: BrushKind, query: String, category: String?): List<BrushPreset> {
        val q = norm(query.trim())
        return all.filter {
            it.kind == kind && (category == null || it.category == category) &&
                (q.isEmpty() || norm(it.name + " " + it.category + " " + it.keywords).contains(q))
        }
    }
}
