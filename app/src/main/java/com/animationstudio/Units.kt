package com.animationstudio

import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp as composeSp

/** Atalho local para tamanhos de letra (MainActivity usa 20.sp / 22.sp sem importar a extensão do Compose). */
internal val Int.sp: TextUnit get() = this.composeSp
