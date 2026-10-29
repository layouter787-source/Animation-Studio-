package com.animationstudio.ui

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.animationstudio.domain.Affine
import com.animationstudio.domain.BrushKind
import com.animationstudio.domain.BrushLibrary
import com.animationstudio.domain.BrushPreset
import com.animationstudio.domain.BrushStroke
import com.animationstudio.domain.History
import com.animationstudio.domain.ImageData
import com.animationstudio.domain.LayerKind
import com.animationstudio.domain.PlayMode
import com.animationstudio.domain.Point
import com.animationstudio.domain.Project
import com.animationstudio.domain.QuadTransform
import com.animationstudio.domain.StrokeSmoothing
import com.animationstudio.domain.ViewTransform

internal enum class Tool { BRUSH, ERASER, BUCKET, LASSO }

internal enum class LassoShape { FREE, RECT, ELLIPSE }

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

    var brushPresetId by mutableStateOf("ink_g")
    var brushSize by mutableFloatStateOf(6f)
    var brushOpacity by mutableFloatStateOf(1f)
    var eraserPresetId by mutableStateOf("eraser_hard")
    var eraserSize by mutableFloatStateOf(24f)
    var eraserOpacity by mutableFloatStateOf(1f)
    var stabilizer by mutableFloatStateOf(0.25f)
    /** Suavização do traço em percentagem (0..1), aplicada aos pontos do traço. */
    var smoothing by mutableFloatStateOf(0.4f)
    /**
     * 0 = normal, 1 = "dentro" (só pinta onde já existe tinta), 2 = "por trás" (só pinta por trás do que existe).
     * Volta sempre a 0 ao trocar de ferramenta ou de pincel: em "dentro" o pincel não pinta numa zona apagada.
     */
    var drawMode by mutableIntStateOf(0)

    var bucketGap by mutableIntStateOf(8)
    var bucketGrow by mutableIntStateOf(2)

    var onionOn by mutableStateOf(true)
    var onionBefore by mutableIntStateOf(1)
    var onionAfter by mutableIntStateOf(1)

    var gridType by mutableStateOf(GridType.NONE)
    var gridCell by mutableFloatStateOf(64f)

    var penOnly by mutableStateOf(false)
    var palmReject by mutableStateOf(true)
    var timelineBottom by mutableStateOf(false)
    var toolsRight by mutableStateOf(false)

    var playing by mutableStateOf(false)
    var playMode by mutableStateOf(PlayMode.LOOP)
    var clip by mutableStateOf<Map<Long, List<BrushStroke>>?>(null)
    val activePoints = mutableStateListOf<Point>()
    var busyFill by mutableStateOf(false)

    // ---- laço: seleção flutuante em pixels ----
    var lassoShape by mutableStateOf(LassoShape.FREE)
    var floating by mutableStateOf<ImageData?>(null)
    /** Transformação em perspetiva: 4 cantos (TL, TR, BR, BL) da seleção; null = transformação normal. */
    var quad by mutableStateOf<List<Point>?>(null)
    /** Pivô da rotação na perspetiva; null = interseção das diagonais. */
    var pivot by mutableStateOf<Point?>(null)
    private var floatOrigin: ImageData? = null
    private var floatLayerId = -1L
    var clipImage by mutableStateOf<ImageData?>(null)
    var busyLift by mutableStateOf(false)

    val isInkTool: Boolean get() = tool == Tool.BRUSH || tool == Tool.ERASER
    val currentSize: Float get() = if (tool == Tool.ERASER) eraserSize else brushSize

    fun presetIdOf(t: Tool): String = if (t == Tool.ERASER) eraserPresetId else brushPresetId

    fun kindOf(t: Tool): BrushKind = if (t == Tool.ERASER) BrushKind.ERASER else BrushKind.BRUSH

    /** Escolhe uma predefinição e adota o tamanho e a opacidade que ela recomenda. */
    fun applyPreset(p: BrushPreset) {
        if (p.kind == BrushKind.ERASER) {
            eraserPresetId = p.id
            eraserSize = p.defaultSize
            eraserOpacity = p.defaultOpacity
        } else {
            brushPresetId = p.id
            brushSize = p.defaultSize
            brushOpacity = p.defaultOpacity
            drawMode = 0
        }
    }

    /** Cor do traço em curso: opacidade do pincel × alfa da cor; a borracha usa preto com a sua opacidade. */
    fun strokeColorArgb(): Int =
        if (tool == Tool.ERASER) Color.Black.copy(alpha = eraserOpacity).toArgb()
        else Color(colorArgb).let { it.copy(alpha = it.alpha * brushOpacity) }.toArgb()

    fun makeStroke(points: List<Point>): BrushStroke {
        val p = BrushLibrary.byId(presetIdOf(tool))
        val erase = tool == Tool.ERASER
        val smooth = StrokeSmoothing.smooth(points, smoothing)
        return BrushStroke(smooth, strokeColorArgb(), currentSize, erase, null, p.taper, p.hardness, p.id, if (erase) 0 else drawMode)
    }

    fun edit(change: (Project) -> Project) {
        if (playing) return
        val next = change(project)
        if (next !== project) {
            history = history.record(project)
            project = next
        }
    }

    fun selectTool(t: Tool) {
        if (tool == t) return
        commitFloating()
        activePoints.clear()
        tool = t
        drawMode = 0
    }

    fun commitStroke() {
        val pts = activePoints.toList()
        activePoints.clear()
        if (pts.isEmpty() || playing || !isInkTool) return
        val layer = project.layers.firstOrNull { it.id == activeLayerId } ?: return
        if (!layer.visible) return
        val stroke = makeStroke(pts)
        edit { it.addStroke(activeLayerId, stroke) }
    }

    // ---- seleção flutuante ----

    fun beginFloating(img: ImageData, layerId: Long) {
        floating = img
        floatOrigin = img
        floatLayerId = layerId
        quad = null
        pivot = null
    }

    /** A imagem flutuante tal como está (com a perspetiva aplicada, se estiver ativa). */
    private fun bakedFloating(): ImageData? {
        val f = floating ?: return null
        val q = quad
        return if (q != null) bakeQuad(f, q) else f
    }

    /** Liga/desliga a transformação em perspetiva; ao desligar, a deformação fica aplicada à seleção. */
    fun togglePerspective() {
        val f = floating ?: return
        val q = quad
        if (q == null) {
            quad = f.corners()
            pivot = null
        } else {
            floating = bakeQuad(f, q)
            quad = null
            pivot = null
        }
    }

    /** Fixa a seleção na camada de onde saiu (um passo no histórico). */
    fun commitFloating() {
        val f = bakedFloating() ?: return
        val lid = floatLayerId
        floating = null
        floatOrigin = null
        quad = null
        pivot = null
        edit { it.addStroke(lid, BrushStroke(emptyList(), 0, 0f, image = f)) }
    }

    fun discardFloating() {
        floating = null
        floatOrigin = null
        quad = null
        pivot = null
    }

    fun flipFloatingH() {
        val q = quad
        if (q != null) {
            val cx = QuadTransform.centroid(q).x
            quad = q.map { Point(2f * cx - it.x, it.y) }
            return
        }
        val f = floating ?: return
        floating = f.transformed(Affine.flipH(f.bounds().cx))
    }

    fun flipFloatingV() {
        val q = quad
        if (q != null) {
            val cy = QuadTransform.centroid(q).y
            quad = q.map { Point(it.x, 2f * cy - it.y) }
            return
        }
        val f = floating ?: return
        floating = f.transformed(Affine.flipV(f.bounds().cy))
    }

    fun rotateFloating(deg: Float) {
        val rad = Math.toRadians(deg.toDouble()).toFloat()
        val q = quad
        if (q != null) {
            quad = QuadTransform.rotate(q, pivot ?: QuadTransform.centroid(q), rad)
            return
        }
        val f = floating ?: return
        val b = f.bounds()
        floating = f.transformed(Affine.around(Point(b.cx, b.cy), 1f, rad, 0f, 0f))
    }

    fun resetFloating() {
        floating = floatOrigin
        val o = floatOrigin
        if (quad != null && o != null) quad = o.corners()
        pivot = null
    }

    fun copyFloating() {
        clipImage = bakedFloating()
    }

    fun cutFloating() {
        clipImage = bakedFloating()
        discardFloating()
    }

    /** Fixa a seleção e deixa uma cópia flutuante no mesmo sítio, pronta a mover. */
    fun duplicateFloating() {
        if (quad != null) togglePerspective()
        val f = floating ?: return
        val lid = floatLayerId
        commitFloating()
        floating = f
        floatOrigin = f
        floatLayerId = lid
    }

    fun pasteClip(center: Point) {
        val c = clipImage ?: return
        commitFloating()
        val b = c.bounds()
        val moved = c.transformed(Affine.translate(center.x - b.cx, center.y - b.cy))
        floating = moved
        floatOrigin = moved
        floatLayerId = activeLayerId
        quad = null
        pivot = null
    }

    // ---- histórico e navegação ----

    fun undo() {
        if (playing) return
        discardFloating()
        val r = history.undo(project) ?: return
        project = r.first
        history = r.second
        fixActiveLayer()
    }

    fun redo() {
        if (playing) return
        discardFloating()
        val r = history.redo(project) ?: return
        project = r.first
        history = r.second
        fixActiveLayer()
    }

    private fun fixActiveLayer() {
        if (project.layers.none { it.id == activeLayerId }) activeLayerId = project.layers.last().id
    }

    fun go(delta: Int) {
        if (playing) return
        commitFloating()
        project = project.select(project.currentFrame + delta)
    }

    fun selectFrame(i: Int) {
        if (playing) return
        commitFloating()
        project = project.select(i)
    }

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
        commitFloating()
        if (playMode == PlayMode.ONCE && project.currentFrame == project.frameCount - 1) project = project.select(0)
        playing = true
    }
}
