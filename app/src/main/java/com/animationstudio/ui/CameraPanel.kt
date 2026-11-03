package com.animationstudio.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.animationstudio.domain.Ease

@Composable
private fun CamChip(modifier: Modifier = Modifier, selected: Boolean = false, onClick: () -> Unit, content: @Composable (Color) -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier.size(width = 40.dp, height = 36.dp).clip(shape)
            .background(if (selected) StudioColors.Accent else StudioColors.Surface.copy(alpha = 0.9f))
            .border(1.dp, if (selected) StudioColors.Accent else StudioColors.Outline, shape)
            .combinedClickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content(if (selected) Color.White else StudioColors.Ink) }
}

/**
 * Painel do modo câmara. Mover (um dedo) e esticar/rodar (dois dedos) o enquadramento cria ou atualiza o keyframe
 * do frame atual. Entre keyframes a câmara interpola (suave ou linear).
 */
@Composable
internal fun CameraPanel(
    hasKey: Boolean,
    ease: Ease,
    viewThroughCamera: Boolean,
    onToggleEase: () -> Unit,
    onDeleteKey: () -> Unit,
    onToggleView: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier.studioCard(12.dp).padding(3.dp), horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.padding(horizontal = 8.dp)) { Text(if (hasKey) "CHAVE" else "SEM CHAVE", style = StudioType.Caption.copy(color = if (hasKey) StudioColors.Amber else StudioColors.InkDim)) }
        CamChip(selected = ease == Ease.SMOOTH, onClick = onToggleEase) {
            Text(if (ease == Ease.SMOOTH) "Suave" else "Linear", style = StudioType.Label.copy(fontSize = 9.sp, color = it))
        }
        CamChip(onClick = onDeleteKey) { Icon(Icons.Default.Delete, "Apagar keyframe", tint = if (hasKey) StudioColors.Danger else StudioColors.InkDim, modifier = Modifier.size(18.dp)) }
        CamChip(selected = viewThroughCamera, onClick = onToggleView) { Icon(Icons.Default.Visibility, "Ver pela câmara", tint = it, modifier = Modifier.size(18.dp)) }
        CamChip(selected = true, onClick = onClose) { Icon(Icons.Default.Check, "Concluir", tint = Color.White, modifier = Modifier.size(18.dp)) }
    }
}
