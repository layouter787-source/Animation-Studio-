package com.animationstudio.domain

import java.text.Normalizer

enum class BrushKind { BRUSH, ERASER }

/** Forma do carimbo desenhado ao longo do traço (gerada por código, sem imagens nem licenças externas). */
enum class StampShape {
    NONE, GRAIN, DOT, LEAF, GRASS, STAR,
    /** Fio curvo que segue a direção do traço (cabelo, pelo, pestanas). */
    HAIR,
    /** Agulha reta (pinheiro). */
    NEEDLE,
    /** Pétala arredondada (flores, cerejeira). */
    PETAL,
    /** Floco de neve de seis braços. */
    FLAKE,
    /** Rasto fino orientado pelo traço (linhas de velocidade, raios de luz). */
    STREAK,
    /** Segmento de raio em ziguezague, encadeado ao longo do traço. */
    BOLT,
    /** Galho com dois ramos laterais. */
    TWIG,
    /** Traço curto orientado pelo traço (tracejado). */
    DASH,
    /** Gota de chuva inclinada (ângulo fixo). */
    RAIN,
    // ---- formas geométricas e de natureza (catálogo) ----
    SQUARE, TRIANGLE, DIAMOND, HEXAGON, RING, HEART, CROWN, SPIRAL, ARROW, CROSS, DROPLET, SPLAT, BRICK,
    FLOWER, CONIFER, ROUNDTREE, BAMBOO, MOUNTAIN, PUFF, WAVE, ZIGZAG, SCRATCH, BURST, LINE, CACTUS, ROCK,
    BUBBLE, FERN, STAR5, FLAME,
}

/** Textura procedural aplicada ao traço (grão). Gerada por código, sem imagens de terceiros. */
enum class GrainKind { PAPER, CANVAS, WOOD, STONE, CLOUD, LEATHER, SAND, GRUNGE, METAL, CONCRETE }

