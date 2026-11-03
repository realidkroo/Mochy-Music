// every launcher icon mochy can wear, ready-made or on a custom colour, each an activity-alias in the manifest with exactly one switched on

package com.mochyy.mochymusic.utils

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.content.edit
import com.mochyy.mochymusic.R

enum class AppIconFamily(@StringRes val title: Int) {
    Mark(R.string.app_icon_family_mark),
    Outline(R.string.app_icon_family_outline),
    Anime(R.string.app_icon_family_anime),
    ;

    // the art this family wears on a custom colour: the outline goes dark on a pale one, like on white
    @DrawableRes
    fun artOn(color: AppIconColor): Int = when (this) {
        Mark -> R.drawable.ic_launcher_mark
        Outline -> if (color.pale) R.drawable.ic_launcher_outline_dark else R.drawable.ic_launcher_monochrome
        Anime -> R.drawable.ic_launcher_anime
    }
}

/** a custom background any family can sit on; [pale] ones take the outline's dark art */
enum class AppIconColor(@ColorRes val background: Int, @StringRes val label: Int, val pale: Boolean = false) {
    Red(R.color.app_icon_custom_red, R.string.app_icon_color_red),
    Orange(R.color.app_icon_custom_orange, R.string.app_icon_color_orange),
    Yellow(R.color.app_icon_custom_yellow, R.string.app_icon_color_yellow, pale = true),
    Green(R.color.app_icon_custom_green, R.string.app_icon_color_green),
    Teal(R.color.app_icon_custom_teal, R.string.app_icon_color_teal),
    Sky(R.color.app_icon_custom_sky, R.string.app_icon_color_sky, pale = true),
    Blue(R.color.app_icon_custom_blue, R.string.app_icon_color_blue),
    Indigo(R.color.app_icon_custom_indigo, R.string.app_icon_color_indigo),
    Purple(R.color.app_icon_custom_purple, R.string.app_icon_color_purple),
    Pink(R.color.app_icon_custom_pink, R.string.app_icon_color_pink),
    Graphite(R.color.app_icon_custom_graphite, R.string.app_icon_color_graphite),
    Cream(R.color.app_icon_custom_cream, R.string.app_icon_color_cream, pale = true),
}

