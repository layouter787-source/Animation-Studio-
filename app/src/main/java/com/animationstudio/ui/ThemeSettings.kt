package com.animationstudio.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/** Definições de cor do app: cor principal (fundo) e cor de destaque. A mudança vê-se logo e fica guardada. */
@Composable
fun ThemeSettingsDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = StudioColors.Surface,
        title = { Text("Cores do app", style = StudioType.Title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("COR PRINCIPAL", style = StudioType.Caption)
                SwatchGrid(StudioColors.MainPalette, StudioColors.mainArgb) { StudioColors.setMain(context, it) }
                Text("COR DE DESTAQUE", style = StudioType.Caption)
                SwatchGrid(StudioColors.AccentPalette, StudioColors.accentArgb) { StudioColors.setAccent(context, it) }
                val main = StudioColors.MainPalette.firstOrNull { it.argb == StudioColors.mainArgb }?.name ?: "Personalizada"
                val acc = StudioColors.AccentPalette.firstOrNull { it.argb == StudioColors.accentArgb }?.name ?: "Personalizada"
                Text("$main  ·  $acc", style = StudioType.Body.copy(color = StudioColors.InkDim))
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Fechar", style = StudioType.Label) } },
        dismissButton = {
            TextButton(onClick = { StudioColors.reset(context) }) { Text("Repor", style = StudioType.Label.copy(color = StudioColors.InkDim)) }
        },
    )
}

@Composable
private fun SwatchGrid(colors: List<PaletteColor>, selected: Int, onPick: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (row in colors.chunked(5)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (c in row) {
                    val on = c.argb == selected
                    val col = Color(c.argb)
                    Box(
                        Modifier.size(38.dp).clip(CircleShape).background(col)
                            .border(if (on) 3.dp else 1.dp, if (on) StudioColors.Ink else StudioColors.Outline, CircleShape)
                            .clickable { onPick(c.argb) },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (on) Icon(Icons.Default.Check, c.name, tint = if (col.luminance() > 0.5f) Color.Black else Color.White, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}
