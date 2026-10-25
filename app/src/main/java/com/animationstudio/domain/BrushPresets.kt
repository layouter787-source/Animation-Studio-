package com.animationstudio.domain

import java.text.Normalizer

enum class BrushKind { BRUSH, ERASER }

/** Forma do carimbo desenhado ao longo do traço (gerada por código, sem imagens nem licenças externas). */
enum class StampShape { NONE, GRAIN, DOT, LEAF, GRASS, STAR }

/**
 * Predefinição de pincel ou borracha. Motores:
 *  - contínuo (por defeito): espessura, pontas afiladas ([taper]);
 *  - [soft]: toques redondos suaves (aerógrafo, aguarela, brilho) com a dureza [hardness];
 *  - [lines] > 1: linhas paralelas (linha dupla, cerdas de tinta a óleo) com variação [lineJitter];
 *  - [stamp]: carimbos com [spacing], [scatter], [sizeJitter] e [density].
 * [grain] > 0 aplica a textura do papel (grão) ao traço, como num lápis real.
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
    val grain: Float = 0f,
    val soft: Boolean = false,
    val lines: Int = 1,
    val lineJitter: Float = 0f,
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
    const val WATER = "Aguarela"
    const val PAINT = "Pintura"
    const val LIGHT = "Luz"
    const val PLANTS = "Plantas"
    const val DOTS = "Pontilhismo"
    const val MATERIALS = "Materiais"
    const val ERASERS = "Borrachas"

    val brushCategories = listOf(SKETCH, INK, MARKER, AIR, WATER, PAINT, LIGHT, PLANTS, DOTS, MATERIALS)
    val eraserCategories = listOf(ERASERS)

    private fun b(
        id: String, name: String, cat: String, size: Float, op: Float, taper: Float, hard: Float = 1f,
        grain: Float = 0f, soft: Boolean = false, lines: Int = 1, jitter: Float = 0f, keywords: String = "",
    ) = BrushPreset(id, name, cat, BrushKind.BRUSH, size, op, taper, hard, keywords = keywords, grain = grain, soft = soft, lines = lines, lineJitter = jitter)

    val all: List<BrushPreset> = listOf(
        // Esboço: lápis com grão de papel
        b("pencil_mech", "Lápis mecânico", SKETCH, 2.5f, 0.85f, 0.35f, grain = 0.2f, keywords = "line art rascunho fino"),
        b("pencil_h", "Lápis H", SKETCH, 3f, 0.75f, 0.4f, grain = 0.35f, keywords = "line art rascunho"),
        b("pencil_hb", "Lápis HB", SKETCH, 4f, 0.9f, 0.4f, grain = 0.4f, keywords = "line art rascunho"),
        b("pencil_2b", "Lápis 2B", SKETCH, 6f, 0.9f, 0.35f, grain = 0.5f, keywords = "line art"),
        b("pencil_mid", "Lápis médio", SKETCH, 9f, 0.85f, 0.3f, grain = 0.6f, keywords = "textura"),
        b("pencil_thick", "Lápis grosso", SKETCH, 16f, 0.8f, 0.2f, grain = 0.75f, keywords = "textura"),
        b("graphite", "Grafite", SKETCH, 5f, 0.8f, 0.45f, grain = 0.3f, keywords = "line art"),
        b("sketch_light", "Esboço claro", SKETCH, 8f, 0.45f, 0.5f, grain = 0.5f, keywords = "rascunho"),
        b("pencil_color", "Lápis de cor", SKETCH, 9f, 0.7f, 0.3f, grain = 0.55f, keywords = "cor textura"),
        b("charcoal", "Carvão", SKETCH, 22f, 0.7f, 0.1f, grain = 0.9f, keywords = "textura"),
        // Tinta
        b("ink_fine", "Caneta fina", INK, 2.5f, 1f, 0.95f, keywords = "line art"),
        b("ink_g", "G-pen", INK, 6f, 1f, 0.8f, keywords = "line art manga"),
        b("ink_brush", "Pincel de tinta", INK, 14f, 1f, 1f),
        b("ink_line", "Line art", INK, 3f, 1f, 0.7f, keywords = "contorno"),
        b("ink_tech", "Caneta técnica", INK, 3f, 1f, 0f, keywords = "uniforme"),
        b("ink_dry", "Pincel seco", INK, 12f, 1f, 0.2f, grain = 0.8f, keywords = "textura"),
        // Marcadores
        b("marker", "Marcador", MARKER, 16f, 1f, 0f),
        b("highlighter", "Realce", MARKER, 40f, 0.4f, 0f),
        b("flat", "Chato", MARKER, 28f, 0.85f, 0f),
        b("felt", "Feltro", MARKER, 8f, 1f, 0f),
        // Aerógrafo: toques suaves com acumulação
        b("airbrush", "Aerógrafo", AIR, 60f, 0.6f, 0f, 0.1f, soft = true),
        b("soft", "Suave", AIR, 30f, 0.8f, 0.2f, 0.45f, soft = true),
        BrushPreset("spray", "Spray", AIR, BrushKind.BRUSH, 80f, 0.8f, 0f, 1f, StampShape.GRAIN, 0.06f, 1f, 0.6f, 8, "partículas"),
        // Aguarela
        b("wc_flat", "Aguarela plana", WATER, 40f, 0.5f, 0.15f, 0.6f, grain = 0.25f, soft = true, keywords = "aquarela"),
        b("wc_texture", "Aguarela textura", WATER, 40f, 0.5f, 0.15f, 0.5f, grain = 0.6f, soft = true, keywords = "aquarela"),
        // Pintura: cerdas
        b("oil", "Óleo", PAINT, 50f, 1f, 0.3f, lines = 9, jitter = 0.5f, keywords = "cerdas tinta"),
        b("acrylic", "Acrílico", PAINT, 40f, 1f, 0.2f, grain = 0.3f, lines = 7, jitter = 0.4f, keywords = "cerdas tinta"),
        b("impress", "Impressionismo", PAINT, 50f, 0.9f, 0.2f, grain = 0.2f, lines = 5, jitter = 0.6f, keywords = "cerdas"),
        b("impasto", "Impasto plano", PAINT, 90f, 0.9f, 0f, grain = 0.2f, keywords = "espesso"),
        // Luz
        b("glow", "Brilho", LIGHT, 16f, 0.9f, 0f, 0.25f, soft = true, keywords = "neon luz"),
        b("neon", "Neon", LIGHT, 14f, 1f, 0.2f, 0.3f, soft = true, keywords = "luz"),
        BrushPreset("sparks", "Faíscas", LIGHT, BrushKind.BRUSH, 40f, 1f, 0f, 1f, StampShape.STAR, 0.9f, 0.9f, 0.6f, 1, "estrelas brilho"),
        // Plantas
        BrushPreset("grass", "Relva", PLANTS, BrushKind.BRUSH, 60f, 1f, 0f, 1f, StampShape.GRASS, 0.15f, 0.5f, 0.5f, 2, "erva árvore paisagem"),
        BrushPreset("leaves", "Folhas", PLANTS, BrushKind.BRUSH, 40f, 1f, 0f, 1f, StampShape.LEAF, 0.8f, 0.8f, 0.5f, 1, "árvore paisagem"),
        BrushPreset("foliage", "Folhagem", PLANTS, BrushKind.BRUSH, 80f, 1f, 0f, 1f, StampShape.LEAF, 0.5f, 0.9f, 0.7f, 3, "árvore copa paisagem"),
        // Pontilhismo
        BrushPreset("dots", "Pontos", DOTS, BrushKind.BRUSH, 30f, 1f, 0f, 1f, StampShape.DOT, 0.6f, 0.9f, 0.6f, 2),
        BrushPreset("dots_fine", "Pontos finos", DOTS, BrushKind.BRUSH, 12f, 1f, 0f, 1f, StampShape.DOT, 0.4f, 0.9f, 0.5f, 2),
        // Materiais: linhas paralelas
        b("line_double", "Linha dupla", MATERIALS, 12f, 1f, 0f, lines = 2, keywords = "paralelas contorno"),
        b("line_triple", "Linha tripla", MATERIALS, 14f, 1f, 0f, lines = 3, keywords = "paralelas"),
        b("line_five", "Cinco linhas", MATERIALS, 20f, 1f, 0f, lines = 5, keywords = "paralelas"),
        b("line_multi", "Linha múltipla", MATERIALS, 40f, 1f, 0f, lines = 8, jitter = 0.3f, keywords = "paralelas sombreamento"),
        // Borrachas
        BrushPreset("eraser_hard", "Borracha dura", ERASERS, BrushKind.ERASER, 24f, 1f, 0f, 1f),
        BrushPreset("eraser_soft", "Borracha suave", ERASERS, BrushKind.ERASER, 48f, 0.8f, 0f, 0.25f, soft = true),
        BrushPreset("eraser_pencil", "Borracha de lápis", ERASERS, BrushKind.ERASER, 6f, 1f, 0.3f, 1f),
        BrushPreset("eraser_texture", "Borracha de textura", ERASERS, BrushKind.ERASER, 30f, 1f, 0f, 1f, grain = 0.8f, keywords = "textura"),
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
