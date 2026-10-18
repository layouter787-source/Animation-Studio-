package com.animationstudio.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateRotation
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.FitScreen
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.NavigateBefore
import androidx.compose.material.icons.filled.NavigateNext
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Redo
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.DrawTransform
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.animationstudio.domain.BrushStroke
import com.animationstudio.domain.History
import com.animationstudio.domain.PlayMode
import com.animationstudio.domain.Point
import com.animationstudio.domain.Timeline
import com.animationstudio.domain.ViewTransform
import com.animationstudio.domain.nextPlaybackStep
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

internal enum class Tool { BRUSH, ERASER, HAND }

@Stable
internal class EditorState {
    var timeline by mutableStateOf(Timeline.initial())
    var history by mutableStateOf(History<Timeline>())
    var view by mutableStateOf(ViewTransform())
    var fitted = false
    var tool by mutableStateOf(Tool.BRUSH)
    var colorArgb by mutableIntStateOf(0xFF111111.toInt())
    var brushSize by mutableFloatStateOf(8f)
    var playing by mutableStateOf(false)
    var playMode by mutableStateOf(PlayMode.LOOP)
    val activePoints = mutableStateListOf<Point>()

    fun commitStroke() {
        val pts = activePoints.toList()
        activePoints.clear()
        if (pts.isEmpty() || playing) return
        val stroke = BrushStroke(pts, colorArgb, brushSize, erase = tool == Tool.ERASER)
        history = history.record(timeline)
        timeline = timeline.addStrokeToCurrent(stroke)
    }

    fun undo() {
        if (playing) return
        val r = history.undo(timeline) ?: return
        timeline = r.first; history = r.second
    }

    fun redo() {
        if (playing) return
        val r = history.redo(timeline) ?: return
        timeline = r.first; history = r.second
    }

    fun addFrame() { if (playing) return; history = history.record(timeline); timeline = timeline.addBlankAfterCurrent() }
    fun duplicateFrame() { if (playing) return; history = history.record(timeline); timeline = timeline.duplicateCurrent() }
    fun deleteFrame() { if (playing || timeline.count == 1) return; history = history.record(timeline); timeline = timeline.deleteCurrent() }
    fun go(delta: Int) { if (playing) return; timeline = timeline.select(timeline.current + delta) }
    fun selectFrame(i: Int) { if (playing) return; timeline = timeline.select(i) }

    fun togglePlay() {
        if (playing) { playing = false; return }
        if (timeline.count < 2) return
        if (playMode == PlayMode.ONCE && timeline.current == timeline.count - 1) timeline = timeline.select(0)
        playing = true
    }
}

private fun Offset.toPoint() = Point(x, y)

private fun DrawTransform.applyView(v: ViewTransform) {
    translate(v.tx, v.ty)
    rotate(v.rotationDegrees, Offset.Zero)
    scale(v.scale, v.scale, Offset.Zero)
}

private fun DrawScope.drawBrushStroke(s: BrushStroke) {
    val color = Color(s.colorArgb)
    val blend = if (s.erase) BlendMode.Clear else BlendMode.SrcOver
    val pts = s.points
    if (pts.isEmpty()) return
    if (pts.size == 1) {
        drawCircle(color, s.width / 2f, Offset(pts[0].x, pts[0].y), blendMode = blend)
        return
    }
    val path = Path()
    path.moveTo(pts[0].x, pts[0].y)
    for (i in 1 until pts.size) {
        val a = pts[i - 1]
        val b = pts[i]
        path.quadraticBezierTo(a.x, a.y, (a.x + b.x) / 2f, (a.y + b.y) / 2f)
    }
    path.lineTo(pts.last().x, pts.last().y)
    drawPath(path, color, style = Stroke(width = s.width, cap = StrokeCap.Round, join = StrokeJoin.Round), blendMode = blend)
}

