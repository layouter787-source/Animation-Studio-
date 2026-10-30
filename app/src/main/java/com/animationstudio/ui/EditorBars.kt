package com.animationstudio.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.FormatColorFill
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Redo
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
import com.animationstudio.domain.LayerKind

/** Cartão do estúdio: superfície azul escura com contorno fino e cantos arredondados. */
internal fun Modifier.studioCard(radius: Dp = 14.dp): Modifier {
    val shape = RoundedCornerShape(radius)
    return this.clip(shape).background(StudioColors.Surface).border(1.dp, StudioColors.Outline, shape)
}

@Composable
internal fun ToolCell(
    modifier: Modifier = Modifier,
    width: Dp = 32.dp,
    height: Dp = 32.dp,
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
// Barra da timeline: pega + menu + "+" | camadas, cenários e frames | câmara, cor, onion
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
    onColor: (ColorMethod) -> Unit,
    onOnionOptions: () -> Unit,
    onLayerOptions: (Layer) -> Unit,
    onMoveLayer: (Long, Int) -> Unit,
    onFrameOptions: (Int) -> Unit,
    onGripEnd: (Float, Float) -> Unit,
    onSwatchDrag: (Offset) -> Unit,
    onSwatchDrop: (Offset) -> Unit,
    onSwatchCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val all = st.project.layers.asReversed()
    val rows = if (expanded) all else all.take(2)
    val cellW = 44.dp
    val cellH = (44f * sh / sw).coerceIn(22f, 30f).dp
    val rowH = cellH + 3.dp
    val rowPx = with(LocalDensity.current) { rowH.toPx() }
    val curMove by rememberUpdatedState(onMoveLayer)
    val curSwDrag by rememberUpdatedState(onSwatchDrag)
    val curSwDrop by rememberUpdatedState(onSwatchDrop)
    val curSwCancel by rememberUpdatedState(onSwatchCancel)
    var swatchRoot by remember { mutableStateOf(Offset.Zero) }
    val hScroll = rememberScrollState()
    val vScroll = rememberScrollState()
    Row(
        modifier.fillMaxWidth().background(StudioColors.Navy).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                GripHandle(Modifier.size(width = 16.dp, height = 30.dp), onGripEnd)
                ToolCell(width = 30.dp, height = 30.dp, onClick = onMenu) { PanelGlyph(it) }
            }
            Row {
                Spacer(Modifier.width(16.dp))
                ToolCell(width = 30.dp, height = 30.dp, onClick = onAdd) { Text("+", style = StudioType.Title.copy(color = it)) }
            }
        }
        Row(
            Modifier.weight(1f).studioCard(12.dp).heightIn(max = if (expanded) rowH * 5 + 6.dp else rowH * 2 + 6.dp)
                .verticalScroll(vScroll).padding(3.dp)
        ) {
            Column(Modifier.width(104.dp)) {
                for (l in rows) {
                    val isActive = l.id == st.activeLayerId
                    val chipShape = RoundedCornerShape(8.dp)
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
                            .combinedClickable(onClick = { st.activeLayerId = l.id }, onLongClick = { onLayerOptions(l) })
                            .padding(horizontal = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier.size(7.dp).clip(RoundedCornerShape(4.dp))
                                .background(if (l.kind == LayerKind.SCENERY) StudioColors.Mint else StudioColors.Accent)
                        )
                        Spacer(Modifier.width(5.dp))
                        Text(
                            l.name, style = StudioType.Label.copy(fontSize = 11.sp), maxLines = 1, overflow = TextOverflow.Ellipsis,
                            textDecoration = if (l.visible) TextDecoration.None else TextDecoration.LineThrough,
                        )
                    }
                }
            }
            Column(Modifier.weight(1f).horizontalScroll(hScroll)) {
                for (l in rows) {
                    Row(Modifier.height(rowH), verticalAlignment = Alignment.CenterVertically) {
                        st.project.frameIds.forEachIndexed { i, fid ->
                            val strokes = l.drawingAt(fid)?.strokes
                            val isCurrent = i == st.project.currentFrame
                            val shape = RoundedCornerShape(6.dp)
                            Box(
                                Modifier.padding(horizontal = 1.5.dp).size(width = cellW, height = cellH).clip(shape)
                                    .background(if (strokes != null) Color.White else StudioColors.Surface2)
                                    .border(if (isCurrent) 2.dp else 1.dp, if (isCurrent) StudioColors.Accent else StudioColors.Outline, shape)
                                    .combinedClickable(
                                        onClick = { st.activeLayerId = l.id; st.selectFrame(i) },
                                        onLongClick = { st.activeLayerId = l.id; st.selectFrame(i); onFrameOptions(i) },
                                    ),
                            ) {
                                if (strokes != null) {
                                    Canvas(Modifier.fillMaxSize()) { drawThumbnail(strokes, sw.toFloat(), sh.toFloat()) }
                                }
                                Text(
                                    "${i + 1}",
                                    style = StudioType.Caption.copy(fontSize = 8.sp, color = if (strokes != null) Color(0xFF30384F) else StudioColors.InkDim),
                                    modifier = Modifier.align(Alignment.TopStart).padding(start = 3.dp, top = 1.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(3.dp), horizontalAlignment = Alignment.End) {
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
                ToolCell(width = 30.dp, height = 30.dp, onClick = onCamera) { Icon(Icons.Default.Videocam, "Câmara", tint = it, modifier = Modifier.size(17.dp)) }
                // Amostra da cor: toque abre o seletor; arrastar para a folha pinta a região onde se solta.
                CheckerBox(
                    Color(st.colorArgb),
                    Modifier.size(width = 38.dp, height = 30.dp).clip(RoundedCornerShape(9.dp))
                        .border(2.dp, StudioColors.Ink, RoundedCornerShape(9.dp))
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
                ToolCell(width = 28.dp, height = 30.dp, onClick = { onColor(ColorMethod.PALETTE) }) { PaletteGlyph(it, Modifier.size(16.dp)) }
                ToolCell(width = 28.dp, height = 30.dp, onClick = { onColor(ColorMethod.RGB) }) { RgbGlyph(it, Modifier.size(16.dp)) }
                ToolCell(width = 28.dp, height = 30.dp, onClick = { onColor(ColorMethod.DISC) }) { HsbGlyph(Modifier.size(16.dp)) }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                Box(Modifier.size(width = 22.dp, height = 28.dp).clickable(onClick = onOnionOptions), contentAlignment = Alignment.Center) {
                    DotsGlyph(StudioColors.InkDim)
                }
                val shape = RoundedCornerShape(9.dp)
                Box(
                    Modifier.height(28.dp).clip(shape)
                        .background(if (st.onionOn) StudioColors.Accent else StudioColors.Surface2)
                        .border(1.dp, if (st.onionOn) StudioColors.Accent else StudioColors.Outline, shape)
                        .clickable { st.onionOn = !st.onionOn }.padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center,
                ) { Text("Onion", style = StudioType.Label.copy(fontSize = 11.sp)) }
            }
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
// Painel de ferramentas compacto (grelha de 2 colunas)
// ---------------------------------------------------------------------------------------------

@Composable
internal fun ToolPanel(
    st: EditorState,
    fps: Int,
    onFpsEdit: () -> Unit,
    onFpsStep: (Int) -> Unit,
    onToolTap: (Tool) -> Unit,
    onToolLibrary: (Tool) -> Unit,
    onToolSettings: (Tool) -> Unit,
    onAddFrame: () -> Unit,
    onCycleMode: () -> Unit,
    onGripEnd: (Float, Float) -> Unit,
) {
    Column(
        Modifier.width(80.dp).fillMaxHeight().verticalScroll(rememberScrollState()).padding(horizontal = 3.dp).padding(bottom = 4.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        GripHandle(Modifier.size(width = 40.dp, height = 16.dp), onGripEnd)
        Column(
            Modifier.studioCard(12.dp).padding(3.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                ToolCell(
                    selected = st.tool == Tool.BRUSH, onLongClick = { onToolLibrary(Tool.BRUSH) },
                    onDoubleClick = { onToolSettings(Tool.BRUSH) }, onClick = { onToolTap(Tool.BRUSH) },
                ) { Icon(Icons.Default.Brush, "Pincel", tint = it, modifier = Modifier.size(17.dp)) }
                ToolCell(
                    selected = st.tool == Tool.ERASER, onLongClick = { onToolLibrary(Tool.ERASER) },
                    onDoubleClick = { onToolSettings(Tool.ERASER) }, onClick = { onToolTap(Tool.ERASER) },
                ) { EraserGlyph(it, Modifier.size(17.dp)) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                ToolCell(
                    selected = st.tool == Tool.BUCKET, onDoubleClick = { onToolSettings(Tool.BUCKET) }, onClick = { onToolTap(Tool.BUCKET) },
                ) { Icon(Icons.Default.FormatColorFill, "Balde de tinta", tint = it, modifier = Modifier.size(17.dp)) }
                ToolCell(selected = st.tool == Tool.LASSO, onClick = { onToolTap(Tool.LASSO) }) { LassoGlyph(it, Modifier.size(17.dp)) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                ToolCell(enabled = st.history.canUndo && !st.playing, onClick = { st.undo() }) { Icon(Icons.Default.Undo, "Desfazer", tint = it, modifier = Modifier.size(17.dp)) }
                ToolCell(enabled = st.history.canRedo && !st.playing, onClick = { st.redo() }) { Icon(Icons.Default.Redo, "Refazer", tint = it, modifier = Modifier.size(17.dp)) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                ToolCell(onClick = onFpsEdit) { Text("FPS", style = StudioType.Caption.copy(fontSize = 9.sp, color = StudioColors.Ink)) }
                ToolCell(onClick = onFpsEdit) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("$fps", style = StudioType.Number.copy(fontSize = 12.sp), modifier = Modifier.padding(start = 3.dp))
                        Column {
                            Icon(Icons.Default.KeyboardArrowUp, "Mais FPS", tint = it, modifier = Modifier.size(13.dp).clickable { onFpsStep(1) })
                            Icon(Icons.Default.KeyboardArrowDown, "Menos FPS", tint = it, modifier = Modifier.size(13.dp).clickable { onFpsStep(-1) })
                        }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                ToolCell(enabled = st.project.currentFrame > 0 && !st.playing, onClick = { st.go(-1) }) { TriangleGlyph(it, true, Modifier.size(14.dp)) }
                ToolCell(enabled = st.project.currentFrame < st.project.frameCount - 1 && !st.playing, onClick = { st.go(1) }) { TriangleGlyph(it, false, Modifier.size(14.dp)) }
            }
            ToolCell(width = 67.dp, onClick = onAddFrame) { Text("+", style = StudioType.Title.copy(fontSize = 18.sp, color = it)) }
            ToolCell(width = 67.dp, selected = st.playing, onLongClick = onCycleMode, onClick = { st.togglePlay() }) {
                Icon(if (st.playing) Icons.Default.Pause else Icons.Default.PlayArrow, if (st.playing) "Parar" else "Reproduzir", tint = it, modifier = Modifier.size(18.dp))
            }
        }
    }
}
