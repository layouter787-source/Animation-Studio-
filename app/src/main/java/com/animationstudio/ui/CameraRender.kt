package com.animationstudio.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.animationstudio.domain.Affine
import com.animationstudio.domain.Project
import androidx.compose.ui.graphics.Canvas as GfxCanvas

/** Matriz 4×4 do Compose a partir de uma afim 2D. */
internal fun affineMatrix(m: Affine): Matrix =
    Matrix(floatArrayOf(m.a, m.b, 0f, 0f, m.c, m.d, 0f, 0f, 0f, 0f, 1f, 0f, m.tx, m.ty, 0f, 1f))

/**
 * Renderiza um frame como o vê a câmara: [camera] leva a folha para a imagem de saída (null = folha inteira).
 * Fundo branco; o que fica fora da folha não tem desenho. Seguro fora da thread principal.
 */
internal fun renderCompositeWithCamera(
    project: Project,
    frameIndex: Int,
    sw: Int,
    sh: Int,
    outW: Int,
    outH: Int,
    camera: Affine?,
): ImageBitmap {
    val bmp = ImageBitmap(outW, outH)
    val canvas = GfxCanvas(bmp)
    CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, canvas, Size(outW.toFloat(), outH.toFloat())) {
        drawRect(Color.White)
        withTransform({ scale(outW / sw.toFloat(), outH / sh.toFloat(), Offset.Zero) }) {
            clipRect(0f, 0f, sw.toFloat(), sh.toFloat()) {
                val fid = project.frameIds[frameIndex]
                if (camera != null) {
                    withTransform({ transform(affineMatrix(camera)) }) {
                        drawFrameLayers(project, fid, sw.toFloat(), sh.toFloat(), null, null, false)
                    }
                } else {
                    drawFrameLayers(project, fid, sw.toFloat(), sh.toFloat(), null, null, false)
                }
            }
        }
    }
    return bmp
}
