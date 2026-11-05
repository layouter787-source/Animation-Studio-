package com.animationstudio

import android.graphics.BitmapFactory
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.animationstudio.data.ProjectMeta
import com.animationstudio.data.ProjectStore
import com.animationstudio.domain.Project
import com.animationstudio.ui.AnimationEditor
import com.animationstudio.ui.StudioColors
import com.animationstudio.ui.StudioTheme
import com.animationstudio.ui.StudioType
import com.animationstudio.ui.studioCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date
import java.util.UUID

class MainActivity : ComponentActivity() {
    private external fun nativeEngineStatus(): String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        System.loadLibrary("animation_engine")
        hideSystemBars()
        val store = ProjectStore(applicationContext)
        setContent {
            StudioTheme {
                StudioApp(store = store, onProjectOpened = { nativeEngineStatus() })
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    private fun hideSystemBars() {
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
    }
}

private sealed interface Screen {
    data object Home : Screen
    data object New : Screen
    class Editor(val meta: ProjectMeta, val project: Project) : Screen
}

@Composable
private fun StudioApp(store: ProjectStore, onProjectOpened: () -> Unit) {
    var screen by remember { mutableStateOf<Screen>(Screen.Home) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    Surface(color = StudioColors.Navy, modifier = Modifier.fillMaxSize()) {
        when (val s = screen) {
            Screen.Home -> HomeScreen(
                store = store,
                onNew = { screen = Screen.New },
                onOpen = { id ->
                    scope.launch {
                        val loaded = withContext(Dispatchers.IO) { store.load(id) }
                        if (loaded == null) {
                            Toast.makeText(context, "Não foi possível abrir o projeto (ficheiro inválido)", Toast.LENGTH_LONG).show()
                        } else {
                            onProjectOpened()
                            screen = Screen.Editor(loaded.first, loaded.second)
                        }
                    }
                },
            )
            Screen.New -> NewProjectScreen(
                onCancel = { screen = Screen.Home },
                onCreate = { name, fps, w, h ->
                    val now = System.currentTimeMillis()
                    val meta = ProjectMeta(UUID.randomUUID().toString(), name, fps, w, h, now, now)
                    val project = Project.initial()
                    scope.launch {
                        val ok = withContext(Dispatchers.IO) {
                            try { store.save(meta, project); true } catch (e: Exception) { false }
                        }
                        if (!ok) Toast.makeText(context, "Não foi possível guardar o projeto", Toast.LENGTH_LONG).show()
                        onProjectOpened()
                        screen = Screen.Editor(meta, project)
                    }
                },
            )
            is Screen.Editor -> key(s.meta.id) {
                AnimationEditor(s.meta, s.project, store) { screen = Screen.Home }
            }
        }
    }
}

@Composable
private fun ProjectThumb(store: ProjectStore, meta: ProjectMeta) {
    val img by produceState<ImageBitmap?>(null, meta.modifiedAt) {
        value = withContext(Dispatchers.IO) { BitmapFactory.decodeFile(store.thumbnailFile(meta.id).path)?.asImageBitmap() }
    }
    val shape = RoundedCornerShape(10.dp)
    Box(
        Modifier.size(width = 120.dp, height = 68.dp).clip(shape).background(if (img != null) Color.White else StudioColors.Surface3)
            .border(1.dp, StudioColors.Outline, shape),
        contentAlignment = Alignment.Center,
    ) {
        val bmp = img
        if (bmp != null) Image(bitmap = bmp, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
        else Icon(Icons.Default.Brush, null, tint = StudioColors.InkDim, modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun HomeScreen(store: ProjectStore, onNew: () -> Unit, onOpen: (String) -> Unit) {
    var projects by remember { mutableStateOf<List<ProjectMeta>?>(null) }
    var toDelete by remember { mutableStateOf<ProjectMeta?>(null) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { projects = withContext(Dispatchers.IO) { store.list() } }

    Row(Modifier.fillMaxSize().padding(14.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Column(Modifier.weight(1.3f).fillMaxHeight().studioCard(18.dp).padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("PROJETOS", style = StudioType.Caption)
            val list = projects
            when {
                list == null -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text("A carregar…", style = StudioType.Body.copy(color = StudioColors.InkDim))
                }
                list.isEmpty() -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text("Ainda não há projetos. Toque em Novo projeto.", style = StudioType.Body.copy(color = StudioColors.InkDim))
                }
                else -> LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(list, key = { it.id }) { m ->
                        val date = remember(m.modifiedAt) {
                            DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(m.modifiedAt))
                        }
                        val shape = RoundedCornerShape(14.dp)
                        Row(
                            Modifier.fillMaxWidth().clip(shape).background(StudioColors.Surface2).border(1.dp, StudioColors.Outline, shape)
                                .clickable { onOpen(m.id) }.padding(8.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            ProjectThumb(store, m)
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(m.name, style = StudioType.Title, maxLines = 1)
                                Text("${m.fps} FPS  ·  ${m.width}×${m.height}", style = StudioType.Caption)
                                Text(date, style = StudioType.Body.copy(color = StudioColors.InkDim))
                            }
                            IconButton(onClick = { toDelete = m }) { Icon(Icons.Default.Delete, "Apagar projeto", tint = StudioColors.InkDim) }
                        }
                    }
                }
            }
        }
        Column(
            Modifier.weight(0.8f).fillMaxHeight(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.size(72.dp).clip(RoundedCornerShape(20.dp)).background(StudioColors.Accent), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Brush, null, tint = Color.White, modifier = Modifier.size(38.dp))
            }
            Spacer(Modifier.height(12.dp))
            Text("Animation Studio", style = StudioType.Title.copy(fontSize = 22.sp))
            Text("ANIMAÇÃO 2D FRAME A FRAME", style = StudioType.Caption)
            Spacer(Modifier.height(18.dp))
            Button(onClick = onNew, modifier = Modifier.fillMaxWidth(0.85f).height(50.dp), shape = RoundedCornerShape(14.dp)) {
                Icon(Icons.Default.Add, null)
                Text("  Novo projeto", style = StudioType.Label.copy(color = Color.White))
            }
        }
    }

    toDelete?.let { m ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            containerColor = StudioColors.Surface,
            title = { Text("Apagar projeto?", style = StudioType.Title) },
            text = { Text("\"${m.name}\" será apagado de forma permanente.", style = StudioType.Body) },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        withContext(Dispatchers.IO) { store.delete(m.id) }
                        projects = withContext(Dispatchers.IO) { store.list() }
                        toDelete = null
                    }
                }) { Text("Apagar", style = StudioType.Label.copy(color = StudioColors.Danger)) }
            },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Cancelar", style = StudioType.Label) } },
        )
    }
}

