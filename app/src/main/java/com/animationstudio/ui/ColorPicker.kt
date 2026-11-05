package com.animationstudio.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/** Métodos de cor: paleta, RGB e três seletores HSB (disco, roda e clássico). */
internal enum class ColorMethod(val label: String) {
    PALETTE("Paleta"), RGB("RGB"), DISC("Disco"), WHEEL("Roda"), CLASSIC("Clássico")
}

private val PaletteColors: List<Int> = listOf(
    0xFF000000, 0xFF404040, 0xFF808080, 0xFFC0C0C0, 0xFFFFFFFF, 0xFF7A4A21, 0xFFB5651D, 0xFFE0B080,
    0xFFFF0000, 0xFFFF6A00, 0xFFFFC000, 0xFFFFFF00, 0xFFA0E000, 0xFF00C000, 0xFF008060, 0xFF00C0C0,
    0xFF00A0FF, 0xFF0050FF, 0xFF0000C0, 0xFF6000C0, 0xFFA000E0, 0xFFFF00FF, 0xFFFF0080, 0xFFFF9090,
).map { it.toInt() }

/** Matiz 0 fica a -30° (ecrã, sentido horário) e cresce no sentido anti-horário, como nas referências. */
private fun hueOf(thetaDeg: Float): Float = (((-thetaDeg - 30f) % 360f) + 360f) % 360f

private fun thetaRad(h: Float): Double = Math.toRadians((-(h + 30f)).toDouble())

private val HueStops: Array<Pair<Float, Color>> = Array(37) { k ->
    Pair(k / 36f, hsvColor(hueOf(k * 10f), 1f, 1f))
}

private val HueSliderColors: List<Color> =
    listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red)

internal fun hsvColor(h: Float, s: Float, v: Float): Color =
    Color(android.graphics.Color.HSVToColor(floatArrayOf(h.coerceIn(0f, 360f), s.coerceIn(0f, 1f), v.coerceIn(0f, 1f))))

private fun DrawScope.drawChecker(cell: Float = 8f) {
    drawRect(Color(0xFF3A3A3A))
    var y = 0f
    var row = 0
    while (y < size.height) {
        var x = 0f
        var col = row % 2
        while (x < size.width) {
            if (col % 2 == 0) drawRect(Color(0xFF5A5A5A), Offset(x, y), Size(cell, cell))
            x += cell
            col += 1
        }
        y += cell
        row += 1
    }
}

private fun DrawScope.knob(center: Offset, radius: Float, fill: Color) {
    drawCircle(fill, radius, center)
    drawCircle(Color.White, radius, center, style = Stroke(width = radius * 0.2f))
}

/** Amostra de cor sobre xadrez (mostra a transparência). */
@Composable
internal fun CheckerBox(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        drawChecker()
        drawRect(color)
    }
}

@Composable
private fun Modifier.dragPick(onPick: (Offset, IntSize, Boolean) -> Unit): Modifier {
    val cur by rememberUpdatedState(onPick)
    return this.pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            cur(down.position, size, true)
            down.consume()
            do {
                val event = awaitPointerEvent()
                val ch = event.changes.firstOrNull { it.id == down.id }
                if (ch != null && ch.pressed) {
                    cur(ch.position, size, false)
                    ch.consume()
                }
            } while (event.changes.any { it.pressed })
        }
    }
}

@Composable
private fun GradSlider(
    value: Float,
    onChange: (Float) -> Unit,
    colors: List<Color>,
    knobColor: Color,
    checker: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Canvas(
        modifier.fillMaxWidth().height(28.dp).dragPick { o, sz, _ ->
            val r = sz.height / 2f
            onChange(((o.x - r) / (sz.width - 2f * r)).coerceIn(0f, 1f))
        }
    ) {
        val r = size.height / 2f
        val shape = Path().apply { addRoundRect(RoundRect(0f, 0f, size.width, size.height, CornerRadius(r, r))) }
        clipPath(shape) {
            if (checker) drawChecker()
            drawRect(Brush.horizontalGradient(colors))
        }
        drawRoundRect(Color.White.copy(alpha = 0.35f), Offset.Zero, size, CornerRadius(r, r), style = Stroke(width = 1.5f))
        knob(Offset(r + value * (size.width - 2f * r), r), r * 0.85f, knobColor)
    }
}

