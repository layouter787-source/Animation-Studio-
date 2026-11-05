package com.animationstudio.ui

import androidx.compose.ui.graphics.Color

/**
 * Paleta do estúdio. Duas cores de marca: azul-marinho escuro #0B1020 (fundo) e azul elétrico #0866F5 (destaque).
 * Os nomes antigos (Workbench, Workspace, Panel, PanelLight, Cream) continuam como aliases.
 */
object StudioColors {
    val Navy = Color(0xFF0B1020)
    val Accent = Color(0xFF0866F5)
    val AccentSoft = Color(0x330866F5)
    val Surface = Color(0xFF121A32)
    val Surface2 = Color(0xFF1A2444)
    val Surface3 = Color(0xFF243260)
    val Outline = Color(0xFF2D3A68)
    val Ink = Color(0xFFEAF0FF)
    val InkDim = Color(0xFF98A6CE)
    val Danger = Color(0xFFFF6B7A)
    val Mint = Color(0xFF3DD9A0)
    val Rose = Color(0xFFFF5C7A)
    val Amber = Color(0xFFFFC24D)

    val Workbench = Navy
    val Workspace = Color(0xFF070B16)
    val Panel = Surface
    val PanelLight = Surface2
    val Cream = Accent
}
