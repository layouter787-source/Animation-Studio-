package com.animationstudio.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import com.animationstudio.domain.BrushStroke
import com.animationstudio.domain.FillRegion
import com.animationstudio.domain.Project
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max

/**
 * Balde de tinta: renderiza o que está visível no frame atual (todas as camadas), faz o enchimento por
 * inundação a partir do ponto tocado e devolve a região como máscara. A região é expandida 1 célula para
 * ficar por baixo das linhas (sem fresta nos contornos suavizados). Chamar fora da thread principal.
 */
internal fun computeFill(project: Project, sw: Int, sh: Int, seedX: Float, seedY: Float, colorArgb: Int): BrushStroke? {
    val unit = max(1f, max(sw, sh) / 2048f)
    val w = ceil(sw / unit).toInt().coerceAtLeast(1)
    val h = ceil(sh / unit).toInt().coerceAtLeast(1)
    val sx = (seedX / unit).toInt()
    val sy = (seedY / unit).toInt()
    if (sx !in 0 until w || sy !in 0 until h) return null

    val bmp = renderComposite(project, project.currentFrame, sw, sh, w, h, background = null, useCache = false)
    val px = IntArray(w * h)
    bmp.asAndroidBitmap().getPixels(px, 0, w, 0, 0, w, h)
    val seed = px[sy * w + sx]
    val tol = 90

    fun similar(p: Int): Boolean {
        val da = abs((p ushr 24) - (seed ushr 24))
        val dr = abs(((p shr 16) and 0xFF) - ((seed shr 16) and 0xFF))
        val dg = abs(((p shr 8) and 0xFF) - ((seed shr 8) and 0xFF))
        val db = abs((p and 0xFF) - (seed and 0xFF))
        return da + dr + dg + db <= tol
    }

    val visited = BooleanArray(w * h)
    val stack = IntArray(w * h)
    var sp = 0
    val start = sy * w + sx
    stack[sp++] = start
    visited[start] = true
    var count = 0
    var minX = w
    var minY = h
    var maxX = -1
    var maxY = -1
    while (sp > 0) {
        val i = stack[--sp]
        val x = i % w
        val y = i / w
        count += 1
        if (x < minX) minX = x
        if (x > maxX) maxX = x
        if (y < minY) minY = y
        if (y > maxY) maxY = y
        if (x > 0 && !visited[i - 1] && similar(px[i - 1])) { visited[i - 1] = true; stack[sp++] = i - 1 }
        if (x < w - 1 && !visited[i + 1] && similar(px[i + 1])) { visited[i + 1] = true; stack[sp++] = i + 1 }
        if (y > 0 && !visited[i - w] && similar(px[i - w])) { visited[i - w] = true; stack[sp++] = i - w }
        if (y < h - 1 && !visited[i + w] && similar(px[i + w])) { visited[i + w] = true; stack[sp++] = i + w }
    }
    if (count == 0) return null

    // Expande 1 célula (4-vizinhos) dentro de uma caixa 1 maior que a região.
    val bx = (minX - 1).coerceAtLeast(0)
    val by = (minY - 1).coerceAtLeast(0)
    val bw = (maxX + 1).coerceAtMost(w - 1) - bx + 1
    val bh = (maxY + 1).coerceAtMost(h - 1) - by + 1
    val mask = BooleanArray(bw * bh)
    for (y in 0 until bh) {
        for (x in 0 until bw) {
            val gx = bx + x
            val gy = by + y
            val on = visited[gy * w + gx] ||
                (gx > 0 && visited[gy * w + gx - 1]) || (gx < w - 1 && visited[gy * w + gx + 1]) ||
                (gy > 0 && visited[(gy - 1) * w + gx]) || (gy < h - 1 && visited[(gy + 1) * w + gx])
            mask[y * bw + x] = on
        }
    }
    val region = FillRegion.encode(mask, bw, bh, bx, by, unit)
    return BrushStroke(emptyList(), colorArgb or 0, 0f, erase = false, fill = region)
}

/** Cor de fundo do papel usada nas exportações e miniaturas. */
internal val PaperColor = Color.White
