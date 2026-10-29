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
import kotlin.math.sqrt

/**
 * Balde de tinta rápido (tempo O(pixels), independente do tamanho da fresta):
 *  - referência = o que está visível no frame, em até ~2 milhões de células (memória baixa, sem estourar a RAM);
 *  - fecho de frestas por fecho morfológico (dilatar e erodir com somas de prefixos): as frestas fecham sem engrossar as linhas;
 *  - expansão: a região entra [growPx] POR BAIXO das linhas, mas só nos pixels de tinta reais (nunca para o outro lado);
 *  - bolsas: cantos minúsculos entre linhas que se cruzam (triângulos brancos) juntam-se ao preenchimento;
 *  - o preenchimento é guardado em modo "por trás" (mode = 2): a tinta das linhas fica sempre por cima e o fio
 *    semitransparente das bordas (por exemplo, a borda de uma zona apagada) fica coberto, sem "marca".
 * Chamar fora da thread principal. Devolve null se o ponto está fora da folha ou numa linha.
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
    val unit = max(1f, sqrt(sw.toFloat() * sh.toFloat() / 2_000_000f))
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

    val sa = seed ushr 24
    val sr = (seed shr 16) and 0xFF
    val sg = (seed shr 8) and 0xFF
    val sb = seed and 0xFF
    val barrier = BooleanArray(w * h)
    for (i in px.indices) {
        val p = px[i]
        barrier[i] = if (transparentSeed) {
            (p ushr 24) > 24
        } else {
            abs((p ushr 24) - sa) + abs(((p shr 16) and 0xFF) - sr) + abs(((p shr 8) and 0xFF) - sg) + abs((p and 0xFF) - sb) > 60
        }
    }

    val gapCells = (gapPx / unit).roundToInt().coerceIn(0, 24)
    val growCells = (growPx / unit).roundToInt().coerceIn(0, 12)
    // Fecho morfológico: tapa frestas até 2×gap sem engrossar as linhas.
    val closed = if (gapCells > 0) close(barrier, w, h, gapCells) else barrier
    // Se o toque caiu numa zona estreita que o fecho tapou, usa a referência original (sem fecho).
    val blocked = if (closed[sy * w + sx]) barrier else closed

    var start = sy * w + sx
    if (blocked[start]) {
        // O toque caiu mesmo em cima de uma linha: procura a célula livre mais próxima.
        var found = -1
        search@ for (r in 1..6) {
            for (dy in -r..r) {
                for (dx in -r..r) {
                    val x = sx + dx
                    val y = sy + dy
                    if (x < 0 || y < 0 || x >= w || y >= h) continue
                    if (!blocked[y * w + x]) { found = y * w + x; break@search }
                }
            }
        }
        if (found < 0) return null
        start = found
    }

    val visited = BooleanArray(w * h)
    val stack = IntArray(w * h)
    var sp = 0
    stack[sp++] = start
    visited[start] = true
    while (sp > 0) {
        val i = stack[--sp]
        val x = i % w
        val y = i / w
        if (x > 0 && !visited[i - 1] && !blocked[i - 1]) { visited[i - 1] = true; stack[sp++] = i - 1 }
        if (x < w - 1 && !visited[i + 1] && !blocked[i + 1]) { visited[i + 1] = true; stack[sp++] = i + 1 }
        if (y > 0 && !visited[i - w] && !blocked[i - w]) { visited[i - w] = true; stack[sp++] = i - w }
        if (y < h - 1 && !visited[i + w] && !blocked[i + w]) { visited[i + w] = true; stack[sp++] = i + w }
    }

    // Bolsas: células livres, coladas ao preenchimento, presas entre linhas e minúsculas (cantos de linhas que se
    // cruzam). Se a componente cresce além do limite, é outra região e fica de fora.
    val maxPocket = max(40, w * h / 20000)
    val near = dilate(visited, w, h, 3)
    val seen = BooleanArray(w * h)
    val pocket = IntArray(maxPocket + 1)
    val pocketCells = ArrayList<Int>()
    for (i0 in 0 until w * h) {
        if (!near[i0] || visited[i0] || barrier[i0] || seen[i0]) continue
        var count = 0
        var overflow = false
        sp = 0
        stack[sp++] = i0
        seen[i0] = true
        while (sp > 0) {
            val i = stack[--sp]
            if (count < maxPocket) pocket[count] = i else overflow = true
            count++
            val x = i % w
            val y = i / w
            if (x > 0 && !seen[i - 1] && !visited[i - 1] && !barrier[i - 1]) { seen[i - 1] = true; stack[sp++] = i - 1 }
            if (x < w - 1 && !seen[i + 1] && !visited[i + 1] && !barrier[i + 1]) { seen[i + 1] = true; stack[sp++] = i + 1 }
            if (y > 0 && !seen[i - w] && !visited[i - w] && !barrier[i - w]) { seen[i - w] = true; stack[sp++] = i - w }
            if (y < h - 1 && !seen[i + w] && !visited[i + w] && !barrier[i + w]) { seen[i + w] = true; stack[sp++] = i + w }
        }
        if (!overflow) for (k in 0 until count) pocketCells.add(pocket[k])
    }
    for (c in pocketCells) visited[c] = true

    // Expansão por baixo da tinta: só entra em pixels de tinta reais (barrier), nunca em células livres do outro lado
    // da linha. Assim a cor não sai do contorno e não fica fio de borda.
    val region: BooleanArray = if (growCells > 0) {
        val grown = dilate(visited, w, h, growCells)
        BooleanArray(w * h) { visited[it] || (grown[it] && barrier[it]) }
    } else visited

    var minX = w
    var minY = h
    var maxX = -1
    var maxY = -1
    for (y in 0 until h) {
        val row = y * w
        for (x in 0 until w) {
            if (region[row + x]) {
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
        System.arraycopy(region, (minY + y) * w + minX, reg, y * bw, bw)
    }
    // mode = 2: desenhado por trás da tinta existente na camada.
    return BrushStroke(emptyList(), colorArgb, 0f, erase = false, fill = FillRegion.encode(reg, bw, bh, minX, minY, unit), mode = 2)
}

/** Dilatação quadrada de raio [r] em O(n) (somas de prefixos por linha e por coluna). */
private fun dilate(src: BooleanArray, w: Int, h: Int, r: Int): BooleanArray {
    if (r <= 0) return src
    val tmp = BooleanArray(w * h)
    val pre = IntArray(max(w, h) + 1)
    for (y in 0 until h) {
        val row = y * w
        for (x in 0 until w) pre[x + 1] = pre[x] + (if (src[row + x]) 1 else 0)
        for (x in 0 until w) {
            val a = max(0, x - r)
            val b = min(w - 1, x + r)
            tmp[row + x] = pre[b + 1] - pre[a] > 0
        }
    }
    val out = BooleanArray(w * h)
    for (x in 0 until w) {
        for (y in 0 until h) pre[y + 1] = pre[y] + (if (tmp[y * w + x]) 1 else 0)
        for (y in 0 until h) {
            val a = max(0, y - r)
            val b = min(h - 1, y + r)
            out[y * w + x] = pre[b + 1] - pre[a] > 0
        }
    }
    return out
}

/** Fecho morfológico: dilatar e depois erodir (erodir = dilatar o complemento). */
private fun close(src: BooleanArray, w: Int, h: Int, r: Int): BooleanArray {
    val dilated = dilate(src, w, h, r)
    val inverted = BooleanArray(w * h) { !dilated[it] }
    val eroded = dilate(inverted, w, h, r)
    // Nas bordas da folha a erosão não deve apagar linhas: nunca fica menos que o original.
    return BooleanArray(w * h) { src[it] || !eroded[it] }
}

/** Cor de fundo do papel usada nas exportações e miniaturas. */
internal val PaperColor = Color.White