@Composable
private fun VerticalAlphaSlider(alpha: Float, onChange: (Float) -> Unit, opaque: Color, modifier: Modifier = Modifier) {
    Canvas(
        modifier.width(28.dp).fillMaxHeight().dragPick { o, sz, _ ->
            val r = sz.width / 2f
            onChange(1f - ((o.y - r) / (sz.height - 2f * r)).coerceIn(0f, 1f))
        }
    ) {
        val r = size.width / 2f
        val shape = Path().apply { addRoundRect(RoundRect(0f, 0f, size.width, size.height, CornerRadius(r, r))) }
        clipPath(shape) {
            drawChecker()
            drawRect(Brush.verticalGradient(listOf(opaque, opaque.copy(alpha = 0f))))
        }
        drawRoundRect(Color.White.copy(alpha = 0.35f), Offset.Zero, size, CornerRadius(r, r), style = Stroke(width = 1.5f))
        knob(Offset(r, r + (1f - alpha) * (size.height - 2f * r)), r * 0.85f, opaque.copy(alpha = alpha))
    }
}

/** Estilo "disco": anel de matiz + círculo interior de saturação (horizontal) e brilho (vertical). */
@Composable
private fun DiscPicker(h: Float, s: Float, v: Float, onHue: (Float) -> Unit, onSv: (Float, Float) -> Unit, modifier: Modifier = Modifier) {
    var zone by remember { mutableStateOf(0) }
    Canvas(
        modifier.dragPick { o, sz, start ->
            val half = min(sz.width, sz.height) / 2f
            val cx = sz.width / 2f
            val cy = sz.height / 2f
            val ri = half * 0.66f
            val dx = o.x - cx
            val dy = o.y - cy
            if (start) zone = if (hypot(dx, dy) > ri + half * 0.04f) 1 else 2
            if (zone == 1) {
                onHue(hueOf(Math.toDegrees(atan2(dy, dx).toDouble()).toFloat()))
            } else {
                onSv(
                    ((o.x - (cx - ri)) / (2f * ri)).coerceIn(0f, 1f),
                    1f - ((o.y - (cy - ri)) / (2f * ri)).coerceIn(0f, 1f)
                )
            }
        }
    ) {
        val half = min(size.width, size.height) / 2f
        val c = Offset(size.width / 2f, size.height / 2f)
        val ringW = half * 0.2f
        val ringC = half - ringW / 2f
        val ri = half * 0.66f
        drawCircle(brush = Brush.sweepGradient(*HueStops, center = c), radius = ringC, center = c, style = Stroke(width = ringW))
        val hueCol = hsvColor(h, 1f, 1f)
        val circle = Path().apply { addOval(Rect(c.x - ri, c.y - ri, c.x + ri, c.y + ri)) }
        clipPath(circle) {
            drawRect(
                Brush.horizontalGradient(listOf(Color.White, hueCol), startX = c.x - ri, endX = c.x + ri),
                Offset(c.x - ri, c.y - ri), Size(2f * ri, 2f * ri)
            )
            drawRect(
                Brush.verticalGradient(listOf(Color.Transparent, Color.Black), startY = c.y - ri, endY = c.y + ri),
                Offset(c.x - ri, c.y - ri), Size(2f * ri, 2f * ri)
            )
        }
        val ang = thetaRad(h)
        knob(Offset(c.x + ringC * cos(ang).toFloat(), c.y + ringC * sin(ang).toFloat()), ringW * 0.62f, hueCol)
        knob(Offset(c.x - ri + s * 2f * ri, c.y - ri + (1f - v) * 2f * ri), half * 0.06f, hsvColor(h, s, v))
    }
}

/** Estilo "roda": matiz pelo ângulo, saturação pelo raio; o brilho escurece a roda. */
@Composable
private fun WheelPicker(h: Float, s: Float, v: Float, onHs: (Float, Float) -> Unit, modifier: Modifier = Modifier) {
    Canvas(
        modifier.dragPick { o, sz, _ ->
            val half = min(sz.width, sz.height) / 2f
            val dx = o.x - sz.width / 2f
            val dy = o.y - sz.height / 2f
            onHs(hueOf(Math.toDegrees(atan2(dy, dx).toDouble()).toFloat()), (hypot(dx, dy) / half).coerceIn(0f, 1f))
        }
    ) {
        val half = min(size.width, size.height) / 2f
        val c = Offset(size.width / 2f, size.height / 2f)
        drawCircle(brush = Brush.sweepGradient(*HueStops, center = c), radius = half, center = c)
        drawCircle(brush = Brush.radialGradient(listOf(Color.White, Color.Transparent), center = c, radius = half), radius = half, center = c)
        drawCircle(Color.Black.copy(alpha = 1f - v), half, c)
        val ang = thetaRad(h)
        knob(Offset(c.x + s * half * cos(ang).toFloat(), c.y + s * half * sin(ang).toFloat()), half * 0.09f, hsvColor(h, s, v))
    }
}

