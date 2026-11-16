package com.mochyy.mochymusic.i18n

import android.app.Activity
import android.app.Application
import android.app.LocaleManager
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.os.LocaleList
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import com.mochyy.mochymusic.constants.AppLanguageKey
import com.mochyy.mochymusic.constants.SYSTEM_DEFAULT
import com.mochyy.mochymusic.utils.dataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.util.Locale

private val migratedToPlatformKey = booleanPreferencesKey("appLanguageMigratedToPlatform")

/** Resolves the UI language independently of the country/content/lyrics preferences. */
internal fun resolveUiLocale(selection: String?, system: Locale): Locale {
    val selected = selection
        ?.takeUnless { it.isBlank() || it == SYSTEM_DEFAULT }
        ?.let(Locale::forLanguageTag)
        ?.takeIf { it.language.isNotBlank() }
    return selected ?: system.takeIf { it.language.isNotBlank() } ?: Locale.ENGLISH
}

/** Put English before any other device language when a string is missing. */
internal fun uiLocaleFallbacks(locale: Locale): List<Locale> =
    if (locale == Locale.ENGLISH) listOf(Locale.ENGLISH) else listOf(locale, Locale.ENGLISH)

object AppLanguage {
    /** The device language, not the app's override. */
    fun systemLocale(context: Context): Locale {
        val locales = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.getSystemService(LocaleManager::class.java).systemLocales
        } else {
            Resources.getSystem().configuration.locales
        }
        return locales[0] ?: Locale.ENGLISH
    }

    /** SYSTEM_DEFAULT is distinct from explicitly choosing English. */
    fun selection(context: Context): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val locales = context.getSystemService(LocaleManager::class.java).applicationLocales
            return if (locales.isEmpty) SYSTEM_DEFAULT else locales[0]?.toLanguageTag() ?: SYSTEM_DEFAULT
        }
        // Read the store itself, not the asynchronous UI cache: Activity recreation may follow
        // immediately after a completed edit, before the cache's collector has caught up.
        return runBlocking(Dispatchers.IO) {
            context.applicationContext.dataStore.data.first()[AppLanguageKey] ?: SYSTEM_DEFAULT
        }
    }

    private fun uiLocales(context: Context): LocaleList {
        val locale = resolveUiLocale(selection(context), systemLocale(context))
        return LocaleList(*uiLocaleFallbacks(locale).toTypedArray())
    }

    private fun configuration(context: Context): Configuration =
        Configuration(context.resources.configuration).apply { setLocales(uiLocales(context)) }

    // before the activity inflates, so compose reads the chosen language; only the language, a full copy pinned the window's size and the app stayed upright after a turn
    fun wrapActivityContext(base: Context): Context =
        base.createConfigurationContext(Configuration().apply { setLocales(uiLocales(base)) })

    /**
     * Migrate a legacy pre-Android-13 choice once, unless the platform already has an override.
     * Android's per-app setting is then the only authority on 13+, including system Settings.
     */
    fun initialize(application: Application) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val prefs = runBlocking(Dispatchers.IO) { application.dataStore.data.first() }
            if (prefs[migratedToPlatformKey] != true) {
                val previous = prefs[AppLanguageKey]
                // Mark first, so a platform-triggered recreation cannot repeat migration.
                runBlocking(Dispatchers.IO) {
                    application.dataStore.edit { it[migratedToPlatformKey] = true }
                }
                val manager = application.getSystemService(LocaleManager::class.java)
                if (manager.applicationLocales.isEmpty && !previous.isNullOrBlank() && previous != SYSTEM_DEFAULT) {
                    manager.applicationLocales = LocaleList.forLanguageTags(previous)
                }
            }
        }
        refreshApplicationResources(application)
    }

    /** Keep application-context UI, such as notifications, consistent on older Android too. */
    @Suppress("DEPRECATION")
    fun refreshApplicationResources(context: Context) {
        val app = context.applicationContext
        app.resources.updateConfiguration(configuration(app), app.resources.displayMetrics)
    }

    /** Persist before recreating, so the first frame and a process restart use the same choice. */
    suspend fun select(activity: Activity, tag: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val locales = if (tag == SYSTEM_DEFAULT) LocaleList.getEmptyLocaleList()
                else LocaleList.forLanguageTags(tag)
            activity.getSystemService(LocaleManager::class.java).applicationLocales = locales
            // Android normally recreates the Activity for a locale change. It can skip
            // that when a legacy locale was migrated during cold start, or when the
            // selected locale resolves to the same language as the device. If the old
            // Activity is still alive after the platform has had time to act, recreate
            // it so both the wrapped resources and the picker reflect the new choice.
            activity.window.decorView.postDelayed({
                if (!activity.isFinishing && !activity.isDestroyed && !activity.isChangingConfigurations) {
                    refreshApplicationResources(activity.applicationContext)
                    activity.recreate()
                }
            }, 1_000L)
        } else {
            activity.applicationContext.dataStore.edit { it[AppLanguageKey] = tag }
            refreshApplicationResources(activity.applicationContext)
            activity.recreate()
        }
    }
}

internal tailrec fun Context.activityOrNull(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> if (baseContext === this) null else baseContext.activityOrNull()
    else -> null
}
