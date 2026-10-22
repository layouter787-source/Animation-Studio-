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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
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
private fun StudioSlider(value: Float, onChange: (Float) -> Unit, range: ClosedFloatingPointRange<Float> = 0f..1f) {
    Slider(
        value = value, onValueChange = onChange, valueRange = range,
        colors = SliderDefaults.colors(
            thumbColor = StudioColors.Accent, activeTrackColor = StudioColors.Accent, inactiveTrackColor = StudioColors.Surface3,
        ),
    )
}

@Composable
private fun PresetRow(p: BrushPreset, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        Modifier.fillMaxWidth().clip(shape).background(if (selected) StudioColors.AccentSoft else StudioColors.Surface2)
            .border(if (selected) 2.dp else 1.dp, if (selected) StudioColors.Accent else StudioColors.Outline, shape)
            .clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Canvas(Modifier.weight(1f).height(38.dp).graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }) {
            drawBrushSample(p.kind, p.defaultSize, p.defaultOpacity, p.taper, p.hardness, StudioColors.Ink)
        }
        Column(Modifier.width(118.dp)) {
            Text(p.name, style = StudioType.Label)
            Text("${p.defaultSize.roundToInt()} PX  ·  ${(p.defaultOpacity * 100).roundToInt()}%", style = StudioType.Caption)
        }
    }
}

/**
 * Pincel ou borracha: biblioteca organizada por categoria, cada predefinição com a sua pré-visualização
 * renderizada, e controlos de tamanho (1–2000 px), opacidade e estabilizador de traço.
 */
@Composable
internal fun BrushDialog(
    kind: BrushKind,
    presetId: String,
    size: Float,
    opacity: Float,
    stabilizer: Float,
    onPreset: (BrushPreset) -> Unit,
    onSize: (Float) -> Unit,
    onOpacity: (Float) -> Unit,
    onStabilizer: (Float) -> Unit,
    onDismiss: () -> Unit,
) {
    val cats = BrushLibrary.categoriesOf(kind)
    var category by remember { mutableStateOf(BrushLibrary.byId(presetId).category) }
    val presets = BrushLibrary.ofKind(kind).filter { it.category == category }
    val current = BrushLibrary.byId(presetId)

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            color = StudioColors.Surface, shape = RoundedCornerShape(18.dp),
            modifier = Modifier.fillMaxWidth(0.94f).fillMaxHeight(0.94f),
        ) {
            Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(if (kind == BrushKind.BRUSH) "PINCÉIS" else "BORRACHAS", style = StudioType.Caption)
                    if (cats.size > 1) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            for (c in cats) {
                                val sel = c == category
                                Box(
                                    Modifier.clip(RoundedCornerShape(10.dp))
                                        .background(if (sel) StudioColors.Accent else StudioColors.Surface2)
                                        .clickable { category = c }.padding(horizontal = 12.dp, vertical = 6.dp)
                                ) { Text(c, style = StudioType.Label) }
                            }
                        }
                    }
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        for (p in presets) PresetRow(p, p.id == presetId) { onPreset(p) }
                    }
                }
                Column(Modifier.width(260.dp).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(current.name, style = StudioType.Title)
                    Canvas(
                        Modifier.fillMaxWidth().height(70.dp).clip(RoundedCornerShape(12.dp))
                            .background(StudioColors.Navy).graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                    ) {
                        drawBrushSample(kind, size, opacity, current.taper, current.hardness, StudioColors.Ink)
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("TAMANHO", style = StudioType.Caption)
                        Text("${size.roundToInt()} px", style = StudioType.Number)
                    }
                    StudioSlider(sliderFromSize(size), { onSize(sizeFromSlider(it)) })
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("OPACIDADE", style = StudioType.Caption)
                        Text("${(opacity * 100).roundToInt()}%", style = StudioType.Number)
                    }
                    StudioSlider(opacity, { onOpacity(it.coerceIn(0.01f, 1f)) })
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("ESTABILIZADOR", style = StudioType.Caption)
                        Text("${(stabilizer * 100).roundToInt()}%", style = StudioType.Number)
                    }
                    StudioSlider(stabilizer, onStabilizer)
                    Spacer(Modifier.weight(1f))
                    Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Concluir") }
                }
            }
        }
    }
}
