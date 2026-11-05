package com.animationstudio.domain

import com.animationstudio.domain.BrushLibrary.ANIM
import com.animationstudio.domain.BrushLibrary.BLOTS
import com.animationstudio.domain.BrushLibrary.COLORFX
import com.animationstudio.domain.BrushLibrary.COMICS
import com.animationstudio.domain.BrushLibrary.ELEMENTS
import com.animationstudio.domain.BrushLibrary.GRUNGE
import com.animationstudio.domain.BrushLibrary.HAIR
import com.animationstudio.domain.BrushLibrary.INK
import com.animationstudio.domain.BrushLibrary.LIQUIDS
import com.animationstudio.domain.BrushLibrary.MARKER
import com.animationstudio.domain.BrushLibrary.PATTERNS
import com.animationstudio.domain.BrushLibrary.PLANTS
import com.animationstudio.domain.BrushLibrary.SHADOW
import com.animationstudio.domain.BrushLibrary.SKETCH
import com.animationstudio.domain.BrushLibrary.SMOKEFIRE
import com.animationstudio.domain.BrushLibrary.TEXTURES
import com.animationstudio.domain.BrushLibrary.TREES
import com.animationstudio.domain.BrushLibrary.VEGETATION
import com.animationstudio.domain.BrushLibrary.WATER
import com.animationstudio.domain.BrushLibrary.WEATHER

/**
 * Catálogo grande de pincéis procedurais: tudo é gerado por código (formas, texturas, parâmetros) com os motores
 * do estúdio. Não inclui imagens nem pacotes de terceiros.
 */
object BrushCatalog {
    private fun b(
        id: String, name: String, cat: String, size: Float, op: Float, grain: Float = 0f, tex: GrainKind = GrainKind.PAPER,
        hard: Float = 1f, soft: Boolean = false, lines: Int = 1, jitter: Float = 0f, kw: String = "",
    ) = BrushPreset("x_$id", name, cat, BrushKind.BRUSH, size, op, 0f, hard, keywords = kw, grain = grain, soft = soft, lines = lines, lineJitter = jitter, texture = tex)

    private fun s(
        id: String, name: String, cat: String, size: Float, op: Float, shape: StampShape,
        spacing: Float, scatter: Float, jit: Float, density: Int, kw: String = "", grain: Float = 0f, tex: GrainKind = GrainKind.PAPER,
    ) = BrushPreset("x_$id", name, cat, BrushKind.BRUSH, size, op, 0f, 1f, shape, spacing, scatter, jit, density, kw, grain, texture = tex)

    private val P = GrainKind.PAPER
    private val CV = GrainKind.CANVAS
    private val WD = GrainKind.WOOD
    private val ST = GrainKind.STONE
    private val CL = GrainKind.CLOUD
    private val LE = GrainKind.LEATHER
    private val SA = GrainKind.SAND
    private val GR = GrainKind.GRUNGE
    private val ME = GrainKind.METAL
    private val CO = GrainKind.CONCRETE

