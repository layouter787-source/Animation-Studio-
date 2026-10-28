package com.animationstudio.ui

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.ControlPointDuplicate
import androidx.compose.material.icons.filled.CropSquare
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FitScreen
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material.icons.filled.Gesture
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawTransform
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.animationstudio.data.ProjectMeta
import com.animationstudio.data.ProjectStore
import com.animationstudio.domain.Affine
import com.animationstudio.domain.BrushLibrary
import com.animationstudio.domain.BrushStroke
import com.animationstudio.domain.ImageData
import com.animationstudio.domain.Lasso
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
import kotlin.math.atan2
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

private fun Offset.toPoint() = Point(x, y)

private fun isPen(c: PointerInputChange): Boolean = c.type == PointerType.Stylus || c.type == PointerType.Eraser

/** Pega de rotação: acima do meio do lado de cima da imagem, a [dist] (unidades da folha). */
private fun rotateHandleProject(f: ImageData, dist: Float): Point {
    val c = f.corners()
    val top = Point((c[0].x + c[1].x) / 2f, (c[0].y + c[1].y) / 2f)
    val bottom = Point((c[3].x + c[2].x) / 2f, (c[3].y + c[2].y) / 2f)
    var ux = top.x - bottom.x
    var uy = top.y - bottom.y
    val l = hypot(ux, uy)
    if (l > 0f) { ux /= l; uy /= l }
    return Point(top.x + ux * dist, top.y + uy * dist)
}

/** 0 = fora, 1 = corpo (mover), 2 = canto (escalar), 3 = pega de rotação. */
private fun hitFloating(f: ImageData, v: ViewTransform, pos: Offset, slop: Float): Int {
    val handle = v.projectToScreen(rotateHandleProject(f, slop / v.scale))
    if (hypot(pos.x - handle.x, pos.y - handle.y) <= slop) return 3
    val corners = f.corners().map { v.projectToScreen(it) }
    for (c in corners) if (hypot(pos.x - c.x, pos.y - c.y) <= slop) return 2
    return if (Lasso.pointInPolygon(Point(pos.x, pos.y), corners)) 1 else 0
}

/** Balde com fecho automático de frestas: se o preenchimento sair a cobrir quase a folha toda, repete com frestas maiores. */
private fun fillWithRetry(project: Project, sw: Int, sh: Int, p: Point, color: Int, gap0: Int, grow: Int): BrushStroke? {
    var gap = gap0
    var best: BrushStroke? = null
    for (attempt in 0..2) {
        val r = computeFill(project, sw, sh, p.x, p.y, color, gap, grow) ?: return best
        best = r
        if (fillCoverage(r, sw, sh) < 0.85f || gap >= 24) return r
        gap = (gap * 2).coerceIn(8, 24)
    }
    return best
}

/** Carrega uma imagem de referência (reduzida a no máximo ~2048 px para poupar memória). */
private suspend fun loadReference(context: Context, uri: Uri): ImageBitmap? = withContext(Dispatchers.IO) {
    try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (bounds.outWidth / sample > 2048 || bounds.outHeight / sample > 2048) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }?.asImageBitmap()
    } catch (e: Exception) {
        null
    }
}

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

