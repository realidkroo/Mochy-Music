// OwnScope.kt

package com.mochyy.mochymusic.ui.utils

import androidx.compose.runtime.Composable

/** runs [content] as a recompose scope of its own, so a flag read inside it recomposes just this piece instead of the big composable around it */
@Composable
fun OwnScope(content: @Composable () -> Unit) {
    content()
}
