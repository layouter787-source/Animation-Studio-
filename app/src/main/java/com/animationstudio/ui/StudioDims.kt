package com.animationstudio.ui

import androidx.compose.ui.unit.dp

/** Medidas únicas do estúdio: todos os botões das barras têm o mesmo tamanho, espaçamento e margem. */
internal object StudioDims {
    val Cell = 36.dp
    val Gap = 4.dp
    val Pad = 4.dp
    val Icon = 18.dp
    /** Largura de um painel de duas colunas: 2 botões + espaço + margem do cartão. */
    val PanelWidth = Cell * 2 + Gap + Pad * 2 + 8.dp
}
