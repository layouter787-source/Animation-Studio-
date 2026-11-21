package com.animationstudio.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.ControlPointDuplicate
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Redo
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.animationstudio.domain.Layer
import com.animationstudio.domain.extendHold
import com.animationstudio.domain.frameClip
import com.animationstudio.domain.holdEnd
import com.animationstudio.domain.insertFrameAt
import com.animationstudio.domain.shrinkHold

// ---------------------------------------------------------------------------------------------
// Interface v2 (esboço 3): barra única em baixo, com cantos arredondados.
//   Linha 1: CAMADAS + | FRAME x/y | fps | < ▶ > | duplicar, copiar, colar, novo à esq., novo à dir. |
//            desfazer, refazer | câmara | pincel, borracha, laço | tinta (círculo)
//   Linha 2: nome da camada + olho | frames (miniaturas e barras de duração) | célula tracejada "+"
// Botões soltos nos cantos de cima: menu, centrar a folha e rigging. A pílula em cima da barra estica a timeline.
// Todos os controlos têm a mesma altura (StudioDims.Cell) e todos os ícones o mesmo tamanho (StudioDims.Icon).
// ---------------------------------------------------------------------------------------------

private val NameW2 = 116.dp
private val FrameW2 = 46.dp

private fun EditorState.holdBy(steps: Int) {
    if (playing || steps == 0) return
    edit { p -> if (steps > 0) p.extendHold(p.currentFrame, steps) else p.shrinkHold(p.currentFrame, -steps) }
}

/** Botão solto sobre a folha (canto do ecrã): uma célula dentro de um cartão com brilho azul e cantos arredondados. */
@Composable
internal fun FloatingCell(
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onClick: () -> Unit,
    content: @Composable (Color) -> Unit,
) {
    Box(modifier.studioGlow(14.dp).padding(4.dp)) { ToolCell(selected = selected, onClick = onClick, content = content) }
}

/** Novo frame à esquerda/direita: contorno tracejado com "+" do lado onde entra e o frame existente do outro. */
@Composable
private fun FrameAddGlyph(tint: Color, left: Boolean, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val bw = w * 0.56f
        val bh = h * 0.78f
        val top = (h - bh) / 2f
        val sw = 1.6.dp.toPx()
        val r = CornerRadius(2.5.dp.toPx())
        val solidX = if (left) w - bw else 0f
        val dashX = if (left) 0f else w - bw
        drawRoundRect(tint, Offset(solidX, top), Size(bw, bh), r, Stroke(sw))
        drawRoundRect(
            tint, Offset(dashX, top), Size(bw, bh), r,
            Stroke(sw, pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 4f))),
        )
        val cx = dashX + bw / 2f
        val cy = h / 2f
        val arm = bw * 0.22f
        drawLine(tint, Offset(cx - arm, cy), Offset(cx + arm, cy), strokeWidth = sw)
        drawLine(tint, Offset(cx, cy - arm), Offset(cx, cy + arm), strokeWidth = sw)
    }
}

/** Tinta (círculo): toque abre as cores; arrastar para a folha pinta a região onde se solta. */
@Composable
private fun InkSwatch(
    st: EditorState,
    onColor: (ColorMethod) -> Unit,
    onSwatchDrag: (Offset) -> Unit,
    onSwatchDrop: (Offset) -> Unit,
    onSwatchCancel: () -> Unit,
) {
    val curDrag by rememberUpdatedState(onSwatchDrag)
    val curDrop by rememberUpdatedState(onSwatchDrop)
    val curCancel by rememberUpdatedState(onSwatchCancel)
    var root by remember { mutableStateOf(Offset.Zero) }
    CheckerBox(
        Color(st.colorArgb),
        Modifier.size(StudioDims.Cell).clip(CircleShape)
            .border(2.dp, StudioColors.Ink, CircleShape)
            .onGloballyPositioned { root = it.positionInRoot() }
            .pointerInput(Unit) {
                var cur = Offset.Zero
                detectDragGestures(
                    onDragStart = { o -> cur = root + o; curDrag(cur) },
                    onDragEnd = { curDrop(cur) },
                    onDragCancel = { curCancel() },
                ) { change, d ->
                    change.consume()
                    cur += d
                    curDrag(cur)
                }
            }
            .clickable { onColor(ColorMethod.DISC) }
    )
}

