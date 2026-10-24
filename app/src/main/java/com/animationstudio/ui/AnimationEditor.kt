package com.animationstudio.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateRotation
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FitScreen
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.DrawTransform
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.animationstudio.data.ProjectMeta
import com.animationstudio.data.ProjectStore
import com.animationstudio.domain.BrushLibrary
import com.animationstudio.domain.LayerKind
import com.animationstudio.domain.PlayMode
import com.animationstudio.domain.Point
import com.animationstudio.domain.Project
import com.animationstudio.domain.StrokeStabilizer
import com.animationstudio.domain.ViewTransform
import com.animationstudio.domain.addFill
import com.animationstudio.domain.frameClip
import com.animationstudio.domain.insertFrameAt
import com.animationstudio.domain.moveLayer
import com.animationstudio.domain.nextPlaybackStep
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.hypot
import kotlin.math.roundToInt

private sealed interface EditorDialog {
    data object Menu : EditorDialog
    data class ColorPicker(val method: ColorMethod) : EditorDialog
    data object AddLayer : EditorDialog
    data class LayerOptions(val layerId: Long) : EditorDialog
    data class FrameActions(val index: Int) : EditorDialog
    data class BrushLibrary(val tool: Tool) : EditorDialog
    data class BrushSettings(val tool: Tool) : EditorDialog
    data object BucketSettings : EditorDialog
    data object FpsEdit : EditorDialog
    data object Onion : EditorDialog
    data object Grid : EditorDialog
    data object Resolution : EditorDialog
    data object Organize : EditorDialog
}

private fun DrawTransform.applyView(v: ViewTransform) {
    translate(v.tx, v.ty)
    rotate(v.rotationDegrees, Offset.Zero)
    scale(v.scale, v.scale, Offset.Zero)
}

private fun selTransform(pivot: Offset, dx: Float, dy: Float, deg: Float, scale: Float): (DrawTransform) -> Unit = { t ->
    t.translate(dx, dy)
    t.rotate(deg, pivot)
    t.scale(scale, scale, pivot)
}

private fun Offset.toPoint() = Point(x, y)

private fun isPen(c: PointerInputChange): Boolean = c.type == PointerType.Stylus || c.type == PointerType.Eraser

private suspend fun persist(store: ProjectStore, meta: ProjectMeta, fps: Int, project: Project): Boolean =
    withContext(Dispatchers.IO) {
        try {
            store.save(meta.copy(fps = fps, modifiedAt = System.currentTimeMillis()), project)
            val tw = 360
            val th = (tw.toFloat() * meta.height / meta.width).roundToInt().coerceAtLeast(1)
            val bmp = renderComposite(project, project.currentFrame, meta.width, meta.height, tw, th, PaperColor, useCache = false)
            store.saveThumbnail(meta.id, bmp.asAndroidBitmap())
            true
        } catch (e: Exception) {
            false
        }
    }

@Composable
private fun OverlayChip(
    text: String,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    danger: Boolean = false,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(9.dp)
    Box(
        modifier.clip(shape)
            .background(if (selected) StudioColors.Accent else StudioColors.Surface.copy(alpha = 0.94f))
            .border(1.dp, if (selected) StudioColors.Accent else StudioColors.Outline, shape)
            .combinedClickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = StudioType.Label.copy(fontSize = 11.sp, color = if (danger) StudioColors.Danger else Color.White))
    }
}

