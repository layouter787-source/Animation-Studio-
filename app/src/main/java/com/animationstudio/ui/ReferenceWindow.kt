package com.animationstudio.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

/**
 * Janela de referência flutuante: arrasta-se pela barra, redimensiona-se pelo canto, aproxima-se com dois dedos
 * (até 8×) e tem três níveis de opacidade para ver o desenho por baixo. Fica por cima de tudo, fora da folha.
 */
@Composable
internal fun ReferenceWindow(
    image: ImageBitmap,
    offset: Offset,
    widthPx: Float,
    opacity: Float,
    container: IntSize,
    onMove: (Offset) -> Unit,
    onResize: (Float) -> Unit,
    onCycleOpacity: () -> Unit,
    onClose: () -> Unit,
) {
    val density = LocalDensity.current
    val aspect = image.height.toFloat() / image.width.toFloat()
    var zoom by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    val widthDp = with(density) { widthPx.toDp() }
    val heightDp = widthDp * aspect
    val curMove by rememberUpdatedState(onMove)
    val curResize by rememberUpdatedState(onResize)
    val maxX = (container.width - widthPx).coerceAtLeast(0f)
    val maxY = (container.height - 60f).coerceAtLeast(0f)

    Column(
        Modifier.offset { IntOffset(offset.x.coerceIn(0f, maxX).roundToInt(), offset.y.coerceIn(0f, maxY).roundToInt()) }
            .width(widthDp).graphicsLayer { alpha = opacity }.studioCard(12.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().height(26.dp).background(StudioColors.Surface2)
                .pointerInput(Unit) { detectDragGestures { change, d -> change.consume(); curMove(d) } },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(Modifier.padding(start = 4.dp)) { GripGlyph(StudioColors.InkDim, Modifier.size(16.dp)) }
            Text("REFERÊNCIA", style = StudioType.Caption.copy(fontSize = 9.sp), modifier = Modifier.weight(1f))
            Box(
                Modifier.clip(RoundedCornerShape(6.dp)).clickable(onClick = onCycleOpacity).padding(horizontal = 6.dp, vertical = 2.dp),
            ) { Text("${(opacity * 100).roundToInt()}%", style = StudioType.Number.copy(fontSize = 10.sp)) }
            Box(
                Modifier.size(width = 26.dp, height = 26.dp).clickable(onClick = onClose),
                contentAlignment = Alignment.Center,
            ) { Text("✕", style = StudioType.Label.copy(fontSize = 12.sp)) }
        }
        Box(
            Modifier.fillMaxWidth().height(heightDp).background(Color.White)
                .pointerInput(Unit) {
                    detectTransformGestures { _, p, z, _ ->
                        zoom = (zoom * z).coerceIn(1f, 8f)
                        pan = if (zoom <= 1.001f) Offset.Zero else pan + p
                    }
                },
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val center = Offset(size.width / 2f, size.height / 2f)
                withTransform({ translate(pan.x, pan.y); scale(zoom, zoom, center) }) {
                    drawImage(image, dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()))
                }
            }
            Box(
                Modifier.align(Alignment.BottomEnd).size(28.dp)
                    .pointerInput(Unit) { detectDragGestures { change, d -> change.consume(); curResize(d.x) } },
                contentAlignment = Alignment.BottomEnd,
            ) {
                Box(
                    Modifier.padding(4.dp).size(14.dp).clip(RoundedCornerShape(4.dp))
                        .background(StudioColors.Accent).border(1.dp, Color.White, RoundedCornerShape(4.dp))
                )
            }
        }
    }
}