/** [background] null is see-through: the launcher shows the wallpaper behind the art; [color] is set on a custom one */
class AppIcon private constructor(
    /** what's saved for the icon in use, so it must never change */
    val name: String,
    val alias: String,
    val family: AppIconFamily,
    @DrawableRes val foreground: Int,
    @ColorRes val background: Int?,
    @StringRes val label: Int,
    val color: AppIconColor? = null,
) {
    companion object {
        val Default = AppIcon("Default", "IconDefault", AppIconFamily.Mark, R.drawable.ic_launcher_mark, R.color.ic_launcher_background, R.string.app_icon_default)

        // the ready-made ones, each family's in the order its row shows them
        val presets: List<AppIcon> = listOf(
            Default,
            AppIcon("MarkWhite", "IconMarkWhite", AppIconFamily.Mark, R.drawable.ic_launcher_mark, R.color.app_icon_white, R.string.app_icon_white),
            AppIcon("MarkBlack", "IconMarkBlack", AppIconFamily.Mark, R.drawable.ic_launcher_mark, R.color.app_icon_black, R.string.app_icon_black),
            AppIcon("MarkMaterial", "IconMarkMaterial", AppIconFamily.Mark, R.drawable.ic_launcher_mark, R.color.app_icon_material, R.string.app_icon_material),
            AppIcon("MarkTransparent", "IconMarkTransparent", AppIconFamily.Mark, R.drawable.ic_launcher_mark, null, R.string.app_icon_transparent),
            AppIcon("Outline", "IconOutline", AppIconFamily.Outline, R.drawable.ic_launcher_monochrome, R.color.ic_launcher_background, R.string.app_icon_outline),
            AppIcon("OutlineWhite", "IconOutlineWhite", AppIconFamily.Outline, R.drawable.ic_launcher_outline_dark, R.color.app_icon_white, R.string.app_icon_outline_white),
            AppIcon("OutlineBlack", "IconOutlineBlack", AppIconFamily.Outline, R.drawable.ic_launcher_monochrome, R.color.app_icon_black, R.string.app_icon_outline_black),
            AppIcon("OutlineMaterial", "IconOutlineMaterial", AppIconFamily.Outline, R.drawable.ic_launcher_monochrome, R.color.app_icon_material, R.string.app_icon_outline_material),
            AppIcon("OutlineTransparent", "IconOutlineTransparent", AppIconFamily.Outline, R.drawable.ic_launcher_monochrome, null, R.string.app_icon_outline_transparent),
            AppIcon("AnimeWhite", "IconAnimeWhite", AppIconFamily.Anime, R.drawable.ic_launcher_anime, R.color.app_icon_white, R.string.app_icon_anime_white),
            AppIcon("AnimeDark", "IconAnimeDark", AppIconFamily.Anime, R.drawable.ic_launcher_anime, R.color.app_icon_black, R.string.app_icon_anime_dark),
            AppIcon("AnimeMaterial", "IconAnimeMaterial", AppIconFamily.Anime, R.drawable.ic_launcher_anime, R.color.app_icon_material, R.string.app_icon_anime_material),
            AppIcon("AnimeTransparent", "IconAnimeTransparent", AppIconFamily.Anime, R.drawable.ic_launcher_anime, null, R.string.app_icon_anime_transparent),
        )

        // every family on every custom colour, named MarkRed with the alias IconMarkRed, as the manifest has them
        private val customs: List<AppIcon> = AppIconFamily.entries.flatMap { family ->
            AppIconColor.entries.map { color ->
                AppIcon(
                    name = family.name + color.name,
                    alias = "Icon${family.name}${color.name}",
                    family = family,
                    foreground = family.artOn(color),
                    background = color.background,
                    label = color.label,
                    color = color,
                )
            }
        }

        val entries: List<AppIcon> = presets + customs

        fun custom(family: AppIconFamily, color: AppIconColor): AppIcon = customs.first { it.family == family && it.color == color }
    }
}

object AppIcons {

    fun current(context: Context): AppIcon =
        prefs(context).getString(Key, null)?.let { name -> AppIcon.entries.firstOrNull { it.name == name } } ?: AppIcon.Default

    /** the icon a launch came from, read off the alias that started it; null for anything but a tap on an icon */
    fun launchedFrom(intent: Intent?): AppIcon? {
        val started = intent?.component?.className ?: return null
        return AppIcon.entries.firstOrNull { started.endsWith(".${it.alias}") }
    }

    /** whether this launch's splash showed [icon] itself: only an anime one, and only on android 13+, which can pick a splash per launch */
    fun splashShows(icon: AppIcon?): Boolean = icon?.family == AppIconFamily.Anime && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    // the next launch's splash: the launcher icon for an anime one to bounce out of, the bare mark for the rest
    fun syncSplash(activity: Activity, icon: AppIcon = current(activity)) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val theme = if (icon.family == AppIconFamily.Anime) R.style.Theme_App_Starting_Icon else R.style.Theme_App_Starting
        runCatching { activity.splashScreen.setSplashScreenTheme(theme) }
    }

    /** switches the launcher to [icon]; the launcher redraws it on its own time, a few seconds on some phones */
    fun apply(context: Context, icon: AppIcon) {
        val pm = context.packageManager
        // the new one first, so for a moment there are two icons rather than none
        pm.setComponentEnabledSetting(componentOf(context, icon), PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP)
        AppIcon.entries.filter { it != icon }.forEach {
            pm.setComponentEnabledSetting(componentOf(context, it), PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
        }
        prefs(context).edit { putString(Key, icon.name) }
    }

    // the aliases are named after the code's package, which a debug build's app id doesn't share
    private fun componentOf(context: Context, icon: AppIcon) = ComponentName(context, "com.mochyy.mochymusic.${icon.alias}")

    private fun prefs(context: Context) = context.getSharedPreferences("app_icon", Context.MODE_PRIVATE)

    private const val Key = "icon"
}
