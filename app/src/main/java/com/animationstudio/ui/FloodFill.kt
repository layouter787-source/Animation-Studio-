package com.animationstudio.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import com.animationstudio.domain.BrushStroke
import com.animationstudio.domain.FillRegion
import com.animationstudio.domain.Project
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Balde de tinta de qualidade de estúdio:
 *  - referência = o que está visível no frame (todas as camadas), a 2× de resolução em folhas até 1400 px
 *    (bordas suaves ao reduzir) ou até 2048 px no maior lado;
 *  - fecho de frestas: as linhas são engrossadas [gapPx] antes de inundar, e a região volta a crescer até às linhas;
 *  - expansão: a região entra [growPx] por baixo das linhas, sem halo nos contornos suavizados.
 * Chamar fora da thread principal. Devolve null se o ponto está fora da folha.
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
    val unit = if (max(sw, sh) <= 1400) 0.5f else max(1f, max(sw, sh) / 2048f)
    val w = ceil(sw / unit).toInt().coerceAtLeast(1)
    val h = ceil(sh / unit).toInt().coerceAtLeast(1)
    val sx = (seedX / unit).toInt()
    val sy = (seedY / unit).toInt()
    if (sx !in 0 until w || sy !in 0 until h) return null

    val bmp = renderComposite(project, project.currentFrame, sw, sh, w, h, background = null, useCache = false)
    val px = IntArray(w * h)
    bmp.asAndroidBitmap().getPixels(px, 0, w, 0, 0, w, h)
    val seed = px[sy * w + sx]
    val transparentSeed = (seed ushr 24) <= 24

    fun similar(p: Int): Boolean {
        val da = abs((p ushr 24) - (seed ushr 24))
        val dr = abs(((p shr 16) and 0xFF) - ((seed shr 16) and 0xFF))
        val dg = abs(((p shr 8) and 0xFF) - ((seed shr 8) and 0xFF))
        val db = abs((p and 0xFF) - (seed and 0xFF))
        return da + dr + dg + db <= 60
    }

    val barrier = BooleanArray(w * h)
    for (i in px.indices) {
        val p = px[i]
        barrier[i] = if (transparentSeed) (p ushr 24) > 24 else !similar(p)
    }

    val gapCells = (gapPx / unit).roundToInt().coerceIn(0, 24)
    val growCells = (growPx / unit).roundToInt().coerceIn(0, 12)
    val zone = if (gapCells > 0) dilate(barrier, w, h, gapCells) else barrier
    // Se o toque está colado a uma linha, não se fecham frestas (senão a semente ficava bloqueada).
    val blocked = if (zone[sy * w + sx]) barrier else zone
    if (blocked[sy * w + sx]) return null

    val visited = BooleanArray(w * h)
    val stack = IntArray(w * h)
    var sp = 0
    val start = sy * w + sx
    stack[sp++] = start
    visited[start] = true
    var minX = w
    var minY = h
    var maxX = -1
    var maxY = -1
    while (sp > 0) {
        val i = stack[--sp]
        val x = i % w
        val y = i / w
        if (x < minX) minX = x
        if (x > maxX) maxX = x
        if (y < minY) minY = y
        if (y > maxY) maxY = y
        if (x > 0 && !visited[i - 1] && !blocked[i - 1]) { visited[i - 1] = true; stack[sp++] = i - 1 }
        if (x < w - 1 && !visited[i + 1] && !blocked[i + 1]) { visited[i + 1] = true; stack[sp++] = i + 1 }
        if (y > 0 && !visited[i - w] && !blocked[i - w]) { visited[i - w] = true; stack[sp++] = i - w }
        if (y < h - 1 && !visited[i + w] && !blocked[i + w]) { visited[i + w] = true; stack[sp++] = i + w }
    }

    // Cresce a região até às linhas e por baixo delas, sem sair da zona das linhas engrossadas.
    val steps = gapCells + growCells
    val margin = steps + 1
    val bx = max(0, minX - margin)
    val by = max(0, minY - margin)
    val bw = min(w - 1, maxX + margin) - bx + 1
    val bh = min(h - 1, maxY + margin) - by + 1
    var reg = BooleanArray(bw * bh)
    for (y in 0 until bh) for (x in 0 until bw) reg[y * bw + x] = visited[(by + y) * w + (bx + x)]
    repeat(steps) {
        val next = reg.copyOf()
        for (y in 0 until bh) {
            for (x in 0 until bw) {
                if (!reg[y * bw + x]) continue
                if (x > 0 && zone[(by + y) * w + bx + x - 1]) next[y * bw + x - 1] = true
                if (x < bw - 1 && zone[(by + y) * w + bx + x + 1]) next[y * bw + x + 1] = true
                if (y > 0 && zone[(by + y - 1) * w + bx + x]) next[(y - 1) * bw + x] = true
                if (y < bh - 1 && zone[(by + y + 1) * w + bx + x]) next[(y + 1) * bw + x] = true
            }
        }
        reg = next
    }
    return BrushStroke(emptyList(), colorArgb, 0f, erase = false, fill = FillRegion.encode(reg, bw, bh, bx, by, unit))
}

private fun dilate(src: BooleanArray, w: Int, h: Int, r: Int): BooleanArray {
    var cur = src
    repeat(r) {
        val next = cur.copyOf()
        for (y in 0 until h) {
            val row = y * w
            for (x in 0 until w) {
                if (!cur[row + x]) continue
                if (x > 0) next[row + x - 1] = true
                if (x < w - 1) next[row + x + 1] = true
                if (y > 0) next[row - w + x] = true
                if (y < h - 1) next[row + w + x] = true
            }
        }
        cur = next
    }
    return cur
}

/** Cor de fundo do papel usada nas exportações e miniaturas. */
internal val PaperColor = Color.White
