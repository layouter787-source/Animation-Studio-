package com.animationstudio.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.FitScreen
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Redo
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.animationstudio.domain.Layer
import com.animationstudio.domain.extendHold
import com.animationstudio.domain.holdEnd
import com.animationstudio.domain.shrinkHold

/** Barra flutuante do estúdio: fundo azul escuro, contorno com brilho azul e cantos arredondados. */
internal fun Modifier.studioGlow(radius: Dp = 16.dp): Modifier {
    val shape = RoundedCornerShape(radius)
    return this.shadow(10.dp, shape, ambientColor = StudioColors.Accent, spotColor = StudioColors.Accent)
        .clip(shape).background(StudioColors.Navy)
        .border(
            1.dp,
            androidx.compose.ui.graphics.Brush.verticalGradient(listOf(StudioColors.Accent.copy(alpha = 0.6f), StudioColors.Outline)),
            shape,
        )
}

/** Cartão do estúdio: superfície azul escura com contorno fino e cantos arredondados. */
internal fun Modifier.studioCard(radius: Dp = 14.dp): Modifier {
    val shape = RoundedCornerShape(radius)
    return this.clip(shape).background(StudioColors.Surface).border(1.dp, StudioColors.Outline, shape)
}

/**
 * Estende ([steps] > 0) ou encurta ([steps] < 0) a duração do frame atual: frames que partilham o mesmo desenho.
 * Cada passo é um passo no histórico (desfazer volta um frame).
 */
private fun EditorState.holdSteps(steps: Int) {
    if (playing || steps == 0) return
    edit { p -> if (steps > 0) p.extendHold(p.currentFrame, steps) else p.shrinkHold(p.currentFrame, -steps) }
}

@Composable
internal fun ToolCell(
    modifier: Modifier = Modifier,
    width: Dp = StudioDims.Cell,
    height: Dp = StudioDims.Cell,
    selected: Boolean = false,
    enabled: Boolean = true,
    onLongClick: (() -> Unit)? = null,
    onDoubleClick: (() -> Unit)? = null,
    onClick: () -> Unit,
    content: @Composable (Color) -> Unit,
) {
    val tint = if (selected) Color.White else if (enabled) StudioColors.Ink else StudioColors.InkDim.copy(alpha = 0.5f)
    val shape = RoundedCornerShape(9.dp)
    Box(
        modifier.size(width = width, height = height).clip(shape)
            .background(if (selected) StudioColors.Accent else StudioColors.Surface2)
            .border(1.dp, if (selected) StudioColors.Accent else StudioColors.Outline, shape)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick, onDoubleClick = onDoubleClick),
        contentAlignment = Alignment.Center,
    ) { content(tint) }
}


// ---------------------------------------------------------------------------------------------
// Barra da timeline, como no esboço. Todos os controlos têm a mesma altura (StudioDims.Cell) e todos os ícones o
// mesmo tamanho (StudioDims.Icon); as duas linhas alinham-se com as duas linhas da coluna da direita.
//   [+] | Camada 1  [frame] | câmara  Studio  ajustar
//       | Cenário 1 [frame] | cor  ⋮  Onion
// ---------------------------------------------------------------------------------------------

private val NameW = 116.dp
private val FrameW = 46.dp
private val IconW = StudioDims.Cell

@Composable
internal fun TopBar(
    st: EditorState,
    sw: Int,
    sh: Int,
    expanded: Boolean,
    onAdd: () -> Unit,
    onCamera: () -> Unit,
    onStudio: () -> Unit,
    onFit: () -> Unit,
    onColor: (ColorMethod) -> Unit,
    onOnionOptions: () -> Unit,
    onLayerOptions: (Layer) -> Unit,
    onMoveLayer: (Long, Int) -> Unit,
    onFrameOptions: (Int) -> Unit,
    onSwatchDrag: (Offset) -> Unit,
    onSwatchDrop: (Offset) -> Unit,
    onSwatchCancel: () -> Unit,
    modifier: Modifier = Modifier,
    /** Modo câmara ligado (realça o botão da câmara). */
    cameraActive: Boolean = false,
    /** Ids dos frames que têm keyframe de câmara (marcados com um losango âmbar). */
    cameraKeys: Set<Long> = emptySet(),
) {
    Row(
        modifier.studioGlow(16.dp).padding(5.dp),
        horizontalArrangement = Arrangement.spacedBy(StudioDims.Gap),
        verticalAlignment = Alignment.Top,
    ) {
        ToolCell(width = 40.dp, onClick = onAdd) { Icon(Icons.Default.Add, "Adicionar", tint = it, modifier = Modifier.size(StudioDims.Icon)) }
        TopBarLayers(st, sw, sh, expanded, onLayerOptions, onMoveLayer, onFrameOptions, cameraKeys)
        TopBarRight(st, cameraActive, onCamera, onStudio, onFit, onColor, onOnionOptions, onSwatchDrag, onSwatchDrop, onSwatchCancel)
    }
}

