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

/**
 * Acrescenta o preenchimento no FIM da lista, pela ordem do tempo. O desenho separa depois os preenchimentos
 * (por baixo) da tinta (por cima), e cada grupo recebe as borrachas pela ordem em que foram feitas. Assim:
 *  - uma borracha anterior ao balde não apaga o preenchimento novo (pintar sobre uma zona apagada funciona);
 *  - uma borracha posterior apaga também o preenchimento;
 *  - um preenchimento novo fica por cima dos antigos e por baixo de toda a tinta.
 */
fun Project.addFill(layerId: Long, fill: BrushStroke): Project = addStroke(layerId, fill)

fun Project.transformElements(layerId: Long, indices: Set<Int>, m: Affine): Project =
    updateCurrentStrokes(layerId) { list ->
        list.mapIndexed { i, s -> if (i in indices) (s.transformed(m) ?: s) else s }
    }

fun Project.removeElements(layerId: Long, indices: Set<Int>): Project =
    updateCurrentStrokes(layerId) { list -> list.filterIndexed { i, _ -> i !in indices } }
