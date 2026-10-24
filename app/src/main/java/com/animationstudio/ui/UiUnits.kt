package com.animationstudio.ui

import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp as composeSp

/** Atalho local: o editor usa tamanhos de letra pequenos (9.sp, 11.sp…) sem repetir o import. */
internal val Int.sp: TextUnit get() = this.composeSp
