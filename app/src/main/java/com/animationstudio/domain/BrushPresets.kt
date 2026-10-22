package com.animationstudio.domain

enum class BrushKind { BRUSH, ERASER }

/** Predefinição de pincel ou borracha. Opacidade e tamanho iniciais podem ser ajustados pelo utilizador. */
data class BrushPreset(
    val id: String,
    val name: String,
    val category: String,
    val kind: BrushKind,
    val defaultSize: Float,
    val defaultOpacity: Float,
    val taper: Float,
    val hardness: Float,
)

object BrushLimits {
    const val MIN_SIZE = 1f
    const val MAX_SIZE = 2000f
}

object BrushLibrary {
    const val INK = "Tinta"
    const val PENCIL = "Lápis"
    const val MARKER = "Marcadores"
    const val AIR = "Aerógrafo"
    const val ERASERS = "Borrachas"

    val brushCategories = listOf(INK, PENCIL, MARKER, AIR)
    val eraserCategories = listOf(ERASERS)

    val all: List<BrushPreset> = listOf(
        BrushPreset("ink_fine", "Tinta fina", INK, BrushKind.BRUSH, 3f, 1f, 0.9f, 1f),
        BrushPreset("ink", "Tinta", INK, BrushKind.BRUSH, 6f, 1f, 0.7f, 1f),
        BrushPreset("ink_brush", "Pincel de tinta", INK, BrushKind.BRUSH, 14f, 1f, 1f, 1f),
        BrushPreset("pencil_hb", "Lápis HB", PENCIL, BrushKind.BRUSH, 4f, 0.9f, 0.35f, 0.85f),
        BrushPreset("sketch", "Esboço", PENCIL, BrushKind.BRUSH, 8f, 0.55f, 0.5f, 0.7f),
        BrushPreset("charcoal", "Carvão", PENCIL, BrushKind.BRUSH, 22f, 0.6f, 0.2f, 0.5f),
        BrushPreset("marker", "Marcador", MARKER, BrushKind.BRUSH, 16f, 1f, 0f, 1f),
        BrushPreset("highlighter", "Realce", MARKER, BrushKind.BRUSH, 40f, 0.4f, 0f, 1f),
        BrushPreset("flat", "Chato", MARKER, BrushKind.BRUSH, 28f, 0.85f, 0f, 0.9f),
        BrushPreset("airbrush", "Aerógrafo", AIR, BrushKind.BRUSH, 60f, 0.5f, 0f, 0.15f),
        BrushPreset("soft", "Suave", AIR, BrushKind.BRUSH, 30f, 0.7f, 0.2f, 0.35f),
        BrushPreset("eraser_hard", "Borracha dura", ERASERS, BrushKind.ERASER, 24f, 1f, 0f, 1f),
        BrushPreset("eraser_soft", "Borracha suave", ERASERS, BrushKind.ERASER, 48f, 0.8f, 0f, 0.25f),
        BrushPreset("eraser_pencil", "Borracha de lápis", ERASERS, BrushKind.ERASER, 6f, 1f, 0.3f, 0.9f),
    )

    fun byId(id: String): BrushPreset = all.firstOrNull { it.id == id } ?: all.first()
    fun ofKind(kind: BrushKind): List<BrushPreset> = all.filter { it.kind == kind }
    fun categoriesOf(kind: BrushKind): List<String> = if (kind == BrushKind.BRUSH) brushCategories else eraserCategories
}
