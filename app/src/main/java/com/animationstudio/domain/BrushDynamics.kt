package com.animationstudio.domain

/** Tipo de pincel da natureza, com comportamento de cor próprio (ver [BrushDynamics.nature]). */
enum class Nature { NONE, TREE, BRANCH, GRASS }

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

    private val treeIds = setOf("x_nat_trees", "x_nat_pines", "x_veg_cypress", "x_nat_bush")
    private val branchIds = setOf("tree_branch", "tree_twigs", "x_nat_branches", "x_nat_roots")
    private val grassIds = setOf(
        "grass", "grass_lawn", "grass_tall", "grass_wild", "grass_reed", "grass_field", "tree_palm", "x_veg_grass",
    )

    /** Contraste das cerdas do pincel (0 = pincel sem cerdas). */
    fun bristle(p: BrushPreset): Float = bristleById[p.id] ?: 0f

    /** Comportamento de cor de um pincel da natureza ([Nature.NONE] para os restantes). */
    fun nature(p: BrushPreset): Nature = when (p.id) {
        in treeIds -> Nature.TREE
        in branchIds -> Nature.BRANCH
        in grassIds -> Nature.GRASS
        else -> Nature.NONE
    }
}
