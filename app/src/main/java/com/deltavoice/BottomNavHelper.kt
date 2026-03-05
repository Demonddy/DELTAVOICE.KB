package com.deltavoice

import android.app.Activity
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat

/**
 * Highlights the active icon in the bottom navigation bar.
 * Call from MainActivity (nav_home) or SearchActivity (nav_search).
 */
object BottomNavHelper {

    private val navItems = listOf(
        R.id.nav_home to (R.id.nav_home_icon to R.id.nav_home_label),
        R.id.nav_camera to (R.id.nav_camera_icon to R.id.nav_camera_label),
        R.id.nav_mic to (R.id.nav_mic_icon to R.id.nav_mic_label),
        R.id.nav_search to (R.id.nav_search_icon to R.id.nav_search_label),
    )

    fun setActiveItem(activity: Activity, activeNavId: Int) {
        val activeColor = ContextCompat.getColor(activity, R.color.primary)
        val inactiveColor = ContextCompat.getColor(activity, R.color.text_secondary)

        for ((navId, iconAndLabel) in navItems) {
            val icon = activity.findViewById<ImageView>(iconAndLabel.first)
            val label = activity.findViewById<TextView>(iconAndLabel.second)
            val isActive = navId == activeNavId
            val color = if (isActive) activeColor else inactiveColor
            icon?.setColorFilter(color)
            label?.setTextColor(color)
        }
    }
}
