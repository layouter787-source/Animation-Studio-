package com.animationstudio.domain

/**
 * Importa imagens (uma por frame): cada imagem cria um frame novo logo após o atual, com a imagem como
 * elemento da camada [layerId]. Usado na importação de vídeo (frames extraídos).
 */
fun Project.importImages(layerId: Long, images: List<ImageData>): Project {
    var p = this
    for (img in images) {
        p = p.insertFrameAt(p.currentFrame + 1).addStroke(layerId, BrushStroke(emptyList(), 0, 0f, image = img))
    }
    return p
}
