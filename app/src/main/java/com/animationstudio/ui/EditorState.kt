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
import com.animationstudio.domain.Bounds
import com.animationstudio.domain.BrushLibrary
import com.animationstudio.domain.BrushStroke
import com.animationstudio.domain.History
import com.animationstudio.domain.Lasso
import com.animationstudio.domain.LayerKind
import com.animationstudio.domain.PlayMode
import com.animationstudio.domain.Point
import com.animationstudio.domain.Project
import com.animationstudio.domain.ViewTransform
import com.animationstudio.domain.removeElements
import com.animationstudio.domain.transformElements

internal enum class Tool { BRUSH, ERASER, BUCKET, LASSO }

internal enum class SelMode { MOVE, TRANSFORM }

/** Elementos (por índice) selecionados com o laço, no desenho atual de uma camada. */
internal class Selection(val layerId: Long, val frameId: Long, val indices: Set<Int>, val bounds: Bounds)

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

    var brushPresetId by mutableStateOf("ink")
    var brushSize by mutableFloatStateOf(6f)
    var brushOpacity by mutableFloatStateOf(1f)
    var eraserPresetId by mutableStateOf("eraser_hard")
    var eraserSize by mutableFloatStateOf(24f)
    var eraserOpacity by mutableFloatStateOf(1f)
    var stabilizer by mutableFloatStateOf(0.35f)

    var onionOn by mutableStateOf(true)
    var onionBefore by mutableIntStateOf(1)
    var onionAfter by mutableIntStateOf(1)

    var playing by mutableStateOf(false)
    var playMode by mutableStateOf(PlayMode.LOOP)
    var clip by mutableStateOf<Map<Long, List<BrushStroke>>?>(null)
    val activePoints = mutableStateListOf<Point>()

    var fillRequest by mutableStateOf<Point?>(null)
    var busyFill by mutableStateOf(false)

    var selection by mutableStateOf<Selection?>(null)
    var selMode by mutableStateOf(SelMode.MOVE)
    var selDx by mutableFloatStateOf(0f)
    var selDy by mutableFloatStateOf(0f)
    var selScale by mutableFloatStateOf(1f)
    var selRot by mutableFloatStateOf(0f)

    val isInkTool: Boolean get() = tool == Tool.BRUSH || tool == Tool.ERASER
    val currentPreset get() = BrushLibrary.byId(if (tool == Tool.ERASER) eraserPresetId else brushPresetId)
    val currentSize: Float get() = if (tool == Tool.ERASER) eraserSize else brushSize

    /** Cor do traço em curso: opacidade do pincel × alfa da cor; a borracha usa preto com a sua opacidade. */
    fun strokeColorArgb(): Int =
        if (tool == Tool.ERASER) Color.Black.copy(alpha = eraserOpacity).toArgb()
        else Color(colorArgb).let { it.copy(alpha = it.alpha * brushOpacity) }.toArgb()

    fun makeStroke(points: List<Point>): BrushStroke {
        val p = currentPreset
        return BrushStroke(points, strokeColorArgb(), currentSize, tool == Tool.ERASER, null, p.taper, p.hardness)
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
        clearSelection()
        activePoints.clear()
        tool = t
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

    // ---- laço ----

    private fun resetSelTransform() {
        selDx = 0f
        selDy = 0f
        selScale = 1f
        selRot = 0f
    }

    fun clearSelection() {
        selection = null
        resetSelTransform()
    }

    private fun activeStrokes(frameId: Long): List<BrushStroke> =
        project.layers.firstOrNull { it.id == activeLayerId }?.drawingAt(frameId)?.strokes ?: emptyList()

    fun finishLasso() {
        val poly = activePoints.toList()
        activePoints.clear()
        val fid = project.currentFrameId
        val strokes = activeStrokes(fid)
        val idx = Lasso.select(strokes, poly)
        val b = Lasso.boundsOf(strokes, idx)
        resetSelTransform()
        selection = if (idx.isEmpty() || b == null) null else Selection(activeLayerId, fid, idx, b)
    }

    fun commitSelection() {
        val s = selection ?: return
        if (selDx == 0f && selDy == 0f && selScale == 1f && selRot == 0f) return
        val m = Affine.around(Point(s.bounds.cx, s.bounds.cy), selScale, selRot, selDx, selDy)
        edit { it.transformElements(s.layerId, s.indices, m) }
        val nb = Lasso.boundsOf(activeStrokes(s.frameId), s.indices)
        resetSelTransform()
        selection = if (nb == null) null else Selection(s.layerId, s.frameId, s.indices, nb)
    }

    fun deleteSelection() {
        val s = selection ?: return
        edit { it.removeElements(s.layerId, s.indices) }
        clearSelection()
    }

    // ---- histórico e navegação ----

    fun undo() {
        if (playing) return
        val r = history.undo(project) ?: return
        project = r.first
        history = r.second
        clearSelection()
        fixActiveLayer()
    }

    fun redo() {
        if (playing) return
        val r = history.redo(project) ?: return
        project = r.first
        history = r.second
        clearSelection()
        fixActiveLayer()
    }

    private fun fixActiveLayer() {
        if (project.layers.none { it.id == activeLayerId }) activeLayerId = project.layers.last().id
    }

    fun go(delta: Int) {
        if (playing) return
        clearSelection()
        project = project.select(project.currentFrame + delta)
    }

    fun selectFrame(i: Int) {
        if (playing) return
        clearSelection()
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
        clearSelection()
        fixActiveLayer()
    }

    fun togglePlay() {
        if (playing) { playing = false; return }
        if (project.frameCount < 2) return
        clearSelection()
        if (playMode == PlayMode.ONCE && project.currentFrame == project.frameCount - 1) project = project.select(0)
        playing = true
    }
}