@Composable
fun AnimationEditor(projectName: String, initialFps: Int, sheetWidth: Int, sheetHeight: Int, onClose: () -> Unit) {
    val st = remember { EditorState() }
    var fps by remember { mutableIntStateOf(initialFps) }
    var barsVisible by remember { mutableStateOf(true) }
    var showColor by remember { mutableStateOf(false) }
    var viewSize by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current
    var topBarPos by remember { mutableStateOf(with(density) { Offset(8.dp.toPx(), 8.dp.toPx()) }) }
    var toolBarPos by remember { mutableStateOf(with(density) { Offset(8.dp.toPx(), 72.dp.toPx()) }) }
    var timelinePos by remember { mutableStateOf(with(density) { Offset(8.dp.toPx(), 2000.dp.toPx()) }) }
    val sw = sheetWidth.toFloat()
    val sh = sheetHeight.toFloat()

    LaunchedEffect(viewSize) {
        if (viewSize.width > 0 && !st.fitted) {
            st.view = ViewTransform.fitSheet(sw, sh, viewSize.width.toFloat(), viewSize.height.toFloat())
            st.fitted = true
        }
    }

    LaunchedEffect(st.playing, fps) {
        if (!st.playing) return@LaunchedEffect
        var dir = 1
        val interval = 1000L / fps.coerceAtLeast(1)
        while (st.playing) {
            delay(interval)
            val step = nextPlaybackStep(st.timeline.current, st.timeline.count, st.playMode, dir)
            dir = step.direction
            st.timeline = st.timeline.select(step.index)
            if (step.finished) st.playing = false
        }
    }

    Box(Modifier.fillMaxSize().background(StudioColors.Workspace).onSizeChanged { viewSize = it }) {
        // Folha branca sobre o espaço de trabalho escuro.
        Canvas(Modifier.fillMaxSize()) {
            val v = st.view
            withTransform({ applyView(v) }) {
                drawRect(Color.White, topLeft = Offset.Zero, size = Size(sw, sh))
                drawRect(Color.Black, topLeft = Offset.Zero, size = Size(sw, sh), style = Stroke(width = 1f / v.scale))
            }
        }
        // Traços do frame atual (camada offscreen para a borracha apagar de verdade), cortados à folha.
        Canvas(Modifier.fillMaxSize().graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }) {
            val v = st.view
            val frame = st.timeline.currentFrame
            val active = st.activePoints.toList()
            withTransform({ applyView(v) }) {
                clipRect(0f, 0f, sw, sh) {
                    frame.strokes.forEach { drawBrushStroke(it) }
                    if (active.isNotEmpty()) {
                        drawBrushStroke(BrushStroke(active, st.colorArgb, st.brushSize, st.tool == Tool.ERASER))
                    }
                }
            }
        }
        // Camada de entrada: desenho (1 dedo) ou pinça/pan/rotação (2 dedos).
        Box(
            Modifier.fillMaxSize().pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    if (st.playing) {
                        do { val e = awaitPointerEvent() } while (e.changes.any { it.pressed })
                        return@awaitEachGesture
                    }
                    var drawing = st.tool == Tool.BRUSH || st.tool == Tool.ERASER
                    val handTool = st.tool == Tool.HAND
                    var multi = false
                    st.activePoints.clear()
                    if (drawing) st.activePoints.add(st.view.screenToProject(down.position.toPoint()))
                    down.consume()
                    do {
                        val event = awaitPointerEvent()
                        val pressedCount = event.changes.count { it.pressed }
                        if (pressedCount >= 2) {
                            if (drawing) { drawing = false; st.activePoints.clear() }
                            multi = true
                        }
                        if (pressedCount >= 2 || (handTool && pressedCount >= 1)) {
                            val c = event.calculateCentroid(useCurrent = true)
                            if (c.isSpecified) {
                                val pan = event.calculatePan()
                                val zoom = event.calculateZoom()
                                val rotDeg = event.calculateRotation()
                                val focus = c.toPoint()
                                var v = st.view.panBy(pan.x, pan.y)
                                v = v.zoomAround(focus, zoom)
                                v = v.rotateAround(focus, Math.toRadians(rotDeg.toDouble()).toFloat())
                                st.view = v
                            }
                        } else if (drawing && !multi) {
                            val ch = event.changes.firstOrNull { it.id == down.id }
                            if (ch != null && ch.pressed) {
                                ch.historical.forEach { st.activePoints.add(st.view.screenToProject(it.position.toPoint())) }
                                st.activePoints.add(st.view.screenToProject(ch.position.toPoint()))
                            }
                        }
                        event.changes.forEach { if (it.positionChanged()) it.consume() }
                    } while (event.changes.any { it.pressed })
                    if (drawing) st.commitStroke() else st.activePoints.clear()
                }
            }
        )

        // Indicador persistente do frame atual/total (não desaparece com as barras).
        Text(
            "Frame ${st.timeline.current + 1}/${st.timeline.count}",
            color = StudioColors.Cream, fontSize = 12.sp,
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 12.dp, bottom = 8.dp)
                .background(StudioColors.Panel.copy(alpha = .8f), RoundedCornerShape(6.dp)).padding(horizontal = 8.dp, vertical = 4.dp)
        )

        if (!barsVisible) {
            IconButton(
                onClick = { barsVisible = true },
                modifier = Modifier.align(Alignment.BottomStart).padding(8.dp).background(StudioColors.Panel.copy(alpha = .85f), RoundedCornerShape(10.dp))
            ) { Icon(Icons.Default.Visibility, "Mostrar barras", tint = StudioColors.Cream) }
        }

        if (barsVisible) {
            val containerW = with(density) { viewSize.width.toDp() }
            val containerH = with(density) { viewSize.height.toDp() }

            FloatingBar(topBarPos, { topBarPos = it }, viewSize, vertical = false) {
                Row(
                    Modifier.widthIn(max = containerW - 56.dp).horizontalScroll(rememberScrollState()),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BarButton(Icons.Default.Menu, "Fechar projeto") { onClose() }
                    TextButton(onClick = { fps = (fps - 1).coerceAtLeast(1) }) { Text("−", color = StudioColors.Ink) }
                    Text("$fps FPS", color = StudioColors.Ink, fontSize = 12.sp)
                    TextButton(onClick = { fps = (fps + 1).coerceAtMost(120) }) { Text("+", color = StudioColors.Ink) }
                    BarButton(if (st.playing) Icons.Default.Pause else Icons.Default.PlayArrow, if (st.playing) "Parar" else "Reproduzir") { st.togglePlay() }
                    TextButton(onClick = { st.playMode = PlayMode.values()[(st.playMode.ordinal + 1) % PlayMode.values().size] }) {
                        Text(when (st.playMode) { PlayMode.ONCE -> "Uma vez"; PlayMode.LOOP -> "Loop"; PlayMode.PING_PONG -> "Ping-pong" }, color = StudioColors.Cream, fontSize = 12.sp)
                    }
                    Box(
                        Modifier.size(32.dp).background(Color(st.colorArgb), RoundedCornerShape(16.dp)).border(2.dp, StudioColors.Ink, RoundedCornerShape(16.dp)).clickable { showColor = true }
                    )
                    Slider(
                        value = st.brushSize, onValueChange = { st.brushSize = it }, valueRange = 1f..64f,
                        modifier = Modifier.width(96.dp).padding(horizontal = 6.dp)
                    )
                    Text("${st.brushSize.roundToInt()} px", color = StudioColors.Ink, fontSize = 11.sp)
                    BarButton(Icons.Default.VisibilityOff, "Ocultar barras") { barsVisible = false }
                }
            }

            FloatingBar(toolBarPos, { toolBarPos = it }, viewSize, vertical = true) {
                Column(
                    Modifier.heightIn(max = containerH - 56.dp).verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    BarButton(Icons.Default.Brush, "Pincel", st.tool == Tool.BRUSH) { st.tool = Tool.BRUSH }
                    BarButton(Icons.Default.Backspace, "Borracha", st.tool == Tool.ERASER) { st.tool = Tool.ERASER }
                    BarButton(Icons.Default.PanTool, "Mão (mover vista)", st.tool == Tool.HAND) { st.tool = Tool.HAND }
                    BarButton(Icons.Default.Undo, "Desfazer", enabled = st.history.canUndo && !st.playing) { st.undo() }
                    BarButton(Icons.Default.Redo, "Refazer", enabled = st.history.canRedo && !st.playing) { st.redo() }
                    BarButton(Icons.Default.NavigateBefore, "Frame anterior", enabled = st.timeline.current > 0) { st.go(-1) }
                    BarButton(Icons.Default.NavigateNext, "Frame seguinte", enabled = st.timeline.current < st.timeline.count - 1) { st.go(1) }
                    BarButton(Icons.Default.Add, "Novo frame") { st.addFrame() }
                    BarButton(Icons.Default.ContentCopy, "Duplicar frame") { st.duplicateFrame() }
                    BarButton(Icons.Default.Delete, "Apagar frame", enabled = st.timeline.count > 1) { st.deleteFrame() }
                    BarButton(Icons.Default.FitScreen, "Ajustar à folha e repor vista") {
                        st.view = ViewTransform.fitSheet(sw, sh, viewSize.width.toFloat(), viewSize.height.toFloat())
                    }
                }
            }

            FloatingBar(timelinePos, { timelinePos = it }, viewSize, vertical = false) {
                LazyRow(Modifier.width(containerW - 64.dp).height(52.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    itemsIndexed(st.timeline.frames, key = { _, f -> f.id }) { i, _ ->
                        val selected = i == st.timeline.current
                        Box(
                            Modifier.size(width = 44.dp, height = 44.dp)
                                .background(if (selected) StudioColors.Cream else StudioColors.PanelLight, RoundedCornerShape(6.dp))
                                .clickable { st.selectFrame(i) },
                            contentAlignment = Alignment.Center
                        ) { Text("${i + 1}", color = if (selected) Color.Black else StudioColors.Ink, fontSize = 12.sp) }
                    }
                }
            }
        }
    }

    if (showColor) {
        ColorDialog(st.colorArgb, onCancel = { showColor = false }, onConfirm = { st.colorArgb = it; showColor = false })
    }
}

