package com.animationstudio

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.animationstudio.ui.AnimationEditor
import com.animationstudio.ui.StudioColors

class MainActivity : ComponentActivity() {
    private external fun nativeEngineStatus(): String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        System.loadLibrary("animation_engine")
        setContent {
            MaterialTheme {
                StudioApp(onProjectOpened = { nativeEngineStatus() })
            }
        }
    }
}

@Composable
private fun StudioApp(onProjectOpened: () -> Unit) {
    var projectOpen by remember { mutableStateOf(false) }
    var showNew by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("Sem título") }
    var fps by remember { mutableIntStateOf(24) }
    var w by remember { mutableIntStateOf(1280) }
    var h by remember { mutableIntStateOf(720) }

    Surface(color = StudioColors.Workbench, modifier = Modifier.fillMaxSize()) {
        if (projectOpen) {
            AnimationEditor(name, fps, w, h) { projectOpen = false }
        } else {
            ProjectHome(onNew = { showNew = true })
        }
        if (showNew) {
            NewProjectDialog(
                onCancel = { showNew = false },
                onCreate = { n, f, pw, ph ->
                    name = n; fps = f; w = pw; h = ph
                    showNew = false
                    projectOpen = true
                    onProjectOpened()
                }
            )
        }
    }
}

@Composable
private fun ProjectHome(onNew: () -> Unit) {
    Row(Modifier.fillMaxSize().padding(8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(
            Modifier.weight(1.05f).fillMaxSize().background(StudioColors.Panel, RoundedCornerShape(4.dp)).border(1.dp, Color(0xFF171819))
        ) {
            Text("Animation Studio", color = StudioColors.Ink, fontSize = 19.sp, modifier = Modifier.padding(12.dp))
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                // Sem projetos fictícios: a persistência real ainda não existe (ver docs/RASTREABILIDADE.md, REQ-PROJ-01).
                Text("Ainda não há projetos guardados.", color = StudioColors.Ink.copy(alpha = .7f), fontSize = 13.sp)
            }
        }
        Column(
            Modifier.weight(1f).fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Brush, null, tint = StudioColors.Cream, modifier = Modifier.size(72.dp))
                    Text("2D Animation Studio", color = StudioColors.Ink, fontSize = 20.sp)
                    Text("Cria animações frame a frame", color = StudioColors.Ink.copy(alpha = .7f), fontSize = 13.sp)
                }
            }
            Button(onClick = onNew, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(5.dp)) {
                Icon(Icons.Default.Add, null)
                Text("  Novo projeto")
            }
        }
    }
}

@Composable
private fun NewProjectDialog(onCancel: () -> Unit, onCreate: (String, Int, Int, Int) -> Unit) {
    var name by remember { mutableStateOf("Sem título") }
    var fps by remember { mutableStateOf("24") }
    var w by remember { mutableStateOf("1280") }
    var h by remember { mutableStateOf("720") }
    val fpsV = fps.toIntOrNull()
    val wV = w.toIntOrNull()
    val hV = h.toIntOrNull()
    val valid = name.isNotBlank() && fpsV in 1..120 && wV in 64..8192 && hV in 64..8192
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = StudioColors.Ink, unfocusedTextColor = StudioColors.Ink,
        focusedLabelColor = StudioColors.Cream, unfocusedLabelColor = StudioColors.Ink.copy(alpha = .7f)
    )
    AlertDialog(
        onDismissRequest = onCancel,
        containerColor = StudioColors.Panel,
        title = { Text("Novo projeto", color = StudioColors.Ink) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Nome do projeto") }, singleLine = true, colors = fieldColors)
                OutlinedTextField(fps, { fps = it }, label = { Text("FPS (1–120)") }, singleLine = true, isError = fpsV !in 1..120, colors = fieldColors)
                OutlinedTextField(w, { w = it }, label = { Text("Largura da folha (64–8192 px)") }, singleLine = true, isError = wV !in 64..8192, colors = fieldColors)
                OutlinedTextField(h, { h = it }, label = { Text("Altura da folha (64–8192 px)") }, singleLine = true, isError = hV !in 64..8192, colors = fieldColors)
            }
        },
        confirmButton = { Button(enabled = valid, onClick = { onCreate(name.trim(), fpsV!!, wV!!, hV!!) }) { Text("Criar") } },
        dismissButton = { TextButton(onClick = onCancel) { Text("Cancelar") } }
    )
}
