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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.unit.dp
import com.animationstudio.domain.Layer
import com.animationstudio.domain.LayerKind
import com.animationstudio.domain.Timing
import kotlin.math.roundToInt

@Composable
private fun TlGrip(modifier: Modifier = Modifier, onEnd: (Float, Float) -> Unit) {
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

/**
 * Barra da timeline com duração de frames e keyframes da câmara: cada célula ocupa tantos tempos quantos o frame
 * dura; arrastar a ponta direita estica-a (o desenho fica visível mais tempo, sem duplicar frames). Um losango
 * âmbar marca os frames com keyframe de câmara.
 */
@Composable
internal fun TimelineBar(
    st: EditorState,
    timing: Timing,
    cameraActive: Boolean,
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
    onHold: (Long, Int) -> Unit,
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
    val density = LocalDensity.current
    val rowPx = with(density) { rowH.toPx() }
    val cellPx = with(density) { cellW.toPx() }
    val curMove by rememberUpdatedState(onMoveLayer)
    val curHold by rememberUpdatedState(onHold)
    val curTiming by rememberUpdatedState(timing)
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
                TlGrip(Modifier.size(width = 16.dp, height = 30.dp), onGripEnd)
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
                            val hold = timing.hold(fid)
                            val shape = RoundedCornerShape(6.dp)
                            Box(
                                Modifier.padding(horizontal = 1.5.dp).size(width = cellW * hold, height = cellH).clip(shape)
                                    .background(if (strokes != null) Color.White else StudioColors.Surface2)
                                    .border(if (isCurrent) 2.dp else 1.dp, if (isCurrent) StudioColors.Accent else StudioColors.Outline, shape)
                                    .combinedClickable(
                                        onClick = { st.activeLayerId = l.id; st.selectFrame(i) },
                                        onLongClick = { st.activeLayerId = l.id; st.selectFrame(i); onFrameOptions(i) },
                                    ),
                            ) {
                                if (strokes != null) {
                                    Canvas(Modifier.fillMaxSize()) {
                                        // A miniatura ocupa só o primeiro tempo; o resto é a duração do frame.
                                        drawThumbnail(strokes, sw.toFloat(), sh.toFloat())
                                    }
                                }
                                Text(
                                    "${i + 1}",
                                    style = StudioType.Caption.copy(fontSize = 8.sp, color = if (strokes != null) Color(0xFF30384F) else StudioColors.InkDim),
                                    modifier = Modifier.align(Alignment.TopStart).padding(start = 3.dp, top = 1.dp),
                                )
                                if (timing.camera.containsKey(fid)) {
                                    Box(
                                        Modifier.align(Alignment.BottomStart).padding(start = 5.dp, bottom = 4.dp)
                                            .size(7.dp).rotate(45f).background(StudioColors.Amber)
                                    )
                                }
                                // Pega da ponta direita: arrastar estica ou encolhe a duração do frame.
                                Box(
                                    Modifier.align(Alignment.CenterEnd).width(14.dp).fillMaxHeight()
                                        .background(if (hold > 1) StudioColors.Accent.copy(alpha = 0.35f) else Color.Transparent)
                                        .pointerInput(fid) {
                                            var acc = 0f
                                            var base = 1
                                            detectHorizontalDragGestures(
                                                onDragStart = { acc = 0f; base = curTiming.hold(fid) },
                                            ) { change, dx ->
                                                change.consume()
                                                acc += dx
                                                curHold(fid, base + (acc / cellPx).roundToInt())
                                            }
                                        },
                                    contentAlignment = Alignment.CenterEnd,
                                ) {
                                    Box(Modifier.padding(end = 3.dp).width(3.dp).height(12.dp).clip(RoundedCornerShape(2.dp)).background(StudioColors.Accent))
                                }
                            }
                        }
                    }
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(3.dp), horizontalAlignment = Alignment.End) {
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
                ToolCell(width = 30.dp, height = 30.dp, selected = cameraActive, onClick = onCamera) {
                    Icon(Icons.Default.Videocam, "Câmara", tint = it, modifier = Modifier.size(17.dp))
                }
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
