package com.animationstudio.domain

/**
 * Dinâmica dos pincéis que pintam com cerdas (óleo, acrílico, guache, leque…). Em vez de linhas paralelas, cada toque
 * leva um padrão de cerdas (fios de tinta com intensidades diferentes) rodado segundo a direção do traço: as
 * cerdas ficam contínuas ao longo do traço, com bordas redondas e tinta que acumula, como num pincel real.
 * O valor é o contraste das cerdas: 0 = nenhum, 1 = cerdas muito marcadas.
 */
object BrushDynamics {
    private val bristleById: Map<String, Float> = mapOf(
        "oil" to 0.65f, "acrylic" to 0.5f, "impress" to 0.7f, "gouache" to 0.25f,
        "dry_bristle" to 0.9f, "fan_brush" to 0.95f, "pencil_real" to 0.2f, "la_sketchy" to 0.25f,
        "crayon" to 0.4f, "hair_straight" to 0.95f, "tree_bark" to 0.8f,
        "x_pencil_artistic" to 0.3f, "x_sketch_rough" to 0.5f, "x_wc_fan" to 0.9f,
        "x_blot_acrylic" to 0.5f, "x_blot_oil" to 0.65f,
    )

    /** Contraste das cerdas do pincel (0 = pincel sem cerdas). */
    fun bristle(p: BrushPreset): Float = bristleById[p.id] ?: 0f
}
