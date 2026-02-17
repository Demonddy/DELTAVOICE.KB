package com.deltavoice

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/**
 * Main launcher activity - Professional homepage for deltavoice keyboard
 */
class MainActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences
    
    companion object {
        private const val PREFS_NAME = "deltavoice_prefs"
        private const val KEY_FIRST_LAUNCH = "first_launch"
        private const val KEY_PERMISSIONS_GRANTED = "permissions_granted"
        private const val KEY_USER_NAME = "user_name"
        private const val KEY_IS_PREMIUM = "is_premium"
        private const val KEY_STAT_VIDEOS = "stat_videos"
        private const val KEY_STAT_VOICES = "stat_voices"
        private const val KEY_STAT_CHATS = "stat_chats"
        private const val PERMISSION_REQUEST_CODE = 100
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        
        // Check if this is first launch and permissions not granted
        val isFirstLaunch = prefs.getBoolean(KEY_FIRST_LAUNCH, true)
        val permissionsGranted = prefs.getBoolean(KEY_PERMISSIONS_GRANTED, false)
        
        if (isFirstLaunch && !permissionsGranted) {
            // Request permissions on first launch
            requestRequiredPermissions()
        }
        
        setContentView(R.layout.activity_main)
        
        setupUI()
        updateStats()
    }
    
    override fun onResume() {
        super.onResume()
        updateStats()
    }
    
    /**
     * Setup all UI components and click listeners
     */
    private fun setupUI() {
        // Header - Profile Section
        findViewById<FrameLayout>(R.id.profile_section).setOnClickListener {
            startActivity(Intent(this, AccountActivity::class.java))
        }
        
        // Update user name
        val userName = prefs.getString(KEY_USER_NAME, "Guest User")
        findViewById<TextView>(R.id.user_name).text = userName
        
        // Greeting based on time of day
        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        val greeting = when {
            hour < 12 -> "Good morning"
            hour < 17 -> "Good afternoon"
            else -> "Good evening"
        }
        findViewById<TextView>(R.id.greeting_text).text = greeting
        
        // Settings button
        findViewById<ImageButton>(R.id.btn_settings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        
        // Premium banner
        findViewById<LinearLayout>(R.id.premium_banner).setOnClickListener {
            // Navigate to subscription page
            startActivity(Intent(this, AccountActivity::class.java))
            Toast.makeText(this, "Upgrade to Premium for unlimited features!", Toast.LENGTH_SHORT).show()
        }
        
        // Feature Cards - each navigates to dedicated config page
        findViewById<LinearLayout>(R.id.card_video).setOnClickListener {
            startActivity(Intent(this, VideoConfigActivity::class.java))
        }
        
        findViewById<LinearLayout>(R.id.card_voice).setOnClickListener {
            startActivity(Intent(this, VoiceConfigActivity::class.java))
        }
        
        findViewById<LinearLayout>(R.id.card_ai_chat).setOnClickListener {
            startActivity(Intent(this, AIChatConfigActivity::class.java))
        }
        
        findViewById<LinearLayout>(R.id.card_themes).setOnClickListener {
            startActivity(Intent(this, ThemesActivity::class.java))
        }
        
        // Quick Actions
        findViewById<LinearLayout>(R.id.card_enable_keyboard).setOnClickListener {
            // Open keyboard settings
            val intent = Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)
            startActivity(intent)
        }
        
        findViewById<LinearLayout>(R.id.card_account).setOnClickListener {
            startActivity(Intent(this, AccountActivity::class.java))
        }
        
        findViewById<LinearLayout>(R.id.card_subscription).setOnClickListener {
            startActivity(Intent(this, AccountActivity::class.java))
        }
        
        // Update subscription status
        val isPremium = prefs.getBoolean(KEY_IS_PREMIUM, false)
        findViewById<TextView>(R.id.subscription_status).text = if (isPremium) "Premium" else "Free Plan"
    }
    
    /**
     * Update activity stats from SharedPreferences
     */
    private fun updateStats() {
        val statVideos = prefs.getInt(KEY_STAT_VIDEOS, 0)
        val statVoices = prefs.getInt(KEY_STAT_VOICES, 0)
        val statChats = prefs.getInt(KEY_STAT_CHATS, 0)
        
        findViewById<TextView>(R.id.stat_videos).text = statVideos.toString()
        findViewById<TextView>(R.id.stat_voices).text = statVoices.toString()
        findViewById<TextView>(R.id.stat_chats).text = statChats.toString()
    }
    
    /**
     * Request microphone and camera permissions (only once for new users)
     */
    private fun requestRequiredPermissions() {
        val permissionsToRequest = mutableListOf<String>()
        
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) 
            != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.RECORD_AUDIO)
        }
        
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) 
            != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.CAMERA)
        }
        
        if (permissionsToRequest.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                this,
                permissionsToRequest.toTypedArray(),
                PERMISSION_REQUEST_CODE
            )
        } else {
            // All permissions already granted
            markPermissionsGranted()
        }
    }
    
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        
        if (requestCode == PERMISSION_REQUEST_CODE) {
            // Mark first launch as done regardless of permission result
            prefs.edit().putBoolean(KEY_FIRST_LAUNCH, false).apply()
            
            // Check if all permissions were granted
            val allGranted = grantResults.isNotEmpty() && 
                grantResults.all { it == PackageManager.PERMISSION_GRANTED }
            
            if (allGranted) {
                markPermissionsGranted()
                Toast.makeText(this, "All permissions granted! Enjoy deltavoice.", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Some features may be limited without permissions.", Toast.LENGTH_LONG).show()
            }
        }
    }
    
    /**
     * Mark permissions as granted in SharedPreferences
     */
    private fun markPermissionsGranted() {
        prefs.edit()
            .putBoolean(KEY_PERMISSIONS_GRANTED, true)
            .putBoolean(KEY_FIRST_LAUNCH, false)
            .apply()
    }
}
