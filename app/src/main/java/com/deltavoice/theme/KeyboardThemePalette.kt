package com.deltavoice.theme

import androidx.annotation.ColorInt

/**
 * Colors for the IME typing surface (main keyboard + top bar). Derived presets stay readable on each background.
 */
data class KeyboardThemePalette(
    @ColorInt val background: Int,
    @ColorInt val surface: Int,
    @ColorInt val accent: Int,
    /** Softer accent for small icons (e.g. language mic row). */
    @ColorInt val accentSoft: Int,
    @ColorInt val keyText: Int,
    @ColorInt val keyTextMuted: Int,
    /** Toolbar circles, shift/backspace, and bottom-row icon keys; overridable via [KeyboardThemeStore.KEY_ICON_COLOR]. */
    @ColorInt val iconTint: Int,
)