/** Estilo "clássico": quadrado de saturação/brilho para o matiz escolhido. */
@Composable
private fun SbSquare(h: Float, s: Float, v: Float, onSv: (Float, Float) -> Unit, modifier: Modifier = Modifier) {
    Canvas(
        modifier.dragPick { o, sz, _ ->
            onSv((o.x / sz.width).coerceIn(0f, 1f), 1f - (o.y / sz.height).coerceIn(0f, 1f))
        }
    ) {
        val corner = CornerRadius(14.dp.toPx(), 14.dp.toPx())
        drawRoundRect(Brush.horizontalGradient(listOf(Color.White, hsvColor(h, 1f, 1f))), Offset.Zero, size, corner)
        drawRoundRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)), Offset.Zero, size, corner)
        knob(Offset(s * size.width, (1f - v) * size.height), 10.dp.toPx(), hsvColor(h, s, v))
    }
}

@Composable
private fun ColorTab(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier.clip(RoundedCornerShape(8.dp))
            .background(if (selected) StudioColors.Cream else StudioColors.PanelLight)
            .clickable(onClick = onClick).padding(vertical = 7.dp),
        contentAlignment = Alignment.Center,
    ) { Text(label, color = if (selected) Color.Black else StudioColors.Ink, fontSize = 11.sp, maxLines = 1) }
}

@Composable
private fun LabeledSlider(label: String, content: @Composable () -> Unit) {
    Text(label, color = StudioColors.Ink.copy(alpha = 0.75f), fontSize = 11.sp)
    content()
}

@Composable
private fun PaletteGrid(recents: List<Int>, onPick: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        if (recents.isNotEmpty()) {
            Text("Recentes", color = StudioColors.Ink.copy(alpha = 0.7f), fontSize = 11.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { for (c in recents.take(8)) PaletteSwatch(c, onPick) }
        }
        for (row in PaletteColors.chunked(8)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { for (c in row) PaletteSwatch(c, onPick) }
        }
    }
}

@Composable
private fun PaletteSwatch(c: Int, onPick: (Int) -> Unit) {
    Box(
        Modifier.size(34.dp).clip(RoundedCornerShape(8.dp)).background(Color(c))
            .border(1.dp, StudioColors.Ink.copy(alpha = 0.3f), RoundedCornerShape(8.dp)).clickable { onPick(c) }
    )
}

@Composable
private fun RgbSliders(initial: Int, onChange: (Int) -> Unit, modifier: Modifier = Modifier) {
    var r by remember { mutableFloatStateOf(((initial shr 16) and 0xFF) / 255f) }
    var g by remember { mutableFloatStateOf(((initial shr 8) and 0xFF) / 255f) }
    var b by remember { mutableFloatStateOf((initial and 0xFF) / 255f) }
    fun push() = onChange((0xFF shl 24) or ((r * 255f).roundToInt() shl 16) or ((g * 255f).roundToInt() shl 8) or (b * 255f).roundToInt())
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        LabeledSlider("R  ${(r * 255f).roundToInt()}") {
            GradSlider(r, { r = it; push() }, listOf(Color(0f, g, b), Color(1f, g, b)), Color(r, g, b))
        }
        LabeledSlider("G  ${(g * 255f).roundToInt()}") {
            GradSlider(g, { g = it; push() }, listOf(Color(r, 0f, b), Color(r, 1f, b)), Color(r, g, b))
        }
        LabeledSlider("B  ${(b * 255f).roundToInt()}") {
            GradSlider(b, { b = it; push() }, listOf(Color(r, g, 0f), Color(r, g, 1f)), Color(r, g, b))
        }
    }
}

/**
 * Seletor de cor em ecrã inteiro (horizontal): à esquerda o seletor, à direita os métodos, a transparência,
 * a pré-visualização e OK/Cancelar. A cor só é aplicada ao tocar em OK.
 */
