package com.deltavoice

import android.content.Context
import android.content.SharedPreferences

/**
 * Floating bubble top row: layout style and width scale (shared with [OverlayBubbleService]).
 */
object OverlayPrefs {
    const val PREFS_NAME = "deltavoice_prefs"
    const val KEY_TOP_ROW_STYLE = "overlay_top_row_style"
    const val KEY_TOP_ROW_WIDTH_SCALE = "overlay_top_row_width_scale"

    const val STYLE_HORIZONTAL = "horizontal"
    const val STYLE_ARC = "arc"

    private const val DEFAULT_WIDTH_SCALE = 1f
    private const val MIN_WIDTH_SCALE = 0.65f
    private const val MAX_WIDTH_SCALE = 1f

    fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getTopRowStyle(context: Context): String =
        prefs(context).getString(KEY_TOP_ROW_STYLE, STYLE_HORIZONTAL) ?: STYLE_HORIZONTAL

    fun getTopRowWidthScale(context: Context): Float =
        prefs(context).getFloat(KEY_TOP_ROW_WIDTH_SCALE, DEFAULT_WIDTH_SCALE)
            .coerceIn(MIN_WIDTH_SCALE, MAX_WIDTH_SCALE)

    fun setTopRowStyle(context: Context, style: String) {
        prefs(context).edit().putString(KEY_TOP_ROW_STYLE, style).apply()
    }

    fun setTopRowWidthScale(context: Context, scale: Float) {
        prefs(context).edit()
            .putFloat(KEY_TOP_ROW_WIDTH_SCALE, scale.coerceIn(MIN_WIDTH_SCALE, MAX_WIDTH_SCALE))
            .apply()
    }
}
