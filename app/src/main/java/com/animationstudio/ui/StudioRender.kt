package com.animationstudio.ui

import com.animationstudio.domain.ViewTransform

/**
 * Opções globais de desenho (guardadas em [Prefs]).
 *  - [taperEnds]: pontas afinadas nos traços. Ligado por defeito: pontas finas que dependem da velocidade (ver InkOutline). Desligado: espessura uniforme e pontas redondas.
 *  - [pixelated]: desliga o anti-aliasing (traço com píxeis visíveis). DESLIGADO por defeito: traço limpo, sem
 *    pixelização nem desfoque. O utilizador pode ligar nas definições do pincel.
 *  - [inkPercent]: (já não usado pelo balde) o balde conta qualquer vestígio de tinta como parede, para não vazar por linhas finas.
 *  - [view]: vista atual do editor (zoom, rotação, posição). O desenho usa-a para decidir se a imagem em cache
 *    das camadas ainda tem resolução suficiente (senão desenha em vetor, sempre nítido) e para não desenhar o que
 *    está fora do ecrã.
 */
internal object StudioRender {
    @Volatile var taperEnds: Boolean = true
    @Volatile var pixelated: Boolean = false
    @Volatile var inkPercent: Int = 35
    @Volatile var view: ViewTransform = ViewTransform()

    private var warmed = false

    /** Gera as texturas em segundo plano, uma são vez por arranque da app. */
    @Synchronized fun warmUp() {
        if (warmed) return
        warmed = true
        warmUpTextures()
    }
}
