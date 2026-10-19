package com.animationstudio.domain

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
 * Projeto: frames (ids) + camadas ordenadas de baixo para cima. Imutável e sem dependências Android.
 * Substitui [Timeline] quando a UI migrar para camadas.
 */
data class Project(
    val frameIds: List<Long>,
    val layers: List<Layer>,
    val currentFrame: Int,
    val nextId: Long,
) {
    init {
        require(frameIds.isNotEmpty()) { "Precisa de pelo menos um frame" }
        require(currentFrame in frameIds.indices) { "Frame atual fora dos limites" }
        require(layers.isNotEmpty()) { "Precisa de pelo menos uma camada" }
    }

    val frameCount: Int get() = frameIds.size
    val currentFrameId: Long get() = frameIds[currentFrame]

    fun strokesAt(layerId: Long, frameIndex: Int): List<BrushStroke> =
        layers.firstOrNull { it.id == layerId }?.drawingAt(frameIds[frameIndex])?.strokes ?: emptyList()

    private fun updateLayer(id: Long, f: (Layer) -> Layer): Project =
        copy(layers = layers.map { if (it.id == id) f(it) else it })

    fun select(index: Int): Project = copy(currentFrame = index.coerceIn(0, frameIds.lastIndex))

    /** Acrescenta um traço no frame atual. Num cenário, o primeiro desenho cobre todos os frames sem desenho. */
    fun addStroke(layerId: Long, stroke: BrushStroke): Project {
        val layer = layers.firstOrNull { it.id == layerId } ?: return this
        val fid = currentFrameId
        val existing = layer.drawingAt(fid)
        if (existing != null) {
            return updateLayer(layerId) {
                it.copy(drawings = it.drawings + (existing.id to existing.copy(strokes = existing.strokes + stroke)))
            }
        }
        val fresh = Drawing(nextId, listOf(stroke))
        val targets = if (layer.kind == LayerKind.SCENERY) frameIds.filter { it !in layer.cels } else listOf(fid)
        return copy(nextId = nextId + 1).updateLayer(layerId) {
            it.copy(drawings = it.drawings + (fresh.id to fresh), cels = it.cels + targets.associateWith { fresh.id })
        }
    }

    // ---------- frames ----------

    /** Novo frame após o atual: camadas de desenho em branco; cenários continuam o desenho do frame atual. */
    fun addFrameAfterCurrent(): Project {
        val newId = nextId
        val cur = currentFrameId
        val newLayers = layers.map { l ->
            val shared = if (l.kind == LayerKind.SCENERY) l.cels[cur] else null
            if (shared != null) l.copy(cels = l.cels + (newId to shared)) else l
        }
        val ids = frameIds.toMutableList().apply { add(currentFrame + 1, newId) }
        return Project(ids, newLayers, currentFrame + 1, nextId + 1)
    }

    /** Duplica o frame atual: desenhos copiados de forma independente; cenários partilhados. */
    fun duplicateCurrentFrame(): Project {
        val newId = nextId
        var counter = nextId + 1
        val cur = currentFrameId
        val newLayers = layers.map { l ->
            val src = l.drawingAt(cur)
            if (src == null) {
                l
            } else if (l.kind == LayerKind.SCENERY) {
                l.copy(cels = l.cels + (newId to src.id))
            } else {
                val dup = src.copy(id = counter)
                counter += 1
                l.copy(drawings = l.drawings + (dup.id to dup), cels = l.cels + (newId to dup.id))
            }
        }
        val ids = frameIds.toMutableList().apply { add(currentFrame + 1, newId) }
        return Project(ids, newLayers, currentFrame + 1, counter)
    }

    fun deleteCurrentFrame(): Project {
        if (frameIds.size == 1) return this
        val fid = currentFrameId
        val ids = frameIds.toMutableList().apply { removeAt(currentFrame) }
        val newLayers = layers.map { l ->
            val remaining = l.cels - fid
            val used = remaining.values.toSet()
            l.copy(cels = remaining, drawings = l.drawings.filterKeys { it in used })
        }
        return Project(ids, newLayers, currentFrame.coerceAtMost(ids.lastIndex), nextId)
    }

    /**
     * "Separar a partir deste frame": o frame atual e os seguintes que partilhavam o desenho
     * do cenário passam a usar uma cópia; os anteriores ficam como estavam.
     */
    fun separateSceneryFromCurrent(layerId: Long): Project {
        val layer = layers.firstOrNull { it.id == layerId && it.kind == LayerKind.SCENERY } ?: return this
        val old = layer.drawingAt(currentFrameId) ?: return this
        val dup = old.copy(id = nextId)
        val remapped = layer.cels.toMutableMap()
        for (i in currentFrame until frameIds.size) {
            val fid = frameIds[i]
            if (remapped[fid] == old.id) remapped[fid] = dup.id
        }
        return copy(nextId = nextId + 1).updateLayer(layerId) {
            it.copy(drawings = it.drawings + (dup.id to dup), cels = remapped)
        }
    }

    // ---------- camadas ----------

    /** Camadas de desenho entram no topo; cenários entram abaixo de todas as camadas de desenho. */
    fun addLayer(kind: LayerKind): Project {
        val n = layers.count { it.kind == kind } + 1
        val name = if (kind == LayerKind.DRAWING) "Camada $n" else "Cenário $n"
        val layer = Layer(nextId, name, kind)
        val list = layers.toMutableList()
        val firstDrawing = list.indexOfFirst { it.kind == LayerKind.DRAWING }
        val idx = if (kind == LayerKind.SCENERY) (if (firstDrawing < 0) list.size else firstDrawing) else list.size
        list.add(idx, layer)
        return copy(layers = list, nextId = nextId + 1)
    }

    /** Duplica a camada com todo o conteúdo, de forma independente, logo acima da original. */
    fun duplicateLayer(layerId: Long): Project {
        val i = layers.indexOfFirst { it.id == layerId }
        if (i < 0) return this
        val src = layers[i]
        var counter = nextId + 1
        val idMap = HashMap<Long, Long>()
        for (oldId in src.drawings.keys) {
            idMap[oldId] = counter
            counter += 1
        }
        val dupLayer = Layer(
            id = nextId,
            name = "${src.name} cópia",
            kind = src.kind,
            opacityPercent = src.opacityPercent,
            visible = src.visible,
            drawings = src.drawings.values.associate { d -> idMap.getValue(d.id) to d.copy(id = idMap.getValue(d.id)) },
            cels = src.cels.mapValues { entry -> idMap.getValue(entry.value) },
        )
        val list = layers.toMutableList()
        list.add(i + 1, dupLayer)
        return copy(layers = list, nextId = counter)
    }

    fun renameLayer(layerId: Long, name: String): Project =
        if (name.isBlank()) this else updateLayer(layerId) { it.copy(name = name.trim()) }

    fun setLayerOpacity(layerId: Long, percent: Int): Project =
        updateLayer(layerId) { it.copy(opacityPercent = percent.coerceIn(0, 100)) }

    fun toggleLayerVisible(layerId: Long): Project = updateLayer(layerId) { it.copy(visible = !it.visible) }

    fun deleteLayer(layerId: Long): Project =
        if (layers.size == 1) this else copy(layers = layers.filter { it.id != layerId })

    companion object {
        /** Um frame, um cenário (por baixo) e uma camada de desenho (por cima). */
        fun initial(): Project = Project(
            frameIds = listOf(0L),
            layers = listOf(
                Layer(1, "Cenário 1", LayerKind.SCENERY),
                Layer(2, "Camada 1", LayerKind.DRAWING),
            ),
            currentFrame = 0,
            nextId = 3,
        )
    }
}
