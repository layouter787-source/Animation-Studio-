package com.animationstudio.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlin.math.roundToInt

private val SliderColors
    @Composable get() = SliderDefaults.colors(
        thumbColor = StudioColors.Accent, activeTrackColor = StudioColors.Accent, inactiveTrackColor = StudioColors.Surface3,
    )

@Composable
private fun Divider() {
    Box(Modifier.fillMaxWidth().size(width = 1.dp, height = 1.dp).background(StudioColors.Outline.copy(alpha = 0.6f)))
}

@Composable
private fun MenuRow(text: String, enabled: Boolean = true, trailing: @Composable () -> Unit = {}, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 9.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text, style = StudioType.Body.copy(fontSize = 13.sp, color = if (enabled) StudioColors.Ink else StudioColors.InkDim),
            modifier = Modifier.weight(1f),
        )
        if (!enabled) Text("EM BREVE", style = StudioType.Caption.copy(fontSize = 8.sp)) else trailing()
    }
    Divider()
}

/** Menu compacto (como no esboço): grade, resolução, interface, importar, exportar e opções de caneta. */
@Composable
internal fun MenuPopup(
    penOnly: Boolean,
    palmReject: Boolean,
    onPenOnly: (Boolean) -> Unit,
    onPalm: (Boolean) -> Unit,
    onGrid: () -> Unit,
    onResolution: () -> Unit,
    onOrganize: () -> Unit,
    onSoon: (String) -> Unit,
    onExportPng: () -> Unit,
    onExportSequence: () -> Unit,
    onExportZip: () -> Unit,
    onBack: () -> Unit,
    onDismiss: () -> Unit,
) {
    val maxH = LocalConfiguration.current.screenHeightDp.dp * 0.94f
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            color = StudioColors.Surface, shape = RoundedCornerShape(16.dp),
            modifier = Modifier.width(300.dp).heightIn(max = maxH).border(1.dp, StudioColors.Outline, RoundedCornerShape(16.dp)),
        ) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 10.dp)) {
                PanelGlyph(StudioColors.Ink, Modifier.size(20.dp))
                Spacer(Modifier.size(6.dp))
                MenuRow("Grade", trailing = { PaletteGlyph(StudioColors.InkDim, Modifier.size(16.dp)) }) { onGrid() }
                MenuRow("Definições de resolução") { onResolution() }
                MenuRow("Organização da interface") { onOrganize() }
                MenuRow("Importar vídeo", enabled = false) { onSoon("Importar vídeo") }
                MenuRow("Importar imagem", enabled = false) { onSoon("Importar imagem") }
                MenuRow("Exportar vídeo", enabled = false) { onSoon("Exportar vídeo") }
                MenuRow("Exportar PNG do frame atual") { onExportPng() }
                MenuRow("Sequência de PNG (escolher pasta)") { onExportSequence() }
                MenuRow("ZIP com todos os frames") { onExportZip() }
                MenuRow(
                    "S Pen Samsung",
                    trailing = { Switch(checked = penOnly, onCheckedChange = onPenOnly, colors = SwitchDefaults.colors(checkedTrackColor = StudioColors.Accent)) },
                ) { onPenOnly(!penOnly) }
                MenuRow(
                    "Rejeição de palma",
                    trailing = { Switch(checked = palmReject, onCheckedChange = onPalm, colors = SwitchDefaults.colors(checkedTrackColor = StudioColors.Accent)) },
                ) { onPalm(!palmReject) }
                MenuRow("Voltar aos projetos") { onBack() }
                Text(
                    "S Pen: só a caneta desenha; os dedos movem a folha. Palma: ignora toques enquanto a caneta está em uso.",
                    style = StudioType.Caption.copy(fontSize = 8.sp), modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
    }
}

