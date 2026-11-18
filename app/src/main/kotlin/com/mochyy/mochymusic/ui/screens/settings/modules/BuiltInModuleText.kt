package com.mochyy.mochymusic.ui.screens.settings.modules

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.module.ModuleChoice
import com.mochyy.mochymusic.module.applemusic.AppleMusicModule
import com.mochyy.mochymusic.module.jiosaavn.JioSaavnModule
import com.mochyy.mochymusic.module.monochrome.MonochromeModule
import com.mochyy.mochymusic.modules.ModuleEntry
import com.mochyy.mochymusic.modules.ModuleOrigin
import java.util.Locale

/** Display-only text for the bundled implementations. IDs and saved picks never change. */
@Composable
internal fun builtInModuleDescription(entry: ModuleEntry): String {
    if (entry.origin != ModuleOrigin.BuiltIn) return entry.module.info.description
    val resource = when (entry.module) {
        is AppleMusicModule -> R.string.builtin_module_apple_description
        is MonochromeModule -> R.string.builtin_module_monochrome_description
        is JioSaavnModule -> R.string.builtin_module_jiosaavn_description
        else -> return entry.module.info.description
    }
    return stringResource(resource)
}

/** Unknown choices and external modules retain their own text, including future options. */
@Composable
internal fun builtInModuleChoice(entry: ModuleEntry, choice: ModuleChoice): ModuleChoice {
    if (entry.origin != ModuleOrigin.BuiltIn) return choice
    val apple = entry.module is AppleMusicModule
    val saavn = entry.module is JioSaavnModule
    if (!apple && !saavn) return choice
    if (choice.id == AppleMusicModule.ChoiceQuality) {
        val options = choice.options.map { option ->
            val description = when {
                apple && option.id == AppleMusicModule.OptionAac256 -> R.string.builtin_module_apple_aac_description
                apple && option.id == AppleMusicModule.OptionHe64 -> R.string.builtin_module_apple_he_description
                saavn && option.id == JioSaavnModule.Option320 -> R.string.builtin_module_saavn_320_description
                saavn && option.id == JioSaavnModule.Option160 -> R.string.builtin_module_saavn_160_description
                saavn && option.id == JioSaavnModule.Option96 -> R.string.builtin_module_saavn_96_description
                else -> null
            }
            // Codec labels are technical names; only their explanations need translation.
            if (description == null) option else option.copy(description = stringResource(description))
        }
        return choice.copy(label = stringResource(R.string.builtin_module_stream), options = options)
    }
    if (apple && choice.id == AppleMusicModule.ChoiceRegion) {
        val displayLocale = LocalConfiguration.current.locales[0]
        val options = choice.options.map { option ->
            when {
                option.id == AppleMusicModule.RegionAuto -> option.copy(
                    label = stringResource(R.string.builtin_module_account_region),
                    description = stringResource(R.string.builtin_module_account_region_description),
                )
                option.id in AppleCatalogRegions -> {
                    val country = Locale.Builder().setRegion(option.id.uppercase(Locale.ROOT)).build()
                        .getDisplayCountry(displayLocale)
                    option.copy(label = country, description = stringResource(R.string.builtin_module_region_description, country))
                }
                else -> option
            }
        }
        return choice.copy(label = stringResource(R.string.builtin_module_region), options = options)
    }
    return choice
}

// Public catalog IDs offered by the bundled Apple Music module, never translated values.
private val AppleCatalogRegions = setOf("us", "gb", "jp", "kr", "id", "sg", "my", "ph", "th", "in", "au", "ca", "de", "fr", "br", "mx")