@Composable
private fun TopBarLayers(
    st: EditorState,
    sw: Int,
    sh: Int,
    expanded: Boolean,
    onLayerOptions: (Layer) -> Unit,
    onMoveLayer: (Long, Int) -> Unit,
    onFrameOptions: (Int) -> Unit,
    cameraKeys: Set<Long>,
) {
    val all = st.project.layers.asReversed()
    val rows = if (expanded) all else all.take(2)
    val cellH = StudioDims.Cell
    val gap = StudioDims.Gap
    val rowH = cellH + gap
    val rowPx = with(LocalDensity.current) { rowH.toPx() }
    val curMove by rememberUpdatedState(onMoveLayer)
    val hScroll = rememberScrollState()
    val vScroll = rememberScrollState()
    val curFrame = st.project.currentFrame
    val step = FrameW + 3.dp
    val stepPx = with(LocalDensity.current) { step.toPx() }
    // Fechada: a janela mostra só o frame atual. Aberta: cerca de 5 frames, com o atual a aparecer cedo.
    val visible = if (expanded) 5 else 1
    androidx.compose.runtime.LaunchedEffect(curFrame, expanded) {
        val target = (curFrame * stepPx - (if (expanded) 2 * stepPx else 0f)).toInt().coerceAtLeast(0)
        hScroll.animateScrollTo(target.coerceAtMost(hScroll.maxValue))
    }
    Row(
        Modifier.heightIn(max = rowH * 5).verticalScroll(vScroll),
        horizontalArrangement = Arrangement.spacedBy(gap),
    ) {
        Column(Modifier.width(NameW), verticalArrangement = Arrangement.spacedBy(gap)) {
            for (l in rows) {
                val isActive = l.id == st.activeLayerId
                val shape = RoundedCornerShape(9.dp)
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
                        .padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        l.name, style = StudioType.Label.copy(fontSize = 13.sp), maxLines = 1, overflow = TextOverflow.Ellipsis,
                        textDecoration = if (l.visible) TextDecoration.None else TextDecoration.LineThrough,
                    )
                }
            }
        }
        Box(Modifier.widthIn(max = step * visible).horizontalScroll(hScroll)) {
            Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                for (l in rows) FrameRow(st, l, sw, sh, cellH, cameraKeys, onFrameOptions)
            }
            // Pega de duração: no fim da exposição do frame atual (dentro da janela, se a janela for curta).
            // Arrastar para a direita estende a duração (frames que partilham o desenho); para a esquerda encurta.
            if (!st.playing) {
                val proj = st.project
                val endIdx = proj.holdEnd(proj.currentFrame)
                val shownEnd = if (expanded) endIdx else minOf(endIdx, proj.currentFrame)
                val activeRow = rows.indexOfFirst { it.id == st.activeLayerId }.coerceAtLeast(0)
                Box(
                    Modifier.offset(x = step * (shownEnd + 1) - 12.dp, y = rowH * activeRow)
                        .size(width = 10.dp, height = cellH).clip(RoundedCornerShape(5.dp)).background(StudioColors.Accent)
                        .pointerInput(stepPx) {
                            var acc = 0f
                            detectHorizontalDragGestures(onDragStart = { acc = 0f }) { change, dx ->
                                change.consume()
                                acc += dx
                                while (acc >= stepPx) { st.holdSteps(1); acc -= stepPx }
                                while (acc <= -stepPx) { st.holdSteps(-1); acc += stepPx }
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

/** Uma linha de frames (uma camada): células com miniatura e número, todas com a mesma altura. */
@Composable
private fun FrameRow(
    st: EditorState,
    l: Layer,
    sw: Int,
    sh: Int,
    cellH: Dp,
    cameraKeys: Set<Long>,
    onFrameOptions: (Int) -> Unit,
) {
    Row(Modifier.height(cellH), verticalAlignment = Alignment.CenterVertically) {
        st.project.frameIds.forEachIndexed { i, fid ->
            val strokes = l.drawingAt(fid)?.strokes
            val isCurrent = i == st.project.currentFrame
            val shape = RoundedCornerShape(9.dp)
            // Tocar num frame que já está selecionado (na mesma camada) abre as opções do frame.
            Box(
                Modifier.padding(horizontal = 1.5.dp).size(width = FrameW, height = cellH).clip(shape)
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
    }
}

@Composable
private fun TopBarRight(
    st: EditorState,
    cameraActive: Boolean,
    onCamera: () -> Unit,
    onStudio: () -> Unit,
    onFit: () -> Unit,
    onColor: (ColorMethod) -> Unit,
    onOnionOptions: () -> Unit,
    onSwatchDrag: (Offset) -> Unit,
    onSwatchDrop: (Offset) -> Unit,
    onSwatchCancel: () -> Unit,
) {
    val curSwDrag by rememberUpdatedState(onSwatchDrag)
    val curSwDrop by rememberUpdatedState(onSwatchDrop)
    val curSwCancel by rememberUpdatedState(onSwatchCancel)
    var swatchRoot by remember { mutableStateOf(Offset.Zero) }
    val gap = StudioDims.Gap
    Column(verticalArrangement = Arrangement.spacedBy(gap)) {
        // Câmara, Studio (curvas; em breve) e ajustar à folha.
        Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
            ToolCell(selected = cameraActive, onClick = onCamera) { Icon(Icons.Default.Videocam, "Câmara", tint = it, modifier = Modifier.size(StudioDims.Icon)) }
            ToolCell(onClick = onStudio) { Icon(Icons.Default.ShowChart, "Studio", tint = it, modifier = Modifier.size(StudioDims.Icon)) }
            ToolCell(onClick = onFit) { Icon(Icons.Default.FitScreen, "Ajustar à folha", tint = it, modifier = Modifier.size(StudioDims.Icon)) }
        }
        // Cor (círculo, por baixo da câmara): toque abre a roda de cores; arrastar para a folha pinta a região onde se
        // solta. Os três pontos abrem as opções do onion; "Onion" liga e desliga.
        Row(horizontalArrangement = Arrangement.spacedBy(gap), verticalAlignment = Alignment.CenterVertically) {
            CheckerBox(
                Color(st.colorArgb),
                Modifier.size(StudioDims.Cell).clip(CircleShape)
                    .border(2.dp, StudioColors.Ink, CircleShape)
                    .onGloballyPositioned { swatchRoot = it.positionInRoot() }
                    .pointerInput(Unit) {
                        var cur = Offset.Zero
                        detectDragGestures(
                            onDragStart = { o -> cur = swatchRoot + o; curSwDrag(cur) },
                            onDragEnd = { curSwDrop(cur) },
                            onDragCancel = { curSwCancel() },
                        ) { change, d ->
                            change.consume()
                            cur += d
                            curSwDrag(cur)
                        }
                    }
                    .clickable { onColor(ColorMethod.DISC) }
            )
            Row(Modifier.width(IconW * 2 + gap), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(width = 16.dp, height = StudioDims.Cell).clickable(onClick = onOnionOptions), contentAlignment = Alignment.Center) {
                    DotsGlyph(StudioColors.Ink, Modifier.size(StudioDims.Icon - 4.dp))
                }
                val shape = RoundedCornerShape(9.dp)
                Box(
                    Modifier.weight(1f).height(StudioDims.Cell).clip(shape)
                        .background(if (st.onionOn) StudioColors.Accent else StudioColors.Surface2)
                        .border(1.dp, if (st.onionOn) StudioColors.Accent else StudioColors.Outline, shape)
                        .clickable { st.onionOn = !st.onionOn },
                    contentAlignment = Alignment.Center,
                ) { Text("Onion", style = StudioType.Label.copy(fontSize = 13.sp)) }
            }
        }
    }
}

/** Pílula por baixo (ou por cima) da barra: arrastar ou tocar abre/fecha as camadas; toque longo abre o menu. */
@Composable
internal fun ExpandTab(
    expanded: Boolean,
    atBottom: Boolean,
    onSet: (Boolean) -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val curExpanded by rememberUpdatedState(expanded)
    val curSet by rememberUpdatedState(onSet)
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier.width(120.dp).height(24.dp).clip(shape).background(StudioColors.Navy).border(1.dp, StudioColors.Outline, shape)
            .pointerInput(atBottom) {
                detectVerticalDragGestures { change, dy ->
                    change.consume()
                    val expand = if (atBottom) dy < -4f else dy > 4f
                    val collapse = if (atBottom) dy > 4f else dy < -4f
                    if (expand) curSet(true) else if (collapse) curSet(false)
                }
            }
            .combinedClickable(onClick = { curSet(!curExpanded) }, onLongClick = onLongPress),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.width(36.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(Color(0xFF6E96FF)))
    }
}

// ---------------------------------------------------------------------------------------------
// Painel de ferramentas (grelha de 2 colunas), como no esboço:
//   pincel | borracha / laço | novo frame / desfazer | refazer / reproduzir / frame anterior | seguinte
// ---------------------------------------------------------------------------------------------

@Composable
internal fun ToolPanel(
    st: EditorState,
    onToolTap: (Tool) -> Unit,
    onToolLibrary: (Tool) -> Unit,
    onToolSettings: (Tool) -> Unit,
    onAddFrame: () -> Unit,
    onPlayLong: () -> Unit,
) {
    val gap = StudioDims.Gap
    // A margem de fora deixa espaço ao brilho do contorno, que o contentor da barra cortaria.
    Column(Modifier.padding(8.dp)) {
        Column(
            Modifier.studioGlow(16.dp).padding(5.dp),
            verticalArrangement = Arrangement.spacedBy(gap),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                ToolCell(
                    selected = st.tool == Tool.BRUSH, onLongClick = { onToolLibrary(Tool.BRUSH) },
                    onDoubleClick = { onToolSettings(Tool.BRUSH) }, onClick = { onToolTap(Tool.BRUSH) },
                ) { Icon(Icons.Default.Brush, "Pincel", tint = it, modifier = Modifier.size(StudioDims.Icon)) }
                ToolCell(
                    selected = st.tool == Tool.ERASER, onLongClick = { onToolLibrary(Tool.ERASER) },
                    onDoubleClick = { onToolSettings(Tool.ERASER) }, onClick = { onToolTap(Tool.ERASER) },
                ) { EraserGlyph(it, Modifier.size(StudioDims.Icon)) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                ToolCell(selected = st.tool == Tool.LASSO, onClick = { onToolTap(Tool.LASSO) }) { LassoGlyph(it, Modifier.size(StudioDims.Icon)) }
                ToolCell(onClick = onAddFrame) { Icon(Icons.Default.Add, "Novo frame", tint = it, modifier = Modifier.size(StudioDims.Icon)) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                ToolCell(enabled = st.history.canUndo && !st.playing, onClick = { st.undo() }) { Icon(Icons.Default.Undo, "Desfazer", tint = it, modifier = Modifier.size(StudioDims.Icon)) }
                ToolCell(enabled = st.history.canRedo && !st.playing, onClick = { st.redo() }) { Icon(Icons.Default.Redo, "Refazer", tint = it, modifier = Modifier.size(StudioDims.Icon)) }
            }
            // Reproduzir (toque longo: FPS e modo de reprodução).
            ToolCell(width = StudioDims.Cell * 2 + gap, selected = st.playing, onLongClick = onPlayLong, onClick = { st.togglePlay() }) {
                Icon(if (st.playing) Icons.Default.Pause else Icons.Default.PlayArrow, if (st.playing) "Parar" else "Reproduzir", tint = it, modifier = Modifier.size(StudioDims.Icon))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                ToolCell(enabled = st.project.currentFrame > 0 && !st.playing, onClick = { st.go(-1) }) { TriangleGlyph(it, true, Modifier.size(StudioDims.Icon)) }
                ToolCell(enabled = st.project.currentFrame < st.project.frameCount - 1 && !st.playing, onClick = { st.go(1) }) { TriangleGlyph(it, false, Modifier.size(StudioDims.Icon)) }
            }
        }
    }
}
