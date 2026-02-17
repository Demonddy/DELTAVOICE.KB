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
