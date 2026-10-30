package com.animationstudio.domain

/**
 * Insere um frame na posição [position] (0 = antes do primeiro, frameCount = depois do último) e seleciona-o.
 * Camadas de desenho recebem [content] (se existir, em cópia independente); cenários continuam o desenho do
 * frame vizinho anterior (ou do primeiro, se inserido no início).
 */
fun Project.insertFrameAt(position: Int, content: Map<Long, List<BrushStroke>> = emptyMap()): Project {
    val pos = position.coerceIn(0, frameIds.size)
    val newId = nextId
    var counter = nextId + 1
    val neighbor = frameIds[(pos - 1).coerceAtLeast(0).coerceAtMost(frameIds.lastIndex)]
    val newLayers = layers.map { l ->
        if (l.kind == LayerKind.SCENERY) {
            val shared = l.cels[neighbor]
            if (shared != null) l.copy(cels = l.cels + (newId to shared)) else l
        } else {
            val strokes = content[l.id]
            if (strokes.isNullOrEmpty()) {
                l
            } else {
                val d = Drawing(counter, strokes)
                counter += 1
                l.copy(drawings = l.drawings + (d.id to d), cels = l.cels + (newId to d.id))
            }
        }
    }
    val ids = frameIds.toMutableList()
    ids.add(pos, newId)
    return Project(ids, newLayers, pos, counter)
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
