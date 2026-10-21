package com.animationstudio.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateRotation
import androidx.compose.foundation.gestures.calculateZoom
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.FitScreen
import androidx.compose.material.icons.filled.FormatColorFill
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Redo
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.DrawTransform
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.animationstudio.data.ProjectMeta
import com.animationstudio.data.ProjectStore
import com.animationstudio.domain.BrushStroke
import com.animationstudio.domain.History
import com.animationstudio.domain.Layer
import com.animationstudio.domain.LayerKind
import com.animationstudio.domain.PlayMode
import com.animationstudio.domain.Point
import com.animationstudio.domain.Project
import com.animationstudio.domain.ViewTransform
import com.animationstudio.domain.frameClip
import com.animationstudio.domain.insertFrameAt
import com.animationstudio.domain.nextPlaybackStep
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

internal enum class Tool { BRUSH, ERASER }

private enum class ColorMethod(val label: String) { PALETTE("Paleta"), RGB("RGB"), HSB("HSB") }

private sealed interface EditorDialog {
    data object Menu : EditorDialog
    data class ColorPicker(val method: ColorMethod) : EditorDialog
    data class Plus(val layerId: Long) : EditorDialog
    data class LayerOptions(val layerId: Long) : EditorDialog
    data class FrameOptions(val index: Int) : EditorDialog
    data class BrushOptions(val tool: Tool) : EditorDialog
    data object FpsEdit : EditorDialog
}

@Stable
internal class EditorState(initial: Project) {
    var project by mutableStateOf(initial)
    var history by mutableStateOf(History<Project>())
    var activeLayerId by mutableLongStateOf(initial.layers.lastOrNull { it.kind == LayerKind.DRAWING }?.id ?: initial.layers.last().id)
    var view by mutableStateOf(ViewTransform())
    var fitted = false
    var tool by mutableStateOf(Tool.BRUSH)
    var colorArgb by mutableIntStateOf(0xFF111111.toInt())
    val recentColors = mutableStateListOf<Int>()
    var brushSize by mutableFloatStateOf(8f)
    var eraserSize by mutableFloatStateOf(24f)
    var playing by mutableStateOf(false)
    var playMode by mutableStateOf(PlayMode.LOOP)
    var clip by mutableStateOf<Map<Long, List<BrushStroke>>?>(null)
    val activePoints = mutableStateListOf<Point>()

    fun edit(change: (Project) -> Project) {
        if (playing) return
        val next = change(project)
        if (next !== project) {
            history = history.record(project)
            project = next
        }
    }

    fun commitStroke() {
        val pts = activePoints.toList()
        activePoints.clear()
        if (pts.isEmpty() || playing) return
        val layer = project.layers.firstOrNull { it.id == activeLayerId } ?: return
        if (!layer.visible) return
        val erase = tool == Tool.ERASER
        val stroke = BrushStroke(pts, colorArgb, if (erase) eraserSize else brushSize, erase)
        edit { it.addStroke(activeLayerId, stroke) }
    }

    fun undo() {
        if (playing) return
        val r = history.undo(project) ?: return
        project = r.first
        history = r.second
        fixActiveLayer()
    }

    fun redo() {
        if (playing) return
        val r = history.redo(project) ?: return
        project = r.first
        history = r.second
        fixActiveLayer()
    }

    private fun fixActiveLayer() {
        if (project.layers.none { it.id == activeLayerId }) activeLayerId = project.layers.last().id
    }

    fun go(delta: Int) { if (!playing) project = project.select(project.currentFrame + delta) }
    fun selectFrame(i: Int) { if (!playing) project = project.select(i) }

    fun addLayer(kind: LayerKind) {
        if (playing) return
        val newId = project.nextId
        edit { it.addLayer(kind) }
        activeLayerId = newId
    }

    fun duplicateLayer(id: Long) {
        if (playing) return
        val newId = project.nextId
        edit { it.duplicateLayer(id) }
        activeLayerId = newId
    }

    fun deleteLayer(id: Long) {
        edit { it.deleteLayer(id) }
        fixActiveLayer()
    }

    fun togglePlay() {
        if (playing) { playing = false; return }
        if (project.frameCount < 2) return
        if (playMode == PlayMode.ONCE && project.currentFrame == project.frameCount - 1) project = project.select(0)
        playing = true
    }
}

