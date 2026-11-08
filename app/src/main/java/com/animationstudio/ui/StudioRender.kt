package com.animationstudio.ui

import com.animationstudio.domain.ViewTransform

/**
 * Opções globais de desenho (guardadas em [Prefs]).
 *  - [taperEnds]: pontas afinadas nos traços. Desligado por defeito: espessura uniforme e pontas redondas.
 *  - [pixelated]: desliga o anti-aliasing (traço com píxeis visíveis). DESLIGADO por defeito: traço limpo, sem
 *    pixelização nem desfoque. O utilizador pode ligar nas definições do pincel.
 *  - [inkPercent]: a partir de que opacidade (0–100%) o balde de tinta considera um pixel como tinta (parede).
 *    Abaixo desse valor o pixel é papel e o balde pinta-o. Editável nas definições do balde.
 *  - [view]: vista atual do editor (zoom, rotação, posição). O desenho usa-a para decidir se a imagem em cache
 *    das camadas ainda tem resolução suficiente (senão desenha em vetor, sempre nítido) e para não desenhar o que
 *    está fora do ecrã.
 */
internal object StudioRender {
    @Volatile var taperEnds: Boolean = false
    @Volatile var pixelated: Boolean = false
    @Volatile var inkPercent: Int = 35
    @Volatile var view: ViewTransform = ViewTransform()

    private var warmed = false

    /** Gera as texturas em segundo plano, uma só vez por arranque da app. */
    @Synchronized fun warmUp() {
        if (warmed) return
        warmed = true
        warmUpTextures()
    }
}
