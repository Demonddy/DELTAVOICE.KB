package com.deltavoice

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

/**
 * Activity for selecting keyboard themes
 */
class ThemesActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences
    
    companion object {
        private const val PREFS_NAME = "deltavoice_prefs"
        private const val KEY_SELECTED_THEME = "selected_theme"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_themes)
        
        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        
        setupUI()
    }
    
    private fun setupUI() {
        // Back button
        findViewById<ImageButton>(R.id.btn_back).setOnClickListener {
            finish()
        }
        
        // Theme selections
        val themes = mapOf(
            R.id.theme_dark_purple to "dark_purple",
            R.id.theme_midnight_blue to "midnight_blue",
            R.id.theme_forest_green to "forest_green",
            R.id.theme_sunset_orange to "sunset_orange",
            R.id.theme_rose_pink to "rose_pink",
            R.id.theme_pure_dark to "pure_dark"
        )
        
        themes.forEach { (viewId, themeName) ->
            findViewById<LinearLayout>(viewId)?.setOnClickListener {
                selectTheme(themeName)
            }
        }
        
        // Premium themes
        findViewById<LinearLayout>(R.id.theme_galaxy)?.setOnClickListener {
            val isPremium = prefs.getBoolean("is_premium", false)
            if (isPremium) {
                selectTheme("galaxy")
            } else {
                Toast.makeText(this, "Upgrade to Premium to unlock this theme!", Toast.LENGTH_SHORT).show()
            }
        }
        
        findViewById<LinearLayout>(R.id.theme_neon)?.setOnClickListener {
            val isPremium = prefs.getBoolean("is_premium", false)
            if (isPremium) {
                selectTheme("neon")
            } else {
                Toast.makeText(this, "Upgrade to Premium to unlock this theme!", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun selectTheme(themeName: String) {
        prefs.edit().putString(KEY_SELECTED_THEME, themeName).apply()
        
        val displayName = themeName.replace("_", " ").replaceFirstChar { it.uppercase() }
        Toast.makeText(this, "Theme changed to $displayName", Toast.LENGTH_SHORT).show()
    }
}