@Composable
internal fun StudioBottomBar(
    st: EditorState,
    sw: Int,
    sh: Int,
    fps: Int,
    expanded: Boolean,
    cameraActive: Boolean,
    cameraKeys: Set<Long>,
    onLayerMenu: () -> Unit,
    onFps: () -> Unit,
    onCamera: () -> Unit,
    onToolTap: (Tool) -> Unit,
    onToolLibrary: (Tool) -> Unit,
    onToolSettings: (Tool) -> Unit,
    onColor: (ColorMethod) -> Unit,
    onSwatchDrag: (Offset) -> Unit,
    onSwatchDrop: (Offset) -> Unit,
    onSwatchCancel: () -> Unit,
    onLayerOptions: (Layer) -> Unit,
    onMoveLayer: (Long, Int) -> Unit,
    onFrameOptions: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val gap = StudioDims.Gap
    val ic = Modifier.size(StudioDims.Icon)
    val proj = st.project
    val idle = !st.playing
    val cur = proj.currentFrame
    val count = proj.frameCount
    val scroll1 = rememberScrollState()
    Column(
        modifier.studioGlow(20.dp).padding(6.dp),
        verticalArrangement = Arrangement.spacedBy(gap),
    ) {
        Row(
            Modifier.fillMaxWidth().horizontalScroll(scroll1),
            horizontalArrangement = Arrangement.spacedBy(gap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ToolCell(width = NameW2, onClick = onLayerMenu) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("CAMADAS", style = StudioType.Label.copy(fontSize = 11.sp, color = it), maxLines = 1)
                    Icon(Icons.Default.Add, "Adicionar camada", tint = it, modifier = ic)
                }
            }
            ToolCell(width = 84.dp, onClick = {}) {
                Text("FRAME ${cur + 1}/$count", style = StudioType.Label.copy(fontSize = 11.sp, color = it), maxLines = 1)
            }
            ToolCell(width = 62.dp, onClick = onFps) {
                Text("$fps fps", style = StudioType.Label.copy(fontSize = 11.sp, color = it), maxLines = 1)
            }
            ToolCell(enabled = cur > 0 && idle, onClick = { st.go(-1) }) { TriangleGlyph(it, true, ic) }
            ToolCell(selected = st.playing, onLongClick = onFps, onClick = { st.togglePlay() }) {
                Icon(if (st.playing) Icons.Default.Pause else Icons.Default.PlayArrow, if (st.playing) "Parar" else "Reproduzir", tint = it, modifier = ic)
            }
            ToolCell(enabled = cur < count - 1 && idle, onClick = { st.go(1) }) { TriangleGlyph(it, false, ic) }

            ToolCell(onClick = { if (idle) st.edit { p -> p.duplicateCurrentFrame() } }) {
                Icon(Icons.Default.ControlPointDuplicate, "Duplicar frame", tint = it, modifier = ic)
            }
            ToolCell(onClick = { st.clip = st.project.frameClip(st.project.currentFrame) }) {
                Icon(Icons.Default.ContentCopy, "Copiar frame", tint = it, modifier = ic)
            }
            ToolCell(enabled = st.clip != null && idle, onClick = {
                val c = st.clip
                if (c != null && idle) st.edit { p -> p.insertFrameAt(p.currentFrame + 1, c) }
            }) { Icon(Icons.Default.ContentPaste, "Colar frame", tint = it, modifier = ic) }
            ToolCell(onClick = { if (idle) st.edit { p -> p.insertFrameAt(p.currentFrame) } }) { FrameAddGlyph(it, true, ic) }
            ToolCell(onClick = { if (idle) st.edit { p -> p.insertFrameAt(p.currentFrame + 1) } }) { FrameAddGlyph(it, false, ic) }

            ToolCell(enabled = st.history.canUndo && idle, onClick = { st.undo() }) { Icon(Icons.Default.Undo, "Desfazer", tint = it, modifier = ic) }
            ToolCell(enabled = st.history.canRedo && idle, onClick = { st.redo() }) { Icon(Icons.Default.Redo, "Refazer", tint = it, modifier = ic) }

            ToolCell(selected = cameraActive, onClick = onCamera) { Icon(Icons.Default.Videocam, "Câmara", tint = it, modifier = ic) }

            ToolCell(
                selected = st.tool == Tool.BRUSH, onLongClick = { onToolLibrary(Tool.BRUSH) },
                onDoubleClick = { onToolSettings(Tool.BRUSH) }, onClick = { onToolTap(Tool.BRUSH) },
            ) { Icon(Icons.Default.Brush, "Pincel", tint = it, modifier = ic) }
            ToolCell(
                selected = st.tool == Tool.ERASER, onLongClick = { onToolLibrary(Tool.ERASER) },
                onDoubleClick = { onToolSettings(Tool.ERASER) }, onClick = { onToolTap(Tool.ERASER) },
            ) { EraserGlyph(it, ic) }
            ToolCell(selected = st.tool == Tool.LASSO, onClick = { onToolTap(Tool.LASSO) }) { LassoGlyph(it, ic) }
            InkSwatch(st, onColor, onSwatchDrag, onSwatchDrop, onSwatchCancel)
        }
        Layers2(st, sw, sh, expanded, cameraKeys, onLayerOptions, onMoveLayer, onFrameOptions)
    }
}