@Composable
internal fun GridDialog(type: GridType, cell: Float, onChange: (GridType, Float) -> Unit, onDismiss: () -> Unit) {
    var cellValue by remember { mutableFloatStateOf(cell) }
    StudioDialog("Grade", onDismiss, "OK", { onChange(type, cellValue) }) {
        for (t in GridType.values()) {
            val sel = t == type
            Row(
                Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(RoundedCornerShape(10.dp))
                    .background(if (sel) StudioColors.AccentSoft else StudioColors.Surface2)
                    .border(1.dp, if (sel) StudioColors.Accent else StudioColors.Outline, RoundedCornerShape(10.dp))
                    .clickable { onChange(t, cellValue) }.padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Canvas(Modifier.size(width = 64.dp, height = 40.dp).clip(RoundedCornerShape(6.dp)).background(Color.White)) {
                    drawGridOverlay(t, 12f, 64f * density, 40f * density, Color(0xFF0866F5).copy(alpha = 0.7f), 1f)
                }
                Text(t.label, style = StudioType.Body.copy(fontSize = 13.sp))
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("TAMANHO DA GRADE", style = StudioType.Caption)
            Text("${cellValue.roundToInt()} px", style = StudioType.Number)
        }
        Slider(value = cellValue, onValueChange = { cellValue = it }, valueRange = 16f..256f, colors = SliderColors)
    }
}

@Composable
internal fun ResolutionDialog(width: Int, height: Int, onApply: (Int, Int) -> Unit, onDismiss: () -> Unit) {
    var w by remember { mutableStateOf(width.toString()) }
    var h by remember { mutableStateOf(height.toString()) }
    val wv = w.toIntOrNull()
    val hv = h.toIntOrNull()
    val valid = wv != null && wv in 64..8192 && hv != null && hv in 64..8192
    val number = KeyboardOptions(keyboardType = KeyboardType.Number)
    StudioDialog("Resolução da folha", onDismiss, "Aplicar", { if (valid) onApply(wv!!, hv!!) }) {
        val presets = listOf("HD 1280×720" to (1280 to 720), "Full HD 1920×1080" to (1920 to 1080), "Quadrado 1080" to (1080 to 1080), "4:3 1024×768" to (1024 to 768))
        for (row in presets.chunked(2)) {
            Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for ((label, size) in row) {
                    val sel = wv == size.first && hv == size.second
                    Box(
                        Modifier.weight(1f).clip(RoundedCornerShape(9.dp)).background(if (sel) StudioColors.Accent else StudioColors.Surface2)
                            .clickable { w = size.first.toString(); h = size.second.toString() }.padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) { Text(label, style = StudioType.Label.copy(fontSize = 11.sp)) }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedTextField(w, { w = it.filter { c -> c.isDigit() }.take(4) }, label = { Text("Largura") }, singleLine = true, isError = wv == null || wv !in 64..8192, keyboardOptions = number, modifier = Modifier.weight(1f))
            OutlinedTextField(h, { h = it.filter { c -> c.isDigit() }.take(4) }, label = { Text("Altura") }, singleLine = true, isError = hv == null || hv !in 64..8192, keyboardOptions = number, modifier = Modifier.weight(1f))
        }
        Text("Os desenhos mantêm o tamanho; a folha cresce ou corta nas margens.", style = StudioType.Caption.copy(fontSize = 8.sp), modifier = Modifier.padding(top = 6.dp))
    }
}

@Composable
private fun Choice(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier.clip(RoundedCornerShape(9.dp)).background(if (selected) StudioColors.Accent else StudioColors.Surface2)
            .border(1.dp, if (selected) StudioColors.Accent else StudioColors.Outline, RoundedCornerShape(9.dp))
            .clickable(onClick = onClick).padding(vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) { Text(label, style = StudioType.Label.copy(fontSize = 12.sp)) }
}

@Composable
internal fun OrganizeDialog(timelineBottom: Boolean, toolsRight: Boolean, onChange: (Boolean, Boolean) -> Unit, onDismiss: () -> Unit) {
    StudioDialog("Organização da interface", onDismiss) {
        Text("TIMELINE", style = StudioType.Caption, modifier = Modifier.padding(bottom = 4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Choice("Em cima", !timelineBottom, Modifier.weight(1f)) { onChange(false, toolsRight) }
            Choice("Em baixo", timelineBottom, Modifier.weight(1f)) { onChange(true, toolsRight) }
        }
        Text("FERRAMENTAS", style = StudioType.Caption, modifier = Modifier.padding(top = 10.dp, bottom = 4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Choice("À esquerda", !toolsRight, Modifier.weight(1f)) { onChange(timelineBottom, false) }
            Choice("À direita", toolsRight, Modifier.weight(1f)) { onChange(timelineBottom, true) }
        }
        Text("Também pode arrastar as pegas de seis pontos de cada barra. Posições verticais da timeline: próxima fase.", style = StudioType.Caption.copy(fontSize = 8.sp), modifier = Modifier.padding(top = 8.dp))
    }
}

@Composable
internal fun AddLayerDialog(onLayer: () -> Unit, onScenery: () -> Unit, onDuplicate: () -> Unit, onDismiss: () -> Unit) {
    StudioDialog("Adicionar", onDismiss) {
        MenuItem("Nova camada") { onLayer() }
        MenuItem("Novo cenário") { onScenery() }
        MenuItem("Duplicar a camada ativa") { onDuplicate() }
    }
}

@Composable
private fun ActionButton(label: String, enabled: Boolean = true, onClick: () -> Unit, icon: @Composable (Color) -> Unit) {
    val tint = if (enabled) StudioColors.Ink else StudioColors.InkDim.copy(alpha = 0.5f)
    Column(
        Modifier.width(50.dp).clip(RoundedCornerShape(9.dp)).clickable(enabled = enabled, onClick = onClick).padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        icon(tint)
        Text(label, style = StudioType.Caption.copy(fontSize = 8.sp, color = tint))
    }
}

/** Ações de frame com ícones (colar, novo, copiar, PNG, apagar), à maneira do FlipaClip. */
@Composable
internal fun FrameActionsDialog(
    index: Int,
    canPaste: Boolean,
    canDelete: Boolean,
    onPasteLeft: () -> Unit,
    onNewLeft: () -> Unit,
    onCopy: () -> Unit,
    onPng: () -> Unit,
    onDelete: () -> Unit,
    onNewRight: () -> Unit,
    onPasteRight: () -> Unit,
    onDuplicate: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            color = StudioColors.Surface, shape = RoundedCornerShape(16.dp),
            modifier = Modifier.border(1.dp, StudioColors.Outline, RoundedCornerShape(16.dp)),
        ) {
            Column(Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("FRAME ${index + 1}", style = StudioType.Caption, modifier = Modifier.padding(bottom = 4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    ActionButton("Colar", canPaste, onPasteLeft) { FrameActionGlyph(it, toRight = false, paste = true) }
                    ActionButton("Novo", true, onNewLeft) { FrameActionGlyph(it, toRight = false, paste = false) }
                    ActionButton("Copiar", true, onCopy) { androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Default.ContentCopy, null, tint = it, modifier = Modifier.size(22.dp)) }
                    ActionButton("PNG", true, onPng) { Text("PNG", style = StudioType.Label.copy(color = it, fontSize = 12.sp), modifier = Modifier.size(width = 26.dp, height = 22.dp)) }
                    ActionButton("Duplicar", true, onDuplicate) { androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Default.ContentCopy, null, tint = it, modifier = Modifier.size(22.dp)) }
                    ActionButton("Apagar", canDelete, onDelete) { androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Default.Delete, null, tint = it, modifier = Modifier.size(22.dp)) }
                    ActionButton("Novo", true, onNewRight) { FrameActionGlyph(it, toRight = true, paste = false) }
                    ActionButton("Colar", canPaste, onPasteRight) { FrameActionGlyph(it, toRight = true, paste = true) }
                }
            }
        }
    }
}