@Composable
internal fun ColorPickerDialog(
    initialMethod: ColorMethod,
    initial: Int,
    recents: List<Int>,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val hsv0 = remember { FloatArray(3).also { android.graphics.Color.colorToHSV(initial, it) } }
    var h by remember { mutableFloatStateOf(hsv0[0]) }
    var s by remember { mutableFloatStateOf(hsv0[1]) }
    var v by remember { mutableFloatStateOf(hsv0[2]) }
    var a by remember { mutableFloatStateOf(((initial ushr 24) and 0xFF) / 255f) }
    var method by remember { mutableStateOf(initialMethod) }

    val rgb = android.graphics.Color.HSVToColor(floatArrayOf(h.coerceIn(0f, 360f), s.coerceIn(0f, 1f), v.coerceIn(0f, 1f))) and 0xFFFFFF
    val argb = ((a * 255f).roundToInt() shl 24) or rgb
    val opaque = Color(rgb or (0xFF shl 24))
    val current = Color(argb)

    fun setFrom(c: Int) {
        val t = FloatArray(3)
        android.graphics.Color.colorToHSV(c, t)
        h = t[0]
        s = t[1]
        v = t[2]
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            color = StudioColors.Panel, shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth(0.94f).fillMaxHeight(0.94f),
        ) {
            Row(Modifier.padding(10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                    when (method) {
                        ColorMethod.DISC -> DiscPicker(h, s, v, { h = it }, { ns, nv -> s = ns; v = nv }, Modifier.fillMaxHeight().aspectRatio(1f))
                        ColorMethod.WHEEL -> WheelPicker(h, s, v, { nh, ns -> h = nh; s = ns }, Modifier.fillMaxHeight().aspectRatio(1f))
                        ColorMethod.CLASSIC -> Row(Modifier.fillMaxHeight().padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            SbSquare(h, s, v, { ns, nv -> s = ns; v = nv }, Modifier.fillMaxHeight().aspectRatio(1f))
                            VerticalAlphaSlider(a, { a = it }, opaque)
                        }
                        ColorMethod.PALETTE -> PaletteGrid(recents) { setFrom(it) }
                        ColorMethod.RGB -> RgbSliders(rgb or (0xFF shl 24), { setFrom(it) }, Modifier.fillMaxWidth(0.9f))
                    }
                }
                Column(Modifier.width(210.dp).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        ColorTab(ColorMethod.PALETTE.label, method == ColorMethod.PALETTE, Modifier.weight(1f)) { method = ColorMethod.PALETTE }
                        ColorTab(ColorMethod.RGB.label, method == ColorMethod.RGB, Modifier.weight(1f)) { method = ColorMethod.RGB }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        ColorTab(ColorMethod.DISC.label, method == ColorMethod.DISC, Modifier.weight(1f)) { method = ColorMethod.DISC }
                        ColorTab(ColorMethod.WHEEL.label, method == ColorMethod.WHEEL, Modifier.weight(1f)) { method = ColorMethod.WHEEL }
                        ColorTab(ColorMethod.CLASSIC.label, method == ColorMethod.CLASSIC, Modifier.weight(1f)) { method = ColorMethod.CLASSIC }
                    }
                    when (method) {
                        ColorMethod.WHEEL -> LabeledSlider("Brilho ${(v * 100f).roundToInt()}") {
                            GradSlider(v, { v = it }, listOf(Color.Black, hsvColor(h, s, 1f)), current)
                        }
                        ColorMethod.CLASSIC -> {
                            LabeledSlider("Matiz ${h.roundToInt()}") { GradSlider(h / 360f, { h = it * 360f }, HueSliderColors, hsvColor(h, 1f, 1f)) }
                            LabeledSlider("Saturação ${(s * 100f).roundToInt()}") { GradSlider(s, { s = it }, listOf(hsvColor(h, 0f, v), hsvColor(h, 1f, v)), current) }
                            LabeledSlider("Brilho ${(v * 100f).roundToInt()}") { GradSlider(v, { v = it }, listOf(Color.Black, hsvColor(h, s, 1f)), current) }
                        }
                        else -> {}
                    }
                    if (method != ColorMethod.CLASSIC) {
                        LabeledSlider("Opacidade ${(a * 100f).roundToInt()}%") {
                            GradSlider(a, { a = it }, listOf(opaque.copy(alpha = 0f), opaque), current, checker = true)
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    CheckerBox(current, Modifier.fillMaxWidth().height(34.dp).clip(RoundedCornerShape(8.dp)).border(2.dp, StudioColors.Ink, RoundedCornerShape(8.dp)))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = onDismiss) { Text("Cancelar") }
                        Button(onClick = { onConfirm(argb) }) { Text("OK") }
                    }
                }
            }
        }
    }
}
