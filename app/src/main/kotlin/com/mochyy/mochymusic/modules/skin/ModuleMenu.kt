// the rows switched-on modules add to the player's menu, kept where the menu can read them without a view model

package com.mochyy.mochymusic.modules.skin

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** one module's row, with the skin that resolves its icon and the module whose page it may open */
@Immutable
class ModuleMenuAction(val moduleId: String, val skin: ModuleSkin, val action: SkinAction)

object ModuleMenu {
    var actions: List<ModuleMenuAction> by mutableStateOf(emptyList())
        private set

    // set on every refresh of the switches; the same list again changes nothing, so the menu doesn't redraw for it
    internal fun publish(skins: List<ModuleSkin>) {
        val next = skins.flatMap { skin -> skin.actions.map { ModuleMenuAction(skin.moduleId, skin, it) } }
        if (next.map { it.moduleId to it.action } != actions.map { it.moduleId to it.action }) actions = next
    }
}
