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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.animationstudio.domain.BrushKind
import com.animationstudio.domain.BrushLibrary
import com.animationstudio.domain.BrushLimits
import com.animationstudio.domain.BrushPreset
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.roundToInt

/** Menor tamanho de pincel: 0,1 px (linhas finas como fios). O máximo é [BrushLimits.MAX_SIZE] = 2000 px. */
private const val SizeMin = 0.1f

private val SizeLogMin = ln(SizeMin)
private val SizeLogSpan = ln(BrushLimits.MAX_SIZE) - ln(SizeMin)

/** Menus e janelas ficam 20% transparentes. */
private val PopupAlpha = 0.8f

/** Arredonda ao passo natural: décimas até 100 px, inteiros acima. */
private fun roundSize(v: Float): Float {
    val c = v.coerceIn(SizeMin, BrushLimits.MAX_SIZE)
    return if (c < 100f) ((c * 10f).roundToInt() / 10f).coerceAtLeast(SizeMin) else c.roundToInt().toFloat()
}

/** Tamanho (0,1–2000 px) numa escala logarítmica: o controlo fino fica nos tamanhos pequenos. */
private fun sizeFromSlider(t: Float): Float = roundSize(exp(SizeLogMin + t.coerceIn(0f, 1f) * SizeLogSpan))

private fun sliderFromSize(s: Float): Float = ((ln(s.coerceIn(SizeMin, BrushLimits.MAX_SIZE)) - SizeLogMin) / SizeLogSpan).coerceIn(0f, 1f)

/** Texto do tamanho: "0.3", "1.5", "2", "150". */
private fun fmtSize(v: Float): String {
    if (v >= 100f) return v.roundToInt().toString()
    val r = (v * 10f).roundToInt() / 10f
    return if (r == r.toInt().toFloat()) r.toInt().toString() else r.toString()
}

/** Passo dos botões − e +: 0,1 até 10 px, 1 até 100 px, 10 acima. */
private fun sizeStep(v: Float): Float = if (v < 10f) 0.1f else if (v < 100f) 1f else 10f

@Composable
private fun StudioSlider(value: Float, onChange: (Float) -> Unit) {
    Slider(
        value = value, onValueChange = onChange,
        colors = SliderDefaults.colors(thumbColor = StudioColors.Accent, activeTrackColor = StudioColors.Accent, inactiveTrackColor = StudioColors.Surface3),
    )
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = StudioType.Caption, modifier = Modifier.weight(1f))
        Switch(
            checked = checked, onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedTrackColor = StudioColors.Accent),
        )
    }
}

/**
 * Biblioteca de pincéis/borrachas (como no esboço): aba, pesquisa, categorias e lista com pré-visualização
 * renderizada. Tocar num pincel escolhe-o e abre logo as definições dele (tamanho, opacidade, suavização,
 * smoothing, pontas); os três pontos fazem o mesmo.
 */
