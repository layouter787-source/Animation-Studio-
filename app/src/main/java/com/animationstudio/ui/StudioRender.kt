package com.animationstudio.ui

/**
 * Opções globais de desenho (guardadas em [Prefs]).
 *  - [taperEnds]: pontas afinadas nos traços. Desligado por defeito: espessura uniforme e pontas redondas.
 *  - [pixelated]: desliga o anti-aliasing (traço com píxeis visíveis). Desligado por defeito.
 *  - [inkPercent]: a partir de que opacidade (0–100%) o balde de tinta considera um pixel como tinta (parede).
 *    Abaixo desse valor o pixel é papel e o balde pinta-o. Editável nas definições do balde.
 */
internal object StudioRender {
    @Volatile var taperEnds: Boolean = false
    @Volatile var pixelated: Boolean = false
    @Volatile var inkPercent: Int = 35
}