/** Botão só com ícone, sobre a folha (laço): sem texto. */
@Composable
private fun OverlayIconChip(
    icon: ImageVector,
    description: String,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    danger: Boolean = false,
    iconRotation: Float = 0f,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier.size(36.dp).clip(shape)
            .background(if (selected) StudioColors.Accent else StudioColors.Surface.copy(alpha = 0.8f))
            .border(1.dp, if (selected) StudioColors.Accent else StudioColors.Outline, shape)
            .combinedClickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon, description, tint = if (danger) StudioColors.Danger else Color.White,
            modifier = Modifier.size(20.dp).rotate(iconRotation),
        )
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
            it.smoothing = prefs.smoothing
        }
    }
    var fps by remember { mutableIntStateOf(meta.fps) }
    var sheetW by remember { mutableIntStateOf(meta.width) }
    var sheetH by remember { mutableIntStateOf(meta.height) }
    var expanded by remember { mutableStateOf(false) }
    var topBarHeightPx by remember { mutableIntStateOf(0) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var canvasRoot by remember { mutableStateOf(Offset.Zero) }
    var rootSize by remember { mutableStateOf(IntSize.Zero) }
    var dialog by remember { mutableStateOf<EditorDialog?>(null) }
    var dragPos by remember { mutableStateOf<Offset?>(null) }
    var refImage by remember { mutableStateOf<ImageBitmap?>(null) }
    var refVisible by remember { mutableStateOf(true) }
    var refOffset by remember { mutableStateOf(Offset(40f, 120f)) }
    var refWidth by remember { mutableFloatStateOf(520f) }
    var refOpacity by remember { mutableFloatStateOf(0.85f) }
    val scope = rememberCoroutineScope()
    val sw = sheetW.toFloat()
    val sh = sheetH.toFloat()
    val curMeta = meta.copy(width = sheetW, height = sheetH)
    val baseName = remember(meta.name) { meta.name.replace(Regex("[^A-Za-z0-9_-]"), "_").ifBlank { "animacao" } }

    fun toast(msg: String) = Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
    fun soon(what: String) = toast("$what: em desenvolvimento")
    fun close() {
        scope.launch {
            st.commitFloating()
            if (!persist(store, curMeta, fps, st.project)) toast("Não foi possível guardar o projeto")
            onClose()
        }
    }

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
                withContext(Dispatchers.Default) { fillWithRetry(snapshot, sheetW, sheetH, p, st.colorArgb, st.bucketGap, st.bucketGrow) }
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

    fun requestLift(poly: List<Point>) {
        if (st.busyLift) return
        val layer = st.project.layers.firstOrNull { it.id == st.activeLayerId }
        if (layer == null || !layer.visible) {
            toast("Escolha uma camada visível para selecionar")
            return
        }
        val snapshot = st.project
        val layerId = st.activeLayerId
        st.busyLift = true
        scope.launch {
            val res = try {
                withContext(Dispatchers.Default) { liftSelection(snapshot, layerId, sheetW, sheetH, poly) }
            } catch (e: CancellationException) {
                st.busyLift = false
                throw e
            } catch (e: Throwable) {
                null
            }
            st.busyLift = false
            if (res == null) {
                toast("Nada para selecionar nessa área da camada ativa")
            } else if (st.project.currentFrameId == snapshot.currentFrameId) {
                st.edit { it.addStroke(layerId, res.eraser) }
                st.beginFloating(res.image, layerId)
            }
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
    val refLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) scope.launch {
            val img = loadReference(context, uri)
            if (img == null) toast("Não foi possível abrir a imagem") else { refImage = img; refVisible = true }
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
            onSwatchDrag = { dragPos = it },
            onSwatchDrop = { p ->
                dragPos = null
                // Largar a cor sobre a folha pinta a região em que se largou (como no Procreate Dreams).
                val local = Offset(p.x - canvasRoot.x, p.y - canvasRoot.y)
                if (local.x in 0f..canvasSize.width.toFloat() && local.y in 0f..canvasSize.height.toFloat()) {
                    requestFill(st.view.screenToProject(Point(local.x, local.y)))
                }
            },
            onSwatchCancel = { dragPos = null },
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

    Box(Modifier.fillMaxSize().background(StudioColors.Navy).onSizeChanged { rootSize = it }) {
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
                        .onGloballyPositioned { canvasRoot = it.positionInRoot() }
                ) {
                    // Folha branca sobre o espaço de trabalho escuro.
                    Canvas(Modifier.fillMaxSize()) {
                        val v = st.view
                        withTransform({ applyView(v) }) {
                            drawRect(Color.White, topLeft = Offset.Zero, size = Size(sw, sh))
                            drawRect(Color.Black, topLeft = Offset.Zero, size = Size(sw, sh), style = Stroke(width = 1f / v.scale))
                        }
                    }
                    // Cenário, onion skin, camadas de desenho, seleção flutuante, grelha.
                    Canvas(Modifier.fillMaxSize().graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }) {
                        val v = st.view
                        val proj = st.project
                        val fid = proj.currentFrameId
                        val live = if (st.isInkTool && st.activePoints.isNotEmpty()) st.makeStroke(st.activePoints.toList()) else null
                        val fl = st.floating
                        val dash = PathEffect.dashPathEffect(floatArrayOf(10f / v.scale, 6f / v.scale))
                        withTransform({ applyView(v) }) {
                            clipRect(0f, 0f, sw, sh) {
                                drawFrameLayers(proj, fid, sw, sh, LayerKind.SCENERY, null, true, live, st.activeLayerId)
                                if (st.onionOn && !st.playing) {
                                    val cur = proj.currentFrame
                                    for (k in st.onionBefore downTo 1) {
                                        val i = cur - k
                                        if (i >= 0) drawOnionFrame(proj, proj.frameIds[i], sw, sh, StudioColors.Rose, 0.34f / k)
                                    }
                                    for (k in st.onionAfter downTo 1) {
                                        val i = cur + k
                                        if (i < proj.frameCount) drawOnionFrame(proj, proj.frameIds[i], sw, sh, StudioColors.Mint, 0.34f / k)
                                    }
                                }
                                drawFrameLayers(proj, fid, sw, sh, LayerKind.DRAWING, null, true, live, st.activeLayerId)
                                if (fl != null) drawImageData(fl, null, true)
                                drawGridOverlay(st.gridType, st.gridCell, sw, sh, Color(0xFF3DA5FF).copy(alpha = 0.55f), 1f / v.scale)
                            }
                            if (fl != null) {
                                val cs = fl.corners()
                                val outline = Path()
                                outline.moveTo(cs[0].x, cs[0].y)
                                for (i in 1..3) outline.lineTo(cs[i].x, cs[i].y)
                                outline.close()
                                drawPath(outline, StudioColors.Accent, style = Stroke(width = 2f / v.scale, pathEffect = dash))
                                val hr = 7f / v.scale
                                for (c in cs) {
                                    drawCircle(Color.White, hr, Offset(c.x, c.y))
                                    drawCircle(StudioColors.Accent, hr, Offset(c.x, c.y), style = Stroke(width = 2f / v.scale))
                                }
                                val rh = rotateHandleProject(fl, 28f / v.scale)
                                val top = Offset((cs[0].x + cs[1].x) / 2f, (cs[0].y + cs[1].y) / 2f)
                                drawLine(StudioColors.Accent, top, Offset(rh.x, rh.y), strokeWidth = 2f / v.scale)
                                drawCircle(Color.White, hr, Offset(rh.x, rh.y))
                                drawCircle(StudioColors.Accent, hr, Offset(rh.x, rh.y), style = Stroke(width = 2f / v.scale))
                            }
                            if (st.tool == Tool.LASSO && st.activePoints.size > 1) {
                                val path = Path()
                                val pts = st.activePoints.toList()
                                path.moveTo(pts[0].x, pts[0].y)
                                for (i in 1 until pts.size) path.lineTo(pts[i].x, pts[i].y)
                                path.close()
                                drawPath(path, StudioColors.Accent, style = Stroke(width = 2f / v.scale, pathEffect = dash))
                            }
                        }
                    }
                    // Entrada: 1 dedo/caneta usa a ferramenta; 2 dedos navegam (ou transformam a seleção flutuante).
                    Box(
                        Modifier.fillMaxSize().pointerInput(Unit) {
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                if (st.playing) {
                                    do { val e = awaitPointerEvent() } while (e.changes.any { it.pressed })
                                    return@awaitEachGesture
                                }
                                val tool = st.tool
                                val downIsPen = isPen(down)
                                val navOnly = st.penOnly && !downIsPen
                                val inkTool = st.isInkTool
                                var floatMode = 0
                                if (tool == Tool.LASSO && !navOnly) {
                                    val f0 = st.floating
                                    if (f0 != null) {
                                        floatMode = hitFloating(f0, st.view, down.position, 26.dp.toPx())
                                        if (floatMode == 0) st.commitFloating()
                                    }
                                }
                                val lassoDraw = tool == Tool.LASSO && st.floating == null && !navOnly
                                val stab = if (inkTool && !navOnly) StrokeStabilizer(st.stabilizer) else null
                                var drawing = !navOnly && (inkTool || lassoDraw)
                                var multi = false
                                var moved = false
                                var rejected = false
                                val downPos = down.position
                                var lastScreen = downPos
                                var lastRawS = Point(downPos.x, downPos.y)
                                val startProj = st.view.screenToProject(Point(downPos.x, downPos.y))

                                fun pushPoint(p: Point) {
                                    val last = st.activePoints.lastOrNull()
                                    if (last == null || hypot(p.x - last.x, p.y - last.y) >= 0.2f / st.view.scale) st.activePoints.add(p)
                                }

                                st.activePoints.clear()
                                if (drawing) {
                                    val first = if (stab != null) stab.start(lastRawS, down.uptimeMillis) else lastRawS
                                    st.activePoints.add(st.view.screenToProject(first))
                                }
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
                                            val rotRad = Math.toRadians(event.calculateRotation().toDouble()).toFloat()
                                            val fl = st.floating
                                            if (pressedCount >= 2 && tool == Tool.LASSO && fl != null) {
                                                val cur = st.view.screenToProject(Point(c.x, c.y))
                                                val prev = st.view.screenToProject(Point(c.x - pan.x, c.y - pan.y))
                                                st.floating = fl.transformed(Affine.around(cur, zoom.coerceIn(0.5f, 2f), rotRad, cur.x - prev.x, cur.y - prev.y))
                                            } else {
                                                val focus = Point(c.x, c.y)
                                                var v = st.view.panBy(pan.x, pan.y)
                                                v = v.zoomAround(focus, zoom)
                                                v = v.rotateAround(focus, rotRad)
                                                st.view = v
                                            }
                                        }
                                    } else if (pressedCount == 1) {
                                        val ch = changes.firstOrNull { it.id == down.id }
                                        if (ch != null && ch.pressed) {
                                            if ((ch.position - downPos).getDistance() > 24f) moved = true
                                            if (drawing && !multi) {
                                                if (stab != null) {
                                                    // Estabiliza em pixels de ecrã e só depois converte para a folha.
                                                    for (h in ch.historical) {
                                                        lastRawS = Point(h.position.x, h.position.y)
                                                        pushPoint(st.view.screenToProject(stab.next(lastRawS, h.uptimeMillis)))
                                                    }
                                                    lastRawS = Point(ch.position.x, ch.position.y)
                                                    pushPoint(st.view.screenToProject(stab.next(lastRawS, ch.uptimeMillis)))
                                                } else {
                                                    val raw = st.view.screenToProject(Point(ch.position.x, ch.position.y))
                                                    when (st.lassoShape) {
                                                        LassoShape.FREE -> pushPoint(raw)
                                                        LassoShape.RECT -> { st.activePoints.clear(); st.activePoints.addAll(Lasso.rectangle(startProj, raw)) }
                                                        LassoShape.ELLIPSE -> { st.activePoints.clear(); st.activePoints.addAll(Lasso.ellipse(startProj, raw)) }
                                                    }
                                                }
                                            } else if (floatMode != 0 && !multi) {
                                                val fl = st.floating
                                                if (fl != null) {
                                                    val cur = st.view.screenToProject(Point(ch.position.x, ch.position.y))
                                                    val prev = st.view.screenToProject(Point(lastScreen.x, lastScreen.y))
                                                    val b = fl.bounds()
                                                    val cc = Point(b.cx, b.cy)
                                                    when (floatMode) {
                                                        1 -> st.floating = fl.transformed(Affine.translate(cur.x - prev.x, cur.y - prev.y))
                                                        2 -> {
                                                            val d0 = hypot(prev.x - cc.x, prev.y - cc.y)
                                                            val d1 = hypot(cur.x - cc.x, cur.y - cc.y)
                                                            if (d0 > 0.001f) st.floating = fl.transformed(Affine.around(cc, (d1 / d0).coerceIn(0.5f, 2f), 0f, 0f, 0f))
                                                        }
                                                        3 -> {
                                                            val a0 = atan2(prev.y - cc.y, prev.x - cc.x)
                                                            val a1 = atan2(cur.y - cc.y, cur.x - cc.x)
                                                            st.floating = fl.transformed(Affine.around(cc, 1f, a1 - a0, 0f, 0f))
                                                        }
                                                    }
                                                }
                                            }
                                            lastScreen = ch.position
                                        }
                                    }
                                    event.changes.forEach { if (it.positionChanged()) it.consume() }
                                } while (event.changes.any { it.pressed })

                                if (drawing && stab != null) {
                                    for (p in stab.finish(lastRawS)) pushPoint(st.view.screenToProject(p))
                                    st.commitStroke()
                                } else if (drawing && lassoDraw) {
                                    val poly = st.activePoints.toList()
                                    st.activePoints.clear()
                                    if (poly.size >= 3 && (st.lassoShape == LassoShape.FREE || moved)) requestLift(poly)
                                } else if (tool == Tool.BUCKET && !multi && !moved && !rejected && !navOnly) {
                                    requestFill(startProj)
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
                    ) { Icon(Icons.Default.FitScreen, "Ajustar à folha", tint = it) }
                    ToolCell(
                        width = 30.dp, height = 30.dp, selected = refImage != null && refVisible,
                        modifier = Modifier.align(Alignment.TopEnd).padding(top = 44.dp, end = 8.dp),
                        onLongClick = { refLauncher.launch("image/*") },
                        onClick = { if (refImage == null) refLauncher.launch("image/*") else refVisible = !refVisible },
                    ) { Icon(Icons.Default.Image, "Referência", tint = it, modifier = Modifier.size(18.dp)) }
                    if (st.tool == Tool.LASSO) {
                        Column(
                            Modifier.align(Alignment.TopCenter).padding(top = 8.dp).studioCard(12.dp).padding(3.dp),
                            verticalArrangement = Arrangement.spacedBy(3.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                OverlayIconChip(Icons.Default.Gesture, "Livre", selected = st.lassoShape == LassoShape.FREE) { st.lassoShape = LassoShape.FREE }
                                OverlayIconChip(Icons.Default.CropSquare, "Retângulo", selected = st.lassoShape == LassoShape.RECT) { st.lassoShape = LassoShape.RECT }
                                OverlayIconChip(Icons.Default.RadioButtonUnchecked, "Elipse", selected = st.lassoShape == LassoShape.ELLIPSE) { st.lassoShape = LassoShape.ELLIPSE }
                                if (st.clipImage != null) {
                                    OverlayIconChip(Icons.Default.ContentPaste, "Colar") {
                                        st.pasteClip(st.view.screenToProject(Point(canvasSize.width / 2f, canvasSize.height / 2f)))
                                    }
                                }
                            }
                            if (st.floating != null) {
                                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                    OverlayIconChip(Icons.Default.Flip, "Virar na horizontal") { st.flipFloatingH() }
                                    OverlayIconChip(Icons.Default.Flip, "Virar na vertical", iconRotation = 90f) { st.flipFloatingV() }
                                    OverlayIconChip(Icons.Default.RotateRight, "Rodar 45°") { st.rotateFloating(45f) }
                                    OverlayIconChip(Icons.Default.ContentCopy, "Copiar") { st.copyFloating() }
                                    OverlayIconChip(Icons.Default.ContentCut, "Cortar") { st.cutFloating() }
                                    OverlayIconChip(Icons.Default.ControlPointDuplicate, "Duplicar") { st.duplicateFloating() }
                                    OverlayIconChip(Icons.Default.RestartAlt, "Repor") { st.resetFloating() }
                                    OverlayIconChip(Icons.Default.Delete, "Apagar", danger = true) { st.discardFloating() }
                                    OverlayIconChip(Icons.Default.Check, "Confirmar", selected = true) { st.commitFloating() }
                                }
                            }
                        }
                    }
                    // Estado solto, sem cartão: só o zoom e os avisos de trabalho em curso.
                    Row(
                        Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text("${(st.view.scale * 100f).roundToInt()}%", style = StudioType.Number.copy(fontSize = 11.sp, color = Color.White.copy(alpha = 0.7f)))
                        if (st.busyFill) Text("A PREENCHER…", style = StudioType.Caption.copy(fontSize = 9.sp, color = StudioColors.Accent))
                        if (st.busyLift) Text("A SELECIONAR…", style = StudioType.Caption.copy(fontSize = 9.sp, color = StudioColors.Accent))
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
        val ref = refImage
        if (ref != null && refVisible) {
            ReferenceWindow(
                image = ref, offset = refOffset, widthPx = refWidth, opacity = refOpacity, container = rootSize,
                onMove = { d -> refOffset = Offset(refOffset.x + d.x, refOffset.y + d.y) },
                onResize = { dx -> refWidth = (refWidth + dx).coerceIn(160f, rootSize.width * 0.8f) },
                onCycleOpacity = { refOpacity = if (refOpacity < 0.7f) 0.8f else if (refOpacity < 0.9f) 1f else 0.55f },
                onClose = { refVisible = false },
            )
        }
        val dp0 = dragPos
        if (dp0 != null) {
            Box(
                Modifier.offset { IntOffset(dp0.x.roundToInt() - 18.dp.roundToPx(), dp0.y.roundToInt() - 18.dp.roundToPx()) }
                    .size(36.dp).clip(CircleShape).background(Color(st.colorArgb)).border(3.dp, Color.White, CircleShape)
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
                stabilizer = st.stabilizer, smoothing = st.smoothing, mode = st.drawMode,
                onSize = { if (isBrush) st.brushSize = it else st.eraserSize = it },
                onOpacity = { if (isBrush) st.brushOpacity = it else st.eraserOpacity = it },
                onStabilizer = { st.stabilizer = it; prefs.stabilizer = it },
                onSmoothing = { st.smoothing = it; prefs.smoothing = it },
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