private val Palette: List<Int> = listOf(
    0xFF000000, 0xFF404040, 0xFF808080, 0xFFC0C0C0, 0xFFFFFFFF, 0xFF7A4A21, 0xFFB5651D, 0xFFE0B080,
    0xFFFF0000, 0xFFFF6A00, 0xFFFFC000, 0xFFFFFF00, 0xFFA0E000, 0xFF00C000, 0xFF008060, 0xFF00C0C0,
    0xFF00A0FF, 0xFF0050FF, 0xFF0000C0, 0xFF6000C0, 0xFFA000E0, 0xFFFF00FF, 0xFFFF0080, 0xFFFF9090,
).map { it.toInt() }

private fun DrawTransform.applyView(v: ViewTransform) {
    translate(v.tx, v.ty)
    rotate(v.rotationDegrees, Offset.Zero)
    scale(v.scale, v.scale, Offset.Zero)
}

private fun Offset.toPoint() = Point(x, y)

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

private suspend fun persist(store: ProjectStore, meta: ProjectMeta, fps: Int, project: Project): Boolean =
    withContext(Dispatchers.IO) {
        try {
            store.save(meta.copy(fps = fps, modifiedAt = System.currentTimeMillis()), project)
            true
        } catch (e: Exception) {
            false
        }
    }

