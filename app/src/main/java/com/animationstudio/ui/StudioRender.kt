package com.animationstudio.ui

/**
 * Opções globais de desenho (guardadas em [Prefs]).
 *  - [taperEnds]: pontas afinadas nos traços. Desligado por defeito: espessura uniforme e pontas redondas.
 *  - [pixelated]: desliga o anti-aliasing (traço com píxeis visíveis). Desligado por defeito.
 */
internal object StudioRender {
    @Volatile var taperEnds: Boolean = false
    @Volatile var pixelated: Boolean = false
}
