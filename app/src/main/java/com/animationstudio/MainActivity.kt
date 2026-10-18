package com.animationstudio

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Redo
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Workbench = Color(0xFF303338)
private val Panel = Color(0xFF414449)
private val PanelLight = Color(0xFF55585D)
private val Cream = Color(0xFFFFEFC2)
private val Ink = Color(0xFFF2F0E8)

class MainActivity : ComponentActivity() {
    private external fun nativeEngineStatus(): String
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        System.loadLibrary("animation_engine")
        setContent {
            MaterialTheme {
                var projectOpen by remember { mutableStateOf(false) }
                var showNewProject by remember { mutableStateOf(false) }
                var projectName by remember { mutableStateOf("Untitled") }
                var fps by remember { mutableIntStateOf(24) }
                var sheetWidth by remember { mutableIntStateOf(1280) }
                var sheetHeight by remember { mutableIntStateOf(720) }
                Surface(color = Workbench, modifier = Modifier.fillMaxSize()) {
                    if (projectOpen) {
                        AnimationEditor(projectName, fps, sheetWidth, sheetHeight) { projectOpen = false }
                    } else {
                        ProjectHome(
                            onNew = { showNewProject = true },
                            onOpen = { projectOpen = true; nativeEngineStatus() }
                        )
                    }
                    if (showNewProject) {
                        AlertDialog(
                            onDismissRequest = { showNewProject = false },
                            containerColor = Panel,
                            title = { Text("Novo projeto", color = Ink) },
                            text = {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    OutlinedTextField(projectName, { projectName = it }, label = { Text("Nome do projeto") }, singleLine = true)
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("FPS", color = Ink, modifier = Modifier.width(64.dp))
                                        TextButton(onClick = { fps = (fps - 1).coerceAtLeast(1) }) { Text("−") }
                                        Text("$fps", color = Ink)
                                        TextButton(onClick = { fps = (fps + 1).coerceAtMost(120) }) { Text("+") }
                                    }
                                    OutlinedTextField(sheetWidth.toString(), { it.toIntOrNull()?.let { v -> sheetWidth = v.coerceIn(64, 8192) } }, label = { Text("Largura da folha (px)") }, singleLine = true)
                                    OutlinedTextField(sheetHeight.toString(), { it.toIntOrNull()?.let { v -> sheetHeight = v.coerceIn(64, 8192) } }, label = { Text("Altura da folha (px)") }, singleLine = true)
                                }
                            },
                            confirmButton = { Button(onClick = { projectOpen = true; showNewProject = false; nativeEngineStatus() }) { Text("Criar") } },
                            dismissButton = { TextButton(onClick = { showNewProject = false }) { Text("Cancelar") } }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProjectHome(onNew: () -> Unit, onOpen: () -> Unit) {
    Row(Modifier.fillMaxSize().padding(8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(Modifier.weight(1.05f).fillMaxSize().background(Panel, RoundedCornerShape(4.dp)).border(1.dp, Color(0xFF171819))) {
            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Animation Studio", color = Ink, fontSize = 19.sp, modifier = Modifier.weight(1f))
                IconButton(onClick = onNew) { Icon(Icons.Default.Add, "Novo projeto", tint = Cream) }
                IconButton(onClick = {}) { Icon(Icons.Default.Settings, "Definições", tint = Ink) }
            }
            listOf("Projeto de teste", "Untitled 2", "Untitled").forEach { name ->
                Row(Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(horizontal = 10.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(width = 54.dp, height = 34.dp).background(Color.White))
                    Text(name, color = Ink, modifier = Modifier.padding(start = 10.dp))
                }
            }
        }
        Column(Modifier.weight(1f).fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween, horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Brush, null, tint = Cream, modifier = Modifier.size(72.dp))
                    Text("2D Animation Studio", color = Ink, fontSize = 20.sp)
                    Text("Cria animações frame a frame", color = Ink.copy(alpha = .7f), fontSize = 13.sp)
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
private fun AnimationEditor(projectName: String, fps: Int, sheetWidth: Int, sheetHeight: Int, onClose: () -> Unit) {
    var selectedTool by remember { mutableStateOf("Pincel") }
    var frame by remember { mutableIntStateOf(1) }
    var frameCount by remember { mutableIntStateOf(1) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    var brushSize by remember { mutableFloatStateOf(8f) }
    var timelineVisible by remember { mutableStateOf(true) }
    val strokes = remember { mutableStateListOf<List<Offset>>() }

    Column(Modifier.fillMaxSize().background(Workbench).padding(5.dp)) {
        Row(Modifier.fillMaxWidth().height(48.dp).background(Panel), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(Icons.Default.Menu, "Fechar projeto", tint = Cream) }
            Text("Duração", color = Ink, fontSize = 12.sp)
            TextButton(onClick = { frameCount = (frameCount - 1).coerceAtLeast(1); frame = frame.coerceAtMost(frameCount) }) { Text("−", color = Ink) }
            Text("$frameCount frames", color = Ink, fontSize = 12.sp)
            TextButton(onClick = { frameCount++; frame = frameCount }) { Text("+", color = Ink) }
            Text("FPS $fps", color = Ink, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 6.dp))
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { timelineVisible = !timelineVisible }) { Icon(Icons.Default.Layers, "Timeline", tint = Cream) }
            IconButton(onClick = { frame = (frame - 1).coerceAtLeast(1) }) { Icon(Icons.Default.Undo, "Frame anterior", tint = Ink) }
            IconButton(onClick = { frame = (frame + 1).coerceAtMost(frameCount) }) { Icon(Icons.Default.Redo, "Frame seguinte", tint = Ink) }
        }
        if (timelineVisible) {
            Row(Modifier.fillMaxWidth().height(66.dp).background(Color(0xFF292B2E)).padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.width(112.dp)) {
                    Text("CAMADA 1", color = Ink, fontSize = 12.sp)
                    Text("CENÁRIO 1", color = Ink.copy(alpha = .75f), fontSize = 12.sp)
                }
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    (1..frameCount.coerceAtMost(12)).forEach { n ->
                        Box(Modifier.weight(1f).height(48.dp).background(if (n == frame) Cream else Panel).border(1.dp, if (n == frame) Color.White else Color.DarkGray).clickable { frame = n }, contentAlignment = Alignment.Center) {
                            Text("$n", color = if (n == frame) Color.Black else Ink, fontSize = 11.sp)
                        }
                    }
                }
                IconButton(onClick = { frameCount++; frame = frameCount }) { Icon(Icons.Default.Add, "Adicionar frame", tint = Cream) }
            }
        }
        Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Column(Modifier.width(62.dp).fillMaxSize().background(Panel), horizontalAlignment = Alignment.CenterHorizontally) {
                ToolButton("Pincel", Icons.Default.Brush, selectedTool) { selectedTool = "Pincel" }
                ToolButton("Borracha", Icons.Default.Delete, selectedTool) { selectedTool = "Borracha" }
                ToolButton("Camadas", Icons.Default.Layers, selectedTool) { selectedTool = "Camadas" }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { strokes.clear() }) { Icon(Icons.Default.Undo, "Limpar desenho", tint = Ink) }
                IconButton(onClick = { frameCount++; frame = frameCount }) { Icon(Icons.Default.Add, "Adicionar frame", tint = Cream) }
                IconButton(onClick = {}) { Icon(Icons.Default.PlayArrow, "Reproduzir", tint = Cream) }
            }
            Column(Modifier.weight(1f).fillMaxSize()) {
                Row(Modifier.fillMaxWidth().height(36.dp).background(Panel), verticalAlignment = Alignment.CenterVertically) {
                    Text("Ferramenta: $selectedTool", color = Ink, fontSize = 12.sp, modifier = Modifier.padding(start = 10.dp).weight(1f))
                    Text("Zoom ${(zoom * 100).toInt()}%", color = Ink, fontSize = 12.sp, modifier = Modifier.padding(end = 10.dp))
                }
                Canvas(
                    Modifier.weight(1f).fillMaxWidth().background(Color(0xFF202123))
                        .pointerInput(zoom, pan) {
                            detectTransformGestures { _, panChange, zoomChange, _ ->
                                zoom = (zoom * zoomChange).coerceIn(.25f, 4f)
                                pan += panChange
                            }
                        }
                        .pointerInput(selectedTool, brushSize, zoom, pan) {
                            awaitPointerEventScope {
                                while (true) {
                                    val down = awaitPointerEvent()
                                    val first = down.changes.firstOrNull() ?: continue
                                    if (first.pressed && selectedTool == "Pincel") {
                                        val path = mutableListOf<Offset>()
                                        path.add(first.position)
                                        while (true) {
                                            val event = awaitPointerEvent()
                                            val change = event.changes.firstOrNull() ?: break
                                            if (!change.pressed) break
                                            path.add(change.position)
                                        }
                                        if (path.isNotEmpty()) strokes.add(path)
                                    }
                                }
                            }
                        }
                ) {
                    val aspect = sheetWidth.toFloat() / sheetHeight.coerceAtLeast(1)
                    val baseW = minOf(size.width * .82f, size.height * aspect)
                    val baseH = baseW / aspect
                    val sheetW = baseW * zoom
                    val sheetH = baseH * zoom
                    val left = (size.width - sheetW) / 2f + pan.x
                    val top = (size.height - sheetH) / 2f + pan.y
                    drawRect(Color.White, topLeft = Offset(left, top), size = androidx.compose.ui.geometry.Size(sheetW, sheetH))
                    strokes.forEach { path ->
                        path.zipWithNext().forEach { (a, b) ->
                            drawLine(Color.Black, a, b, strokeWidth = brushSize, cap = StrokeCap.Round)
                        }
                    }
                }
                Row(Modifier.fillMaxWidth().height(28.dp).background(Panel), verticalAlignment = Alignment.CenterVertically) {
                    Text("$projectName • $fps fps • $sheetWidth×$sheetHeight", color = Ink, fontSize = 11.sp, modifier = Modifier.padding(start = 8.dp).weight(1f))
                    Text("Frame $frame/$frameCount", color = Cream, fontSize = 11.sp, modifier = Modifier.padding(end = 8.dp))
                }
            }
        }
        if (selectedTool == "Pincel" || selectedTool == "Borracha") {
            Row(Modifier.fillMaxWidth().height(42.dp).background(Panel).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Tamanho", color = Ink, fontSize = 12.sp)
                Slider(value = brushSize, onValueChange = { brushSize = it }, valueRange = 1f..48f, modifier = Modifier.weight(1f))
                Text("${brushSize.toInt()} px", color = Ink, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun ToolButton(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, selected: String, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(3.dp).background(if (selected == label) Cream else PanelLight, RoundedCornerShape(4.dp)).clickable(onClick = onClick).padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, contentDescription = label, tint = if (selected == label) Color.Black else Ink, modifier = Modifier.size(22.dp))
        Text(label, color = if (selected == label) Color.Black else Ink, fontSize = 8.sp)
    }
}