@Composable
fun AnimationEditor(meta: ProjectMeta, initial: Project, store: ProjectStore, onClose: () -> Unit) {
    val st = remember { EditorState(initial) }
    var fps by remember { mutableIntStateOf(meta.fps) }
    var expanded by remember { mutableStateOf(false) }
    var topBarHeightPx by remember { mutableIntStateOf(0) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var dialog by remember { mutableStateOf<EditorDialog?>(null) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sw = meta.width.toFloat()
    val sh = meta.height.toFloat()

    fun toast(msg: String) = Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
    fun soon(what: String) = toast("$what: em desenvolvimento")
    fun close() {
        scope.launch {
            if (!persist(store, meta, fps, st.project)) toast("Não foi possível guardar o projeto")
            onClose()
        }
    }

    BackHandler { close() }

    // Autosave com atraso curto (cada alteração reinicia a contagem).
    LaunchedEffect(st.project, fps) {
        delay(400)
        if (!persist(store, meta, fps, st.project)) toast("Não foi possível guardar o projeto")
    }

    LaunchedEffect(canvasSize) {
        if (canvasSize.width > 0 && !st.fitted) {
            st.view = ViewTransform.fitSheet(sw, sh, canvasSize.width.toFloat(), canvasSize.height.toFloat(), 12f)
            st.fitted = true
        }
    }

    LaunchedEffect(st.playing, fps) {
        if (!st.playing) return@LaunchedEffect
        var dir = 1
        val interval = 1000L / fps.coerceAtLeast(1)
        while (st.playing) {
            delay(interval)
            val step = nextPlaybackStep(st.project.currentFrame, st.project.frameCount, st.playMode, dir)
            dir = step.direction
            st.project = st.project.select(step.index)
            if (step.finished) st.playing = false
        }
    }

    Box(Modifier.fillMaxSize().background(StudioColors.Workspace)) {
        Column(Modifier.fillMaxSize()) {
            TopBar(
                st = st, expanded = expanded,
                onMenu = { dialog = EditorDialog.Menu },
                onCamera = { soon("Câmara") },
                onColor = { dialog = EditorDialog.ColorPicker(it) },
                onPlus = { dialog = EditorDialog.Plus(it.id) },
                onLayerOptions = { dialog = EditorDialog.LayerOptions(it.id) },
                onFrameOptions = { dialog = EditorDialog.FrameOptions(it) },
                modifier = Modifier.onSizeChanged { topBarHeightPx = it.height },
            )
            Row(Modifier.weight(1f).fillMaxWidth()) {
                ToolPanel(
                    st = st, fps = fps,
                    onFpsEdit = { dialog = EditorDialog.FpsEdit },
                    onFpsStep = { fps = (fps + it).coerceIn(1, 120) },
                    onToolOptions = { dialog = EditorDialog.BrushOptions(it) },
                    onSoon = { soon(it) },
                    onAddFrame = { st.edit { p -> p.insertFrameAt(p.currentFrame + 1) } },
                    onCycleMode = {
                        st.playMode = PlayMode.values()[(st.playMode.ordinal + 1) % PlayMode.values().size]
                        toast("Reprodução: " + when (st.playMode) { PlayMode.ONCE -> "uma vez"; PlayMode.LOOP -> "loop"; PlayMode.PING_PONG -> "ping-pong" })
                    },
                )
                Box(Modifier.weight(1f).fillMaxHeight().onSizeChanged { canvasSize = it }) {
                    // Folha branca sobre o espaço de trabalho escuro.
                    Canvas(Modifier.fillMaxSize()) {
                        val v = st.view
                        withTransform({ applyView(v) }) {
                            drawRect(Color.White, topLeft = Offset.Zero, size = Size(sw, sh))
                            drawRect(Color.Black, topLeft = Offset.Zero, size = Size(sw, sh), style = Stroke(width = 1f / v.scale))
                        }
                    }
                    // Camadas de baixo para cima; cada uma isolada (opacidade e borracha só afetam a própria camada).
                    Canvas(Modifier.fillMaxSize().graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }) {
                        val v = st.view
                        val proj = st.project
                        val fid = proj.currentFrameId
                        val active = st.activePoints.toList()
                        val activeId = st.activeLayerId
                        val erase = st.tool == Tool.ERASER
                        val width = if (erase) st.eraserSize else st.brushSize
                        val colorArgb = st.colorArgb
                        withTransform({ applyView(v) }) {
                            clipRect(0f, 0f, sw, sh) {
                                for (l in proj.layers) {
                                    if (!l.visible) continue
                                    val strokes = l.drawingAt(fid)?.strokes ?: emptyList()
                                    val hasActive = active.isNotEmpty() && l.id == activeId
                                    if (strokes.isEmpty() && !hasActive) continue
                                    val paint = Paint()
                                    paint.alpha = l.opacityPercent / 100f
                                    drawContext.canvas.saveLayer(Rect(0f, 0f, sw, sh), paint)
                                    for (s in strokes) drawBrushStroke(s)
                                    if (hasActive) drawBrushStroke(BrushStroke(active, colorArgb, width, erase))
                                    drawContext.canvas.restore()
                                }
                            }
                        }
                    }
                    // Entrada: 1 dedo desenha; 2 dedos fazem pinça, pan e rotação da vista.
                    Box(
                        Modifier.fillMaxSize().pointerInput(Unit) {
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                if (st.playing) {
                                    do { val e = awaitPointerEvent() } while (e.changes.any { it.pressed })
                                    return@awaitEachGesture
                                }
                                var drawing = true
                                var multi = false
                                st.activePoints.clear()
                                st.activePoints.add(st.view.screenToProject(down.position.toPoint()))
                                down.consume()
                                do {
                                    val event = awaitPointerEvent()
                                    val pressedCount = event.changes.count { it.pressed }
                                    if (pressedCount >= 2) {
                                        if (drawing) { drawing = false; st.activePoints.clear() }
                                        multi = true
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
                    // Localização de frame (sempre visível).
                    Text(
                        "FRAME ${st.project.currentFrame + 1} DE ${st.project.frameCount}",
                        color = StudioColors.Ink, fontSize = 12.sp,
                        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 4.dp)
                            .background(StudioColors.Panel.copy(alpha = 0.75f), RoundedCornerShape(6.dp)).padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                    ToolCell(
                        width = 34.dp, height = 34.dp, onClick = {
                            st.view = ViewTransform.fitSheet(sw, sh, canvasSize.width.toFloat(), canvasSize.height.toFloat(), 12f)
                        }, modifier = Modifier.align(Alignment.TopEnd).padding(6.dp)
                    ) { Icon(Icons.Default.FitScreen, "Ajustar à folha", tint = it) }
                }
            }
        }
        ExpandTab(
            expanded = expanded, onSet = { expanded = it },
            modifier = Modifier.align(Alignment.TopCenter).offset { IntOffset(0, topBarHeightPx) },
        )
    }

    when (val d = dialog) {
        null -> {}
        EditorDialog.Menu -> StudioDialog("Menu", { dialog = null }) {
            MenuItem("Importar imagem", false) { soon("Importar imagem") }
            MenuItem("Importar vídeo", false) { soon("Importar vídeo") }
            MenuItem("Exportar vídeo MP4", false) { soon("Exportar MP4") }
            MenuItem("Exportar PNG (frame atual)", false) { soon("Exportar PNG") }
            MenuItem("Exportar sequência PNG", false) { soon("Exportar sequência PNG") }
            MenuItem("Exportar ZIP com todos os frames", false) { soon("Exportar ZIP") }
            MenuItem("Adicionar camada") { st.addLayer(LayerKind.DRAWING); dialog = null }
            MenuItem("Adicionar cenário") { st.addLayer(LayerKind.SCENERY); dialog = null }
            MenuItem("Voltar aos projetos") { dialog = null; close() }
        }
        is EditorDialog.ColorPicker -> ColorDialog(
            initialMethod = d.method, initial = st.colorArgb, recents = st.recentColors,
            onConfirm = { c ->
                st.colorArgb = c
                st.recentColors.remove(c)
                st.recentColors.add(0, c)
                while (st.recentColors.size > 8) st.recentColors.removeAt(st.recentColors.lastIndex)
                dialog = null
            },
            onDismiss = { dialog = null },
        )
        is EditorDialog.Plus -> {
            val l = st.project.layers.firstOrNull { it.id == d.layerId }
            if (l == null) dialog = null else {
                val scenery = l.kind == LayerKind.SCENERY
                StudioDialog(if (scenery) "Cenário" else "Camada", { dialog = null }) {
                    MenuItem(if (scenery) "Novo cenário" else "Nova camada") { st.addLayer(l.kind); dialog = null }
                    MenuItem(if (scenery) "Duplicar cenário" else "Duplicar camada") { st.duplicateLayer(l.id); dialog = null }
                }
            }
        }
        is EditorDialog.LayerOptions -> {
            val l = st.project.layers.firstOrNull { it.id == d.layerId }
            if (l == null) dialog = null else LayerOptionsDialog(
                layer = l,
                canSeparate = l.kind == LayerKind.SCENERY && l.drawingAt(st.project.currentFrameId) != null,
                canDelete = st.project.layers.size > 1,
                onApply = { n, o, vis ->
                    st.edit { p ->
                        var q = p
                        if (n.trim() != l.name) q = q.renameLayer(l.id, n)
                        if (o != l.opacityPercent) q = q.setLayerOpacity(l.id, o)
                        if (vis != l.visible) q = q.toggleLayerVisible(l.id)
                        q
                    }
                    dialog = null
                },
                onSeparate = { st.edit { p -> p.separateSceneryFromCurrent(l.id) }; dialog = null },
                onDelete = { st.deleteLayer(l.id); dialog = null },
                onDismiss = { dialog = null },
            )
        }
        is EditorDialog.FrameOptions -> {
            val i = d.index
            StudioDialog("Frame ${i + 1}", { dialog = null }) {
                MenuItem("Copiar frame") { st.clip = st.project.frameClip(i); dialog = null }
                MenuItem("Colar à esquerda", st.clip != null) { st.edit { p -> p.insertFrameAt(i, st.clip ?: emptyMap()) }; dialog = null }
                MenuItem("Colar à direita", st.clip != null) { st.edit { p -> p.insertFrameAt(i + 1, st.clip ?: emptyMap()) }; dialog = null }
                MenuItem("Inserir frame em branco à esquerda") { st.edit { p -> p.insertFrameAt(i) }; dialog = null }
                MenuItem("Inserir frame em branco à direita") { st.edit { p -> p.insertFrameAt(i + 1) }; dialog = null }
                MenuItem("Duplicar frame") { st.edit { p -> p.select(i).duplicateCurrentFrame() }; dialog = null }
                MenuItem("Apagar frame", st.project.frameCount > 1) { st.edit { p -> p.select(i).deleteCurrentFrame() }; dialog = null }
            }
        }
        is EditorDialog.BrushOptions -> {
            val isBrush = d.tool == Tool.BRUSH
            StudioDialog(if (isBrush) "Pincel" else "Borracha", { dialog = null }, confirmText = null, onConfirm = null) {
                val size = if (isBrush) st.brushSize else st.eraserSize
                Text("Tamanho: ${size.roundToInt()} px", color = StudioColors.Ink, fontSize = 13.sp)
                Slider(value = size, onValueChange = { if (isBrush) st.brushSize = it else st.eraserSize = it }, valueRange = 1f..80f)
                Text("Categorias e pincéis recentes: em desenvolvimento.", color = StudioColors.Ink.copy(alpha = 0.6f), fontSize = 11.sp)
            }
        }
        EditorDialog.FpsEdit -> {
            var text by remember { mutableStateOf(fps.toString()) }
            StudioDialog("FPS do projeto", { dialog = null }, "OK", {
                val v = text.toIntOrNull()
                if (v != null && v in 1..120) { fps = v; dialog = null } else toast("Use um valor entre 1 e 120")
            }) {
                OutlinedTextField(
                    text, { text = it.filter { ch -> ch.isDigit() }.take(3) }, singleLine = true,
                    label = { Text("FPS (1–120)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Barra superior: menu | camadas e cenários (+) | timeline | câmara | cor
// ---------------------------------------------------------------------------------------------

@Composable
private fun TopBar(
    st: EditorState,
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
    val hScroll = rememberScrollState()
    val vScroll = rememberScrollState()
    Row(
        modifier.fillMaxWidth().background(StudioColors.Panel).padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.Top,
    ) {
        ToolCell(width = 36.dp, height = 36.dp, onClick = onMenu) { Icon(Icons.Default.Menu, "Menu", tint = it) }
        Spacer(Modifier.width(6.dp))
        Row(Modifier.weight(1f).heightIn(max = if (expanded) 170.dp else 68.dp).verticalScroll(vScroll)) {
            Column(Modifier.width(140.dp)) {
                for (l in rows) {
                    Row(Modifier.height(32.dp).padding(vertical = 1.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(24.dp).clip(RoundedCornerShape(6.dp)).background(StudioColors.PanelLight).clickable { onPlus(l) },
                            contentAlignment = Alignment.Center,
                        ) { Text("+", color = StudioColors.Ink, fontSize = 16.sp) }
                        Spacer(Modifier.width(4.dp))
                        val isActive = l.id == st.activeLayerId
                        Box(
                            Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(6.dp))
                                .background(if (isActive) StudioColors.Cream else StudioColors.PanelLight)
                                .combinedClickable(onClick = { st.activeLayerId = l.id }, onLongClick = { onLayerOptions(l) })
                                .padding(horizontal = 6.dp),
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            Text(
                                l.name, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                color = if (isActive) Color.Black else StudioColors.Ink,
                                textDecoration = if (l.visible) TextDecoration.None else TextDecoration.LineThrough,
                            )
                        }
                    }
                }
            }
            Column(Modifier.weight(1f).horizontalScroll(hScroll)) {
                for (l in rows) {
                    Row(Modifier.height(32.dp), verticalAlignment = Alignment.CenterVertically) {
                        st.project.frameIds.forEachIndexed { i, fid ->
                            val hasContent = l.drawingAt(fid) != null
                            val isCurrent = i == st.project.currentFrame
                            val shape = RoundedCornerShape(4.dp)
                            Box(
                                Modifier.padding(horizontal = 1.dp).size(width = 34.dp, height = 28.dp).clip(shape)
                                    .background(
                                        if (!hasContent) StudioColors.PanelLight
                                        else if (l.kind == LayerKind.SCENERY) Color(0xFF8DB48E) else Color(0xFFE8C872)
                                    )
                                    .border(if (isCurrent) 2.dp else 1.dp, if (isCurrent) StudioColors.Cream else StudioColors.Ink.copy(alpha = 0.2f), shape)
                                    .combinedClickable(
                                        onClick = { st.activeLayerId = l.id; st.selectFrame(i) },
                                        onLongClick = { st.activeLayerId = l.id; st.selectFrame(i); onFrameOptions(i) },
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text("${i + 1}", fontSize = 10.sp, color = if (hasContent) Color.Black else StudioColors.Ink.copy(alpha = 0.65f))
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.width(6.dp))
        ToolCell(width = 36.dp, height = 36.dp, onClick = onCamera) { Icon(Icons.Default.Videocam, "Câmara", tint = it) }
        Spacer(Modifier.width(6.dp))
        // Painel de cor: amostra + métodos (paleta, RGB, HSB)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(width = 46.dp, height = 34.dp).clip(RoundedCornerShape(8.dp)).background(Color(st.colorArgb))
                    .border(2.dp, StudioColors.Ink, RoundedCornerShape(8.dp)).clickable { onColor(ColorMethod.PALETTE) }
            )
            Spacer(Modifier.width(4.dp))
            ToolCell(width = 32.dp, height = 34.dp, onClick = { onColor(ColorMethod.PALETTE) }) { PaletteGlyph(it) }
            Spacer(Modifier.width(3.dp))
            ToolCell(width = 32.dp, height = 34.dp, onClick = { onColor(ColorMethod.RGB) }) { RgbGlyph(it) }
            Spacer(Modifier.width(3.dp))
            ToolCell(width = 32.dp, height = 34.dp, onClick = { onColor(ColorMethod.HSB) }) { HsbGlyph() }
        }
    }
}

@Composable
private fun ExpandTab(expanded: Boolean, onSet: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val curExpanded by rememberUpdatedState(expanded)
    val curSet by rememberUpdatedState(onSet)
    Box(
        modifier.width(120.dp).height(18.dp)
            .clip(RoundedCornerShape(bottomStart = 10.dp, bottomEnd = 10.dp))
            .background(StudioColors.Panel)
            .pointerInput(Unit) {
                detectVerticalDragGestures { change, dy ->
                    change.consume()
                    if (dy > 4f) curSet(true) else if (dy < -4f) curSet(false)
                }
            }
            .clickable { curSet(!curExpanded) },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.width(46.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(StudioColors.Ink.copy(alpha = 0.7f)))
    }
}

// ---------------------------------------------------------------------------------------------
// Painel de ferramentas: grelha de 2 colunas + barra de navegação de frames
// ---------------------------------------------------------------------------------------------

@Composable
private fun ToolPanel(
    st: EditorState,
    fps: Int,
    onFpsEdit: () -> Unit,
    onFpsStep: (Int) -> Unit,
    onToolOptions: (Tool) -> Unit,
    onSoon: (String) -> Unit,
    onAddFrame: () -> Unit,
    onCycleMode: () -> Unit,
) {
    Column(
        Modifier.width(100.dp).fillMaxHeight().verticalScroll(rememberScrollState()).padding(4.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Column(
            Modifier.clip(RoundedCornerShape(10.dp)).background(StudioColors.Panel).padding(3.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                ToolCell(selected = st.tool == Tool.BRUSH, onLongClick = { onToolOptions(Tool.BRUSH) }, onClick = { st.tool = Tool.BRUSH }) {
                    Icon(Icons.Default.Brush, "Pincel", tint = it)
                }
                ToolCell(selected = st.tool == Tool.ERASER, onLongClick = { onToolOptions(Tool.ERASER) }, onClick = { st.tool = Tool.ERASER }) {
                    EraserGlyph(it)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                ToolCell(enabled = false, onClick = { onSoon("Balde de tinta") }) { Icon(Icons.Default.FormatColorFill, "Balde de tinta", tint = it) }
                ToolCell(enabled = false, onClick = { onSoon("Laço") }) { LassoGlyph(it) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                ToolCell(enabled = st.history.canUndo && !st.playing, onClick = { st.undo() }) { Icon(Icons.Default.Undo, "Desfazer", tint = it) }
                ToolCell(enabled = st.history.canRedo && !st.playing, onClick = { st.redo() }) { Icon(Icons.Default.Redo, "Refazer", tint = it) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                ToolCell(onClick = onFpsEdit) { Text("FPS", color = it, fontSize = 12.sp) }
                ToolCell(onClick = onFpsEdit) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("$fps", color = it, fontSize = 13.sp, modifier = Modifier.padding(start = 4.dp))
                        Column {
                            Icon(Icons.Default.KeyboardArrowUp, "Mais FPS", tint = it, modifier = Modifier.size(17.dp).clickable { onFpsStep(1) })
                            Icon(Icons.Default.KeyboardArrowDown, "Menos FPS", tint = it, modifier = Modifier.size(17.dp).clickable { onFpsStep(-1) })
                        }
                    }
                }
            }
            ToolCell(width = 83.dp, onClick = onAddFrame) { Text("+", color = it, fontSize = 22.sp) }
        }
        Row(
            Modifier.clip(RoundedCornerShape(10.dp)).background(StudioColors.Panel).padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            ToolCell(width = 25.dp, enabled = st.project.currentFrame > 0 && !st.playing, onClick = { st.go(-1) }) {
                TriangleGlyph(it, pointsLeft = true, modifier = Modifier.size(16.dp))
            }
            ToolCell(width = 25.dp, selected = st.playing, onLongClick = onCycleMode, onClick = { st.togglePlay() }) {
                Icon(if (st.playing) Icons.Default.Pause else Icons.Default.PlayArrow, if (st.playing) "Parar" else "Reproduzir", tint = it, modifier = Modifier.size(18.dp))
            }
            ToolCell(width = 25.dp, enabled = st.project.currentFrame < st.project.frameCount - 1 && !st.playing, onClick = { st.go(1) }) {
                TriangleGlyph(it, pointsLeft = false, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun ToolCell(
    modifier: Modifier = Modifier,
    width: Dp = 40.dp,
    height: Dp = 40.dp,
    selected: Boolean = false,
    enabled: Boolean = true,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
    content: @Composable (Color) -> Unit,
) {
    val tint = if (selected) Color.Black else if (enabled) StudioColors.Ink else StudioColors.Ink.copy(alpha = 0.4f)
    Box(
        modifier.size(width = width, height = height).clip(RoundedCornerShape(8.dp))
            .background(if (selected) StudioColors.Cream else StudioColors.PanelLight)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        contentAlignment = Alignment.Center,
    ) { content(tint) }
}

// ---------------------------------------------------------------------------------------------
// Diálogos (compactos, pensados para ecrã horizontal)
// ---------------------------------------------------------------------------------------------

@Composable
private fun StudioDialog(
    title: String,
    onDismiss: () -> Unit,
    confirmText: String? = "OK",
    onConfirm: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val maxH = LocalConfiguration.current.screenHeightDp.dp * 0.92f
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            color = StudioColors.Panel, shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth(0.82f).widthIn(max = 620.dp).heightIn(max = maxH),
        ) {
            Column(Modifier.padding(12.dp)) {
                Text(title, color = StudioColors.Cream, fontSize = 15.sp)
                Spacer(Modifier.height(6.dp))
                Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) { content() }
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text(if (onConfirm != null) "Cancelar" else "Fechar") }
                    if (onConfirm != null) Button(onClick = onConfirm) { Text(confirmText ?: "OK") }
                }
            }
        }
    }
}

@Composable
private fun MenuItem(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    Text(
        if (enabled) text else "$text (em breve)",
        color = if (enabled) StudioColors.Ink else StudioColors.Ink.copy(alpha = 0.45f),
        fontSize = 14.sp,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 9.dp, horizontal = 4.dp),
    )
}

@Composable
private fun LayerOptionsDialog(
    layer: Layer,
    canSeparate: Boolean,
    canDelete: Boolean,
    onApply: (String, Int, Boolean) -> Unit,
    onSeparate: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(layer.name) }
    var opacity by remember { mutableFloatStateOf(layer.opacityPercent.toFloat()) }
    var visible by remember { mutableStateOf(layer.visible) }
    StudioDialog(if (layer.kind == LayerKind.SCENERY) "Cenário" else "Camada", onDismiss, "OK", { onApply(name, opacity.roundToInt(), visible) }) {
        OutlinedTextField(name, { name = it }, label = { Text("Nome") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Text("Opacidade: ${opacity.roundToInt()}%", color = StudioColors.Ink, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
        Slider(value = opacity, onValueChange = { opacity = it }, valueRange = 0f..100f)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(checked = visible, onCheckedChange = { visible = it })
            Spacer(Modifier.width(8.dp))
            Text("Visível", color = StudioColors.Ink, fontSize = 13.sp)
        }
        if (canSeparate) TextButton(onClick = onSeparate) { Text("Separar a partir deste frame") }
        if (canDelete) TextButton(onClick = onDelete) { Text("Apagar", color = Color(0xFFFF8A80)) }
    }
}

@Composable
private fun ColorDialog(
    initialMethod: ColorMethod,
    initial: Int,
    recents: List<Int>,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var method by remember { mutableStateOf(initialMethod) }
    var color by remember { mutableIntStateOf(initial or (0xFF shl 24)) }
    StudioDialog("Cor", onDismiss, "OK", { onConfirm(color) }) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (m in ColorMethod.values()) {
                Box(
                    Modifier.clip(RoundedCornerShape(8.dp))
                        .background(if (m == method) StudioColors.Cream else StudioColors.PanelLight)
                        .clickable { method = m }.padding(horizontal = 12.dp, vertical = 6.dp)
                ) { Text(m.label, color = if (m == method) Color.Black else StudioColors.Ink, fontSize = 13.sp) }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.Top) {
            Box(Modifier.size(64.dp).clip(RoundedCornerShape(10.dp)).background(Color(color)).border(2.dp, StudioColors.Ink, RoundedCornerShape(10.dp)))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                when (method) {
                    ColorMethod.PALETTE -> PaletteGrid(recents) { color = it }
                    ColorMethod.RGB -> RgbSliders(color) { color = it }
                    ColorMethod.HSB -> HsbSliders(color) { color = it }
                }
            }
        }
    }
}

@Composable
private fun Swatch(c: Int, onClick: () -> Unit) {
    Box(
        Modifier.size(26.dp).clip(RoundedCornerShape(6.dp)).background(Color(c))
            .border(1.dp, StudioColors.Ink.copy(alpha = 0.3f), RoundedCornerShape(6.dp)).clickable(onClick = onClick)
    )
}

@Composable
private fun PaletteGrid(recents: List<Int>, onPick: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (recents.isNotEmpty()) {
            Text("Recentes", color = StudioColors.Ink.copy(alpha = 0.7f), fontSize = 11.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) { for (c in recents.take(8)) Swatch(c) { onPick(c) } }
        }
        for (row in Palette.chunked(8)) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) { for (c in row) Swatch(c) { onPick(c) } }
        }
    }
}

@Composable
private fun ChannelSlider(label: String, value: Float, max: Float, shown: String, onChange: (Float) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = StudioColors.Ink, fontSize = 12.sp, modifier = Modifier.width(18.dp))
        Slider(value = value, onValueChange = onChange, valueRange = 0f..max, modifier = Modifier.weight(1f))
        Text(shown, color = StudioColors.Ink, fontSize = 12.sp, modifier = Modifier.width(34.dp))
    }
}

@Composable
private fun RgbSliders(initial: Int, onChange: (Int) -> Unit) {
    var r by remember { mutableFloatStateOf(((initial shr 16) and 0xFF).toFloat()) }
    var g by remember { mutableFloatStateOf(((initial shr 8) and 0xFF).toFloat()) }
    var b by remember { mutableFloatStateOf((initial and 0xFF).toFloat()) }
    fun push() = onChange((0xFF shl 24) or (r.roundToInt() shl 16) or (g.roundToInt() shl 8) or b.roundToInt())
    ChannelSlider("R", r, 255f, r.roundToInt().toString()) { r = it; push() }
    ChannelSlider("G", g, 255f, g.roundToInt().toString()) { g = it; push() }
    ChannelSlider("B", b, 255f, b.roundToInt().toString()) { b = it; push() }
}

@Composable
private fun HsbSliders(initial: Int, onChange: (Int) -> Unit) {
    val hsv = remember { FloatArray(3).also { android.graphics.Color.colorToHSV(initial, it) } }
    var h by remember { mutableFloatStateOf(hsv[0]) }
    var s by remember { mutableFloatStateOf(hsv[1]) }
    var v by remember { mutableFloatStateOf(hsv[2]) }
    fun push() = onChange(android.graphics.Color.HSVToColor(floatArrayOf(h, s, v)))
    ChannelSlider("H", h, 360f, h.roundToInt().toString()) { h = it; push() }
    ChannelSlider("S", s, 1f, (s * 100).roundToInt().toString()) { s = it; push() }
    ChannelSlider("B", v, 1f, (v * 100).roundToInt().toString()) { v = it; push() }
}
