package com.animationstudio.domain

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** Ponto em coordenadas do projeto (folha) ou do ecrã, conforme o contexto da chamada. */
data class Point(val x: Float, val y: Float)

/**
 * Transformação única da vista: projeto -> ecrã.
 *
 *   screen = R(rotationRad) * (scale * project) + (tx, ty)
 *
 * É a única fonte de verdade para zoom, rotação visual e pan. A entrada de desenho usa
 * [screenToProject] (matriz inversa); nunca se divide apenas pelo zoom quando há rotação.
 * Esta classe é Kotlin puro (sem Android) para poder ser testada na JVM.
 */
data class ViewTransform(
    val scale: Float = 1f,
    val rotationRad: Float = 0f,
    val tx: Float = 0f,
    val ty: Float = 0f,
) {
    val rotationDegrees: Float get() = (rotationRad * 180.0 / PI).toFloat()

    fun projectToScreen(p: Point): Point {
        val c = cos(rotationRad)
        val s = sin(rotationRad)
        val x = scale * p.x
        val y = scale * p.y
        return Point(c * x - s * y + tx, s * x + c * y + ty)
    }

    fun screenToProject(p: Point): Point {
        val c = cos(rotationRad)
        val s = sin(rotationRad)
        val dx = p.x - tx
        val dy = p.y - ty
        return Point((c * dx + s * dy) / scale, (-s * dx + c * dy) / scale)
    }

    /** Desloca a vista. Não altera escala, rotação nem qualquer dado artístico. */
    fun panBy(dx: Float, dy: Float): ViewTransform = copy(tx = tx + dx, ty = ty + dy)

    /** Zoom mantendo o ponto do projeto que está sob [focus] (ecrã) no mesmo sítio. */
    fun zoomAround(focus: Point, factor: Float): ViewTransform {
        val newScale = (scale * factor).coerceIn(MIN_SCALE, MAX_SCALE)
        return anchored(screenToProject(focus), focus, newScale, rotationRad)
    }

    /** Rotação visual em torno de [focus] (ecrã), preservando o foco. */
    fun rotateAround(focus: Point, deltaRad: Float): ViewTransform =
        anchored(screenToProject(focus), focus, scale, rotationRad + deltaRad)

    private fun anchored(projectPoint: Point, screenPoint: Point, newScale: Float, newRotation: Float): ViewTransform {
        val c = cos(newRotation)
        val s = sin(newRotation)
        val x = newScale * projectPoint.x
        val y = newScale * projectPoint.y
        return ViewTransform(newScale, newRotation, screenPoint.x - (c * x - s * y), screenPoint.y - (s * x + c * y))
    }

    companion object {
        const val MIN_SCALE = 0.02f

        /** Zoom praticamente ilimitado (1.000.000×): o limite só existe para a aritmética de 32 bits não estourar. */
        const val MAX_SCALE = 1_000_000f

        /** Ajustar à folha: maior escala que mostra a folha inteira, centrada, sem rotação. */
        fun fitSheet(sheetW: Float, sheetH: Float, viewW: Float, viewH: Float, margin: Float = 16f): ViewTransform {
            val s = min((viewW - 2 * margin) / sheetW, (viewH - 2 * margin) / sheetH).coerceIn(MIN_SCALE, MAX_SCALE)
            return ViewTransform(s, 0f, (viewW - sheetW * s) / 2f, (viewH - sheetH * s) / 2f)
        }
    }
}