@Composable
private fun FloatingBar(
    offset: Offset,
    onOffset: (Offset) -> Unit,
    container: IntSize,
    vertical: Boolean,
    content: @Composable () -> Unit,
) {
    var size by remember { mutableStateOf(IntSize.Zero) }
    val curOffset by rememberUpdatedState(offset)
    val curContainer by rememberUpdatedState(container)
    val curSize by rememberUpdatedState(size)
    val curOnOffset by rememberUpdatedState(onOffset)
    val maxX = (container.width - size.width).coerceAtLeast(0).toFloat()
    val maxY = (container.height - size.height).coerceAtLeast(0).toFloat()
    val shownX = offset.x.coerceIn(0f, maxX)
    val shownY = offset.y.coerceIn(0f, maxY)

    val handle: @Composable () -> Unit = {
        Icon(
            Icons.Default.DragIndicator, "Mover barra", tint = StudioColors.Ink.copy(alpha = .7f),
            modifier = Modifier.size(32.dp).pointerInput(Unit) {
                detectDragGestures { change, drag ->
                    change.consume()
                    val mx = (curContainer.width - curSize.width).coerceAtLeast(0).toFloat()
                    val my = (curContainer.height - curSize.height).coerceAtLeast(0).toFloat()
                    // Parte da posição realmente mostrada (já limitada ao ecrã), não da guardada.
                    val base = Offset(curOffset.x.coerceIn(0f, mx), curOffset.y.coerceIn(0f, my))
                    val n = base + drag
                    curOnOffset(Offset(n.x.coerceIn(0f, mx), n.y.coerceIn(0f, my)))
                }
            }
        )
    }

    Box(
        Modifier.offset { IntOffset(shownX.roundToInt(), shownY.roundToInt()) }
            .onSizeChanged { size = it }
            .background(StudioColors.Panel, RoundedCornerShape(12.dp))
            .padding(2.dp)
    ) {
        if (vertical) Column(horizontalAlignment = Alignment.CenterHorizontally) { handle(); content() }
        else Row(verticalAlignment = Alignment.CenterVertically) { handle(); content() }
    }
}