@Composable
internal fun BrushLibraryDialog(
    kind: BrushKind,
    selectedId: String,
    onSelect: (BrushPreset) -> Unit,
    onSettings: (BrushPreset) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var category by remember { mutableStateOf<String?>(null) }
    var showCats by remember { mutableStateOf(false) }
    val cats = BrushLibrary.categoriesOf(kind)
    val list = BrushLibrary.search(kind, query, category)
    val shape = RoundedCornerShape(16.dp)

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.width(380.dp).fillMaxHeight(0.94f)) {
            Box(
                Modifier.padding(start = 18.dp).clip(RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp))
                    .background(StudioColors.Surface3.copy(alpha = PopupAlpha)).padding(horizontal = 16.dp, vertical = 4.dp)
            ) { Text(if (kind == BrushKind.BRUSH) "PINCÉIS" else "BORRACHAS", style = StudioType.Caption.copy(color = StudioColors.Ink)) }
            Surface(color = StudioColors.Surface.copy(alpha = PopupAlpha), shape = shape, modifier = Modifier.weight(1f).border(1.dp, StudioColors.Outline, shape)) {
                Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Row(
                            Modifier.weight(1f).height(34.dp).clip(RoundedCornerShape(10.dp)).background(StudioColors.Surface2)
                                .border(1.dp, StudioColors.Outline, RoundedCornerShape(10.dp)).padding(horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Icon(Icons.Default.Search, null, tint = StudioColors.InkDim, modifier = Modifier.size(16.dp))
                            BasicTextField(
                                value = query, onValueChange = { query = it.take(30) }, singleLine = true,
                                textStyle = StudioType.Body.copy(fontSize = 13.sp), cursorBrush = SolidColor(StudioColors.Accent),
                                modifier = Modifier.weight(1f),
                                decorationBox = { inner ->
                                    Box(contentAlignment = Alignment.CenterStart) {
                                        if (query.isEmpty()) Text("Pesquisar", style = StudioType.Body.copy(fontSize = 13.sp, color = StudioColors.InkDim))
                                        inner()
                                    }
                                },
                            )
                        }
                        Box(
                            Modifier.height(34.dp).clip(RoundedCornerShape(10.dp))
                                .background(if (showCats) StudioColors.Accent else StudioColors.Surface2)
                                .border(1.dp, if (showCats) StudioColors.Accent else StudioColors.Outline, RoundedCornerShape(10.dp))
                                .clickable { showCats = !showCats }.padding(horizontal = 12.dp),
                            contentAlignment = Alignment.Center,
                        ) { Text("Categorias", style = StudioType.Label.copy(fontSize = 11.sp)) }
                    }
                    Text((category ?: "Todos").uppercase(), style = StudioType.Caption)
                    if (showCats) {
                        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            val all = listOf<String?>(null) + cats
                            items(all) { c ->
                                val count = BrushLibrary.search(kind, "", c).size
                                val sel = c == category
                                Row(
                                    Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                                        .background(if (sel) StudioColors.AccentSoft else StudioColors.Surface2)
                                        .border(1.dp, if (sel) StudioColors.Accent else StudioColors.Outline, RoundedCornerShape(10.dp))
                                        .clickable { category = c; showCats = false }.padding(horizontal = 12.dp, vertical = 10.dp),
                                ) {
                                    Text(c ?: "Todos", style = StudioType.Label, modifier = Modifier.weight(1f))
                                    Text("$count", style = StudioType.Number.copy(color = StudioColors.InkDim))
                                }
                            }
                        }
                    } else if (list.isEmpty()) {
                        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                            Text("Nenhum pincel encontrado.", style = StudioType.Body.copy(color = StudioColors.InkDim))
                        }
                    } else {
                        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            items(list, key = { it.id }) { p ->
                                val sel = p.id == selectedId
                                Row(
                                    Modifier.fillMaxWidth().height(46.dp).clip(RoundedCornerShape(10.dp))
                                        .background(if (sel) StudioColors.AccentSoft else StudioColors.Surface2)
                                        .border(1.dp, if (sel) StudioColors.Accent else StudioColors.Outline, RoundedCornerShape(10.dp))
                                        .clickable { onSettings(p) },
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Canvas(
                                        Modifier.width(104.dp).fillMaxHeight().graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                                    ) { drawBrushSample(p, p.defaultSize, p.defaultOpacity, StudioColors.Ink) }
                                    Column(Modifier.weight(1f).padding(horizontal = 6.dp)) {
                                        Text(p.name, style = StudioType.Label.copy(fontSize = 12.sp), maxLines = 1)
                                        Text(p.category.uppercase(), style = StudioType.Caption.copy(fontSize = 8.sp))
                                    }
                                    Box(
                                        Modifier.size(width = 36.dp, height = 46.dp).clickable { onSettings(p) },
                                        contentAlignment = Alignment.Center,
                                    ) { DotsGlyph(StudioColors.Ink, Modifier.size(16.dp)) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Definições do pincel em uso: tamanho de 0,1 a 2000 px (escrito, com slider ou com −/+), opacidade, suavização,
 * smoothing por corda, modo de desenho, pontas afinadas (opcional) e pixelização (opcional, desligada por defeito).
 */
@Composable
internal fun BrushSettingsDialog(
    kind: BrushKind,
    preset: BrushPreset,
    size: Float,
    opacity: Float,
    stabilizer: Float,
    smoothing: Float,
    mode: Int,
    onSize: (Float) -> Unit,
    onOpacity: (Float) -> Unit,
    onStabilizer: (Float) -> Unit,
    onSmoothing: (Float) -> Unit,
    onMode: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    val prefs = Prefs(LocalContext.current)
    var taper by remember { mutableStateOf(StudioRender.taperEnds) }
    var pixelated by remember { mutableStateOf(StudioRender.pixelated) }
    var sizeText by remember(preset.id) { mutableStateOf(fmtSize(size)) }
    fun setSize(v: Float) {
        val r = roundSize(v)
        onSize(r)
        sizeText = fmtSize(r)
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            color = StudioColors.Surface.copy(alpha = PopupAlpha), shape = shape,
            modifier = Modifier.width(420.dp).border(1.dp, StudioColors.Outline, shape),
        ) {
            Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(preset.name, style = StudioType.Title.copy(fontSize = 15.sp))
                    Canvas(
                        Modifier.fillMaxWidth().height(64.dp).clip(RoundedCornerShape(10.dp)).background(StudioColors.Navy)
                            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                    ) { drawBrushSample(preset, size, opacity, StudioColors.Ink) }
                    if (kind == BrushKind.BRUSH) {
                        Text("DESENHAR", style = StudioType.Caption, modifier = Modifier.padding(top = 8.dp, bottom = 3.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            for ((label, m) in listOf("Normal" to 0, "Dentro" to 1, "Por trás" to 2)) {
                                val sel = m == mode
                                Box(
                                    Modifier.weight(1f).clip(RoundedCornerShape(9.dp)).background(if (sel) StudioColors.Accent else StudioColors.Surface2)
                                        .border(1.dp, if (sel) StudioColors.Accent else StudioColors.Outline, RoundedCornerShape(9.dp))
                                        .clickable { onMode(m) }.padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center,
                                ) { Text(label, style = StudioType.Label.copy(fontSize = 11.sp)) }
                            }
                        }
                    }
                    ToggleRow("PONTAS AFINADAS", taper) { taper = it; prefs.taperEnds = it }
                    ToggleRow("PIXELADO (SEM ANTI-ALIASING)", pixelated) { pixelated = it; prefs.pixelated = it }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(0.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("TAMANHO", style = StudioType.Caption, modifier = Modifier.weight(1f))
                        Box(
                            Modifier.size(24.dp).clip(RoundedCornerShape(7.dp)).background(StudioColors.Surface2)
                                .border(1.dp, StudioColors.Outline, RoundedCornerShape(7.dp)).clickable { setSize(size - sizeStep(size - 0.01f)) },
                            contentAlignment = Alignment.Center,
                        ) { Text("−", style = StudioType.Label.copy(fontSize = 14.sp)) }
                        Box(
                            Modifier.width(56.dp).height(26.dp).clip(RoundedCornerShape(7.dp)).background(StudioColors.Surface2)
                                .border(1.dp, StudioColors.Outline, RoundedCornerShape(7.dp)).padding(horizontal = 4.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            BasicTextField(
                                value = sizeText,
                                onValueChange = { raw ->
                                    val t = raw.replace(',', '.').filter { c -> c.isDigit() || c == '.' }.take(7)
                                    sizeText = t
                                    val v = t.toFloatOrNull()
                                    if (v != null && v > 0f) onSize(roundSize(v))
                                },
                                singleLine = true,
                                textStyle = StudioType.Number.copy(fontSize = 12.sp, textAlign = TextAlign.Center),
                                cursorBrush = SolidColor(StudioColors.Accent),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            )
                        }
                        Text("px", style = StudioType.Caption)
                        Box(
                            Modifier.size(24.dp).clip(RoundedCornerShape(7.dp)).background(StudioColors.Surface2)
                                .border(1.dp, StudioColors.Outline, RoundedCornerShape(7.dp)).clickable { setSize(size + sizeStep(size)) },
                            contentAlignment = Alignment.Center,
                        ) { Text("+", style = StudioType.Label.copy(fontSize = 14.sp)) }
                    }
                    StudioSlider(sliderFromSize(size)) { setSize(sizeFromSlider(it)) }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("OPACIDADE", style = StudioType.Caption)
                        Text("${(opacity * 100).roundToInt()}%", style = StudioType.Number)
                    }
                    StudioSlider(opacity) { onOpacity(it.coerceIn(0.01f, 1f)) }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("SUAVIZAÇÃO", style = StudioType.Caption)
                        Text("${(smoothing * 100).roundToInt()}%", style = StudioType.Number)
                    }
                    StudioSlider(smoothing, onSmoothing)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("SMOOTHING (CORDA)", style = StudioType.Caption)
                        Text("${(stabilizer * 40f).roundToInt()} px", style = StudioType.Number)
                    }
                    StudioSlider(stabilizer, onStabilizer)
                    Spacer(Modifier.height(4.dp))
                    Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Concluir", style = StudioType.Label.copy(color = Color.White)) }
                }
            }
        }
    }
}
