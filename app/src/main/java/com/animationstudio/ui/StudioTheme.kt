package com.animationstudio.ui

import android.graphics.Typeface
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Tipografia do estúdio: três famílias do sistema (regular, média e condensada) com hierarquia fixa.
 * Títulos e rótulos usam a média, números e legendas em maiúsculas usam a condensada com espaçamento.
 */
object StudioType {
    private val regular = FontFamily(Typeface.create("sans-serif", Typeface.NORMAL))
    private val medium = FontFamily(Typeface.create("sans-serif-medium", Typeface.NORMAL))
    private val condensed = FontFamily(Typeface.create("sans-serif-condensed", Typeface.NORMAL))

    val Title = TextStyle(fontFamily = medium, fontSize = 17.sp, letterSpacing = 0.2.sp, color = StudioColors.Ink)
    val Body = TextStyle(fontFamily = regular, fontSize = 13.sp, color = StudioColors.Ink)
    val Label = TextStyle(fontFamily = medium, fontSize = 12.sp, letterSpacing = 0.3.sp, color = StudioColors.Ink)
    val Caption = TextStyle(
        fontFamily = condensed, fontWeight = FontWeight.Medium, fontSize = 10.sp,
        letterSpacing = 1.2.sp, color = StudioColors.InkDim,
    )
    val Number = TextStyle(fontFamily = condensed, fontWeight = FontWeight.Medium, fontSize = 13.sp, color = StudioColors.Ink)
}

@Composable
fun StudioTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = StudioColors.Accent, onPrimary = Color.White,
            secondary = StudioColors.Mint, onSecondary = Color.Black,
            background = StudioColors.Navy, onBackground = StudioColors.Ink,
            surface = StudioColors.Surface, onSurface = StudioColors.Ink,
            surfaceVariant = StudioColors.Surface2, onSurfaceVariant = StudioColors.InkDim,
            outline = StudioColors.Outline, error = StudioColors.Danger,
        ),
    ) {
        CompositionLocalProvider(LocalTextStyle provides StudioType.Body) { content() }
    }
}
