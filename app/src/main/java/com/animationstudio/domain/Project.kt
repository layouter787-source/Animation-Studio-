package com.animationstudio.domain

import java.lang.ref.Reference

enum class LayerKind { DRAWING, SCENERY }

/** Um desenho (conjunto de traços). Pode ser referenciado por vários frames (exposição partilhada). */
data class Drawing(val id: Long, val strokes: List<BrushStroke> = emptyList())

/**
 * Camada de desenho ou de cenário.
 *
 * [cels] liga cada frame (por id) a um desenho. Dois frames que apontam para o mesmo desenho
 * partilham o conteúdo: editar um altera o outro. É assim que o cenário funciona:
 * um único desenho para todos os frames, editável a partir de qualquer frame, e
 * [Project.separateSceneryFromCurrent] separa-o a partir do frame atual sem apagar nada.
 */
data class Layer(
    val id: Long,
    val name: String,
    val kind: LayerKind,
    val opacityPercent: Int = 100,
    val visible: Boolean = true,
    val drawings: Map<Long, Drawing> = emptyMap(),
    val cels: Map<Long, Long> = emptyMap(),
) {
    fun drawingAt(frameId: Long): Drawing? = cels[frameId]?.let { drawings[it] }
}

/**
 * Projeto: frames (ids) + camadas ordenadas de baixo para cima. Imutável. Os DADOS E AS REGRAS vivem no C++
 * (studio_project.hpp); esta classe guarda só o handle e devolve vistas Kotlin só de leitura ([layers], [frameIds]).
 * Cada edição devolve um projeto novo (ou [this], se nada mudou, como sempre).
 */
class Project private constructor(internal val handle: Long) {
    val frameIds: List<Long> by lazy(LazyThreadSafetyMode.PUBLICATION) { fenced { NativeStudio.nFrameIds(handle).asList() } }
    val currentFrame: Int by lazy(LazyThreadSafetyMode.PUBLICATION) { fenced { NativeStudio.nCurrentFrame(handle) } }
    val nextId: Long by lazy(LazyThreadSafetyMode.PUBLICATION) { fenced { NativeStudio.nNextId(handle) } }
    val layers: List<Layer> by lazy(LazyThreadSafetyMode.PUBLICATION) { fenced { materialize() } }

    val frameCount: Int get() = frameIds.size
    val currentFrameId: Long get() = frameIds[currentFrame]

    private val layerInfos: List<LongArray> by lazy(LazyThreadSafetyMode.PUBLICATION) {
        fenced { List(NativeStudio.nLayerCount(handle)) { NativeStudio.nLayerInfo(handle, it) } }
    }

    protected fun finalize() {
        NativeStudio.nFree(handle)
    }

