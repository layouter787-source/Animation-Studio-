package com.animationstudio.domain

/**
 * Insere um frame na posição [position] (0 = antes do primeiro, frameCount = depois do último) e seleciona-o.
 * Camadas de desenho recebem [content] (se existir, em cópia independente); cenários continuam o desenho do
 * frame vizinho anterior (ou do primeiro, se inserido no início).
 */
fun Project.insertFrameAt(position: Int, content: Map<Long, List<BrushStroke>> = emptyMap()): Project {
    val layerIds = ArrayList<Long>()
    val counts = ArrayList<Int>()
    val strokeIds = ArrayList<Long>()
    for ((layerId, strokes) in content) {
        layerIds.add(layerId)
        counts.add(strokes.size)
        for (s in strokes) strokeIds.add(StrokeRegistry.idOf(s))
    }
    return fenced {
        Project.of(NativeStudio.nInsertFrameAt(handle, position, layerIds.toLongArray(), counts.toIntArray(), strokeIds.toLongArray()))
    }
}

/** Conteúdo copiável de um frame: traços das camadas de desenho. O cenário é partilhado e não entra na cópia. */
fun Project.frameClip(index: Int): Map<Long, List<BrushStroke>> {
    val fid = frameIds[index.coerceIn(0, frameIds.lastIndex)]
    val out = LinkedHashMap<Long, List<BrushStroke>>()
    for (l in layers) {
        if (l.kind != LayerKind.DRAWING) continue
        val d = l.drawingAt(fid) ?: continue
        out[l.id] = d.strokes
    }
    return out
}
