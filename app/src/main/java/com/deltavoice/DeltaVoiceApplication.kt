package com.deltavoice

import android.app.Application
import android.content.Context
import androidx.appcompat.app.AppCompatDelegate

/**
 * Application class for deltavoice.
 *
 * When the user picks a language in Settings, [AppCompatDelegate.setApplicationLocales]
 * is used. [AppLocaleHelper.wrap] applies that choice to the application [Context] so
 * [Resources] match Activities and the IME (which also wraps via [AppLocaleHelper]).
 * If the user leaves "System default", locales are empty and Android follows the device.
 *
 * App appearance (light/dark/system) is applied at startup from user preference.
 */
class DeltaVoiceApplication : Application() {

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(AppLocaleHelper.wrap(base))
    }

    override fun onCreate() {
        applyAppTheme()
        super.onCreate()
        PredictiveWordList.initializePredictiveWordAssets(applicationContext)
    }

    private fun applyAppTheme() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val theme = prefs.getString(KEY_APP_THEME, THEME_SYSTEM) ?: THEME_SYSTEM
        val mode = when (theme) {
            THEME_LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
            THEME_DARK -> AppCompatDelegate.MODE_NIGHT_YES
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        AppCompatDelegate.setDefaultNightMode(mode)
    }

    companion object {
        private const val PREFS_NAME = "deltavoice_prefs"
        private const val KEY_APP_THEME = "app_theme"
        const val THEME_LIGHT = "light"
        const val THEME_DARK = "dark"
        const val THEME_SYSTEM = "system"
    }
}
