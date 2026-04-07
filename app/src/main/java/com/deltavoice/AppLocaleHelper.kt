package com.deltavoice

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
/**
 * Applies [AppCompatDelegate] per-app locales to any [Context] (IME, Service, etc.)
 * so [android.content.res.Resources] matches Activities.
 */
object AppLocaleHelper {

    fun wrap(context: Context): Context {
        val locales = AppCompatDelegate.getApplicationLocales()
        if (locales.isEmpty) {
            return context
        }
        val config = Configuration(context.resources.configuration)
        val locale = locales[0] ?: return context
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            config.setLocales(android.os.LocaleList(locale))
        } else {
            @Suppress("DEPRECATION")
            config.locale = locale
        }
        return context.createConfigurationContext(config)
    }
}