@Composable
private fun BarButton(icon: ImageVector, description: String, selected: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    IconButton(
        onClick = onClick, enabled = enabled,
        modifier = Modifier.size(44.dp).background(if (selected) StudioColors.Cream else Color.Transparent, RoundedCornerShape(8.dp))
    ) {
        Icon(
            icon, description,
            tint = if (selected) Color.Black else if (enabled) StudioColors.Ink else StudioColors.Ink.copy(alpha = .35f)
        )
    }
}

@Composable
private fun ColorDialog(initial: Int, onCancel: () -> Unit, onConfirm: (Int) -> Unit) {
    var r by remember { mutableFloatStateOf(((initial shr 16) and 0xFF).toFloat()) }
    var g by remember { mutableFloatStateOf(((initial shr 8) and 0xFF).toFloat()) }
    var b by remember { mutableFloatStateOf((initial and 0xFF).toFloat()) }
    val argb = (0xFF shl 24) or (r.roundToInt() shl 16) or (g.roundToInt() shl 8) or b.roundToInt()
    AlertDialog(
        onDismissRequest = onCancel,
        containerColor = StudioColors.Panel,
        title = { Text("Cor (RGB)", color = StudioColors.Ink) },
        text = {
            Column {
                Box(Modifier.fillMaxWidth().height(40.dp).background(Color(argb), RoundedCornerShape(6.dp)))
                Text("R ${r.roundToInt()}", color = StudioColors.Ink, fontSize = 12.sp)
                Slider(value = r, onValueChange = { r = it }, valueRange = 0f..255f)
                Text("G ${g.roundToInt()}", color = StudioColors.Ink, fontSize = 12.sp)
                Slider(value = g, onValueChange = { g = it }, valueRange = 0f..255f)
                Text("B ${b.roundToInt()}", color = StudioColors.Ink, fontSize = 12.sp)
                Slider(value = b, onValueChange = { b = it }, valueRange = 0f..255f)
            }
        },
        confirmButton = { Button(onClick = { onConfirm(argb) }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onCancel) { Text("Cancelar") } }
    )
}
