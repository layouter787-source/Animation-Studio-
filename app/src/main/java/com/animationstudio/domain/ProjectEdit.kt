package com.animationstudio.domain

/**
 * Edições de elementos do desenho atual de uma camada (balde, laço). Ficam fora de [Project]
 * para não alterar a classe já testada; usam apenas membros públicos.
 */
private fun Project.replaceLayer(id: Long, f: (Layer) -> Layer): Project =
    copy(layers = layers.map { if (it.id == id) f(it) else it })

private fun Project.updateCurrentStrokes(layerId: Long, f: (List<BrushStroke>) -> List<BrushStroke>): Project {
    val layer = layers.firstOrNull { it.id == layerId } ?: return this
    val d = layer.drawingAt(currentFrameId) ?: return this
    val updated = f(d.strokes)
    return replaceLayer(layerId) { it.copy(drawings = it.drawings + (d.id to d.copy(strokes = updated))) }
}

/** Insere o preenchimento por baixo dos traços de tinta e por cima dos preenchimentos anteriores. */
fun Project.addFill(layerId: Long, fill: BrushStroke): Project {
    val layer = layers.firstOrNull { it.id == layerId } ?: return this
    val existing = layer.drawingAt(currentFrameId) ?: return addStroke(layerId, fill)
    val idx = existing.strokes.indexOfLast { it.fill != null } + 1
    val list = existing.strokes.toMutableList()
    list.add(idx, fill)
    return replaceLayer(layerId) { it.copy(drawings = it.drawings + (existing.id to existing.copy(strokes = list))) }
}

fun Project.transformElements(layerId: Long, indices: Set<Int>, m: Affine): Project =
    updateCurrentStrokes(layerId) { list ->
        list.mapIndexed { i, s -> if (i in indices) (s.transformed(m) ?: s) else s }
    }

fun Project.removeElements(layerId: Long, indices: Set<Int>): Project =
    updateCurrentStrokes(layerId) { list -> list.filterIndexed { i, _ -> i !in indices } }

/** Elementos (traços e preenchimentos) do desenho atual da camada. */
fun Project.elementsAt(layerId: Long, frameIndex: Int): List<BrushStroke> = strokesAt(layerId, frameIndex)
