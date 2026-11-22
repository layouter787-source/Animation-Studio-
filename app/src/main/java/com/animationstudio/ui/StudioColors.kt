package com.animationstudio.ui

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance

/** Uma cor da paleta de personalização do app. */
data class PaletteColor(val name: String, val argb: Int)

/**
 * Paleta do estúdio. Duas cores escolhidas pelo utilizador: a principal (fundo e painéis) e o destaque.
 * Tudo o resto (painéis, contornos, texto) deriva da principal; com a cor por defeito (azul-marinho) mantêm-se os tons originais.
 * Os valores são lidos do estado do Compose: ao mudar a cor, o app inteiro redesenha-se.
 * Os nomes antigos (Workbench, Workspace, Panel, PanelLight, Cream) continuam como aliases.
 */
object StudioColors {
    const val DefaultMain = 0xFF0B1020.toInt()
    const val DefaultAccent = 0xFF0866F5.toInt()

    /** Cores principais disponíveis (fundo do app). */
    val MainPalette = listOf(
        PaletteColor("Azul-marinho", 0xFF0B1020.toInt()),
        PaletteColor("Preto", 0xFF0A0A0A.toInt()),
        PaletteColor("Cinza escuro", 0xFF2B2E33.toInt()),
        PaletteColor("Grafite", 0xFF3C4149.toInt()),
        PaletteColor("Cinza claro", 0xFFD9DCE1.toInt()),
        PaletteColor("Roxo escuro", 0xFF1B1030.toInt()),
        PaletteColor("Verde escuro", 0xFF0C1F1A.toInt()),
        PaletteColor("Vinho", 0xFF2A0F1A.toInt()),
        PaletteColor("Castanho", 0xFF241A12.toInt()),
        PaletteColor("Branco", 0xFFF2F3F5.toInt()),
    )

    /** Cores de destaque disponíveis. */
    val AccentPalette = listOf(
        PaletteColor("Azul", 0xFF0866F5.toInt()),
        PaletteColor("Ciano", 0xFF00B8D9.toInt()),
        PaletteColor("Verde", 0xFF1FBF75.toInt()),
        PaletteColor("Amarelo", 0xFFFFC24D.toInt()),
        PaletteColor("Laranja", 0xFFFF8A3D.toInt()),
        PaletteColor("Vermelho", 0xFFFF4D5E.toInt()),
        PaletteColor("Rosa", 0xFFFF5CA8.toInt()),
        PaletteColor("Roxo", 0xFF8B5CF6.toInt()),
        PaletteColor("Cinza claro", 0xFFC8CCD4.toInt()),
        PaletteColor("Cinza escuro", 0xFF5A606B.toInt()),
    )

    private var mainArgbState by mutableIntStateOf(DefaultMain)
    private var accentArgbState by mutableIntStateOf(DefaultAccent)

    val mainArgb: Int get() = mainArgbState
    val accentArgb: Int get() = accentArgbState

    private val isDefault: Boolean get() = mainArgbState == DefaultMain
    private val isLight: Boolean get() = Color(mainArgbState).luminance() > 0.5f

    /** Mistura a cor principal com branco (tema escuro) ou preto (tema claro). */
    private fun shade(t: Float): Color = lerp(Color(mainArgbState), if (isLight) Color.Black else Color.White, t)

    /** Lê a escolha guardada (chamar uma vez ao arrancar, antes de desenhar). */
    fun load(context: Context) {
        val sp = context.getSharedPreferences("studio", Context.MODE_PRIVATE)
        mainArgbState = sp.getInt("themeMain", DefaultMain)
        accentArgbState = sp.getInt("themeAccent", DefaultAccent)
    }

    fun setMain(context: Context, argb: Int) {
        mainArgbState = argb
        context.getSharedPreferences("studio", Context.MODE_PRIVATE).edit().putInt("themeMain", argb).apply()
    }

    fun setAccent(context: Context, argb: Int) {
        accentArgbState = argb
        context.getSharedPreferences("studio", Context.MODE_PRIVATE).edit().putInt("themeAccent", argb).apply()
    }

    fun reset(context: Context) {
        setMain(context, DefaultMain)
        setAccent(context, DefaultAccent)
    }

    val Navy: Color get() = Color(mainArgbState)
    val Accent: Color get() = Color(accentArgbState)
    val AccentSoft: Color get() = Color(accentArgbState).copy(alpha = 0.2f)
    val Surface: Color get() = if (isDefault) Color(0xFF121A32) else shade(0.06f)
    val Surface2: Color get() = if (isDefault) Color(0xFF1A2444) else shade(0.11f)
    val Surface3: Color get() = if (isDefault) Color(0xFF243260) else shade(0.18f)
    val Outline: Color get() = if (isDefault) Color(0xFF2D3A68) else shade(0.24f)
    val Ink: Color get() = if (isDefault) Color(0xFFEAF0FF) else if (isLight) Color(0xFF1A1C20) else Color(0xFFF1F2F5)
    val InkDim: Color get() = if (isDefault) Color(0xFF98A6CE) else lerp(Ink, Navy, 0.4f)
    val Danger = Color(0xFFFF6B7A)
    val Mint = Color(0xFF3DD9A0)
    val Rose = Color(0xFFFF5C7A)
    val Amber = Color(0xFFFFC24D)

    val Workbench: Color get() = Navy
    val Workspace: Color get() = if (isDefault) Color(0xFF070B16) else lerp(Navy, Color.Black, if (isLight) 0.1f else 0.35f)
    val Panel: Color get() = Surface
    val PanelLight: Color get() = Surface2
    val Cream: Color get() = Accent
}
