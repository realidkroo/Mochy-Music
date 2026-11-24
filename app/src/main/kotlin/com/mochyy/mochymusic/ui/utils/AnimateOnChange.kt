// AnimateOnChange.kt

package com.mochyy.mochymusic.ui.utils

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.launch

/** eases to [target] the way animateFloatAsState does, but starts nothing until the target first changes, so a list row mounts without a coroutine per animation */
@Composable
fun animateFloatOnChange(target: Float, spec: AnimationSpec<Float>): State<Float> {
    val animatable = remember { Animatable(target) }
    val scope = rememberCoroutineScope()
    val lastTarget = remember { mutableFloatStateOf(target) }
    SideEffect {
        if (lastTarget.floatValue != target) {
            lastTarget.floatValue = target
            scope.launch { animatable.animateTo(target, spec) }
        }
    }
    return animatable.asState()
}

/** the same for a colour, eased the way animateColorAsState eases it */
@Composable
fun animateColorOnChange(target: Color, spec: AnimationSpec<Color>): State<Color> {
    val animatable = remember { androidx.compose.animation.Animatable(target) }
    val scope = rememberCoroutineScope()
    val lastTarget = remember { mutableStateOf(target) }
    SideEffect {
        if (lastTarget.value != target) {
            lastTarget.value = target
            scope.launch { animatable.animateTo(target, spec) }
        }
    }
    return animatable.asState()
}
