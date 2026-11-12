package com.mochyy.mochymusic.ui.component

import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import coil3.compose.rememberAsyncImagePainter

// a bundled photo or logo decoded by coil off the main thread at the size it's drawn, and kept in memory: painterResource decoded the full picture on the main thread every time its screen composed (119 ms for the discord card on a tab switch)
@Composable
fun rememberResourcePainter(@DrawableRes id: Int): Painter = rememberAsyncImagePainter(model = id)