/**
 * Predefinição de pincel ou borracha. Motores:
 *  - contínuo (por defeito): espessura uniforme, pontas redondas;
 *  - [soft]: toques redondos suaves (aerógrafo, aguarela, brilho) com a dureza [hardness];
 *  - cerdas (ver [BrushDynamics]): toques com padrão de cerdas (óleo, acrílico, leque);
 *  - lápis (ver [BrushDynamics.pencil]): grafite que se acumula, borda esfumada e pontas esbatidas;
 *  - [lines] > 1: linhas paralelas (só borrachas de cerdas);
 *  - [stamp]: carimbos — já não fazem parte da biblioteca (só pincéis com comportamento próprio).
 * [grain] > 0 aplica a textura [texture] (papel, tela, madeira, pedra…) ao traço, como num material real.
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
    val texture: GrainKind = GrainKind.PAPER,
)

object BrushLimits {
    const val MIN_SIZE = 1f
    const val MAX_SIZE = 2000f
}

object BrushLibrary {
    const val SKETCH = "Esboço"
    const val INK = "Tinta"
    const val LINEART = "Lineart"
    const val COMICS = "Comics e mangá"
    const val MARKER = "Marcadores"
    const val AIR = "Aerógrafo"
    const val WATER = "Aguarela"
    const val PAINT = "Pintura"
    const val BLOTS = "Tintas e manchas"
    const val TEXTURES = "Texturas"
    const val SHADOW = "Sombra"
    const val HAIR = "Cabelo"
    const val LIGHT = "Luz"
    const val COLORFX = "Luz e cor"
    const val ELECTRIC = "Raios"
    const val FX = "Efeitos"
    const val SMOKEFIRE = "Fumaça e fogo"
    const val LIQUIDS = "Água e líquidos"
    const val SPEED = "Velocidade"
    const val WEATHER = "Clima"
    const val PLANTS = "Plantas"
    const val VEGETATION = "Vegetação"
    const val TREES = "Árvores"
    const val GRASSES = "Relva e capim"
    const val ELEMENTS = "Elementos"
    const val PATTERNS = "Padrões e fundos"
    const val GRUNGE = "Grunge e sujeira"
    const val ANIM = "Animação e conceito"
    const val DOTS = "Pontilhismo"
    const val ERASERS = "Borrachas"

    /** Categoria dos pincéis que o utilizador importa de imagens (PNG ou com transparência). */
    const val CUSTOM = "Meus pincéis"

    private val allCategories = listOf(
        SKETCH, INK, LINEART, COMICS, MARKER, AIR, WATER, PAINT, BLOTS, TEXTURES, SHADOW, HAIR, LIGHT, COLORFX,
        ELECTRIC, FX, SMOKEFIRE, LIQUIDS, SPEED, WEATHER, PLANTS, VEGETATION, TREES, GRASSES, ELEMENTS, PATTERNS,
        GRUNGE, ANIM, DOTS,
    )
    val eraserCategories = listOf(ERASERS)

    private fun b(
        id: String, name: String, cat: String, size: Float, op: Float, taper: Float, hard: Float = 1f,
        grain: Float = 0f, soft: Boolean = false, lines: Int = 1, jitter: Float = 0f, keywords: String = "",
    ) = BrushPreset(id, name, cat, BrushKind.BRUSH, size, op, taper, hard, keywords = keywords, grain = grain, soft = soft, lines = lines, lineJitter = jitter)

    /** Pincel de carimbos (já não entra na biblioteca; mantido só para referência das definições antigas). */
    private fun s(
        id: String, name: String, cat: String, size: Float, op: Float, shape: StampShape,
        spacing: Float, scatter: Float, jit: Float, density: Int, keywords: String = "", hard: Float = 1f, grain: Float = 0f,
    ) = BrushPreset(id, name, cat, BrushKind.BRUSH, size, op, 0f, hard, shape, spacing, scatter, jit, density, keywords, grain)

    /** Borracha de traço contínuo, suave, de grão ou de cerdas. */
    private fun e(
        id: String, name: String, size: Float, op: Float, taper: Float = 0f, hard: Float = 1f, grain: Float = 0f,
        soft: Boolean = false, lines: Int = 1, jitter: Float = 0f, keywords: String = "",
    ) = BrushPreset(id, name, ERASERS, BrushKind.ERASER, size, op, taper, hard, keywords = keywords, grain = grain, soft = soft, lines = lines, lineJitter = jitter)

    /** Borracha de carimbos (já não entra na biblioteca). */
    private fun es(
        id: String, name: String, size: Float, op: Float, shape: StampShape, spacing: Float, scatter: Float, jit: Float, density: Int, keywords: String = "",
    ) = BrushPreset(id, name, ERASERS, BrushKind.ERASER, size, op, 0f, 1f, shape, spacing, scatter, jit, density, keywords)

    private val core: List<BrushPreset> = listOf(
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
        b("pencil_4h", "Lápis 4H", SKETCH, 2f, 0.6f, 0.45f, grain = 0.25f, keywords = "duro claro fino rascunho"),
        b("pencil_3b", "Lápis 3B", SKETCH, 7f, 0.9f, 0.35f, grain = 0.55f, keywords = "macio"),
        b("pencil_4b", "Lápis 4B", SKETCH, 9f, 0.92f, 0.3f, grain = 0.6f, keywords = "macio escuro"),
        b("pencil_6b", "Lápis 6B", SKETCH, 12f, 0.95f, 0.25f, grain = 0.7f, keywords = "macio escuro sombra"),
        b("pencil_8b", "Lápis 8B", SKETCH, 16f, 0.95f, 0.2f, grain = 0.75f, keywords = "muito macio escuro sombra"),
        b("pencil_real", "Lápis realista", SKETCH, 5f, 0.85f, 0.4f, grain = 0.45f, lines = 2, jitter = 0.6f, keywords = "fibras textura real"),
        b("pencil_sharp", "Lápis afiado", SKETCH, 3f, 0.9f, 0.5f, grain = 0.3f, keywords = "ponta fina line art"),
        b("pencil_crunchy", "Lápis crocante", SKETCH, 10f, 0.85f, 0.2f, grain = 0.95f, keywords = "textura grosso crunchy"),
        b("pencil_wc", "Lápis aguarelável", SKETCH, 8f, 0.6f, 0.3f, 0.6f, grain = 0.45f, soft = true, keywords = "aguarela watercolor"),
        b("pencil_color_soft", "Lápis de cor suave", SKETCH, 10f, 0.6f, 0.25f, 0.8f, grain = 0.7f, soft = true, keywords = "cor colorido"),
        b("pencil_carpenter", "Lápis de carpinteiro", SKETCH, 14f, 0.9f, 0f, grain = 0.6f, keywords = "chato plano"),
        b("crayon", "Giz de cera", SKETCH, 20f, 0.9f, 0.1f, grain = 0.8f, lines = 3, jitter = 0.5f, keywords = "cera infantil"),
        b("pastel", "Pastel seco", SKETCH, 26f, 0.75f, 0.1f, 0.5f, grain = 0.8f, soft = true, keywords = "textura"),
        b("chalk", "Giz", SKETCH, 18f, 0.85f, 0.1f, grain = 0.9f, keywords = "quadro textura"),
        b("conte", "Conté", SKETCH, 12f, 0.85f, 0.2f, grain = 0.7f, keywords = "seco"),
        b("charcoal_vine", "Carvão de vinha", SKETCH, 10f, 0.65f, 0.2f, grain = 0.85f, keywords = "leve"),
        b("charcoal_comp", "Carvão prensado", SKETCH, 20f, 0.95f, 0.1f, grain = 0.6f, keywords = "escuro"),
        // Tinta
        b("ink_fine", "Caneta fina", INK, 2.5f, 1f, 0.95f, keywords = "line art"),
        b("ink_g", "G-pen", INK, 6f, 1f, 0.8f, keywords = "line art manga"),
        b("ink_brush", "Pincel de tinta", INK, 14f, 1f, 1f),
        b("ink_line", "Line art", INK, 3f, 1f, 0.7f, keywords = "contorno"),
        b("ink_tech", "Caneta técnica", INK, 3f, 1f, 0f, keywords = "uniforme"),
        b("ink_dry", "Pincel seco", INK, 12f, 1f, 0.2f, grain = 0.8f, keywords = "textura"),
        b("ink_round", "Caneta redonda", INK, 4f, 1f, 0.85f, keywords = "manga"),
        b("ink_maru", "Maru-pen", INK, 2f, 1f, 0.9f, keywords = "manga fino detalhe"),
        b("ink_kabura", "Kabura", INK, 5f, 1f, 0.75f, keywords = "manga"),
        b("ink_calli", "Caligrafia", INK, 12f, 1f, 0.6f, keywords = "letras"),
        b("ink_brushpen", "Brush pen", INK, 10f, 1f, 1f, keywords = "pincel caneta"),
        b("ink_sumi", "Sumi-e", INK, 24f, 0.95f, 1f, grain = 0.35f, keywords = "oriental"),
        b("ink_ball", "Esferográfica", INK, 3f, 0.9f, 0.15f, grain = 0.12f, keywords = "caneta"),
        b("ink_fountain", "Caneta tinteiro", INK, 5f, 1f, 0.65f, keywords = "caneta"),
        b("ink_rough", "Tinta áspera", INK, 8f, 1f, 0.5f, grain = 0.6f, keywords = "textura"),
        b("ink_blot", "Mancha de tinta", INK, 30f, 0.95f, 0f, 0.85f, soft = true, keywords = "borrão"),
        // Lineart
        b("la_clean", "Linha limpa", LINEART, 3f, 1f, 0.6f, keywords = "contorno"),
        b("la_thin_thick", "Fino-grosso", LINEART, 6f, 1f, 1f, keywords = "pressão"),
        b("la_contour", "Contorno", LINEART, 4f, 1f, 0.8f),
        b("la_sharp", "Linha afiada", LINEART, 2.5f, 1f, 1f, keywords = "pontas"),
        b("la_pressure", "Linha de pressão", LINEART, 5f, 1f, 0.9f),
        b("la_hair", "Fio de linha", LINEART, 1.5f, 1f, 1f, keywords = "muito fino"),
        b("la_sketchy", "Linha de esboço", LINEART, 6f, 0.85f, 0.4f, grain = 0.2f, lines = 2, jitter = 0.6f, keywords = "rascunho"),
        b("la_cel", "Linha cel", LINEART, 5f, 1f, 0.5f, keywords = "animação"),
        // Marcadores
        b("marker", "Marcador", MARKER, 16f, 1f, 0f),
        b("highlighter", "Realce", MARKER, 40f, 0.4f, 0f),
        b("flat", "Chato", MARKER, 28f, 0.85f, 0f),
        b("felt", "Feltro", MARKER, 8f, 1f, 0f),
        b("marker_fine", "Marcador fino", MARKER, 6f, 1f, 0f),
        b("marker_alcohol", "Marcador de álcool", MARKER, 20f, 0.65f, 0f, 0.8f, soft = true, keywords = "copic"),
        b("marker_chisel", "Ponta chanfrada", MARKER, 30f, 0.8f, 0f, keywords = "caligrafia"),
        // Aerógrafo: toques suaves com acumulação
        b("airbrush", "Aerógrafo", AIR, 60f, 0.6f, 0f, 0.1f, soft = true),
        b("soft", "Suave", AIR, 30f, 0.8f, 0.2f, 0.45f, soft = true),
        b("air_grain", "Aerógrafo granulado", AIR, 70f, 0.5f, 0f, 0.15f, grain = 0.6f, soft = true, keywords = "textura"),
        // Aguarela
        b("wc_flat", "Aguarela plana", WATER, 40f, 0.5f, 0.15f, 0.6f, grain = 0.25f, soft = true, keywords = "aquarela"),
        b("wc_texture", "Aguarela textura", WATER, 40f, 0.5f, 0.15f, 0.5f, grain = 0.6f, soft = true, keywords = "aquarela"),
        b("wc_wet", "Aguarela molhada", WATER, 60f, 0.35f, 0.2f, 0.1f, grain = 0.3f, soft = true, keywords = "aquarela água"),
        b("wc_wash", "Aguada", WATER, 90f, 0.25f, 0f, 0f, grain = 0.2f, soft = true, keywords = "aquarela fundo"),
        // Pintura: cerdas (ver BrushDynamics)
        b("oil", "Óleo", PAINT, 50f, 1f, 0.3f, lines = 9, jitter = 0.5f, keywords = "cerdas tinta"),
        b("acrylic", "Acrílico", PAINT, 40f, 1f, 0.2f, grain = 0.3f, lines = 7, jitter = 0.4f, keywords = "cerdas tinta"),
        b("impress", "Impressionismo", PAINT, 50f, 0.9f, 0.2f, grain = 0.2f, lines = 5, jitter = 0.6f, keywords = "cerdas"),
        b("impasto", "Impasto plano", PAINT, 90f, 0.9f, 0f, grain = 0.2f, keywords = "espesso"),
        b("gouache", "Guache", PAINT, 40f, 1f, 0.1f, grain = 0.15f, lines = 3, jitter = 0.3f, keywords = "opaco"),
        b("palette_knife", "Espátula", PAINT, 60f, 1f, 0f, grain = 0.15f, keywords = "faca"),
        b("dry_bristle", "Cerdas secas", PAINT, 60f, 0.9f, 0.1f, grain = 0.6f, lines = 11, jitter = 0.8f, keywords = "textura"),
        b("fan_brush", "Pincel leque", PAINT, 70f, 0.9f, 0f, grain = 0.1f, lines = 12, jitter = 0.6f, keywords = "cerdas"),
        // Sombra
        b("sh_soft", "Sombra suave", SHADOW, 70f, 0.35f, 0f, 0.05f, soft = true, keywords = "sombreado"),
        b("sh_cel", "Sombra cel", SHADOW, 40f, 0.5f, 0f, keywords = "animação plana"),
        b("sh_gradient", "Degradê de sombra", SHADOW, 120f, 0.25f, 0f, 0f, soft = true, keywords = "gradiente"),
        b("sh_ao", "Oclusão ambiente", SHADOW, 90f, 0.22f, 0f, 0f, soft = true, keywords = "contacto"),
        b("sh_grain", "Sombra granulada", SHADOW, 50f, 0.4f, 0f, 0.3f, grain = 0.7f, soft = true, keywords = "textura"),
        // Cabelo
        b("hair_straight", "Cabelo liso", HAIR, 36f, 0.9f, 0f, lines = 8, jitter = 0.4f, keywords = "cabelo linhas"),
        b("hair_shine", "Brilho de cabelo", HAIR, 24f, 0.45f, 0.3f, 0.2f, soft = true, keywords = "cabelo luz"),
        // Luz
        b("glow", "Brilho", LIGHT, 16f, 0.9f, 0f, 0.25f, soft = true, keywords = "neon luz"),
        b("neon", "Neon", LIGHT, 14f, 1f, 0.2f, 0.3f, soft = true, keywords = "luz"),
        b("light_rim", "Luz de contorno", LIGHT, 14f, 0.6f, 0f, 0.5f, soft = true, keywords = "rim"),
        b("light_halo", "Halo", LIGHT, 100f, 0.35f, 0f, 0f, soft = true),
        // Raios
        b("bolt_glow", "Brilho elétrico", ELECTRIC, 30f, 0.6f, 0f, 0.1f, soft = true, keywords = "energia"),
        b("bolt_plasma", "Plasma", ELECTRIC, 26f, 0.9f, 0.2f, 0.2f, soft = true, keywords = "energia"),
        // Efeitos
        b("fx_smoke", "Fumo", FX, 80f, 0.3f, 0f, 0f, grain = 0.5f, soft = true, keywords = "fumaça"),
        b("fx_cloud", "Nuvens", FX, 100f, 0.6f, 0f, 0.15f, grain = 0.35f, soft = true, keywords = "céu"),
        b("fx_aura", "Aura mágica", FX, 60f, 0.5f, 0f, 0.05f, soft = true, keywords = "energia"),
        // Velocidade
        b("speed_trail", "Rasto luminoso", SPEED, 20f, 0.7f, 0.8f, 0.2f, soft = true, keywords = "luz movimento"),
        // Clima
        b("w_fog", "Nevoeiro", WEATHER, 140f, 0.25f, 0f, 0f, grain = 0.3f, soft = true, keywords = "névoa"),
        // Árvores (só o que tem comportamento próprio)
        b("tree_bark", "Casca de árvore", TREES, 40f, 1f, 0f, grain = 0.8f, lines = 6, jitter = 0.6f, keywords = "tronco textura"),
        b("tree_canopy", "Copa suave", TREES, 100f, 0.5f, 0f, 0.3f, grain = 0.4f, soft = true, keywords = "folhagem fundo"),
        // Borrachas: apagam o traço (transparência real), não pintam de branco
        BrushPreset("eraser_hard", "Borracha dura", ERASERS, BrushKind.ERASER, 24f, 1f, 0f, 1f),
        BrushPreset("eraser_soft", "Borracha suave", ERASERS, BrushKind.ERASER, 48f, 0.8f, 0f, 0.25f, soft = true),
        BrushPreset("eraser_pencil", "Borracha de lápis", ERASERS, BrushKind.ERASER, 6f, 1f, 0.3f, 1f),
        BrushPreset("eraser_texture", "Borracha de textura", ERASERS, BrushKind.ERASER, 30f, 1f, 0f, 1f, grain = 0.8f, keywords = "textura"),
        e("eraser_kneaded", "Borracha moldável", 36f, 0.5f, hard = 0.3f, grain = 0.4f, soft = true, keywords = "clarear"),
        e("eraser_precise", "Borracha de precisão", 3f, 1f, taper = 0.3f, keywords = "detalhe fina"),
        e("eraser_big", "Borracha larga", 80f, 1f, keywords = "grande"),
        e("eraser_air", "Borracha aerógrafo", 70f, 0.5f, hard = 0f, soft = true, keywords = "suave gradual"),
        e("eraser_chalk", "Borracha de giz", 40f, 0.9f, hard = 0.5f, grain = 0.9f, soft = true, keywords = "textura giz"),
        e("eraser_ink", "Borracha de tinta", 12f, 1f, taper = 0.6f, keywords = "line art"),
        e("eraser_bristle", "Borracha de cerdas", 50f, 1f, lines = 7, jitter = 0.5f, keywords = "textura"),
    )

    /**
     * Biblioteca final: só pincéis procedurais, cada um com o seu comportamento (contínuo com textura, toques suaves,
     * cerdas, lápis). Retirados: todos os carimbos (árvores, relva, folhas, formas, padrões…) e os de listras paralelas
     * (linha múltipla, hachuras…). Os de cerdas (óleo, acrílico…) usam o motor de cerdas ([BrushDynamics]).
     */
    val all: List<BrushPreset> = (core + BrushCatalog.presets).filterNot {
        it.stamp != StampShape.NONE ||
            (it.kind == BrushKind.BRUSH && it.lines > 1 && BrushDynamics.bristle(it) == 0f)
    }

    /** Só as categorias que ainda têm pincéis. */
    val brushCategories: List<String> = allCategories.filter { c -> all.any { it.kind == BrushKind.BRUSH && it.category == c } }

    @Volatile
    private var customList: List<BrushPreset> = emptyList()

    /** Pincéis importados pelo utilizador (ver a importação de PNG no editor). */
    val custom: List<BrushPreset> get() = customList

    /** Define os pincéis importados; passam a aparecer na categoria [CUSTOM]. */
    fun setCustom(list: List<BrushPreset>) {
        customList = list
    }

    fun byIdOrNull(id: String): BrushPreset? = all.firstOrNull { it.id == id } ?: customList.firstOrNull { it.id == id }
    fun byId(id: String): BrushPreset = byIdOrNull(id) ?: all.first()
    fun ofKind(kind: BrushKind): List<BrushPreset> = all.filter { it.kind == kind }
    fun categoriesOf(kind: BrushKind): List<String> =
        if (kind == BrushKind.BRUSH) brushCategories + (if (customList.isNotEmpty()) listOf(CUSTOM) else emptyList()) else eraserCategories

    private fun norm(s: String): String =
        Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")

    /** Pesquisa por nome, categoria ou palavras-chave, sem diferenciar acentos nem maiúsculas (inclui os importados). */
    fun search(kind: BrushKind, query: String, category: String?): List<BrushPreset> {
        val q = norm(query.trim())
        val pool = if (kind == BrushKind.BRUSH) all + customList else all
        return pool.filter {
            it.kind == kind && (category == null || it.category == category) &&
                (q.isEmpty() || norm(it.name + " " + it.category + " " + it.keywords).contains(q))
        }
    }
}
