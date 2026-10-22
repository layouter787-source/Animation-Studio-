package com.animationstudio.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Redo
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
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
    width: Dp = 40.dp,
    height: Dp = 40.dp,
    selected: Boolean = false,
    enabled: Boolean = true,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
    content: @Composable (Color) -> Unit,
) {
    val tint = if (selected) Color.White else if (enabled) StudioColors.Ink else StudioColors.InkDim.copy(alpha = 0.5f)
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier.size(width = width, height = height).clip(shape)
            .background(if (selected) StudioColors.Accent else StudioColors.Surface2)
            .border(1.dp, if (selected) StudioColors.Accent else StudioColors.Outline, shape)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        contentAlignment = Alignment.Center,
    ) { content(tint) }
}

// ---------------------------------------------------------------------------------------------
// Barra superior: menu | camadas, cenários e timeline com miniaturas | câmara | cor
// ---------------------------------------------------------------------------------------------

@Composable
internal fun TopBar(
    st: EditorState,
    sw: Int,
    sh: Int,
    expanded: Boolean,
    onMenu: () -> Unit,
    onCamera: () -> Unit,
    onColor: (ColorMethod) -> Unit,
    onPlus: (Layer) -> Unit,
    onLayerOptions: (Layer) -> Unit,
    onFrameOptions: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val all = st.project.layers.asReversed()
    val rows = if (expanded) all else all.take(2)
    val cellW = 56.dp
    val cellH = (56f * sh / sw).coerceIn(28f, 44f).dp
    val rowH = cellH + 4.dp
    val hScroll = rememberScrollState()
    val vScroll = rememberScrollState()
    Row(
        modifier.fillMaxWidth().background(StudioColors.Navy).padding(6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.Top,
    ) {
        ToolCell(width = 40.dp, height = 40.dp, onClick = onMenu) { Icon(Icons.Default.Menu, "Menu", tint = it) }
        Row(
            Modifier.weight(1f).studioCard().heightIn(max = if (expanded) rowH * 5 + 8.dp else rowH * 2 + 8.dp)
                .verticalScroll(vScroll).padding(4.dp)
        ) {
            Column(Modifier.width(150.dp)) {
                for (l in rows) {
                    val isActive = l.id == st.activeLayerId
                    Row(Modifier.height(rowH).padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(26.dp).clip(RoundedCornerShape(8.dp)).background(StudioColors.AccentSoft)
                                .clickable { onPlus(l) },
                            contentAlignment = Alignment.Center,
                        ) { Text("+", style = StudioType.Title.copy(color = StudioColors.Accent)) }
                        Spacer(Modifier.width(5.dp))
                        val chipShape = RoundedCornerShape(9.dp)
                        Row(
                            Modifier.weight(1f).fillMaxHeight().clip(chipShape)
                                .background(if (isActive) StudioColors.AccentSoft else StudioColors.Surface2)
                                .border(1.dp, if (isActive) StudioColors.Accent else StudioColors.Outline, chipShape)
                                .combinedClickable(onClick = { st.activeLayerId = l.id }, onLongClick = { onLayerOptions(l) })
                                .padding(horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                Modifier.size(8.dp).clip(RoundedCornerShape(4.dp))
                                    .background(if (l.kind == LayerKind.SCENERY) StudioColors.Mint else StudioColors.Accent)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                l.name, style = StudioType.Label, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                textDecoration = if (l.visible) TextDecoration.None else TextDecoration.LineThrough,
                            )
                        }
                    }
                }
            }
            Column(Modifier.weight(1f).horizontalScroll(hScroll)) {
                for (l in rows) {
                    Row(Modifier.height(rowH), verticalAlignment = Alignment.CenterVertically) {
                        st.project.frameIds.forEachIndexed { i, fid ->
                            val strokes = l.drawingAt(fid)?.strokes
                            val isCurrent = i == st.project.currentFrame
                            val shape = RoundedCornerShape(7.dp)
                            Box(
                                Modifier.padding(horizontal = 2.dp).size(width = cellW, height = cellH).clip(shape)
                                    .background(if (strokes != null) Color.White else StudioColors.Surface2)
                                    .border(if (isCurrent) 2.dp else 1.dp, if (isCurrent) StudioColors.Accent else StudioColors.Outline, shape)
                                    .combinedClickable(
                                        onClick = { st.activeLayerId = l.id; st.selectFrame(i) },
                                        onLongClick = { st.activeLayerId = l.id; st.selectFrame(i); onFrameOptions(i) },
                                    ),
                            ) {
                                if (strokes != null) {
                                    Canvas(Modifier.fillMaxSize()) { drawThumbnail(strokes, sw.toFloat(), sh.toFloat()) }
                                    Box(
                                        Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(3.dp)
                                            .background(if (l.kind == LayerKind.SCENERY) StudioColors.Mint else StudioColors.Accent)
                                    )
                                }
                                Text(
                                    "${i + 1}",
                                    style = StudioType.Caption.copy(fontSize = 9.sp, color = if (strokes != null) Color(0xFF30384F) else StudioColors.InkDim),
                                    modifier = Modifier.align(Alignment.TopStart).padding(start = 4.dp, top = 2.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
        ToolCell(width = 40.dp, height = 40.dp, onClick = onCamera) { Icon(Icons.Default.Videocam, "Câmara", tint = it) }
        Row(
            Modifier.studioCard().padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CheckerBox(
                Color(st.colorArgb),
                Modifier.size(width = 46.dp, height = 36.dp).clip(RoundedCornerShape(10.dp))
                    .border(2.dp, StudioColors.Ink, RoundedCornerShape(10.dp)).clickable { onColor(ColorMethod.DISC) }
            )
            ToolCell(width = 34.dp, height = 36.dp, onClick = { onColor(ColorMethod.PALETTE) }) { PaletteGlyph(it) }
            ToolCell(width = 34.dp, height = 36.dp, onClick = { onColor(ColorMethod.RGB) }) { RgbGlyph(it) }
            ToolCell(width = 34.dp, height = 36.dp, onClick = { onColor(ColorMethod.DISC) }) { HsbGlyph() }
        }
    }
}

@Composable
internal fun ExpandTab(expanded: Boolean, onSet: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val curExpanded by rememberUpdatedState(expanded)
    val curSet by rememberUpdatedState(onSet)
    Box(
        modifier.width(110.dp).height(16.dp)
            .clip(RoundedCornerShape(bottomStart = 10.dp, bottomEnd = 10.dp))
            .background(StudioColors.Surface)
            .border(1.dp, StudioColors.Outline, RoundedCornerShape(bottomStart = 10.dp, bottomEnd = 10.dp))
            .pointerInput(Unit) {
                detectVerticalDragGestures { change, dy ->
                    change.consume()
                    if (dy > 4f) curSet(true) else if (dy < -4f) curSet(false)
                }
            }
            .clickable { curSet(!curExpanded) },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.width(40.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(StudioColors.InkDim))
    }
}

// ---------------------------------------------------------------------------------------------
// Painel de ferramentas em blocos: desenho, histórico e animação
// ---------------------------------------------------------------------------------------------

@Composable
private fun ToolBlock(title: String, content: @Composable () -> Unit) {
    Column(
        Modifier.studioCard().padding(horizontal = 4.dp, vertical = 5.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(title, style = StudioType.Caption, modifier = Modifier.padding(start = 3.dp))
        content()
    }
}

@Composable
internal fun ToolPanel(
    st: EditorState,
    fps: Int,
    onFpsEdit: () -> Unit,
    onFpsStep: (Int) -> Unit,
    onToolTap: (Tool) -> Unit,
    onToolOptions: (Tool) -> Unit,
    onAddFrame: () -> Unit,
    onCycleMode: () -> Unit,
) {
    Column(
        Modifier.width(104.dp).fillMaxHeight().verticalScroll(rememberScrollState()).padding(start = 6.dp, end = 4.dp, bottom = 6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        ToolBlock("DESENHO") {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                ToolCell(selected = st.tool == Tool.BRUSH, onLongClick = { onToolOptions(Tool.BRUSH) }, onClick = { onToolTap(Tool.BRUSH) }) {
                    Icon(Icons.Default.Brush, "Pincel", tint = it)
                }
                ToolCell(selected = st.tool == Tool.ERASER, onLongClick = { onToolOptions(Tool.ERASER) }, onClick = { onToolTap(Tool.ERASER) }) {
                    EraserGlyph(it)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                ToolCell(selected = st.tool == Tool.BUCKET, onClick = { onToolTap(Tool.BUCKET) }) {
                    Icon(Icons.Default.FormatColorFill, "Balde de tinta", tint = it)
                }
                ToolCell(selected = st.tool == Tool.LASSO, onClick = { onToolTap(Tool.LASSO) }) { LassoGlyph(it) }
            }
        }
        ToolBlock("HISTÓRICO") {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                ToolCell(enabled = st.history.canUndo && !st.playing, onClick = { st.undo() }) { Icon(Icons.Default.Undo, "Desfazer", tint = it) }
                ToolCell(enabled = st.history.canRedo && !st.playing, onClick = { st.redo() }) { Icon(Icons.Default.Redo, "Refazer", tint = it) }
            }
        }
        ToolBlock("ANIMAÇÃO") {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                ToolCell(onClick = onFpsEdit) { Text("FPS", style = StudioType.Caption.copy(color = StudioColors.Ink)) }
                ToolCell(onClick = onFpsEdit) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("$fps", style = StudioType.Number, modifier = Modifier.padding(start = 4.dp))
                        Column {
                            Icon(Icons.Default.KeyboardArrowUp, "Mais FPS", tint = it, modifier = Modifier.size(17.dp).clickable { onFpsStep(1) })
                            Icon(Icons.Default.KeyboardArrowDown, "Menos FPS", tint = it, modifier = Modifier.size(17.dp).clickable { onFpsStep(-1) })
                        }
                    }
                }
            }
            ToolCell(width = 84.dp, onClick = onAddFrame) { Text("+", style = StudioType.Title.copy(fontSize = 22.sp, color = it)) }
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                ToolCell(width = 26.dp, enabled = st.project.currentFrame > 0 && !st.playing, onClick = { st.go(-1) }) {
                    TriangleGlyph(it, pointsLeft = true, modifier = Modifier.size(16.dp))
                }
                ToolCell(width = 26.dp, selected = st.playing, onLongClick = onCycleMode, onClick = { st.togglePlay() }) {
                    Icon(if (st.playing) Icons.Default.Pause else Icons.Default.PlayArrow, if (st.playing) "Parar" else "Reproduzir", tint = it, modifier = Modifier.size(18.dp))
                }
                ToolCell(width = 26.dp, enabled = st.project.currentFrame < st.project.frameCount - 1 && !st.playing, onClick = { st.go(1) }) {
                    TriangleGlyph(it, pointsLeft = false, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}
