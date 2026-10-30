package com.animationstudio.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.animationstudio.domain.Layer
import com.animationstudio.domain.LayerKind
import kotlin.math.roundToInt

@Composable
internal fun StudioDialog(
    title: String,
    onDismiss: () -> Unit,
    confirmText: String? = "OK",
    onConfirm: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val maxH = LocalConfiguration.current.screenHeightDp.dp * 0.92f
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            color = StudioColors.Surface, shape = RoundedCornerShape(18.dp),
            modifier = Modifier.fillMaxWidth(0.8f).widthIn(max = 620.dp).heightIn(max = maxH),
        ) {
            Column(Modifier.padding(14.dp)) {
                Text(title, style = StudioType.Title)
                Spacer(Modifier.height(8.dp))
                Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) { content() }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text(if (onConfirm != null) "Cancelar" else "Fechar", style = StudioType.Label) }
                    if (onConfirm != null) Button(onClick = onConfirm) { Text(confirmText ?: "OK", style = StudioType.Label.copy(color = androidx.compose.ui.graphics.Color.White)) }
                }
            }
        }
    }
}

@Composable
internal fun MenuItem(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 11.dp, horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = StudioType.Body.copy(color = if (enabled) StudioColors.Ink else StudioColors.InkDim), modifier = Modifier.weight(1f))
        if (!enabled) Text("EM BREVE", style = StudioType.Caption)
    }
}

@Composable
internal fun SectionLabel(text: String) {
    Text(text, style = StudioType.Caption, modifier = Modifier.padding(top = 10.dp, bottom = 2.dp, start = 6.dp))
}

@Composable
internal fun LayerOptionsDialog(
    layer: Layer,
    canSeparate: Boolean,
    canDelete: Boolean,
    onApply: (String, Int, Boolean) -> Unit,
    onSeparate: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(layer.name) }
    var opacity by remember { mutableFloatStateOf(layer.opacityPercent.toFloat()) }
    var visible by remember { mutableStateOf(layer.visible) }
    StudioDialog(if (layer.kind == LayerKind.SCENERY) "Cenário" else "Camada", onDismiss, "OK", { onApply(name, opacity.roundToInt(), visible) }) {
        OutlinedTextField(name, { name = it }, label = { Text("Nome") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("OPACIDADE", style = StudioType.Caption)
            Text("${opacity.roundToInt()}%", style = StudioType.Number)
        }
        Slider(
            value = opacity, onValueChange = { opacity = it }, valueRange = 0f..100f,
            colors = SliderDefaults.colors(thumbColor = StudioColors.Accent, activeTrackColor = StudioColors.Accent, inactiveTrackColor = StudioColors.Surface3),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(checked = visible, onCheckedChange = { visible = it }, colors = SwitchDefaults.colors(checkedTrackColor = StudioColors.Accent))
            Spacer(Modifier.width(10.dp))
            Text("Visível", style = StudioType.Body)
        }
        if (canSeparate) TextButton(onClick = onSeparate) { Text("Separar a partir deste frame", style = StudioType.Label.copy(color = StudioColors.Accent)) }
        if (canDelete) TextButton(onClick = onDelete) { Text("Apagar", style = StudioType.Label.copy(color = StudioColors.Danger)) }
    }
}

@Composable
internal fun OnionDialog(
    on: Boolean,
    before: Int,
    after: Int,
    onChange: (Boolean, Int, Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var enabled by remember { mutableStateOf(on) }
    var b by remember { mutableFloatStateOf(before.toFloat()) }
    var a by remember { mutableFloatStateOf(after.toFloat()) }
    val colors = SliderDefaults.colors(thumbColor = StudioColors.Accent, activeTrackColor = StudioColors.Accent, inactiveTrackColor = StudioColors.Surface3)
    StudioDialog("Onion skin", onDismiss, "OK", { onChange(enabled, b.roundToInt(), a.roundToInt()) }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(checked = enabled, onCheckedChange = { enabled = it }, colors = SwitchDefaults.colors(checkedTrackColor = StudioColors.Accent))
            Spacer(Modifier.width(10.dp))
            Text("Mostrar frames vizinhos", style = StudioType.Body)
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("FRAMES ANTERIORES (VERMELHO)", style = StudioType.Caption)
            Text("${b.roundToInt()}", style = StudioType.Number)
        }
        Slider(value = b, onValueChange = { b = it }, valueRange = 0f..3f, steps = 2, colors = colors)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("FRAMES SEGUINTES (VERDE)", style = StudioType.Caption)
            Text("${a.roundToInt()}", style = StudioType.Number)
        }
        Slider(value = a, onValueChange = { a = it }, valueRange = 0f..3f, steps = 2, colors = colors)
    }
}
