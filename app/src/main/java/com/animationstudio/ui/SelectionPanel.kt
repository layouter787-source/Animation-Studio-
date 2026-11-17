package com.animationstudio.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Botão com texto do painel da seleção. Todos têm a mesma altura ([StudioMetrics.TextButtonHeight]) e a mesma largura
 * (metade da linha); cinzento quando não se pode usar.
 */
@Composable
private fun PanelButton(
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    selected: Boolean = false,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(9.dp)
    val tint = if (!enabled) StudioColors.InkDim.copy(alpha = 0.5f) else if (selected) Color.White else StudioColors.Ink
    Box(
        modifier.height(StudioMetrics.TextButtonHeight).clip(shape)
            .background(if (selected) StudioColors.Accent else StudioColors.Surface2)
            .border(1.dp, if (selected) StudioColors.Accent else StudioColors.Outline, shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 3.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = StudioType.Label.copy(fontSize = 10.sp, color = tint), textAlign = TextAlign.Center, maxLines = 2)
    }
}

/**
 * Painel da ferramenta de seleção (laço). Ocupa o lugar da barra de ferramentas: ao tocar no laço a barra fecha e este
 * painel abre; tocar no ícone do laço, no topo, fecha o painel e volta à barra. Desenha-se o contorno na folha com o
 * dedo; a seleção fica pronta a mover, ampliar, rodar ou deformar. Sem opções de forma nem botões de confirmar:
 * a seleção fixa-se ao tocar fora dela, em "Selec. nada" ou ao mudar de ferramenta.
 *
 * Medidas: o botão do laço tem o tamanho de qualquer botão de ferramenta ([StudioMetrics.Cell]) e os restantes são
 * todos iguais entre si, em duas colunas.
 */
@Composable
internal fun SelectionPanel(
    hasSelection: Boolean,
    canPaste: Boolean,
    perspectiveOn: Boolean,
    busy: Boolean,
    onClose: () -> Unit,
    onSelectAll: () -> Unit,
    onSelectNone: () -> Unit,
    onCopy: () -> Unit,
    onPaste: () -> Unit,
    onFlipH: () -> Unit,
    onFlipV: () -> Unit,
    onPerspective: () -> Unit,
    onMesh: () -> Unit,
) {
    Column(
        Modifier.width(150.dp).fillMaxHeight().verticalScroll(rememberScrollState()).padding(horizontal = 3.dp).padding(bottom = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            Modifier.studioCard(12.dp).padding(StudioMetrics.Pad),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(StudioMetrics.Gap),
        ) {
            ToolCell(selected = true, onClick = onClose) { LassoGlyph(it, Modifier.height(17.dp).width(17.dp)) }
            Row(horizontalArrangement = Arrangement.spacedBy(StudioMetrics.Gap)) {
                PanelButton("Selec. todos", Modifier.weight(1f), enabled = !busy, onClick = onSelectAll)
                PanelButton("Selec. nada", Modifier.weight(1f), enabled = hasSelection, onClick = onSelectNone)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(StudioMetrics.Gap)) {
                PanelButton("Copiar", Modifier.weight(1f), enabled = hasSelection, onClick = onCopy)
                PanelButton("Colar", Modifier.weight(1f), enabled = canPaste, onClick = onPaste)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(StudioMetrics.Gap)) {
                PanelButton("Flip horizontal", Modifier.weight(1f), enabled = hasSelection, onClick = onFlipH)
                PanelButton("Flip vertical", Modifier.weight(1f), enabled = hasSelection, onClick = onFlipV)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(StudioMetrics.Gap)) {
                PanelButton("Perspetiva", Modifier.weight(1f), enabled = hasSelection, selected = perspectiveOn, onClick = onPerspective)
                PanelButton("Malha", Modifier.weight(1f), enabled = hasSelection, onClick = onMesh)
            }
        }
    }
}
