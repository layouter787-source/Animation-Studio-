package com.animationstudio.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@Composable
private fun MmRow(text: String, trailing: @Composable () -> Unit = {}, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 9.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = StudioType.Body.copy(fontSize = 13.sp), modifier = Modifier.weight(1f))
        trailing()
    }
    Box(Modifier.fillMaxWidth().size(width = 1.dp, height = 1.dp).background(StudioColors.Outline.copy(alpha = 0.6f)))
}

@Composable
private fun MmSection(text: String) {
    Text(text, style = StudioType.Caption.copy(fontSize = 8.sp), modifier = Modifier.padding(top = 8.dp, bottom = 2.dp, start = 4.dp))
}

/** Menu principal compacto, agora com importação de imagem e vídeo e exportação de vídeo. */
@Composable
internal fun MainMenu(
    penOnly: Boolean,
    palmReject: Boolean,
    onPenOnly: (Boolean) -> Unit,
    onPalm: (Boolean) -> Unit,
    onGrid: () -> Unit,
    onResolution: () -> Unit,
    onOrganize: () -> Unit,
    onImportImage: () -> Unit,
    onImportVideo: () -> Unit,
    onExportVideo: () -> Unit,
    onExportPng: () -> Unit,
    onExportSequence: () -> Unit,
    onExportZip: () -> Unit,
    onBack: () -> Unit,
    onDismiss: () -> Unit,
) {
    val maxH = LocalConfiguration.current.screenHeightDp.dp * 0.94f
    val shape = RoundedCornerShape(16.dp)
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            color = StudioColors.Surface, shape = shape,
            modifier = Modifier.width(300.dp).heightIn(max = maxH).border(1.dp, StudioColors.Outline, shape),
        ) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 10.dp)) {
                PanelGlyph(StudioColors.Ink, Modifier.size(20.dp))
                Spacer(Modifier.size(4.dp))
                MmRow("Grade", trailing = { PaletteGlyph(StudioColors.InkDim, Modifier.size(16.dp)) }) { onGrid() }
                MmRow("Definições de resolução") { onResolution() }
                MmRow("Organização da interface") { onOrganize() }
                MmSection("IMPORTAR")
                MmRow("Importar imagem (para o frame atual)") { onImportImage() }
                MmRow("Importar vídeo (um frame por imagem)") { onImportVideo() }
                MmSection("EXPORTAR")
                MmRow("Exportar vídeo (MP4)") { onExportVideo() }
                MmRow("Exportar PNG do frame atual") { onExportPng() }
                MmRow("Sequência de PNG (escolher pasta)") { onExportSequence() }
                MmRow("ZIP com todos os frames") { onExportZip() }
                MmSection("CANETA")
                MmRow(
                    "S Pen Samsung",
                    trailing = { Switch(checked = penOnly, onCheckedChange = onPenOnly, colors = SwitchDefaults.colors(checkedTrackColor = StudioColors.Accent)) },
                ) { onPenOnly(!penOnly) }
                MmRow(
                    "Rejeição de palma",
                    trailing = { Switch(checked = palmReject, onCheckedChange = onPalm, colors = SwitchDefaults.colors(checkedTrackColor = StudioColors.Accent)) },
                ) { onPalm(!palmReject) }
                MmRow("Voltar aos projetos") { onBack() }
            }
        }
    }
}
