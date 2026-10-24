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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
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

private val SizeLog = ln(BrushLimits.MAX_SIZE)

/** Tamanho (1–2000 px) numa escala logarítmica: o controlo fino fica nos tamanhos pequenos. */
private fun sizeFromSlider(t: Float): Float = exp(t.coerceIn(0f, 1f) * SizeLog).coerceIn(BrushLimits.MIN_SIZE, BrushLimits.MAX_SIZE)

private fun sliderFromSize(s: Float): Float = (ln(s.coerceIn(BrushLimits.MIN_SIZE, BrushLimits.MAX_SIZE)) / SizeLog).coerceIn(0f, 1f)

@Composable
private fun StudioSlider(value: Float, onChange: (Float) -> Unit) {
    Slider(
        value = value, onValueChange = onChange,
        colors = SliderDefaults.colors(thumbColor = StudioColors.Accent, activeTrackColor = StudioColors.Accent, inactiveTrackColor = StudioColors.Surface3),
    )
}

/**
 * Biblioteca de pincéis/borrachas (como no esboço): aba, pesquisa, categorias e lista com pré-visualização
 * renderizada. Os três pontos de cada linha abrem o tamanho e a opacidade dessa predefinição.
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
                    .background(StudioColors.Surface3).padding(horizontal = 16.dp, vertical = 4.dp)
            ) { Text(if (kind == BrushKind.BRUSH) "PINCÉIS" else "BORRACHAS", style = StudioType.Caption.copy(color = StudioColors.Ink)) }
            Surface(color = StudioColors.Surface, shape = shape, modifier = Modifier.weight(1f).border(1.dp, StudioColors.Outline, shape)) {
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
                                        .clickable { onSelect(p) },
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

/** Definições do pincel em uso (duplo toque): tamanho 1–2000 px, opacidade, estabilizador e modo de desenho. */
@Composable
internal fun BrushSettingsDialog(
    kind: BrushKind,
    preset: BrushPreset,
    size: Float,
    opacity: Float,
    stabilizer: Float,
    mode: Int,
    onSize: (Float) -> Unit,
    onOpacity: (Float) -> Unit,
    onStabilizer: (Float) -> Unit,
    onMode: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            color = StudioColors.Surface, shape = shape,
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
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(0.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("TAMANHO", style = StudioType.Caption)
                        Text("${size.roundToInt()} px", style = StudioType.Number)
                    }
                    StudioSlider(sliderFromSize(size)) { onSize(sizeFromSlider(it)) }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("OPACIDADE", style = StudioType.Caption)
                        Text("${(opacity * 100).roundToInt()}%", style = StudioType.Number)
                    }
                    StudioSlider(opacity) { onOpacity(it.coerceIn(0.01f, 1f)) }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("ESTABILIZADOR", style = StudioType.Caption)
                        Text("${(stabilizer * 100).roundToInt()}%", style = StudioType.Number)
                    }
                    StudioSlider(stabilizer, onStabilizer)
                    Spacer(Modifier.height(4.dp))
                    Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Concluir", style = StudioType.Label.copy(color = Color.White)) }
                }
            }
        }
    }
}
