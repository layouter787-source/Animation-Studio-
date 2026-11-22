package com.animationstudio.ui

import androidx.compose.ui.unit.dp

/** Medidas únicas do estúdio: todos os botões das barras têm o mesmo tamanho, espaçamento e margem. */
internal object StudioDims {
    val Cell = 30.dp
    val Gap = 3.dp
    val Pad = 3.dp
    val Icon = 15.dp
    /** Largura de um painel de duas colunas: 2 botões + espaço + margem do cartão. */
    val PanelWidth = Cell * 2 + Gap + Pad * 2 + 6.dp
}