@Composable
private fun Layers2(
    st: EditorState,
    sw: Int,
    sh: Int,
    expanded: Boolean,
    cameraKeys: Set<Long>,
    onLayerOptions: (Layer) -> Unit,
    onMoveLayer: (Long, Int) -> Unit,
    onFrameOptions: (Int) -> Unit,
) {
    val all = st.project.layers.asReversed()
    // Fechada mostra as duas camadas de cima (camada e cenário); esticada (pílula) mostra todas.
    val rows = if (expanded) all else all.take(2)
    val cellH = StudioDims.Cell
    val gap = StudioDims.Gap
    val rowH = cellH + gap
    val rowPx = with(LocalDensity.current) { rowH.toPx() }
    val curMove by rememberUpdatedState(onMoveLayer)
    val hScroll = rememberScrollState()
    val vScroll = rememberScrollState()
    val curFrame = st.project.currentFrame
    val frameCount = st.project.frameCount
    val step = FrameW2 + 3.dp
    val stepPx = with(LocalDensity.current) { step.toPx() }
    var viewport by remember { mutableIntStateOf(0) }
    // Quando os frames já não cabem, a janela desloca-se para o frame atual ficar sempre à vista.
    LaunchedEffect(curFrame, frameCount, viewport) {
        val left = curFrame * stepPx
        val right = left + stepPx
        val v = hScroll.value
        if (left < v) hScroll.animateScrollTo(left.toInt().coerceAtLeast(0))
        else if (viewport > 0 && right > v + viewport) hScroll.animateScrollTo((right - viewport).toInt().coerceAtLeast(0))
    }
    val addCell: @Composable () -> Unit = {
        val dash = StudioColors.Accent
        Box(
            Modifier.padding(horizontal = 1.5.dp).size(width = FrameW2, height = cellH)
                .drawBehind {
                    drawRoundRect(
                        dash, cornerRadius = CornerRadius(9.dp.toPx()),
                        style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))),
                    )
                }
                .clip(RoundedCornerShape(9.dp))
                .clickable { if (!st.playing) st.edit { p -> p.insertFrameAt(p.frameCount) } },
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Default.Add, "Novo frame", tint = StudioColors.Ink, modifier = Modifier.size(StudioDims.Icon)) }
    }
    Row(
        Modifier.fillMaxWidth().heightIn(max = rowH * 5).verticalScroll(vScroll),
        horizontalArrangement = Arrangement.spacedBy(gap),
    ) {
        Column(Modifier.width(NameW2), verticalArrangement = Arrangement.spacedBy(gap)) {
            for (l in rows) {
                val isActive = l.id == st.activeLayerId
                val shape = RoundedCornerShape(10.dp)
                // A camada ativa distingue-se pelo contorno. Tocar numa camada já ativa abre as opções dela.
                Row(
                    Modifier.height(cellH).fillMaxWidth().clip(shape)
                        .background(if (isActive) StudioColors.AccentSoft else StudioColors.Surface2)
                        .border(1.dp, if (isActive) StudioColors.Accent else StudioColors.Outline, shape)
                        .pointerInput(l.id) {
                            var acc = 0f
                            detectVerticalDragGestures(onDragStart = { acc = 0f }) { change, dy ->
                                change.consume()
                                acc += dy
                                if (acc > rowPx) { curMove(l.id, -1); acc = 0f } else if (acc < -rowPx) { curMove(l.id, 1); acc = 0f }
                            }
                        }
                        .clickable { if (isActive) onLayerOptions(l) else st.activeLayerId = l.id }
                        .padding(start = 10.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        l.name.uppercase(), style = StudioType.Label.copy(fontSize = 11.sp),
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                    )
                    Box(
                        Modifier.size(28.dp).clip(CircleShape).clickable { st.edit { p -> p.toggleLayerVisible(l.id) } },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            if (l.visible) Icons.Default.Visibility else Icons.Default.VisibilityOff, "Visibilidade",
                            tint = StudioColors.Ink, modifier = Modifier.size(StudioDims.Icon),
                        )
                    }
                }
            }
        }
        Box(Modifier.weight(1f).onSizeChanged { viewport = it.width }.horizontalScroll(hScroll)) {
            Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                rows.forEachIndexed { idx, l ->
                    FrameRow2(st, l, sw, sh, cellH, cameraKeys, onFrameOptions, if (idx == 0) addCell else null)
                }
            }
            // Pega de duração: no fim da exposição do frame atual. Arrastar para a direita estende a duração
            // (frames que partilham o desenho); para a esquerda encurta.
            if (!st.playing) {
                val proj = st.project
                val endIdx = proj.holdEnd(proj.currentFrame)
                val activeRow = rows.indexOfFirst { it.id == st.activeLayerId }.coerceAtLeast(0)
                Box(
                    Modifier.offset(x = step * (endIdx + 1) - 12.dp, y = rowH * activeRow)
                        .size(width = 10.dp, height = cellH).clip(RoundedCornerShape(5.dp)).background(StudioColors.Accent)
                        .pointerInput(stepPx) {
                            var acc = 0f
                            detectHorizontalDragGestures(onDragStart = { acc = 0f }) { change, dx ->
                                change.consume()
                                acc += dx
                                while (acc >= stepPx) { st.holdBy(1); acc -= stepPx }
                                while (acc <= -stepPx) { st.holdBy(-1); acc += stepPx }
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(Modifier.size(width = 2.dp, height = cellH * 0.45f).background(Color.White.copy(alpha = 0.9f)))
                }
            }
        }
    }
}

