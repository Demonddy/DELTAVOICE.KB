package com.deltavoice

import android.app.Application
import android.content.Context
import androidx.appcompat.app.AppCompatDelegate

/**
 * Application class for deltavoice.
 *
 * The app uses the device's system language by default. Android automatically
 * selects the appropriate string resources (e.g. values-es/strings.xml for Spanish)
 * based on the user's phone language settings. No locale override is applied.
 *
 * App appearance (light/dark/system) is applied at startup from user preference.
 */
class DeltaVoiceApplication : Application() {

    override fun onCreate() {
        applyAppTheme()
        super.onCreate()
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
