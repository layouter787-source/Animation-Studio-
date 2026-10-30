package com.animationstudio.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy as LibContentCopy
import androidx.compose.material.icons.filled.Delete as LibDelete
import androidx.compose.ui.graphics.vector.ImageVector

// Pequenos alias para os pop-ups usarem os ícones Material sem importar nomes repetidos.
internal val Icons.Filled.ContentCopy: ImageVector get() = Icons.Filled.LibContentCopy
internal val Icons.Filled.Delete: ImageVector get() = Icons.Filled.LibDelete
