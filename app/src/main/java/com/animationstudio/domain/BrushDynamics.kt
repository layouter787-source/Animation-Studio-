package com.animationstudio.domain

/** Tipo de pincel da natureza, com comportamento de cor próprio (ver [BrushDynamics.nature]). */
enum class Nature { NONE, TREE, BRANCH, GRASS }

/**
 * Lápis realista. Um lápis de grafite não pinta uma linha cheia: deposita pequenas partículas que se acumulam.
 *  - [hardness]: dureza da ponta. Lápis duros (H) têm borda nítida; macios (B) têm borda esfumada;
 *  - [flow]: quanto grafite cada toque deposita. Os macios (6B, 8B) depositam muito e escurecem depressa, os duros (4H) pouco;
 *  - [fade]: comprimento (em espessuras) do esbatido nas pontas: o traço começa e acaba suave, sem afinar;
 *  - [jitter]: irregularidade da ponta (posição e tamanho de cada toque).
 * A textura do papel (grão do pincel) corta o grafite nos vales do papel, e sobrepor traços escurece, como no real.
 */
data class PencilSpec(val hardness: Float, val flow: Float, val fade: Float, val jitter: Float) {
    val spacing: Float get() = 0.07f
}

/**
 * Dinâmica dos pincéis que pintam com cerdas (óleo, acrílico, guache, leque…). Em vez de linhas paralelas, cada toque
 * leva um padrão de cerdas (fios de tinta com intensidades diferentes) rodado segundo a direção do traço: as
 * cerdas ficam contínuas ao longo do traço, com bordas redondas e tinta que acumula, como num pincel real.
 * O valor é o contraste das cerdas: 0 = nenhum, 1 = cerdas muito marcadas.
 *
 * Também define os pincéis da natureza com cores próprias:
 *  - [Nature.TREE]: a cor escolhida pinta a copa (com tons mais escuros e mais claros) e o tronco sai sempre castanho,
 *    com rugas, como numa árvore real;
 *  - [Nature.BRANCH]: galhos e raízes saem castanhos, com tons variados, seja qual for a cor escolhida;
 *  - [Nature.GRASS]: cada lâmina leva um tom da cor escolhida (mais escura, base, mais clara), como relva com sombras.
 */
object BrushDynamics {
    private val bristleById: Map<String, Float> = mapOf(
        "oil" to 0.65f, "acrylic" to 0.5f, "impress" to 0.7f, "gouache" to 0.25f,
        "dry_bristle" to 0.9f, "fan_brush" to 0.95f, "pencil_real" to 0.2f, "la_sketchy" to 0.25f,
        "crayon" to 0.4f, "hair_straight" to 0.95f, "tree_bark" to 0.8f,
        "x_pencil_artistic" to 0.3f, "x_sketch_rough" to 0.5f, "x_wc_fan" to 0.9f,
        "x_blot_acrylic" to 0.5f, "x_blot_oil" to 0.65f,
    )

    /** Lápis de grafite: das pontas duras (4H) às macias (8B), mais o grafite, o conté, o lápis de cera e o carvão em lápis. */
    private val pencilById: Map<String, PencilSpec> = mapOf(
        "pencil_4h" to PencilSpec(0.88f, 0.10f, 2.5f, 0.02f),
        "pencil_h" to PencilSpec(0.85f, 0.13f, 2.5f, 0.02f),
        "pencil_mech" to PencilSpec(0.85f, 0.16f, 2.0f, 0.02f),
        "x_pencil_hard" to PencilSpec(0.85f, 0.12f, 2.5f, 0.02f),
        "pencil_sharp" to PencilSpec(0.85f, 0.18f, 2.0f, 0.02f),
        "pencil_hb" to PencilSpec(0.75f, 0.20f, 2.5f, 0.03f),
        "graphite" to PencilSpec(0.70f, 0.22f, 2.5f, 0.03f),
        "sketch_light" to PencilSpec(0.60f, 0.12f, 3.0f, 0.04f),
        "x_pencil_soft" to PencilSpec(0.60f, 0.28f, 3.0f, 0.04f),
        "pencil_2b" to PencilSpec(0.65f, 0.26f, 3.0f, 0.04f),
        "pencil_mid" to PencilSpec(0.62f, 0.28f, 3.0f, 0.04f),
        "pencil_3b" to PencilSpec(0.60f, 0.30f, 3.0f, 0.045f),
        "pencil_thick" to PencilSpec(0.55f, 0.30f, 3.0f, 0.05f),
        "pencil_4b" to PencilSpec(0.55f, 0.34f, 3.5f, 0.05f),
        "pencil_color" to PencilSpec(0.70f, 0.28f, 2.5f, 0.04f),
        "x_pencil_textured" to PencilSpec(0.60f, 0.30f, 3.0f, 0.05f),
        "pencil_crunchy" to PencilSpec(0.50f, 0.30f, 3.0f, 0.06f),
        "pencil_6b" to PencilSpec(0.50f, 0.40f, 4.0f, 0.055f),
        "conte" to PencilSpec(0.50f, 0.34f, 3.5f, 0.06f),
        "x_pencil_wax" to PencilSpec(0.55f, 0.38f, 3.0f, 0.05f),
        "pencil_8b" to PencilSpec(0.45f, 0.46f, 4.5f, 0.06f),
        "x_pencil_charcoal" to PencilSpec(0.45f, 0.42f, 4.0f, 0.07f),
    )

    private val treeIds = setOf("x_nat_trees", "x_nat_pines", "x_veg_cypress", "x_nat_bush")
    private val branchIds = setOf("tree_branch", "tree_twigs", "x_nat_branches", "x_nat_roots")
    private val grassIds = setOf(
        "grass", "grass_lawn", "grass_tall", "grass_wild", "grass_reed", "grass_field", "tree_palm", "x_veg_grass",
    )

    /** Contraste das cerdas do pincel (0 = pincel sem cerdas). */
    fun bristle(p: BrushPreset): Float = bristleById[p.id] ?: 0f

    /** Definições do lápis realista, ou null se o pincel não é um lápis. */
    fun pencil(p: BrushPreset): PencilSpec? = pencilById[p.id]

    /** Comportamento de cor de um pincel da natureza ([Nature.NONE] para os restantes). */
    fun nature(p: BrushPreset): Nature = when (p.id) {
        in treeIds -> Nature.TREE
        in branchIds -> Nature.BRANCH
        in grassIds -> Nature.GRASS
        else -> Nature.NONE
    }
}
