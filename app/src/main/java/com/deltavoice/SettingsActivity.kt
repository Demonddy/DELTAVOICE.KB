package com.deltavoice

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.Switch
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import java.util.Locale

/**
 * Activity for app settings
 */
class SettingsActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences
    
    companion object {
        private const val PREFS_NAME = "deltavoice_prefs"
        private const val KEY_SOUND_ENABLED = "sound_enabled"
        private const val KEY_VIBRATION_ENABLED = "vibration_enabled"
        private const val KEY_AUTO_CAPITALIZE = "auto_capitalize"
        private const val KEY_KEYBOARD_HEIGHT = "keyboard_height"
        private const val KEY_KEYBOARD_HEIGHT_CUSTOM = "keyboard_height_custom"
        private const val KEY_PREDICTIVE_TEXT = "predictive_text"
        private const val KEY_AUTO_CORRECTION = "auto_correction"
        private const val KEY_APP_THEME = "app_theme"

        /** App theme options: light, dark, system */
        private val APP_THEME_OPTIONS = listOf(
            DeltaVoiceApplication.THEME_LIGHT to R.string.theme_light,
            DeltaVoiceApplication.THEME_DARK to R.string.theme_dark,
            DeltaVoiceApplication.THEME_SYSTEM to R.string.theme_system
        )

        /** Supported app locales: empty = system default, then en, es, fr, de */
        private val APP_LOCALES = listOf(
            "" to "system_default",
            "en" to "English",
            "es" to "Español",
            "fr" to "Français",
            "de" to "Deutsch"
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        
        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        
        setupUI()
    }
    
    private fun setupUI() {
        // Back button
        findViewById<ImageButton>(R.id.btn_back).setOnClickListener {
            finish()
        }
        
        // Sound switch
        val soundSwitch = findViewById<Switch>(R.id.switch_sound)
        soundSwitch.isChecked = prefs.getBoolean(KEY_SOUND_ENABLED, false)
        soundSwitch.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(KEY_SOUND_ENABLED, isChecked).apply()
        }
        
        // Vibration switch
        val vibrationSwitch = findViewById<Switch>(R.id.switch_vibration)
        vibrationSwitch.isChecked = prefs.getBoolean(KEY_VIBRATION_ENABLED, true)
        vibrationSwitch.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(KEY_VIBRATION_ENABLED, isChecked).apply()
        }
        
        // Auto-capitalize switch
        val autoCapSwitch = findViewById<Switch>(R.id.switch_auto_cap)
        autoCapSwitch.isChecked = prefs.getBoolean(KEY_AUTO_CAPITALIZE, true)
        autoCapSwitch.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(KEY_AUTO_CAPITALIZE, isChecked).apply()
        }
        
        // Keyboard height setting
        setupKeyboardHeightSetting()
        
        // Predictive text switch
        val predictiveSwitch = findViewById<Switch>(R.id.switch_predictive_text)
        predictiveSwitch.isChecked = prefs.getBoolean(KEY_PREDICTIVE_TEXT, true)
        predictiveSwitch.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(KEY_PREDICTIVE_TEXT, isChecked).apply()
        }
        
        // Auto-correction switch
        val autoCorrectionSwitch = findViewById<Switch>(R.id.switch_auto_correction)
        autoCorrectionSwitch.isChecked = prefs.getBoolean(KEY_AUTO_CORRECTION, true)
        autoCorrectionSwitch.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(KEY_AUTO_CORRECTION, isChecked).apply()
        }
        
        // App appearance setting
        findViewById<LinearLayout>(R.id.setting_app_appearance).setOnClickListener {
            showAppThemeDialog()
        }
        updateAppThemeDisplay()

        // App language setting
        findViewById<LinearLayout>(R.id.setting_app_language).setOnClickListener {
            showAppLanguageDialog()
        }
        updateAppLanguageDisplay()
        
        // Default voice setting
        findViewById<LinearLayout>(R.id.setting_default_voice).setOnClickListener {
            showVoiceSelectionDialog()
        }
        
        // Default language setting
        findViewById<LinearLayout>(R.id.setting_default_language).setOnClickListener {
            showLanguageSelectionDialog()
        }
        
        // Permissions
        findViewById<LinearLayout>(R.id.setting_permissions).setOnClickListener {
            startActivity(Intent(this, PermissionsActivity::class.java))
        }
        
        // Clear data
        findViewById<LinearLayout>(R.id.setting_clear_data).setOnClickListener {
            showClearDataDialog()
        }
        
        // Privacy Policy
        findViewById<LinearLayout>(R.id.setting_privacy_policy).setOnClickListener {
            Toast.makeText(this, "Privacy Policy page coming soon", Toast.LENGTH_SHORT).show()
        }
        
        // Terms of Service
        findViewById<LinearLayout>(R.id.setting_terms).setOnClickListener {
            Toast.makeText(this, "Terms of Service page coming soon", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun setupKeyboardHeightSetting() {
        val heightOptions = arrayOf("Extra short", "Short", "Normal", "Tall", "Custom")
        val currentHeight = prefs.getString(KEY_KEYBOARD_HEIGHT, "Normal") ?: "Normal"
        val currentCustom = prefs.getInt(KEY_KEYBOARD_HEIGHT_CUSTOM, 44)
        val heightLabel = findViewById<android.widget.TextView>(R.id.current_keyboard_height)
        val sliderContainer = findViewById<LinearLayout>(R.id.keyboard_height_slider_container)
        val seekbar = findViewById<SeekBar>(R.id.seekbar_keyboard_height)
        
        fun updateHeightDisplay() {
            val h = prefs.getString(KEY_KEYBOARD_HEIGHT, "Normal") ?: "Normal"
            val c = prefs.getInt(KEY_KEYBOARD_HEIGHT_CUSTOM, 44)
            heightLabel.text = if (h == "Custom") "Custom (${c}dp)" else h
            sliderContainer.visibility = if (h == "Custom") android.view.View.VISIBLE else android.view.View.GONE
        }
        
        updateHeightDisplay()
        seekbar.progress = (currentCustom - 36).coerceIn(0, 60)
        seekbar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser && prefs.getString(KEY_KEYBOARD_HEIGHT, "") == "Custom") {
                    val dp = progress + 36
                    prefs.edit().putInt(KEY_KEYBOARD_HEIGHT_CUSTOM, dp).apply()
                    updateHeightDisplay()
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
        
        findViewById<LinearLayout>(R.id.setting_keyboard_height).setOnClickListener {
            var selectedIndex = heightOptions.indexOf(currentHeight).coerceAtLeast(0)
            if (currentHeight == "Custom") selectedIndex = 4
            
            AlertDialog.Builder(this, R.style.Theme_DeltaVoice_Dialog)
                .setTitle("Keyboard height")
                .setSingleChoiceItems(heightOptions, selectedIndex) { _, which ->
                    selectedIndex = which
                }
                .setPositiveButton("OK") { _, _ ->
                    val selected = heightOptions[selectedIndex]
                    prefs.edit().putString(KEY_KEYBOARD_HEIGHT, selected).apply()
                    if (selected == "Custom") {
                        prefs.edit().putInt(KEY_KEYBOARD_HEIGHT_CUSTOM, seekbar.progress + 36).apply()
                    }
                    updateHeightDisplay()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }
    
    private fun updateAppThemeDisplay() {
        val currentTheme = prefs.getString(KEY_APP_THEME, DeltaVoiceApplication.THEME_SYSTEM)
            ?: DeltaVoiceApplication.THEME_SYSTEM
        val displayResId = APP_THEME_OPTIONS.find { it.first == currentTheme }?.second
            ?: R.string.theme_system
        findViewById<android.widget.TextView>(R.id.current_app_theme).text = getString(displayResId)
    }

    private fun showAppThemeDialog() {
        val currentTheme = prefs.getString(KEY_APP_THEME, DeltaVoiceApplication.THEME_SYSTEM)
            ?: DeltaVoiceApplication.THEME_SYSTEM
        val options = APP_THEME_OPTIONS.map { getString(it.second) }.toTypedArray()
        var selectedIndex = APP_THEME_OPTIONS.indexOfFirst { it.first == currentTheme }.coerceAtLeast(0)

        AlertDialog.Builder(this, R.style.Theme_DeltaVoice_Dialog)
            .setTitle(getString(R.string.select_app_theme))
            .setSingleChoiceItems(options, selectedIndex) { _, which ->
                selectedIndex = which
            }
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val (themeValue, _) = APP_THEME_OPTIONS[selectedIndex]
                prefs.edit().putString(KEY_APP_THEME, themeValue).apply()
                val mode = when (themeValue) {
                    DeltaVoiceApplication.THEME_LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
                    DeltaVoiceApplication.THEME_DARK -> AppCompatDelegate.MODE_NIGHT_YES
                    else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                }
                AppCompatDelegate.setDefaultNightMode(mode)
                // Activity recreates automatically
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun updateAppLanguageDisplay() {
        val displayLocale = Locale.getDefault()
        val currentLocales = AppCompatDelegate.getApplicationLocales()
        val displayText = if (currentLocales.isEmpty) {
            getString(R.string.system_default)
        } else {
            val locale = currentLocales[0]
            locale?.getDisplayName(displayLocale) ?: getString(R.string.system_default)
        }
        findViewById<android.widget.TextView>(R.id.current_app_language).text = displayText
    }
    
    private fun showAppLanguageDialog() {
        val displayLocale = Locale.getDefault()
        val currentLocales = AppCompatDelegate.getApplicationLocales()
        val currentTag = if (currentLocales.isEmpty) "" else currentLocales[0]?.toLanguageTag() ?: ""
        
        val options = APP_LOCALES.map { (tag, _) ->
            if (tag.isEmpty()) getString(R.string.system_default)
            else Locale.forLanguageTag(tag).getDisplayName(displayLocale)
        }.toTypedArray()
        
        var selectedIndex = APP_LOCALES.indexOfFirst { it.first == currentTag }.coerceAtLeast(0)
        
        AlertDialog.Builder(this, R.style.Theme_DeltaVoice_Dialog)
            .setTitle(getString(R.string.select_app_language))
            .setSingleChoiceItems(options, selectedIndex) { _, which ->
                selectedIndex = which
            }
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val (tag, _) = APP_LOCALES[selectedIndex]
                val newLocales = if (tag.isEmpty()) {
                    LocaleListCompat.getEmptyLocaleList()
                } else {
                    LocaleListCompat.forLanguageTags(tag)
                }
                AppCompatDelegate.setApplicationLocales(newLocales)
                // Activity is recreated automatically by setApplicationLocales
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
    
    private fun showVoiceSelectionDialog() {
        val voices = arrayOf("Aria", "Adam", "Roger", "Sarah", "Laura", "Charlie", "George", "Liam")
        val currentVoice = prefs.getString("default_voice", "Aria") ?: "Aria"
        var selectedIndex = voices.indexOf(currentVoice).coerceAtLeast(0)
        
        AlertDialog.Builder(this, R.style.Theme_DeltaVoice_Dialog)
            .setTitle("Select default voice")
            .setSingleChoiceItems(voices, selectedIndex) { _, which ->
                selectedIndex = which
            }
            .setPositiveButton("OK") { _, _ ->
                prefs.edit().putString("default_voice", voices[selectedIndex]).apply()
                findViewById<android.widget.TextView>(R.id.current_voice).text = voices[selectedIndex]
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun showLanguageSelectionDialog() {
        val languages = arrayOf("English", "Spanish", "French", "German", "Italian", "Portuguese", 
            "Russian", "Japanese", "Korean", "Chinese", "Arabic", "Hindi")
        val currentLanguage = prefs.getString("default_language", "English") ?: "English"
        var selectedIndex = languages.indexOf(currentLanguage).coerceAtLeast(0)
        
        AlertDialog.Builder(this, R.style.Theme_DeltaVoice_Dialog)
            .setTitle("Select default language")
            .setSingleChoiceItems(languages, selectedIndex) { _, which ->
                selectedIndex = which
            }
            .setPositiveButton("OK") { _, _ ->
                prefs.edit().putString("default_language", languages[selectedIndex]).apply()
                findViewById<android.widget.TextView>(R.id.current_language).text = languages[selectedIndex]
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun showClearDataDialog() {
        AlertDialog.Builder(this, R.style.Theme_DeltaVoice_Dialog)
            .setTitle("Clear all data")
            .setMessage("This will delete all cached files and recordings. This action cannot be undone.")
            .setPositiveButton("Clear") { _, _ ->
                clearAllData()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun clearAllData() {
        try {
            // Clear recordings directory
            val recordingsDir = filesDir.resolve("recordings")
            recordingsDir.deleteRecursively()
            
            // Clear videos directory
            val videosDir = filesDir.resolve("videos")
            videosDir.deleteRecursively()
            
            // Reset stats
            prefs.edit()
                .putInt("stat_videos", 0)
                .putInt("stat_voices", 0)
                .putInt("stat_chats", 0)
                .apply()
            
            Toast.makeText(this, "All data cleared successfully", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to clear data: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}
