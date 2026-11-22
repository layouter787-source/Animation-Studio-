package com.animationstudio.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Movimento de uma barra entre dois bordos (cima/baixo ou esquerda/direita).
 * Durante o arrasto a barra acompanha o dedo; ao largar, assenta com uma animação no bordo mais próximo.
 * [off] é o deslocamento (px) em relação à posição de repouso do bordo atual.
 */
internal class EdgeMove(private val scope: CoroutineScope) {
    val off = Animatable(0f)
    private var cur = 0f

    private fun rest(far: Boolean, size: Float, total: Float) = if (far) (total - size).coerceAtLeast(0f) else 0f

    fun start() {
        cur = off.value
        scope.launch { off.stop() }
    }

    fun drag(d: Float, far: Boolean, size: Float, total: Float) {
        val r = rest(far, size, total)
        val lo = -r
        val hi = (total - size).coerceAtLeast(0f) - r
        cur = (cur + d).coerceIn(lo, hi)
        val v = cur
        scope.launch { off.snapTo(v) }
    }

    /** [onFlip] recebe true se a barra passa para o bordo "longe" (baixo / direita). */
    fun release(far: Boolean, size: Float, total: Float, onFlip: (Boolean) -> Unit) {
        scope.launch {
            val abs = rest(far, size, total) + cur
            val toFar = abs + size / 2f > total / 2f
            val newRest = rest(toFar, size, total)
            if (toFar != far) onFlip(toFar)
            cur = abs - newRest
            off.snapTo(cur)
            off.animateTo(0f, spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessLow))
            cur = 0f
        }
    }
}

/** Pega pequena, colada à barra, para a mover. Só no telemóvel. */
@Composable
internal fun MoveTab(
    atBottom: Boolean,
    onStart: () -> Unit,
    onDrag: (Float, Float) -> Unit,
    onEnd: () -> Unit,
    modifier: Modifier = Modifier,
    allSides: Boolean = false,
) {
    val curStart by rememberUpdatedState(onStart)
    val curDrag by rememberUpdatedState(onDrag)
    val curEnd by rememberUpdatedState(onEnd)
    val r = 8.dp
    val shape = when {
        allSides -> RoundedCornerShape(r)
        atBottom -> RoundedCornerShape(topStart = r, topEnd = r)
        else -> RoundedCornerShape(bottomStart = r, bottomEnd = r)
    }
    Box(
        modifier.width(30.dp).height(14.dp).clip(shape).background(StudioColors.Navy).border(1.dp, StudioColors.Outline, shape)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { curStart() },
                    onDragEnd = { curEnd() },
                    onDragCancel = { curEnd() },
                ) { change, d ->
                    change.consume()
                    curDrag(d.x, d.y)
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(2.dp)) {
            Box(Modifier.width(12.dp).height(2.dp).clip(RoundedCornerShape(1.dp)).background(Color(0xFF6E96FF)))
            Box(Modifier.width(12.dp).height(2.dp).clip(RoundedCornerShape(1.dp)).background(Color(0xFF6E96FF)))
        }
    }
}