    val presets: List<BrushPreset> = listOf(
        // ---- Lápis e esboço ----
        b("pencil_soft", "Lápis macio", SKETCH, 8f, 0.85f, 0.5f),
        b("pencil_textured", "Lápis texturizado", SKETCH, 9f, 0.85f, 0.8f, CV),
        b("pencil_hard", "Lápis duro", SKETCH, 2.5f, 0.8f, 0.3f),
        b("pencil_artistic", "Lápis artístico", SKETCH, 10f, 0.8f, 0.65f, lines = 2, jitter = 0.4f),
        b("pencil_wax", "Lápis de cera", SKETCH, 14f, 0.9f, 0.8f, CV),
        b("pencil_charcoal", "Lápis carvão", SKETCH, 12f, 0.9f, 0.85f, ST),
        b("sketch_rough", "Esboço áspero", SKETCH, 10f, 0.7f, 0.9f, SA, lines = 2, jitter = 0.7f),
        // ---- Canetas e tintas ----
        b("ink_hydro", "Caneta hidrográfica", INK, 3f, 1f),
        b("ink_nanquim", "Nanquim", INK, 6f, 1f, 0.15f),
        b("ink_posca", "Caneta posca", INK, 10f, 0.95f, 0.1f, CV),
        b("ink_gel", "Caneta gel", INK, 3.5f, 1f),
        b("ink_roller", "Caneta roller", INK, 2.5f, 1f),
        b("ink_calligraphy", "Caneta caligráfica", INK, 8f, 1f),
        b("ink_markerbrush", "Pincel marcador", INK, 18f, 0.9f, hard = 0.8f, soft = true),
        b("marker_thick", "Marcador grosso", MARKER, 24f, 1f),
        b("marker_chamfer", "Marcador chanfrado", MARKER, 32f, 0.9f, 0.1f, CV),
        // ---- Aguarela e pincéis ----
        b("wc_round", "Pincel redondo", WATER, 30f, 0.6f, 0.3f, P, 0.5f, true),
        b("wc_flat2", "Pincel chato", WATER, 50f, 0.55f, 0.3f, CV, 0.7f, true),
        b("wc_cat", "Pincel língua de gato", WATER, 40f, 0.6f, 0.3f, P, 0.6f, true),
        b("wc_filbert", "Pincel filbert", WATER, 36f, 0.6f, 0.35f, P, 0.55f, true),
        b("wc_detail", "Pincel detalhes", WATER, 10f, 0.7f, 0.25f, P, 0.6f, true),
        b("wc_dry", "Aquarela seca", WATER, 40f, 0.7f, 0.85f, CV, 0.8f, true),
        b("wc_gran", "Aquarela granulada", WATER, 44f, 0.55f, 0.8f, SA, 0.5f, true),
        b("wc_intense", "Aquarela intensa", WATER, 44f, 0.85f, 0.35f, P, 0.6f, true),
        b("wc_fan", "Pincel leque", WATER, 60f, 0.7f, 0.2f, lines = 10, jitter = 0.5f),
        b("wc_palette", "Pincel espátula", WATER, 60f, 0.9f, 0.3f, ME),
        // ---- Texturas ----
        b("tex_paper", "Textura de papel", TEXTURES, 60f, 0.8f, 0.9f, P),
        b("tex_canvas", "Textura de tela", TEXTURES, 60f, 0.85f, 0.95f, CV),
        b("tex_wood", "Textura de madeira", TEXTURES, 60f, 0.9f, 0.95f, WD),
        b("tex_stone", "Textura de pedra", TEXTURES, 60f, 0.9f, 0.95f, ST),
        b("tex_cloud", "Textura de nuvem", TEXTURES, 70f, 0.8f, 0.9f, CL),
        b("tex_concrete", "Textura de concreto", TEXTURES, 60f, 0.9f, 0.95f, CO),
        b("tex_leather", "Textura de couro", TEXTURES, 60f, 0.9f, 0.95f, LE),
        b("tex_sand", "Textura de areia", TEXTURES, 60f, 0.9f, 0.95f, SA),
        b("tex_grunge", "Textura de grunge", TEXTURES, 60f, 0.9f, 0.95f, GR),
        b("tex_metal", "Textura de metal", TEXTURES, 60f, 0.9f, 0.9f, ME),
        b("tex_fabric", "Textura de tecido", TEXTURES, 50f, 0.85f, 0.8f, CV),
        b("tex_rustic", "Textura rústica", TEXTURES, 60f, 0.9f, 1f, P),
        s("tex_leaves", "Textura de folhas", TEXTURES, 50f, 1f, StampShape.LEAF, 0.2f, 1f, 0.6f, 5, "folhas"),
        // ---- Luz e cor ----
        b("fx_gradient_soft", "Gradiente suave", COLORFX, 120f, 0.35f, hard = 0f, soft = true),
        b("fx_gradient_strong", "Gradiente intenso", COLORFX, 100f, 0.7f, hard = 0.1f, soft = true),
        b("fx_neon", "Neon", COLORFX, 14f, 1f, hard = 0.3f, soft = true),
        b("fx_holo", "Holográfico", COLORFX, 40f, 0.6f, hard = 0.2f, soft = true),
        b("fx_arc", "Arco-íris", COLORFX, 50f, 0.7f, lines = 7, jitter = 0.1f, kw = "arcoiris"),
        b("fx_leak", "Luz vazada", COLORFX, 90f, 0.5f, hard = 0f, soft = true, kw = "light leak"),
        s("fx_bokeh", "Bokeh", COLORFX, 60f, 0.5f, StampShape.DOT, 0.8f, 1.4f, 0.8f, 2, "luzes desfocadas"),
        s("fx_particles", "Partículas coloridas", COLORFX, 50f, 0.9f, StampShape.GRAIN, 0.07f, 1.2f, 0.7f, 8),
        s("fx_lumdust", "Poeira luminosa", COLORFX, 70f, 0.9f, StampShape.GRAIN, 0.06f, 1.4f, 0.8f, 10),
        s("fx_sparks_c", "Faíscas coloridas", COLORFX, 36f, 1f, StampShape.STAR5, 0.7f, 1.2f, 0.6f, 2),
        s("fx_energy", "Energia colorida", COLORFX, 30f, 1f, StampShape.BOLT, 1f, 0.3f, 0.2f, 2),
        b("fx_aura2", "Aura", COLORFX, 70f, 0.5f, hard = 0f, soft = true),
        // ---- Fumaça e fogo ----
        b("smoke_soft", "Fumaça suave", SMOKEFIRE, 80f, 0.3f, 0.7f, CL, 0f, true),
        b("smoke_dense", "Fumaça densa", SMOKEFIRE, 90f, 0.55f, 0.6f, CL, 0.1f, true),
        b("smoke_color", "Fumaça colorida", SMOKEFIRE, 80f, 0.45f, 0.65f, CL, 0.05f, true),
        s("fire_small", "Fogo pequeno", SMOKEFIRE, 30f, 1f, StampShape.FLAME, 0.4f, 0.6f, 0.5f, 2),
        s("fire_mid", "Fogo médio", SMOKEFIRE, 60f, 1f, StampShape.FLAME, 0.35f, 0.6f, 0.5f, 3),
        s("fire_big", "Fogo grande", SMOKEFIRE, 110f, 1f, StampShape.FLAME, 0.3f, 0.7f, 0.5f, 3),
        s("fire_styled", "Chamas estilizadas", SMOKEFIRE, 80f, 1f, StampShape.FLAME, 0.5f, 0.3f, 0.3f, 1),
        s("fire_magic", "Fogo mágico", SMOKEFIRE, 50f, 0.95f, StampShape.FLAME, 0.3f, 1f, 0.6f, 3),
        s("blast_small", "Explosão pequena", SMOKEFIRE, 50f, 1f, StampShape.BURST, 1.2f, 0.4f, 0.4f, 1),
        s("blast_mid", "Explosão média", SMOKEFIRE, 90f, 1f, StampShape.BURST, 1.2f, 0.4f, 0.4f, 1),
        s("blast_big", "Explosão grande", SMOKEFIRE, 150f, 1f, StampShape.BURST, 1.2f, 0.4f, 0.4f, 1),
        s("flare", "Labareda", SMOKEFIRE, 140f, 1f, StampShape.FLAME, 0.2f, 0.5f, 0.5f, 4),
        // ---- Água e líquidos ----
        s("water_waves", "Ondas suaves", LIQUIDS, 40f, 0.9f, StampShape.WAVE, 0.9f, 0.1f, 0.2f, 1),
        s("water_big", "Ondas grandes", LIQUIDS, 90f, 0.9f, StampShape.WAVE, 0.9f, 0.2f, 0.3f, 1),
        s("water_splash", "Respingo de água", LIQUIDS, 40f, 0.9f, StampShape.DROPLET, 0.4f, 1.5f, 0.7f, 4),
        s("water_drops", "Gotas de água", LIQUIDS, 30f, 0.9f, StampShape.DROPLET, 1.4f, 1.2f, 0.5f, 1),
        b("water_stream", "Correnteza", LIQUIDS, 50f, 0.7f, lines = 6, jitter = 0.5f),
        s("water_fall", "Cachoeira", LIQUIDS, 90f, 0.7f, StampShape.RAIN, 0.08f, 1.2f, 0.4f, 3),
        s("water_foam", "Espuma do mar", LIQUIDS, 40f, 0.9f, StampShape.BUBBLE, 0.4f, 1.3f, 0.7f, 3),
        s("water_bubbles", "Bolhas", LIQUIDS, 40f, 0.8f, StampShape.BUBBLE, 1f, 1.3f, 0.8f, 1),
        s("water_swirl", "Redemoinho", LIQUIDS, 70f, 0.9f, StampShape.SPIRAL, 1.2f, 0.2f, 0.2f, 1),
        s("water_drip", "Gotejamento", LIQUIDS, 30f, 0.95f, StampShape.DROPLET, 0.9f, 0.1f, 0.3f, 1),
        b("water_pool", "Poças", LIQUIDS, 60f, 0.7f, 0.3f, CL, 0.2f, true),
        s("water_styled", "Água estilizada", LIQUIDS, 60f, 1f, StampShape.WAVE, 0.7f, 0.2f, 0.2f, 1),
        // ---- Céu e clima ----
        s("sky_cloud_soft", "Nuvens suaves", WEATHER, 90f, 0.8f, StampShape.PUFF, 0.5f, 0.5f, 0.5f, 2),
        s("sky_cloud_heavy", "Nuvens carregadas", WEATHER, 120f, 0.9f, StampShape.PUFF, 0.4f, 0.5f, 0.5f, 3),
        b("sky_mist", "Névoa", WEATHER, 120f, 0.25f, 0.4f, CL, 0f, true),
        s("sky_stars", "Céu estrelado", WEATHER, 36f, 1f, StampShape.STAR5, 1.5f, 1.4f, 0.7f, 1),
        b("sky_rainbow", "Arco-íris do céu", WEATHER, 70f, 0.7f, lines = 7, jitter = 0.05f),
        s("sky_dust", "Poeira", WEATHER, 50f, 0.6f, StampShape.GRAIN, 0.06f, 1.2f, 0.7f, 8),
        // ---- Vegetação ----
        s("veg_leaves_small", "Folhas pequenas", VEGETATION, 30f, 1f, StampShape.LEAF, 0.4f, 0.9f, 0.5f, 3),
        s("veg_leaves_big", "Folhas grandes", VEGETATION, 70f, 1f, StampShape.LEAF, 0.7f, 0.9f, 0.4f, 1),
        s("veg_leaves_fall", "Folhas caídas", VEGETATION, 36f, 1f, StampShape.LEAF, 0.7f, 1.4f, 0.5f, 2),
        s("veg_flowers_small", "Flores pequenas", VEGETATION, 24f, 1f, StampShape.FLOWER, 0.9f, 1f, 0.5f, 2),
        s("veg_flowers_big", "Flores grandes", VEGETATION, 54f, 1f, StampShape.FLOWER, 1.4f, 0.8f, 0.4f, 1),
        s("veg_grass", "Gramas", VEGETATION, 50f, 1f, StampShape.GRASS, 0.07f, 1f, 0.6f, 5),
        s("veg_moss", "Musgo", VEGETATION, 22f, 1f, StampShape.DOT, 0.18f, 1f, 0.8f, 5),
        s("veg_fern", "Samambaías", VEGETATION, 80f, 1f, StampShape.FERN, 0.9f, 0.8f, 0.4f, 1),
        s("veg_tropical", "Plantas tropicais", VEGETATION, 100f, 1f, StampShape.LEAF, 0.8f, 0.6f, 0.4f, 1),
        s("veg_vines", "Trepadeiras", VEGETATION, 36f, 1f, StampShape.LEAF, 0.3f, 0.3f, 0.5f, 1),
        s("veg_cypress", "Cipreste", VEGETATION, 90f, 1f, StampShape.CONIFER, 1.1f, 0.5f, 0.3f, 1),
        s("veg_bamboo", "Bambu", VEGETATION, 100f, 1f, StampShape.BAMBOO, 0.5f, 0.4f, 0.2f, 1),
        // ---- Natureza (plantas e árvores) ----
        s("nat_bush", "Arbustos", PLANTS, 70f, 1f, StampShape.ROUNDTREE, 0.9f, 0.6f, 0.4f, 1),
        s("nat_cactus", "Cactos", PLANTS, 80f, 1f, StampShape.CACTUS, 1.4f, 0.6f, 0.3f, 1),
        s("nat_branches", "Galhos", TREES, 70f, 1f, StampShape.TWIG, 0.45f, 0.4f, 0.4f, 2),
        s("nat_roots", "Raízes", TREES, 60f, 1f, StampShape.TWIG, 0.3f, 0.3f, 0.4f, 2),
        s("nat_mountains", "Montanhas", PLANTS, 110f, 1f, StampShape.MOUNTAIN, 1.2f, 0.2f, 0.4f, 1),
        s("nat_rocks", "Rochas", PLANTS, 50f, 1f, StampShape.ROCK, 0.7f, 0.9f, 0.5f, 2),
        s("nat_pines", "Pinheiros", TREES, 100f, 1f, StampShape.CONIFER, 0.9f, 0.5f, 0.3f, 1),
        s("nat_trees", "Árvores redondas", TREES, 100f, 1f, StampShape.ROUNDTREE, 1f, 0.5f, 0.3f, 1),
        // ---- Cabelo e pelos ----
        s("hair_short", "Pelos curtos", HAIR, 18f, 0.95f, StampShape.HAIR, 0.08f, 0.6f, 0.4f, 4),
        s("hair_long", "Pelos longos", HAIR, 90f, 0.95f, StampShape.HAIR, 0.06f, 0.7f, 0.5f, 4),
        s("hair_wavy", "Cabelo ondulado", HAIR, 70f, 0.95f, StampShape.HAIR, 0.05f, 0.9f, 0.5f, 5),
        s("hair_curly", "Cabelo cacheado", HAIR, 40f, 0.95f, StampShape.SPIRAL, 0.5f, 0.8f, 0.4f, 2),
        s("hair_afro", "Cabelo afro", HAIR, 36f, 0.95f, StampShape.SPIRAL, 0.3f, 1.2f, 0.5f, 3),
        s("mustache", "Bigode", HAIR, 24f, 0.95f, StampShape.HAIR, 0.08f, 0.5f, 0.3f, 3),
        s("eyelash2", "Cílios", HAIR, 22f, 1f, StampShape.HAIR, 0.2f, 0.1f, 0.2f, 1),
        s("animal_fur", "Pelos de animais", HAIR, 30f, 0.95f, StampShape.HAIR, 0.07f, 0.9f, 0.5f, 5),
        // ---- Comics e mangá ----
        b("comic_thick", "Linha grossa", COMICS, 8f, 1f),
        b("comic_mid", "Linha média", COMICS, 4f, 1f),
        b("comic_thin", "Linha fina", COMICS, 2f, 1f),
        s("comic_dashed", "Linha tracejada", COMICS, 6f, 1f, StampShape.DASH, 1.6f, 0f, 0f, 1),
        s("comic_dotted", "Linha pontilhada", COMICS, 5f, 1f, StampShape.DOT, 1.1f, 0f, 0f, 1),
        s("comic_speed", "Speed lines", COMICS, 130f, 1f, StampShape.STREAK, 0.07f, 1f, 0.5f, 2),
        s("comic_impact", "Impacto", COMICS, 120f, 1f, StampShape.BURST, 1.3f, 0.2f, 0.3f, 1),
        s("comic_radial", "Radial", COMICS, 200f, 1f, StampShape.BURST, 1.6f, 0f, 0f, 1),
        s("comic_screentone", "Trama (screentone)", COMICS, 30f, 0.9f, StampShape.DOT, 0.45f, 0f, 0f, 1),
        b("comic_hatch", "Hachura", COMICS, 30f, 0.9f, lines = 6, jitter = 0.2f),
        b("comic_crosshatch", "Hachura cruzada", COMICS, 34f, 0.9f, lines = 9, jitter = 0.35f),
        // ---- Sombreamento ----
        b("shade_soft", "Sombra suave", SHADOW, 60f, 0.3f, hard = 0f, soft = true),
        b("shade_mid", "Sombra média", SHADOW, 60f, 0.5f, hard = 0.1f, soft = true),
        b("shade_strong", "Sombra forte", SHADOW, 60f, 0.8f, hard = 0.2f, soft = true),
        b("shade_hard", "Sombra dura", SHADOW, 50f, 0.7f),
        b("shade_textured", "Sombra texturizada", SHADOW, 60f, 0.5f, 0.8f, CV, 0.1f, true),
        b("shade_backlight", "Luz contraluz", SHADOW, 40f, 0.7f, hard = 0.2f, soft = true),
        b("shade_ambient", "Luz ambiente", SHADOW, 110f, 0.25f, hard = 0f, soft = true),
        b("shade_reflected", "Luz refletida", SHADOW, 40f, 0.45f, hard = 0.3f, soft = true),
        // ---- Tintas e manchas ----
        b("blot_wash", "Tinta aguada", BLOTS, 80f, 0.35f, 0.6f, CL, 0.1f, true),
        s("blot_splash", "Tinta respingo", BLOTS, 50f, 1f, StampShape.SPLAT, 0.8f, 1.4f, 0.8f, 3),
        s("blot_stain", "Tinta mancha", BLOTS, 80f, 0.9f, StampShape.SPLAT, 0.6f, 0.6f, 0.6f, 1),
        s("blot_smudge", "Tinta borrão", BLOTS, 60f, 0.8f, StampShape.SPLAT, 0.3f, 0.5f, 0.6f, 2),
        s("blot_drip", "Tinta gotejamento", BLOTS, 30f, 1f, StampShape.DROPLET, 0.8f, 0.3f, 0.5f, 1),
        b("blot_spatula", "Tinta espatulada", BLOTS, 60f, 0.95f, 0.5f, ME),
        b("blot_dirty", "Tinta suja", BLOTS, 50f, 0.9f, 0.85f, GR),
        b("blot_grunge", "Tinta grunge", BLOTS, 60f, 0.9f, 0.9f, GR),
        b("blot_watercolor", "Tinta aquarelada", BLOTS, 70f, 0.5f, 0.5f, P, 0.2f, true),
        b("blot_liquid", "Tinta líquida", BLOTS, 40f, 0.95f, 0.1f, P, 0.8f, true),
        b("blot_acrylic", "Tinta acrílica", BLOTS, 50f, 1f, 0.35f, CV, lines = 5, jitter = 0.4f),
        b("blot_oil", "Tinta óleo", BLOTS, 56f, 1f, 0.2f, lines = 8, jitter = 0.5f),
        // ---- Elementos ----
        s("el_circles", "Círculos", ELEMENTS, 40f, 1f, StampShape.RING, 1.2f, 0f, 0f, 1),
        s("el_squares", "Quadrados", ELEMENTS, 36f, 1f, StampShape.SQUARE, 1.2f, 0f, 0f, 1),
        s("el_triangles", "Triângulos", ELEMENTS, 36f, 1f, StampShape.TRIANGLE, 1.2f, 0f, 0f, 1),
        s("el_lines", "Linhas retas", ELEMENTS, 50f, 1f, StampShape.LINE, 1.1f, 0f, 0f, 1),
        s("el_curves", "Linhas curvas", ELEMENTS, 50f, 1f, StampShape.WAVE, 0.9f, 0f, 0f, 1),
        s("el_arrows", "Setas", ELEMENTS, 44f, 1f, StampShape.ARROW, 1.4f, 0f, 0f, 1),
        s("el_spirals", "Espirais", ELEMENTS, 50f, 1f, StampShape.SPIRAL, 1.3f, 0f, 0f, 1),
        s("el_crowns", "Coroas", ELEMENTS, 40f, 1f, StampShape.CROWN, 1.4f, 0f, 0f, 1),
        s("el_hearts", "Corações", ELEMENTS, 36f, 1f, StampShape.HEART, 1.3f, 0f, 0f, 1),
        s("el_stars", "Estrelas", ELEMENTS, 40f, 1f, StampShape.STAR5, 1.3f, 0f, 0f, 1),
        s("el_burst", "Raios/explosão", ELEMENTS, 60f, 1f, StampShape.BURST, 1.4f, 0f, 0f, 1),
        s("el_deco_mist", "Névoa decorativa", ELEMENTS, 60f, 0.7f, StampShape.PUFF, 0.7f, 0.3f, 0.4f, 1),
        // ---- Padrões e fundos ----
        s("pat_dots", "Pontos", PATTERNS, 24f, 1f, StampShape.DOT, 0.5f, 0f, 0f, 1),
        s("pat_dots_big", "Pontos grandes", PATTERNS, 40f, 1f, StampShape.DOT, 0.7f, 0f, 0f, 1),
        s("pat_checker", "Xadrez", PATTERNS, 30f, 1f, StampShape.SQUARE, 0.9f, 0f, 0f, 1),
        b("pat_hlines", "Linhas horizontais", PATTERNS, 40f, 1f, lines = 6),
        s("pat_vlines", "Linhas verticais", PATTERNS, 40f, 1f, StampShape.CROSS, 0.5f, 0f, 0f, 1),
        s("pat_waves", "Ondas", PATTERNS, 40f, 1f, StampShape.WAVE, 0.9f, 0f, 0f, 1),
        s("pat_zigzag", "Zig zag", PATTERNS, 40f, 1f, StampShape.ZIGZAG, 0.9f, 0f, 0f, 1),
        s("pat_tiles", "Tijolos", PATTERNS, 40f, 1f, StampShape.BRICK, 1.1f, 0f, 0f, 1),
        s("pat_grid", "Grade", PATTERNS, 30f, 1f, StampShape.CROSS, 0.9f, 0f, 0f, 1),
        s("pat_hex", "Hexágonos", PATTERNS, 34f, 1f, StampShape.HEXAGON, 1f, 0f, 0f, 1),
        s("pat_mosaic", "Mosaico", PATTERNS, 28f, 1f, StampShape.DIAMOND, 1f, 0f, 0f, 1),
        s("pat_fibers", "Fibras", PATTERNS, 50f, 0.9f, StampShape.SCRATCH, 0.3f, 1f, 0.5f, 3),
        // ---- Grunge e sujeira ----
        s("gr_scratches", "Arranhões", GRUNGE, 60f, 0.9f, StampShape.SCRATCH, 0.3f, 1f, 0.6f, 3),
        s("gr_marks", "Riscos", GRUNGE, 50f, 0.9f, StampShape.LINE, 0.5f, 1f, 0.6f, 2),
        s("gr_dirt", "Sujeira", GRUNGE, 30f, 0.9f, StampShape.SPLAT, 0.3f, 1.2f, 0.8f, 4),
        s("gr_powder", "Pó", GRUNGE, 50f, 0.8f, StampShape.GRAIN, 0.05f, 1.2f, 0.7f, 10),
        s("gr_cracks", "Rachaduras", GRUNGE, 50f, 1f, StampShape.ZIGZAG, 0.8f, 0.4f, 0.5f, 1),
        s("gr_ink", "Manchas de tinta", GRUNGE, 40f, 0.9f, StampShape.SPLAT, 0.5f, 1f, 0.7f, 3),
        b("gr_heavy", "Grunge pesado", GRUNGE, 70f, 0.95f, 1f, GR),
        b("gr_wear", "Desgaste", GRUNGE, 60f, 0.8f, 0.8f, GR),
        b("gr_rust", "Ferrugem", GRUNGE, 60f, 0.9f, 0.9f, ST),
        b("gr_noise", "Ruído", GRUNGE, 60f, 0.9f, 0.9f, SA),
        b("gr_vignette", "Vinhete", GRUNGE, 160f, 0.35f, hard = 0f, soft = true),
        // ---- Animação e conceito ----
        s("an_rotations", "Rotações", ANIM, 50f, 1f, StampShape.SPIRAL, 1.4f, 0f, 0f, 1),
        s("an_motion", "Movimento", ANIM, 100f, 0.9f, StampShape.STREAK, 0.1f, 0.8f, 0.5f, 2),
        b("an_motion_blur", "Desfoque de movimento", ANIM, 60f, 0.5f, lines = 9, jitter = 0.8f),
        s("an_radial_blur", "Desfoque radial", ANIM, 160f, 0.6f, StampShape.BURST, 1.4f, 0f, 0f, 1),
        s("an_perspective", "Perspetiva", ANIM, 120f, 0.9f, StampShape.LINE, 0.6f, 0.3f, 0.4f, 1),
        s("an_persp_guides", "Guias de perspetiva", ANIM, 160f, 0.6f, StampShape.LINE, 0.5f, 0f, 0f, 1),
        s("an_base3d", "Forma básica 3D", ANIM, 50f, 1f, StampShape.SQUARE, 1.2f, 0f, 0f, 1),
        b("an_shadow3d", "Sombra 3D", ANIM, 60f, 0.5f, hard = 0.1f, soft = true),
        b("an_concept_tex", "Conceito textura", ANIM, 60f, 0.9f, 0.8f, ST),
        b("an_concept_hard", "Conceito hard", ANIM, 40f, 1f, 0.4f, CV),
        b("an_concept_soft", "Conceito soft", ANIM, 60f, 0.7f, 0.3f, CL, 0.3f, true),
        b("an_thumbs", "Thumbnails", ANIM, 20f, 0.9f, 0.5f, P),
    )
}