/** Uma linha de frames (uma camada): miniatura e número, todas as células com a mesma altura. */
@Composable
private fun FrameRow2(
    st: EditorState,
    l: Layer,
    sw: Int,
    sh: Int,
    cellH: Dp,
    cameraKeys: Set<Long>,
    onFrameOptions: (Int) -> Unit,
    trailing: (@Composable () -> Unit)?,
) {
    Row(Modifier.height(cellH), verticalAlignment = Alignment.CenterVertically) {
        st.project.frameIds.forEachIndexed { i, fid ->
            val strokes = l.drawingAt(fid)?.strokes
            val isCurrent = i == st.project.currentFrame
            val shape = RoundedCornerShape(9.dp)
            // Tocar num frame que já está selecionado (na mesma camada) abre as opções do frame.
            Box(
                Modifier.padding(horizontal = 1.5.dp).size(width = FrameW2, height = cellH).clip(shape)
                    .background(if (strokes != null) Color.White else StudioColors.Surface2)
                    .border(if (isCurrent) 2.dp else 1.dp, if (isCurrent) StudioColors.Accent else StudioColors.Outline, shape)
                    .clickable {
                        if (isCurrent && st.activeLayerId == l.id) {
                            onFrameOptions(i)
                        } else {
                            st.activeLayerId = l.id
                            st.selectFrame(i)
                        }
                    },
            ) {
                if (strokes != null) {
                    Canvas(Modifier.fillMaxSize()) { drawThumbnail(strokes, sw.toFloat(), sh.toFloat()) }
                }
                Text(
                    "${i + 1}",
                    style = StudioType.Label.copy(
                        fontSize = 13.sp,
                        color = if (strokes != null) Color(0xFF30384F) else StudioColors.Ink,
                    ),
                    modifier = Modifier.align(Alignment.CenterStart).padding(start = 9.dp),
                )
                if (fid in cameraKeys) {
                    Box(
                        Modifier.align(Alignment.BottomStart).padding(start = 5.dp, bottom = 3.dp)
                            .size(6.dp).rotate(45f).background(StudioColors.Amber)
                    )
                }
            }
        }
        trailing?.invoke()
    }
}
