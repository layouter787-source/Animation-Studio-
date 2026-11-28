package com.animationstudio.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import com.animationstudio.domain.BrushStroke
import com.animationstudio.domain.FillRegion
import com.animationstudio.domain.NativeStudio
import com.animationstudio.domain.Project
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** Memória da referência do balde: até ~4 milhões de células (Full HD fica 1:1, folhas maiores são reduzidas). */
private const val MAX_CELLS = 4_000_000f

/**
 * Balde de tinta. A lógica está em C++ (studio_fill.hpp); aqui só se desenha a referência e se guarda o resultado:
 *  - referência = o que está visível no frame, desenhado em vetor, em até ~4 milhões de células;
 *  - NÃO VAZA: qualquer vestígio de tinta (opacidade acima de 0) é parede, por mais fina que seja a linha; a opção de
 *    opacidade mínima já não deixa passar linhas leves;
 *  - SEM VÃOS: debaixo de cada linha a cor chega até ao meio dela (a região livre mais perto), sem passar para o outro lado;
 *  - frestas pequenas só se fecham se o preenchimento sem fecho cobrir quase a folha toda;
 *  - o preenchimento é guardado em modo "por trás" (mode = 2): a tinta das linhas fica sempre por cima.
 * Chamar fora da thread principal. Devolve null se o ponto está fora da folha ou não há onde preencher.
 */
internal fun computeFill(
    project: Project,
    sw: Int,
    sh: Int,
    seedX: Float,
    seedY: Float,
    colorArgb: Int,
    gapPx: Int,
    growPx: Int,
): BrushStroke? {
    val unit = max(1f, sqrt(sw.toFloat() * sh.toFloat() / MAX_CELLS))
    val w = ceil(sw / unit).toInt().coerceAtLeast(1)
    val h = ceil(sh / unit).toInt().coerceAtLeast(1)
    val sx = (seedX / unit).toInt()
    val sy = (seedY / unit).toInt()
    if (sx !in 0 until w || sy !in 0 until h) return null

    val bmp = renderComposite(project, project.currentFrame, sw, sh, w, h, background = null, useCache = false)
    val px = IntArray(w * h)
    bmp.asAndroidBitmap().getPixels(px, 0, w, 0, 0, w, h)

    val gapCells = (gapPx / unit).roundToInt().coerceIn(0, 24)
    val growCap = (growPx / unit).roundToInt().coerceIn(16, 40)
    val mask = NativeStudio.nComputeFill(px, w, h, sx, sy, 0, 8, gapCells, growCap) ?: return null

    var minX = w
    var minY = h
    var maxX = -1
    var maxY = -1
    for (y in 0 until h) {
        val row = y * w
        for (x in 0 until w) {
            if (mask[row + x].toInt() != 0) {
                if (x < minX) minX = x
                if (x > maxX) maxX = x
                if (y < minY) minY = y
                if (y > maxY) maxY = y
            }
        }
    }
    if (maxX < 0) return null
    val bw = maxX - minX + 1
    val bh = maxY - minY + 1
    val reg = BooleanArray(bw * bh)
    for (y in 0 until bh) {
        val src = (minY + y) * w + minX
        for (x in 0 until bw) reg[y * bw + x] = mask[src + x].toInt() != 0
    }
    // mode = 2: desenhado por trás da tinta existente na camada.
    return BrushStroke(emptyList(), colorArgb, 0f, erase = false, fill = FillRegion.encode(reg, bw, bh, minX, minY, unit), mode = 2)
}

/** Cor de fundo do papel usada nas exportações e miniaturas. */
internal val PaperColor = Color.White
