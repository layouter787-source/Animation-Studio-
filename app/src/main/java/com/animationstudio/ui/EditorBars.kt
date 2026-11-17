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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.FitScreen
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Layers
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

/**
 * Medidas únicas da interface: todos os botões de ferramenta têm o mesmo tamanho e o mesmo espaçamento, nas barras
 * e nos painéis. Para mudar o tamanho dos botões de toda a app basta mudar [Cell].
 */
internal object StudioMetrics {
    /** Lado de cada botão quadrado (barras e painéis). */
    val Cell = 32.dp

    /** Espaço entre botões. */
    val Gap = 3.dp

    /** Margem interior das barras e dos cartões. */
    val Pad = 3.dp

    /** Altura dos botões com texto do painel da seleção (todos iguais). */
    val TextButtonHeight = 40.dp
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
    width: Dp = StudioMetrics.Cell,
    height: Dp = StudioMetrics.Cell,
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

/** Pega de seis pontos: arrastar para mudar a barra de sítio. [onEnd] recebe o deslocamento total (px). */
@Composable
private fun GripHandle(modifier: Modifier = Modifier, onEnd: (Float, Float) -> Unit) {
    val curEnd by rememberUpdatedState(onEnd)
    Box(
        modifier.pointerInput(Unit) {
            var tx = 0f
            var ty = 0f
            detectDragGestures(
                onDragStart = { tx = 0f; ty = 0f },
                onDragEnd = { curEnd(tx, ty) },
                onDragCancel = { },
            ) { change, drag ->
                change.consume()
                tx += drag.x
                ty += drag.y
            }
        },
        contentAlignment = Alignment.Center,
    ) { GripGlyph(StudioColors.InkDim) }
}

// ---------------------------------------------------------------------------------------------
// Barra da timeline, fina: [menu e "+" um abaixo do outro] | camadas e frames | [câmara e Studio um abaixo do outro].
// Todos os botões têm o tamanho [StudioMetrics.Cell]. Cor, onion, ajustar e referência estão no painel de ferramentas.
// ---------------------------------------------------------------------------------------------

@Composable
internal fun TopBar(
    st: EditorState,
    sw: Int,
    sh: Int,
    expanded: Boolean,
    onMenu: () -> Unit,
    onAdd: () -> Unit,
    onCamera: () -> Unit,
    onStudio: () -> Unit,
    onLayerOptions: (Layer) -> Unit,
    onMoveLayer: (Long, Int) -> Unit,
    onFrameOptions: (Int) -> Unit,
    onGripEnd: (Float, Float) -> Unit,
    modifier: Modifier = Modifier,
    /** Modo câmara ligado (realça o botão da câmara). */
    cameraActive: Boolean = false,
    /** Ids dos frames que têm keyframe de câmara (marcados com um losango âmbar). */
    cameraKeys: Set<Long> = emptySet(),
) {
    val all = st.project.layers.asReversed()
    val rows = if (expanded) all else all.take(2)
    val cellW = 44.dp
    val cellH = (44f * sh / sw).coerceIn(22f, 30f).dp
    val rowH = cellH + 3.dp
    val rowPx = with(LocalDensity.current) { rowH.toPx() }
    val curMove by rememberUpdatedState(onMoveLayer)
    // Altura de uma pilha de dois botões: a lista de camadas nunca fica mais baixa que ela.
    val stackH = StudioMetrics.Cell * 2 + StudioMetrics.Gap
    val hScroll = rememberScrollState()
    val vScroll = rememberScrollState()
    Row(
        modifier.fillMaxWidth().background(StudioColors.Navy).padding(StudioMetrics.Pad),
        horizontalArrangement = Arrangement.spacedBy(StudioMetrics.Gap),
        verticalAlignment = Alignment.Top,
    ) {
        GripHandle(Modifier.size(width = 14.dp, height = stackH), onGripEnd)
        Column(verticalArrangement = Arrangement.spacedBy(StudioMetrics.Gap)) {
            ToolCell(onClick = onMenu) { PanelGlyph(it) }
            ToolCell(onClick = onAdd) { Text("+", style = StudioType.Title.copy(color = it)) }
        }
        Row(
            Modifier.weight(1f).studioCard(12.dp)
                .heightIn(max = if (expanded) rowH * 5 + 6.dp else maxOf(rowH * 2 + 6.dp, stackH))
                .verticalScroll(vScroll).padding(3.dp)
        ) {
            Column(Modifier.width(104.dp)) {
                for (l in rows) {
                    val isActive = l.id == st.activeLayerId
                    val chipShape = RoundedCornerShape(8.dp)
                    // Sem pontos coloridos: a camada ativa distingue-se só pelo fundo e pelo contorno.
                    // Tocar numa camada que já está ativa abre as opções dela (visibilidade, opacidade, nome, apagar).
                    Row(
                        Modifier.height(rowH).padding(vertical = 1.dp, horizontal = 1.dp).fillMaxWidth().clip(chipShape)
                            .background(if (isActive) StudioColors.AccentSoft else StudioColors.Surface2)
                            .border(1.dp, if (isActive) StudioColors.Accent else StudioColors.Outline, chipShape)
                            .pointerInput(l.id) {
                                var acc = 0f
                                detectVerticalDragGestures(onDragStart = { acc = 0f }) { change, dy ->
                                    change.consume()
                                    acc += dy
                                    if (acc > rowPx) { curMove(l.id, -1); acc = 0f } else if (acc < -rowPx) { curMove(l.id, 1); acc = 0f }
                                }
                            }
                            .clickable { if (isActive) onLayerOptions(l) else st.activeLayerId = l.id }
                            .padding(horizontal = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            l.name, style = StudioType.Label.copy(fontSize = 11.sp), maxLines = 1, overflow = TextOverflow.Ellipsis,
                            textDecoration = if (l.visible) TextDecoration.None else TextDecoration.LineThrough,
                        )
                    }
                }
            }
            Box(Modifier.weight(1f).horizontalScroll(hScroll)) {
                Column {
                    for (l in rows) {
                        Row(Modifier.height(rowH), verticalAlignment = Alignment.CenterVertically) {
                            st.project.frameIds.forEachIndexed { i, fid ->
                                val strokes = l.drawingAt(fid)?.strokes
                                val isCurrent = i == st.project.currentFrame
                                val shape = RoundedCornerShape(6.dp)
                                // Tocar num frame que já está selecionado (na mesma camada) abre as opções do frame.
                                Box(
                                    Modifier.padding(horizontal = 1.5.dp).size(width = cellW, height = cellH).clip(shape)
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
                                        style = StudioType.Caption.copy(fontSize = 8.sp, color = if (strokes != null) Color(0xFF30384F) else StudioColors.InkDim),
                                        modifier = Modifier.align(Alignment.TopStart).padding(start = 3.dp, top = 1.dp),
                                    )
                                    if (fid in cameraKeys) {
                                        Box(
                                            Modifier.align(Alignment.BottomStart).padding(start = 5.dp, bottom = 4.dp)
                                                .size(7.dp).rotate(45f).background(StudioColors.Amber)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                // Pega de duração: fica no fim da exposição do frame atual. Arrastar para a direita estende a duração
                // (acrescenta frames que partilham o desenho); para a esquerda encurta.
                if (!st.playing) {
                    val proj = st.project
                    val endIdx = proj.holdEnd(proj.currentFrame)
                    val stepDp = cellW + 3.dp
                    val stepPx = with(LocalDensity.current) { stepDp.toPx() }
                    val activeRow = rows.indexOfFirst { it.id == st.activeLayerId }.coerceAtLeast(0)
                    Box(
                        Modifier.offset(x = stepDp * (endIdx + 1) - 11.dp, y = rowH * activeRow + (rowH - cellH) / 2f)
                            .size(width = 10.dp, height = cellH).clip(RoundedCornerShape(4.dp)).background(StudioColors.Accent)
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
                        Box(Modifier.size(width = 2.dp, height = cellH * 0.5f).background(Color.White.copy(alpha = 0.85f)))
                    }
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(StudioMetrics.Gap)) {
            ToolCell(selected = cameraActive, onClick = onCamera) { Icon(Icons.Default.Videocam, "Câmara", tint = it, modifier = Modifier.size(17.dp)) }
            ToolCell(onClick = onStudio) { Icon(Icons.Default.ShowChart, "Studio", tint = it, modifier = Modifier.size(17.dp)) }
        }
    }
}

@Composable
internal fun ExpandTab(expanded: Boolean, atBottom: Boolean, onSet: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val curExpanded by rememberUpdatedState(expanded)
    val curSet by rememberUpdatedState(onSet)
    val shape = if (atBottom) RoundedCornerShape(topStart = 9.dp, topEnd = 9.dp) else RoundedCornerShape(bottomStart = 9.dp, bottomEnd = 9.dp)
    Box(
        modifier.width(96.dp).height(14.dp).clip(shape).background(StudioColors.Surface).border(1.dp, StudioColors.Outline, shape)
            .pointerInput(atBottom) {
                detectVerticalDragGestures { change, dy ->
                    change.consume()
                    val expand = if (atBottom) dy < -4f else dy > 4f
                    val collapse = if (atBottom) dy > 4f else dy < -4f
                    if (expand) curSet(true) else if (collapse) curSet(false)
                }
            }
            .clickable { curSet(!curExpanded) },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.width(34.dp).height(3.dp).clip(RoundedCornerShape(2.dp)).background(StudioColors.InkDim))
    }
}

// ---------------------------------------------------------------------------------------------
// Painel de ferramentas: grelha de 2 colunas, todos os botões do mesmo tamanho ([StudioMetrics.Cell]):
//   pincel | borracha
//   laço | novo frame
//   desfazer | refazer
//   frame anterior | seguinte
//   reproduzir | cor
//   onion | ajustar à folha
//   referência
// ---------------------------------------------------------------------------------------------

/** Cor atual (círculo): toque abre a roda de cores; arrastar para a folha pinta a região onde se solta. */
@Composable
private fun SwatchCell(
    st: EditorState,
    onColor: (ColorMethod) -> Unit,
    onDrag: (Offset) -> Unit,
    onDrop: (Offset) -> Unit,
    onCancel: () -> Unit,
) {
    val curDrag by rememberUpdatedState(onDrag)
    val curDrop by rememberUpdatedState(onDrop)
    val curCancel by rememberUpdatedState(onCancel)
    var swatchRoot by remember { mutableStateOf(Offset.Zero) }
    CheckerBox(
        Color(st.colorArgb),
        Modifier.size(StudioMetrics.Cell).clip(CircleShape)
            .border(2.dp, StudioColors.Ink, CircleShape)
            .onGloballyPositioned { swatchRoot = it.positionInRoot() }
            .pointerInput(Unit) {
                var cur = Offset.Zero
                detectDragGestures(
                    onDragStart = { o -> cur = swatchRoot + o; curDrag(cur) },
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
internal fun ToolPanel(
    st: EditorState,
    onToolTap: (Tool) -> Unit,
    onToolLibrary: (Tool) -> Unit,
    onToolSettings: (Tool) -> Unit,
    onAddFrame: () -> Unit,
    onPlayLong: () -> Unit,
    onColor: (ColorMethod) -> Unit,
    onOnionOptions: () -> Unit,
    onFit: () -> Unit,
    onReference: () -> Unit,
    onReferenceLong: () -> Unit,
    refActive: Boolean,
    onSwatchDrag: (Offset) -> Unit,
    onSwatchDrop: (Offset) -> Unit,
    onSwatchCancel: () -> Unit,
    onGripEnd: (Float, Float) -> Unit,
) {
    Column(
        Modifier.width(80.dp).fillMaxHeight().verticalScroll(rememberScrollState()).padding(horizontal = 3.dp).padding(bottom = 4.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        GripHandle(Modifier.size(width = 40.dp, height = 16.dp), onGripEnd)
        Column(
            Modifier.studioCard(12.dp).padding(StudioMetrics.Pad),
            verticalArrangement = Arrangement.spacedBy(StudioMetrics.Gap),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(StudioMetrics.Gap)) {
                ToolCell(
                    selected = st.tool == Tool.BRUSH, onLongClick = { onToolLibrary(Tool.BRUSH) },
                    onDoubleClick = { onToolSettings(Tool.BRUSH) }, onClick = { onToolTap(Tool.BRUSH) },
                ) { Icon(Icons.Default.Brush, "Pincel", tint = it, modifier = Modifier.size(17.dp)) }
                ToolCell(
                    selected = st.tool == Tool.ERASER, onLongClick = { onToolLibrary(Tool.ERASER) },
                    onDoubleClick = { onToolSettings(Tool.ERASER) }, onClick = { onToolTap(Tool.ERASER) },
                ) { EraserGlyph(it, Modifier.size(17.dp)) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(StudioMetrics.Gap)) {
                ToolCell(selected = st.tool == Tool.LASSO, onClick = { onToolTap(Tool.LASSO) }) { LassoGlyph(it, Modifier.size(17.dp)) }
                ToolCell(onClick = onAddFrame) { Text("+", style = StudioType.Title.copy(fontSize = 18.sp, color = it)) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(StudioMetrics.Gap)) {
                ToolCell(enabled = st.history.canUndo && !st.playing, onClick = { st.undo() }) { Icon(Icons.Default.Undo, "Desfazer", tint = it, modifier = Modifier.size(17.dp)) }
                ToolCell(enabled = st.history.canRedo && !st.playing, onClick = { st.redo() }) { Icon(Icons.Default.Redo, "Refazer", tint = it, modifier = Modifier.size(17.dp)) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(StudioMetrics.Gap)) {
                ToolCell(enabled = st.project.currentFrame > 0 && !st.playing, onClick = { st.go(-1) }) { TriangleGlyph(it, true, Modifier.size(14.dp)) }
                ToolCell(enabled = st.project.currentFrame < st.project.frameCount - 1 && !st.playing, onClick = { st.go(1) }) { TriangleGlyph(it, false, Modifier.size(14.dp)) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(StudioMetrics.Gap), verticalAlignment = Alignment.CenterVertically) {
                // Reproduzir (toque longo: FPS e modo de reprodução).
                ToolCell(selected = st.playing, onLongClick = onPlayLong, onClick = { st.togglePlay() }) {
                    Icon(if (st.playing) Icons.Default.Pause else Icons.Default.PlayArrow, if (st.playing) "Parar" else "Reproduzir", tint = it, modifier = Modifier.size(19.dp))
                }
                SwatchCell(st, onColor, onSwatchDrag, onSwatchDrop, onSwatchCancel)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(StudioMetrics.Gap)) {
                // Onion: toque liga ou desliga; toque longo abre as opções.
                ToolCell(selected = st.onionOn, onLongClick = onOnionOptions, onClick = { st.onionOn = !st.onionOn }) {
                    Icon(Icons.Default.Layers, "Onion", tint = it, modifier = Modifier.size(17.dp))
                }
                ToolCell(onClick = onFit) { Icon(Icons.Default.FitScreen, "Ajustar à folha", tint = it, modifier = Modifier.size(17.dp)) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(StudioMetrics.Gap)) {
                // Janela de referência: toque mostra ou esconde; toque longo troca a imagem.
                ToolCell(selected = refActive, onLongClick = onReferenceLong, onClick = onReference) {
                    Icon(Icons.Default.Image, "Referência", tint = it, modifier = Modifier.size(17.dp))
                }
            }
        }
    }
}
