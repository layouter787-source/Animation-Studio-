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

/** Uma fatia é "fina" se a sua espessura média (2×área/perímetro) for até isto, em células. */
private const val SLIVER_THICKNESS = 10f

/**
 * Balde de tinta rápido (tempo O(pixels), independente do tamanho da fresta):
 *  - referência = o que está visível no frame, em até ~2 milhões de células (memória baixa, sem estourar a RAM);
 *  - um pixel conta como tinta a partir da opacidade escolhida pelo utilizador ([StudioRender.inkPercent]);
 *  - tenta primeiro SEM fechar frestas (preenche tudo o que está ligado, incluindo riscos finos e zonas apagadas);
 *    só se o preenchimento "vazar" (cobrir quase a folha toda) tenta fechar frestas, cada vez maiores;
 *  - o fecho de frestas é morfológico (dilatar e erodir com somas de prefixos): não engrossa as linhas;
 *  - expansão: a região entra [growPx] POR BAIXO das linhas, mas só nos pixels de tinta reais (nunca para o outro lado);
 *  - bolsas e fatias: cantos minúsculos e tiras finas coladas ao preenchimento (a sobra onde o fim de um círculo
 *    não coincide com o início) juntam-se ao preenchimento; áreas grandes de outras regiões ficam de fora;
 *  - o preenchimento é guardado em modo "por trás" (mode = 2): a tinta das linhas fica sempre por cima.
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
    // Limiar de tinta escolhido pelo utilizador: 35% => alfa 89. Com 0% qualquer vestígio conta como tinta.
    val inkAlpha = (StudioRender.inkPercent.coerceIn(0, 100) * 255 / 100)
    val seed = px[sy * w + sx]
    val transparentSeed = (seed ushr 24) <= inkAlpha

    val sa = seed ushr 24
    val sr = (seed shr 16) and 0xFF
    val sg = (seed shr 8) and 0xFF
    val sb = seed and 0xFF
    val barrier = BooleanArray(w * h)
    var anyBarrier = false
    for (i in px.indices) {
        val p = px[i]
        val b = if (transparentSeed) {
            (p ushr 24) > inkAlpha
        } else {
            abs((p ushr 24) - sa) + abs(((p shr 16) and 0xFF) - sr) + abs(((p shr 8) and 0xFF) - sg) + abs((p and 0xFF) - sb) > 60
        }
        barrier[i] = b
        if (b) anyBarrier = true
    }

    val growCells = (growPx / unit).roundToInt().coerceIn(0, 12)
    val total = (w * h).toFloat()
    val stack = IntArray(w * h)

    /** Calcula a região com um fecho de frestas de [gapCells] células (0 = sem fecho). */
    fun regionFor(gapCells: Int): BooleanArray? {
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

        // Bolsas e fatias coladas ao preenchimento: componentes livres presas entre linhas. Entram no preenchimento
        // se forem minúsculas (cantos) ou finas (tiras de sobra). Se a componente cresce além do limite, é outra
        // região e fica de fora.
        val maxPocket = max(40, w * h / 20000)
        val sliverCap = max(maxPocket, w * h / 80)
        val near = dilate(visited, w, h, 3)
        val seen = BooleanArray(w * h)
        val pocket = IntArray(sliverCap + 1)
        val pocketCells = ArrayList<Int>()
        for (i0 in 0 until w * h) {
            if (!near[i0] || visited[i0] || barrier[i0] || seen[i0]) continue
            var count = 0
            var edgeCells = 0
            var overflow = false
            sp = 0
            stack[sp++] = i0
            seen[i0] = true
            while (sp > 0) {
                val i = stack[--sp]
                if (count < sliverCap) pocket[count] = i else overflow = true
                count++
                val x = i % w
                val y = i / w
                var edge = false
                if (x > 0) {
                    if (barrier[i - 1] || visited[i - 1]) edge = true else if (!seen[i - 1]) { seen[i - 1] = true; stack[sp++] = i - 1 }
                } else edge = true
                if (x < w - 1) {
                    if (barrier[i + 1] || visited[i + 1]) edge = true else if (!seen[i + 1]) { seen[i + 1] = true; stack[sp++] = i + 1 }
                } else edge = true
                if (y > 0) {
                    if (barrier[i - w] || visited[i - w]) edge = true else if (!seen[i - w]) { seen[i - w] = true; stack[sp++] = i - w }
                } else edge = true
                if (y < h - 1) {
                    if (barrier[i + w] || visited[i + w]) edge = true else if (!seen[i + w]) { seen[i + w] = true; stack[sp++] = i + w }
                } else edge = true
                if (edge) edgeCells++
            }
            if (overflow) continue
            val thickness = count * 2f / max(1, edgeCells)
            if (count <= maxPocket || thickness <= SLIVER_THICKNESS) for (k in 0 until count) pocketCells.add(pocket[k])
        }
        for (c in pocketCells) visited[c] = true

        // Expansão por baixo da tinta: só entra em pixels de tinta reais (barrier), nunca em células livres do outro lado
        // da linha. Assim a cor não sai do contorno e não fica fio de borda.
        return if (growCells > 0) {
            val grown = dilate(visited, w, h, growCells)
            BooleanArray(w * h) { visited[it] || (grown[it] && barrier[it]) }
        } else visited
    }

    fun coverage(r: BooleanArray): Float {
        var n = 0
        for (v in r) if (v) n++
        return n / total
    }

    // Frestas a tentar, da mais pequena para a maior. Sem fecho primeiro: preenche tudo o que está ligado.
    val userGap = (gapPx / unit).roundToInt().coerceIn(0, 24)
    val gaps = listOf(0, userGap, max(userGap * 2, 12), 24).map { it.coerceIn(0, 24) }.distinct().sorted()
    var region: BooleanArray? = null
    var firstTry: BooleanArray? = null
    for (g in gaps) {
        val r = regionFor(g) ?: return null
        if (firstTry == null) firstTry = r
        // Sem nenhuma linha na folha, preencher tudo é o pretendido; caso contrário, cobrir quase tudo é um vazamento.
        if (!anyBarrier || coverage(r) < 0.85f) { region = r; break }
    }
    // Se nenhum fecho conseguiu conter o preenchimento, fica com a versão exata (sem fecho).
    val finalRegion = region ?: firstTry ?: return null

    var minX = w
    var minY = h
    var maxX = -1
    var maxY = -1
    for (y in 0 until h) {
        val row = y * w
        for (x in 0 until w) {
            if (finalRegion[row + x]) {
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
        System.arraycopy(finalRegion, (minY + y) * w + minX, reg, y * bw, bw)
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