private val SizePresets = listOf(
    Triple("HD  1280×720", 1280, 720),
    Triple("Full HD  1920×1080", 1920, 1080),
    Triple("Quadrado  1080×1080", 1080, 1080),
    Triple("4:3  1024×768", 1024, 768),
)

@Composable
private fun NewProjectScreen(onCancel: () -> Unit, onCreate: (String, Int, Int, Int) -> Unit) {
    var name by remember { mutableStateOf("Sem título") }
    var fps by remember { mutableStateOf("24") }
    var w by remember { mutableStateOf("1280") }
    var h by remember { mutableStateOf("720") }
    val fpsV = fps.toIntOrNull()
    val wV = w.toIntOrNull()
    val hV = h.toIntOrNull()
    val valid = name.isNotBlank() && fpsV != null && fpsV in 1..120 && wV != null && wV in 64..8192 && hV != null && hV in 64..8192
    val number = KeyboardOptions(keyboardType = KeyboardType.Number)
    BackHandler { onCancel() }

    Column(Modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Novo projeto", style = StudioType.Title.copy(fontSize = 20.sp))
        Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(
                Modifier.weight(1f).fillMaxHeight().studioCard(18.dp).verticalScroll(rememberScrollState()).padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("PROJETO", style = StudioType.Caption)
                OutlinedTextField(name, { name = it.take(60) }, label = { Text("Nome") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    fps, { fps = it.filter { c -> c.isDigit() }.take(3) }, label = { Text("FPS (1–120)") }, singleLine = true,
                    isError = fpsV == null || fpsV !in 1..120, keyboardOptions = number, modifier = Modifier.fillMaxWidth()
                )
            }
            Column(
                Modifier.weight(1f).fillMaxHeight().studioCard(18.dp).verticalScroll(rememberScrollState()).padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("FOLHA", style = StudioType.Caption)
                for (pair in SizePresets.chunked(2)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        for ((label, pw, ph) in pair) {
                            val selected = wV == pw && hV == ph
                            val shape = RoundedCornerShape(10.dp)
                            Box(
                                Modifier.weight(1f).clip(shape)
                                    .background(if (selected) StudioColors.Accent else StudioColors.Surface2)
                                    .border(1.dp, if (selected) StudioColors.Accent else StudioColors.Outline, shape)
                                    .clickable { w = pw.toString(); h = ph.toString() }.padding(horizontal = 8.dp, vertical = 10.dp),
                                contentAlignment = Alignment.Center,
                            ) { Text(label, style = StudioType.Label) }
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(
                        w, { w = it.filter { c -> c.isDigit() }.take(4) }, label = { Text("Largura") }, singleLine = true,
                        isError = wV == null || wV !in 64..8192, keyboardOptions = number, modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        h, { h = it.filter { c -> c.isDigit() }.take(4) }, label = { Text("Altura") }, singleLine = true,
                        isError = hV == null || hV !in 64..8192, keyboardOptions = number, modifier = Modifier.weight(1f)
                    )
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onCancel) { Text("Cancelar", style = StudioType.Label) }
            Spacer(Modifier.width(8.dp))
            Button(enabled = valid, onClick = { onCreate(name.trim(), fpsV!!, wV!!, hV!!) }, shape = RoundedCornerShape(12.dp)) {
                Text("Criar", style = StudioType.Label.copy(color = Color.White))
            }
        }
    }
}
