package com.deltavoice.theme

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import androidx.core.graphics.ColorUtils
import com.deltavoice.OverlayPrefs

/**
 * Resolves [KeyboardThemePalette] from [OverlayPrefs.PREFS_NAME] and shared preset/custom logic for IME + Themes preview.
 */
object KeyboardThemeStore {

    const val KEY_SELECTED_THEME = "selected_theme"
    const val KEY_CUSTOM_THEME_COLOR = "custom_theme_color"

    const val THEME_DARK_PURPLE = "dark_purple"
    const val THEME_MIDNIGHT_BLUE = "midnight_blue"
    const val THEME_FOREST_GREEN = "forest_green"
    const val THEME_SUNSET_ORANGE = "sunset_orange"
    const val THEME_ROSE_PINK = "rose_pink"
    const val THEME_PURE_DARK = "pure_dark"
    const val THEME_GALAXY = "galaxy"
    const val THEME_NEON = "neon"
    const val THEME_CUSTOM = "custom"

    private const val DEFAULT_THEME_ID = THEME_DARK_PURPLE
    private val defaultCustomAccent: Int = Color.parseColor("#A78BFA")

    fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(OverlayPrefs.PREFS_NAME, Context.MODE_PRIVATE)

    fun loadPalette(context: Context): KeyboardThemePalette {
        val p = prefs(context)
        val themeId = p.getString(KEY_SELECTED_THEME, DEFAULT_THEME_ID) ?: DEFAULT_THEME_ID
        val custom = p.getInt(KEY_CUSTOM_THEME_COLOR, defaultCustomAccent)
        return paletteForThemeId(themeId, custom)
    }

    /**
     * Preview or resolve without reading prefs (staged selection in ThemesActivity).
     */
    fun paletteForThemeId(themeId: String, customAccentArgb: Int): KeyboardThemePalette {
        return when (themeId) {
            THEME_CUSTOM -> paletteFromAccent(customAccentArgb)
            else -> PRESETS[themeId] ?: PRESETS[DEFAULT_THEME_ID]!!
        }
    }

    fun defaultPalette(): KeyboardThemePalette = PRESETS[DEFAULT_THEME_ID]!!

    /**
     * Single accent chosen by user: dark background, readable text, softer icon accent.
     */
    fun paletteFromAccent(accent: Int): KeyboardThemePalette {
        val bg = ColorUtils.blendARGB(accent, Color.BLACK, 0.82f)
        val surface = ColorUtils.blendARGB(bg, Color.WHITE, 0.08f)
        val accentSoft = ColorUtils.blendARGB(accent, Color.WHITE, 0.12f)
        val keyText = ColorUtils.blendARGB(Color.WHITE, accent, 0.04f)
        val muted = ColorUtils.blendARGB(keyText, bg, 0.45f)
        return KeyboardThemePalette(
            background = bg,
            surface = surface,
            accent = accent,
            accentSoft = accentSoft,
            keyText = keyText,
            keyTextMuted = muted
        )
    }

    private val PRESETS: Map<String, KeyboardThemePalette> = mapOf(
        THEME_DARK_PURPLE to KeyboardThemePalette(
            background = Color.parseColor("#1A1F2E"),
            surface = Color.parseColor("#252B3D"),
            accent = Color.parseColor("#A78BFA"),
            accentSoft = Color.parseColor("#C084FC"),
            keyText = Color.parseColor("#F2F2F2"),
            keyTextMuted = Color.parseColor("#9CA3AF")
        ),
        THEME_MIDNIGHT_BLUE to KeyboardThemePalette(
            background = Color.parseColor("#0F172A"),
            surface = Color.parseColor("#1E293B"),
            accent = Color.parseColor("#60A5FA"),
            accentSoft = Color.parseColor("#93C5FD"),
            keyText = Color.parseColor("#F1F5F9"),
            keyTextMuted = Color.parseColor("#94A3B8")
        ),
        THEME_FOREST_GREEN to KeyboardThemePalette(
            background = Color.parseColor("#14532D"),
            surface = Color.parseColor("#166534"),
            accent = Color.parseColor("#34D399"),
            accentSoft = Color.parseColor("#6EE7B7"),
            keyText = Color.parseColor("#ECFDF5"),
            keyTextMuted = Color.parseColor("#A7F3D0")
        ),
        THEME_SUNSET_ORANGE to KeyboardThemePalette(
            background = Color.parseColor("#431407"),
            surface = Color.parseColor("#7C2D12"),
            accent = Color.parseColor("#FB923C"),
            accentSoft = Color.parseColor("#FDBA74"),
            keyText = Color.parseColor("#FFF7ED"),
            keyTextMuted = Color.parseColor("#FED7AA")
        ),
        THEME_ROSE_PINK to KeyboardThemePalette(
            background = Color.parseColor("#831843"),
            surface = Color.parseColor("#9D174D"),
            accent = Color.parseColor("#F472B6"),
            accentSoft = Color.parseColor("#F9A8D4"),
            keyText = Color.parseColor("#FFF1F2"),
            keyTextMuted = Color.parseColor("#FBCFE8")
        ),
        THEME_PURE_DARK to KeyboardThemePalette(
            background = Color.parseColor("#09090B"),
            surface = Color.parseColor("#18181B"),
            accent = Color.parseColor("#A1A1AA"),
            accentSoft = Color.parseColor("#D4D4D8"),
            keyText = Color.parseColor("#FAFAFA"),
            keyTextMuted = Color.parseColor("#A1A1AA")
        ),
        THEME_GALAXY to KeyboardThemePalette(
            background = Color.parseColor("#1E1B4B"),
            surface = Color.parseColor("#312E81"),
            accent = Color.parseColor("#C4B5FD"),
            accentSoft = Color.parseColor("#DDD6FE"),
            keyText = Color.parseColor("#F5F3FF"),
            keyTextMuted = Color.parseColor("#C4B5FD")
        ),
        THEME_NEON to KeyboardThemePalette(
            background = Color.parseColor("#0A1628"),
            surface = Color.parseColor("#132A45"),
            accent = Color.parseColor("#00FF88"),
            accentSoft = Color.parseColor("#34D399"),
            keyText = Color.parseColor("#ECFDF5"),
            keyTextMuted = Color.parseColor("#6EE7B7")
        ),
    )
}