@Composable
fun AnimationEditor(meta: ProjectMeta, initial: Project, store: ProjectStore, onClose: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { Prefs(context) }
    val st = remember {
        EditorState(initial).also {
            it.penOnly = prefs.penOnly
            it.palmReject = prefs.palmReject
            it.timelineBottom = prefs.timelineBottom
            it.toolsRight = prefs.toolsRight
            it.stabilizer = prefs.stabilizer
        }
    }
    var fps by remember { mutableIntStateOf(meta.fps) }
    var sheetW by remember { mutableIntStateOf(meta.width) }
    var sheetH by remember { mutableIntStateOf(meta.height) }
    var expanded by remember { mutableStateOf(false) }
    var topBarHeightPx by remember { mutableIntStateOf(0) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var dialog by remember { mutableStateOf<EditorDialog?>(null) }
    val scope = rememberCoroutineScope()
    val sw = sheetW.toFloat()
    val sh = sheetH.toFloat()
    val curMeta = meta.copy(width = sheetW, height = sheetH)
    val baseName = remember(meta.name) { meta.name.replace(Regex("[^A-Za-z0-9_-]"), "_").ifBlank { "animacao" } }

    fun toast(msg: String) = Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
    fun soon(what: String) = toast("$what: em desenvolvimento")
    fun close() {
        scope.launch {
            if (!persist(store, curMeta, fps, st.project)) toast("Não foi possível guardar o projeto")
            onClose()
        }
    }

    // Balde: corre fora da thread principal. (A causa do erro anterior: o pedido era reposto a null dentro
    // do próprio efeito que o observava, o que cancelava o trabalho e mostrava "não foi possível preencher".)
    fun requestFill(p: Point) {
        if (st.busyFill) return
        val snapshot = st.project
        val layer = snapshot.layers.firstOrNull { it.id == st.activeLayerId }
        if (layer == null || !layer.visible) {
            toast("Escolha uma camada visível para preencher")
            return
        }
        st.busyFill = true
        scope.launch {
            val region = try {
                withContext(Dispatchers.Default) { computeFill(snapshot, sheetW, sheetH, p.x, p.y, st.colorArgb, st.bucketGap, st.bucketGrow) }
            } catch (e: CancellationException) {
                st.busyFill = false
                throw e
            } catch (e: Throwable) {
                null
            }
            st.busyFill = false
            if (region == null) toast("Toque numa área dentro da folha, fora das linhas")
            else if (st.project.currentFrameId == snapshot.currentFrameId) st.edit { it.addFill(st.activeLayerId, region) }
        }
    }

    val pngLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/png")) { uri ->
        if (uri != null) scope.launch {
            val ok = Exporter.exportPng(context, uri, st.project, st.project.currentFrame, sheetW, sheetH)
            toast(if (ok) "PNG exportado" else "Falha ao exportar o PNG")
        }
    }
    val zipLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) scope.launch {
            toast("A exportar…")
            val ok = Exporter.exportZip(context, uri, st.project, sheetW, sheetH, baseName)
            toast(if (ok) "ZIP exportado" else "Falha ao exportar o ZIP")
        }
    }
    val treeLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) scope.launch {
            toast("A exportar…")
            val ok = Exporter.exportSequence(context, uri, st.project, sheetW, sheetH, baseName)
            toast(if (ok) "Sequência exportada" else "Falha ao exportar a sequência")
        }
    }

    BackHandler { close() }

    LaunchedEffect(st.project, fps, sheetW, sheetH) {
        delay(400)
        if (!persist(store, curMeta, fps, st.project)) toast("Não foi possível guardar o projeto")
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

    val onTimelineGrip: (Float, Float) -> Unit = { _, dy ->
        if (dy > 40f) { st.timelineBottom = true; prefs.timelineBottom = true }
        else if (dy < -40f) { st.timelineBottom = false; prefs.timelineBottom = false }
    }
    val onToolsGrip: (Float, Float) -> Unit = { dx, _ ->
        if (dx > 40f) { st.toolsRight = true; prefs.toolsRight = true }
        else if (dx < -40f) { st.toolsRight = false; prefs.toolsRight = false }
    }

    val timelineBar: @Composable () -> Unit = {
        TopBar(
            st = st, sw = sheetW, sh = sheetH, expanded = expanded,
            onMenu = { dialog = EditorDialog.Menu },
            onAdd = { dialog = EditorDialog.AddLayer },
            onCamera = { soon("Câmara") },
            onColor = { dialog = EditorDialog.ColorPicker(it) },
            onOnionOptions = { dialog = EditorDialog.Onion },
            onLayerOptions = { dialog = EditorDialog.LayerOptions(it.id) },
            onMoveLayer = { id, delta -> st.edit { p -> p.moveLayer(id, delta) } },
            onFrameOptions = { dialog = EditorDialog.FrameActions(it) },
            onGripEnd = onTimelineGrip,
            modifier = Modifier.onSizeChanged { topBarHeightPx = it.height },
        )
    }

    val toolPanel: @Composable () -> Unit = {
        ToolPanel(
            st = st, fps = fps,
            onFpsEdit = { dialog = EditorDialog.FpsEdit },
            onFpsStep = { fps = (fps + it).coerceIn(1, 120) },
            onToolTap = { t ->
                if (t == st.tool && (t == Tool.BRUSH || t == Tool.ERASER)) dialog = EditorDialog.BrushLibrary(t) else st.selectTool(t)
            },
            onToolLibrary = { t ->
                if (t == Tool.BRUSH || t == Tool.ERASER) { st.selectTool(t); dialog = EditorDialog.BrushLibrary(t) }
            },
            onToolSettings = { t ->
                st.selectTool(t)
                dialog = if (t == Tool.BUCKET) EditorDialog.BucketSettings
                else if (t == Tool.BRUSH || t == Tool.ERASER) EditorDialog.BrushSettings(t) else null
            },
            onAddFrame = { st.edit { p -> p.insertFrameAt(p.currentFrame + 1) } },
            onCycleMode = {
                st.playMode = PlayMode.values()[(st.playMode.ordinal + 1) % PlayMode.values().size]
                toast("Reprodução: " + when (st.playMode) { PlayMode.ONCE -> "uma vez"; PlayMode.LOOP -> "loop"; PlayMode.PING_PONG -> "ping-pong" })
            },
            onGripEnd = onToolsGrip,
        )
    }

    Box(Modifier.fillMaxSize().background(StudioColors.Navy)) {
        Column(Modifier.fillMaxSize()) {
            if (!st.timelineBottom) timelineBar()
            Row(Modifier.weight(1f).fillMaxWidth()) {
                if (!st.toolsRight) toolPanel()
                Box(
                    Modifier.weight(1f).fillMaxHeight()
                        .padding(
                            start = if (st.toolsRight) 6.dp else 0.dp, end = if (st.toolsRight) 0.dp else 6.dp,
                            top = if (st.timelineBottom) 6.dp else 0.dp, bottom = if (st.timelineBottom) 0.dp else 6.dp,
                        )
                        .clip(RoundedCornerShape(14.dp)).background(StudioColors.Workspace)
                        .onSizeChanged { canvasSize = it }
                ) {
                    // Folha branca sobre o espaço de trabalho escuro.
                    Canvas(Modifier.fillMaxSize()) {
                        val v = st.view
                        withTransform({ applyView(v) }) {
                            drawRect(Color.White, topLeft = Offset.Zero, size = Size(sw, sh))
                            drawRect(Color.Black, topLeft = Offset.Zero, size = Size(sw, sh), style = Stroke(width = 1f / v.scale))
                        }
                    }
                    // Cenário, onion skin, camadas de desenho, grelha e seleção do laço.
                    Canvas(Modifier.fillMaxSize().graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }) {
                        val v = st.view
                        val proj = st.project
                        val fid = proj.currentFrameId
                        val live = if (st.isInkTool && st.activePoints.isNotEmpty()) st.makeStroke(st.activePoints.toList()) else null
                        val sel = st.selection
                        val selActive = sel != null && sel.frameId == fid
                        val selT: ((DrawTransform) -> Unit)? = if (sel != null && selActive) {
                            selTransform(Offset(sel.bounds.cx, sel.bounds.cy), st.selDx, st.selDy, Math.toDegrees(st.selRot.toDouble()).toFloat(), st.selScale)
                        } else null
                        val selLayer = if (sel != null && selActive) sel.layerId else -1L
                        val selIdx = if (sel != null && selActive) sel.indices else null
                        withTransform({ applyView(v) }) {
                            clipRect(0f, 0f, sw, sh) {
                                drawFrameLayers(proj, fid, sw, sh, LayerKind.SCENERY, null, true, live, st.activeLayerId, selLayer, selIdx, selT)
                                if (st.onionOn && !st.playing) {
                                    val cur = proj.currentFrame
                                    for (k in st.onionBefore downTo 1) {
                                        val i = cur - k
                                        if (i >= 0) drawFrameLayers(proj, proj.frameIds[i], sw, sh, LayerKind.DRAWING, StudioColors.Rose.copy(alpha = 0.34f / k), true)
                                    }
                                    for (k in st.onionAfter downTo 1) {
                                        val i = cur + k
                                        if (i < proj.frameCount) drawFrameLayers(proj, proj.frameIds[i], sw, sh, LayerKind.DRAWING, StudioColors.Mint.copy(alpha = 0.34f / k), true)
                                    }
                                }
                                drawFrameLayers(proj, fid, sw, sh, LayerKind.DRAWING, null, true, live, st.activeLayerId, selLayer, selIdx, selT)
                                drawGridOverlay(st.gridType, st.gridCell, sw, sh, Color(0xFF3DA5FF).copy(alpha = 0.55f), 1f / v.scale)
                                val dash = PathEffect.dashPathEffect(floatArrayOf(10f / v.scale, 6f / v.scale))
                                if (sel != null && selActive) {
                                    val b = sel.bounds
                                    withTransform({ selT?.invoke(this) }) {
                                        drawRect(
                                            StudioColors.Accent, Offset(b.minX, b.minY), Size(b.maxX - b.minX, b.maxY - b.minY),
                                            style = Stroke(width = 2f / v.scale, pathEffect = dash)
                                        )
                                    }
                                }
                                if (st.tool == Tool.LASSO && st.activePoints.size > 1) {
                                    val path = Path()
                                    val pts = st.activePoints.toList()
                                    path.moveTo(pts[0].x, pts[0].y)
                                    for (i in 1 until pts.size) path.lineTo(pts[i].x, pts[i].y)
                                    drawPath(path, StudioColors.Accent, style = Stroke(width = 2f / v.scale, pathEffect = dash))
                                }
                            }
                        }
                    }
                    // Entrada: 1 dedo/caneta usa a ferramenta; 2 dedos navegam (ou transformam a seleção).
                    Box(
                        Modifier.fillMaxSize().pointerInput(Unit) {
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                if (st.playing) {
                                    do { val e = awaitPointerEvent() } while (e.changes.any { it.pressed })
                                    return@awaitEachGesture
                                }
                                val tool = st.tool
                                val sel = st.selection
                                val downIsPen = isPen(down)
                                val navOnly = st.penOnly && !downIsPen
                                val inkTool = st.isInkTool
                                val lassoDraw = tool == Tool.LASSO && sel == null
                                val stab = if (inkTool && !navOnly) StrokeStabilizer(st.stabilizer) else null
                                var drawing = !navOnly && (inkTool || lassoDraw)
                                var multi = false
                                var moved = false
                                var rejected = false
                                val downPos = down.position
                                var lastScreen = downPos
                                var lastRaw = st.view.screenToProject(downPos.toPoint())

                                fun pushPoint(p: Point) {
                                    val last = st.activePoints.lastOrNull()
                                    if (last == null || hypot(p.x - last.x, p.y - last.y) >= 0.2f / st.view.scale) st.activePoints.add(p)
                                }

                                st.activePoints.clear()
                                if (drawing) st.activePoints.add(if (stab != null) stab.start(lastRaw) else lastRaw)
                                down.consume()
                                do {
                                    val event = awaitPointerEvent()
                                    val penDown = event.changes.any { it.pressed && isPen(it) }
                                    // Rejeição de palma: se a caneta aparece, o toque que começou antes é ignorado.
                                    if (st.palmReject && !downIsPen && penDown) {
                                        if (drawing) { drawing = false; st.activePoints.clear() }
                                        rejected = true
                                    }
                                    if (rejected) {
                                        event.changes.forEach { it.consume() }
                                        continue
                                    }
                                    val changes = if (st.palmReject && downIsPen) event.changes.filter { isPen(it) } else event.changes
                                    val pressedCount = changes.count { it.pressed }
                                    if (pressedCount >= 2) {
                                        multi = true
                                        if (drawing) { drawing = false; st.activePoints.clear() }
                                    }
                                    val navigating = pressedCount >= 2 || (navOnly && pressedCount >= 1)
                                    if (navigating) {
                                        val c = event.calculateCentroid(useCurrent = true)
                                        if (c.isSpecified) {
                                            val pan = event.calculatePan()
                                            val zoom = event.calculateZoom()
                                            val rotDeg = event.calculateRotation()
                                            if (pressedCount >= 2 && tool == Tool.LASSO && sel != null && st.selMode == SelMode.TRANSFORM) {
                                                val cur = st.view.screenToProject(c.toPoint())
                                                val prev = st.view.screenToProject(Point(c.x - pan.x, c.y - pan.y))
                                                st.selDx += cur.x - prev.x
                                                st.selDy += cur.y - prev.y
                                                st.selScale = (st.selScale * zoom).coerceIn(0.05f, 40f)
                                                st.selRot += Math.toRadians(rotDeg.toDouble()).toFloat()
                                            } else {
                                                val focus = c.toPoint()
                                                var v = st.view.panBy(pan.x, pan.y)
                                                v = v.zoomAround(focus, zoom)
                                                v = v.rotateAround(focus, Math.toRadians(rotDeg.toDouble()).toFloat())
                                                st.view = v
                                            }
                                        }
                                    } else if (pressedCount == 1) {
                                        val ch = changes.firstOrNull { it.id == down.id }
                                        if (ch != null && ch.pressed) {
                                            if ((ch.position - downPos).getDistance() > 12f) moved = true
                                            if (drawing && !multi) {
                                                if (stab != null) {
                                                    for (h in ch.historical) {
                                                        lastRaw = st.view.screenToProject(h.position.toPoint())
                                                        pushPoint(stab.next(lastRaw))
                                                    }
                                                    lastRaw = st.view.screenToProject(ch.position.toPoint())
                                                    pushPoint(stab.next(lastRaw))
                                                } else {
                                                    pushPoint(st.view.screenToProject(ch.position.toPoint()))
                                                }
                                            } else if (tool == Tool.LASSO && sel != null && st.selMode == SelMode.MOVE && !multi && !navOnly) {
                                                val a = st.view.screenToProject(lastScreen.toPoint())
                                                val b = st.view.screenToProject(ch.position.toPoint())
                                                st.selDx += b.x - a.x
                                                st.selDy += b.y - a.y
                                            }
                                            lastScreen = ch.position
                                        }
                                    }
                                    event.changes.forEach { if (it.positionChanged()) it.consume() }
                                } while (event.changes.any { it.pressed })

                                if (drawing && stab != null) {
                                    for (p in stab.finish(lastRaw)) pushPoint(p)
                                    st.commitStroke()
                                } else if (drawing && lassoDraw) {
                                    st.finishLasso()
                                } else if (tool == Tool.BUCKET && !multi && !moved && !rejected && !navOnly) {
                                    requestFill(st.view.screenToProject(downPos.toPoint()))
                                } else if (tool == Tool.LASSO && sel != null && !navOnly) {
                                    st.commitSelection()
                                } else {
                                    st.activePoints.clear()
                                }
                            }
                        }
                    )
                    // Controlos sobre a folha.
                    ToolCell(
                        width = 30.dp, height = 30.dp, modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
                        onClick = { st.view = ViewTransform.fitSheet(sw, sh, canvasSize.width.toFloat(), canvasSize.height.toFloat(), 12f) },
                    ) { Icon(Icons.Default.FitScreen, "Ajustar à folha", tint = it, modifier = androidx.compose.ui.Modifier.then(Modifier)) }
                    if (st.selection != null) {
                        Row(
                            Modifier.align(Alignment.TopCenter).padding(top = 8.dp).studioCard(12.dp).padding(3.dp),
                            horizontalArrangement = Arrangement.spacedBy(3.dp),
                        ) {
                            OverlayChip("Mover", selected = st.selMode == SelMode.MOVE) { st.selMode = SelMode.MOVE }
                            OverlayChip("Transformar", selected = st.selMode == SelMode.TRANSFORM) { st.selMode = SelMode.TRANSFORM }
                            OverlayChip("Apagar", danger = true) { st.deleteSelection() }
                            OverlayChip("OK") { st.clearSelection() }
                        }
                    }
                    Row(
                        Modifier.align(Alignment.BottomCenter).padding(bottom = 6.dp)
                            .clip(RoundedCornerShape(9.dp)).background(StudioColors.Surface.copy(alpha = 0.94f))
                            .border(1.dp, StudioColors.Outline, RoundedCornerShape(9.dp)).padding(horizontal = 10.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text("FRAME", style = StudioType.Caption.copy(fontSize = 9.sp))
                        Text("${st.project.currentFrame + 1} / ${st.project.frameCount}", style = StudioType.Number.copy(fontSize = 12.sp))
                        if (st.busyFill) Text("A PREENCHER…", style = StudioType.Caption.copy(fontSize = 9.sp, color = StudioColors.Accent))
                    }
                }
                if (st.toolsRight) toolPanel()
            }
            if (st.timelineBottom) timelineBar()
        }
        if (st.timelineBottom) {
            ExpandTab(
                expanded = expanded, atBottom = true, onSet = { expanded = it },
                modifier = Modifier.align(Alignment.BottomCenter).offset { IntOffset(0, -topBarHeightPx) },
            )
        } else {
            ExpandTab(
                expanded = expanded, atBottom = false, onSet = { expanded = it },
                modifier = Modifier.align(Alignment.TopCenter).offset { IntOffset(0, topBarHeightPx) },
            )
        }
    }

    when (val d = dialog) {
        null -> {}
        EditorDialog.Menu -> MenuPopup(
            penOnly = st.penOnly, palmReject = st.palmReject,
            onPenOnly = { st.penOnly = it; prefs.penOnly = it },
            onPalm = { st.palmReject = it; prefs.palmReject = it },
            onGrid = { dialog = EditorDialog.Grid },
            onResolution = { dialog = EditorDialog.Resolution },
            onOrganize = { dialog = EditorDialog.Organize },
            onSoon = { soon(it) },
            onExportPng = { dialog = null; pngLauncher.launch("${baseName}_${st.project.currentFrame + 1}.png") },
            onExportSequence = { dialog = null; treeLauncher.launch(null) },
            onExportZip = { dialog = null; zipLauncher.launch("$baseName.zip") },
            onBack = { dialog = null; close() },
            onDismiss = { dialog = null },
        )
        is EditorDialog.ColorPicker -> ColorPickerDialog(
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
        EditorDialog.AddLayer -> AddLayerDialog(
            onLayer = { st.addLayer(LayerKind.DRAWING); dialog = null },
            onScenery = { st.addLayer(LayerKind.SCENERY); dialog = null },
            onDuplicate = { st.duplicateLayer(st.activeLayerId); dialog = null },
            onDismiss = { dialog = null },
        )
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
        is EditorDialog.FrameActions -> {
            val i = d.index
            FrameActionsDialog(
                index = i, canPaste = st.clip != null, canDelete = st.project.frameCount > 1,
                onPasteLeft = { st.edit { p -> p.insertFrameAt(i, st.clip ?: emptyMap()) }; dialog = null },
                onNewLeft = { st.edit { p -> p.insertFrameAt(i) }; dialog = null },
                onCopy = { st.clip = st.project.frameClip(i); dialog = null },
                onPng = { dialog = null; st.selectFrame(i); pngLauncher.launch("${baseName}_${i + 1}.png") },
                onDelete = { st.edit { p -> p.select(i).deleteCurrentFrame() }; dialog = null },
                onNewRight = { st.edit { p -> p.insertFrameAt(i + 1) }; dialog = null },
                onPasteRight = { st.edit { p -> p.insertFrameAt(i + 1, st.clip ?: emptyMap()) }; dialog = null },
                onDuplicate = { st.edit { p -> p.select(i).duplicateCurrentFrame() }; dialog = null },
                onDismiss = { dialog = null },
            )
        }
        is EditorDialog.BrushLibrary -> {
            val isBrush = d.tool == Tool.BRUSH
            BrushLibraryDialog(
                kind = st.kindOf(d.tool), selectedId = if (isBrush) st.brushPresetId else st.eraserPresetId,
                onSelect = { p -> st.applyPreset(p); dialog = null },
                onSettings = { p -> st.applyPreset(p); dialog = EditorDialog.BrushSettings(d.tool) },
                onDismiss = { dialog = null },
            )
        }
        is EditorDialog.BrushSettings -> {
            val isBrush = d.tool == Tool.BRUSH
            BrushSettingsDialog(
                kind = st.kindOf(d.tool),
                preset = BrushLibrary.byId(st.presetIdOf(d.tool)),
                size = if (isBrush) st.brushSize else st.eraserSize,
                opacity = if (isBrush) st.brushOpacity else st.eraserOpacity,
                stabilizer = st.stabilizer, mode = st.drawMode,
                onSize = { if (isBrush) st.brushSize = it else st.eraserSize = it },
                onOpacity = { if (isBrush) st.brushOpacity = it else st.eraserOpacity = it },
                onStabilizer = { st.stabilizer = it; prefs.stabilizer = it },
                onMode = { st.drawMode = it },
                onDismiss = { dialog = null },
            )
        }
        EditorDialog.BucketSettings -> BucketDialog(
            gap = st.bucketGap, grow = st.bucketGrow,
            onChange = { g, e -> st.bucketGap = g; st.bucketGrow = e; dialog = null },
            onDismiss = { dialog = null },
        )
        EditorDialog.Onion -> OnionDialog(
            on = st.onionOn, before = st.onionBefore, after = st.onionAfter,
            onChange = { on, b, a -> st.onionOn = on; st.onionBefore = b; st.onionAfter = a; dialog = null },
            onDismiss = { dialog = null },
        )
        EditorDialog.Grid -> GridDialog(
            type = st.gridType, cell = st.gridCell,
            onChange = { t, c -> st.gridType = t; st.gridCell = c; dialog = null },
            onDismiss = { dialog = null },
        )
        EditorDialog.Resolution -> ResolutionDialog(
            width = sheetW, height = sheetH,
            onApply = { w, h ->
                sheetW = w
                sheetH = h
                st.view = ViewTransform.fitSheet(w.toFloat(), h.toFloat(), canvasSize.width.toFloat(), canvasSize.height.toFloat(), 12f)
                dialog = null
            },
            onDismiss = { dialog = null },
        )
        EditorDialog.Organize -> OrganizeDialog(
            timelineBottom = st.timelineBottom, toolsRight = st.toolsRight,
            onChange = { b, r ->
                st.timelineBottom = b; prefs.timelineBottom = b
                st.toolsRight = r; prefs.toolsRight = r
            },
            onDismiss = { dialog = null },
        )
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