/**
 * Definições do balde: fechar frestas, expandir por baixo da linha e a percentagem a partir da qual um pixel conta
 * como tinta. Esta última é livre (0–100%), com slider ou valor escrito: guarda-se nas preferências.
 */
@Composable
internal fun BucketDialog(gap: Int, grow: Int, onChange: (Int, Int) -> Unit, onDismiss: () -> Unit) {
    val prefs = Prefs(LocalContext.current)
    var g by remember { mutableFloatStateOf(gap.toFloat()) }
    var e by remember { mutableFloatStateOf(grow.toFloat()) }
    var ink by remember { mutableFloatStateOf(StudioRender.inkPercent.toFloat()) }
    var inkText by remember { mutableStateOf(StudioRender.inkPercent.toString()) }
    StudioDialog("Balde de tinta", onDismiss, "OK", {
        prefs.inkPercent = ink.roundToInt()
        onChange(g.roundToInt(), e.roundToInt())
    }) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("FECHAR FRESTAS", style = StudioType.Caption)
            Text("${g.roundToInt()} px", style = StudioType.Number)
        }
        Slider(value = g, onValueChange = { g = it }, valueRange = 0f..12f, colors = SliderColors)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("EXPANDIR POR BAIXO DA LINHA", style = StudioType.Caption)
            Text("${e.roundToInt()} px", style = StudioType.Number)
        }
        Slider(value = e, onValueChange = { e = it }, valueRange = 0f..6f, colors = SliderColors)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text("TINTA A PARTIR DE (%)", style = StudioType.Caption)
            OutlinedTextField(
                inkText,
                {
                    val digits = it.filter { c -> c.isDigit() }.take(3)
                    inkText = digits
                    val n = digits.toIntOrNull()
                    if (n != null) ink = n.coerceIn(0, 100).toFloat()
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.width(84.dp),
            )
        }
        Slider(
            value = ink, onValueChange = { ink = it; inkText = it.roundToInt().toString() },
            valueRange = 0f..100f, colors = SliderColors,
        )
        Text(
            "Frestas maiores que o valor escolhido deixam a tinta escapar. Pixels com menos opacidade do que a percentagem da tinta contam como papel e são pintados.",
            style = StudioType.Caption.copy(fontSize = 8.sp),
        )
    }
}