    /** Igualdade por conteúdo (como a classe de dados que isto substitui). Listas iguais por identidade saem logo. */
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Project) return false
        return currentFrame == other.currentFrame && nextId == other.nextId && frameIds == other.frameIds && layers == other.layers
    }

    override fun hashCode(): Int = (frameIds.hashCode() * 31 + currentFrame) * 31 + nextId.hashCode()

    internal inline fun <T> fenced(block: () -> T): T = try {
        block()
    } finally {
        Reference.reachabilityFence(this)
    }

    private fun materialize(): List<Layer> {
        StrokeRegistry.drain()
        return List(layerInfos.size) { i ->
            val info = layerInfos[i]
            val flat = NativeStudio.nLayerDrawings(handle, i)
            val drawings = LinkedHashMap<Long, Drawing>()
            var k = 0
            while (k + 2 < flat.size) {
                val n = flat[k + 2].toInt()
                drawings[flat[k]] = StrokeRegistry.drawing(flat[k + 1], flat[k], flat, k + 3, n)
                k += 3 + n
            }
            val cf = NativeStudio.nLayerCels(handle, i)
            val cels = LinkedHashMap<Long, Long>()
            var c = 0
            while (c + 1 < cf.size) {
                cels[cf[c]] = cf[c + 1]
                c += 2
            }
            Layer(
                id = info[0],
                name = NativeStudio.nLayerName(handle, i),
                kind = if (info[1] == 1L) LayerKind.SCENERY else LayerKind.DRAWING,
                opacityPercent = info[2].toInt(),
                visible = info[3] != 0L,
                drawings = drawings,
                cels = cels,
            )
        }
    }

    private fun hasLayer(id: Long) = layerInfos.any { it[0] == id }

    private fun derive(newHandle: Long): Project = of(newHandle)

    fun strokesAt(layerId: Long, frameIndex: Int): List<BrushStroke> =
        layers.firstOrNull { it.id == layerId }?.drawingAt(frameIds[frameIndex])?.strokes ?: emptyList()

    fun select(index: Int): Project = fenced { derive(NativeStudio.nSelect(handle, index)) }

    /** Acrescenta um traço no frame atual. Num cenário, o primeiro desenho cobre todos os frames sem desenho. */
    fun addStroke(layerId: Long, stroke: BrushStroke): Project {
        if (!hasLayer(layerId)) return this
        val sid = StrokeRegistry.idOf(stroke)
        return fenced { derive(NativeStudio.nAddStroke(handle, layerId, sid)) }
    }

    // ---------- frames ----------

    /** Novo frame após o atual: camadas de desenho em branco; cenários continuam o desenho do frame atual. */
    fun addFrameAfterCurrent(): Project = fenced { derive(NativeStudio.nAddFrame(handle)) }

    /** Duplica o frame atual: desenhos copiados de forma independente; cenários partilhados. */
    fun duplicateCurrentFrame(): Project = fenced { derive(NativeStudio.nDuplicateFrame(handle)) }

    fun deleteCurrentFrame(): Project = if (frameCount == 1) this else fenced { derive(NativeStudio.nDeleteFrame(handle)) }

    /**
     * "Separar a partir deste frame": o frame atual e os seguintes que partilhavam o desenho
     * do cenário passam a usar uma cópia; os anteriores ficam como estavam.
     */
    fun separateSceneryFromCurrent(layerId: Long): Project {
        val layer = layers.firstOrNull { it.id == layerId && it.kind == LayerKind.SCENERY } ?: return this
        if (layer.drawingAt(currentFrameId) == null) return this
        return fenced { derive(NativeStudio.nSeparateScenery(handle, layerId)) }
    }

    // ---------- camadas ----------

    /** Camadas de desenho entram no topo; cenários entram abaixo de todas as camadas de desenho. */
    fun addLayer(kind: LayerKind): Project =
        fenced { derive(NativeStudio.nAddLayer(handle, if (kind == LayerKind.SCENERY) 1 else 0)) }

    /** Duplica a camada com todo o conteúdo, de forma independente, logo acima da original. */
    fun duplicateLayer(layerId: Long): Project =
        if (!hasLayer(layerId)) this else fenced { derive(NativeStudio.nDuplicateLayer(handle, layerId)) }

    fun renameLayer(layerId: Long, name: String): Project =
        if (name.isBlank()) this else fenced { derive(NativeStudio.nRenameLayer(handle, layerId, name)) }

    fun setLayerOpacity(layerId: Long, percent: Int): Project =
        fenced { derive(NativeStudio.nSetLayerOpacity(handle, layerId, percent)) }

    fun toggleLayerVisible(layerId: Long): Project = fenced { derive(NativeStudio.nToggleLayerVisible(handle, layerId)) }

    fun deleteLayer(layerId: Long): Project =
        if (layerInfos.size == 1) this else fenced { derive(NativeStudio.nDeleteLayer(handle, layerId)) }

    /** Move uma camada [delta] posições na pilha (positivo = para cima, negativo = para baixo). */
    internal fun moveLayerImpl(layerId: Long, delta: Int): Project {
        val i = layerInfos.indexOfFirst { it[0] == layerId }
        if (i < 0) return this
        val j = (i + delta).coerceIn(0, layerInfos.lastIndex)
        if (i == j) return this
        return fenced { derive(NativeStudio.nMoveLayer(handle, layerId, delta)) }
    }

    companion object {
        internal fun of(handle: Long): Project = Project(handle)

        /** Um frame, um cenário (por baixo) e uma camada de desenho (por cima). */
        fun initial(): Project = Project(NativeStudio.nInitial())
    }
}

/** Monta um projeto a partir de peças (abrir ficheiro, importar). Precisa de pelo menos um frame e uma camada. */
fun Project(frameIds: List<Long>, layers: List<Layer>, currentFrame: Int, nextId: Long): Project {
    require(frameIds.isNotEmpty()) { "Precisa de pelo menos um frame" }
    require(currentFrame in frameIds.indices) { "Frame atual fora dos limites" }
    require(layers.isNotEmpty()) { "Precisa de pelo menos uma camada" }
    val layerIdx = LongArray(layers.size) { layers[it].id }
    val meta = LongArray(layers.size * 3)
    val drawings = ArrayList<Long>()
    val cels = ArrayList<Long>()
    layers.forEachIndexed { i, l ->
        meta[3 * i] = if (l.kind == LayerKind.SCENERY) 1L else 0L
        meta[3 * i + 1] = l.opacityPercent.toLong()
        meta[3 * i + 2] = if (l.visible) 1L else 0L
        for (d in l.drawings.values) {
            drawings.add(i.toLong())
            drawings.add(d.id)
            drawings.add(d.strokes.size.toLong())
            for (s in d.strokes) drawings.add(StrokeRegistry.idOf(s))
        }
        for ((frameId, drawingId) in l.cels) {
            cels.add(i.toLong())
            cels.add(frameId)
            cels.add(drawingId)
        }
    }
    val h = NativeStudio.nBuild(
        frameIds.toLongArray(), currentFrame, nextId, layerIdx, meta, Array(layers.size) { layers[it].name },
        drawings.toLongArray(), cels.toLongArray(),
    )
    return Project.of(h)
}
