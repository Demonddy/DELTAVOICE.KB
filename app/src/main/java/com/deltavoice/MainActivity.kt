package com.deltavoice

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.bottomsheet.BottomSheetDialog

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
        setupBottomNav()
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
        val userName = prefs.getString(KEY_USER_NAME, getString(R.string.guest_user))
        findViewById<TextView>(R.id.user_name).text = userName
        
        // Greeting based on time of day (uses device language)
        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        val greeting = when {
            hour < 12 -> getString(R.string.good_morning)
            hour < 17 -> getString(R.string.good_afternoon)
            else -> getString(R.string.good_evening)
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
            Toast.makeText(this, getString(R.string.upgrade_premium_toast), Toast.LENGTH_SHORT).show()
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
        findViewById<TextView>(R.id.subscription_status).text = if (isPremium) getString(R.string.premium) else getString(R.string.free_plan)
    }

    /**
     * Setup bottom navigation bar: Home, Camera, Add (Video/Voice), Mic, Search
     */
    private fun setupBottomNav() {
        // Home - already on homepage, scroll to top or no-op
        findViewById<FrameLayout>(R.id.nav_home).setOnClickListener {
            // Already on home; could scroll to top if needed
        }

        // Camera - open video recording/config
        findViewById<FrameLayout>(R.id.nav_camera).setOnClickListener {
            startActivity(Intent(this, VideoRecordingActivity::class.java))
        }

        // Add (center) - show bottom sheet to choose Video or Voice
        findViewById<FrameLayout>(R.id.nav_add).setOnClickListener {
            showAddMediaBottomSheet()
        }

        // Mic - open voice config
        findViewById<FrameLayout>(R.id.nav_mic).setOnClickListener {
            startActivity(Intent(this, VoiceConfigActivity::class.java))
        }

        // Search - open search screen
        findViewById<FrameLayout>(R.id.nav_search).setOnClickListener {
            startActivity(Intent(this, SearchActivity::class.java))
        }

        BottomNavHelper.setActiveItem(this, R.id.nav_home)
    }

    /**
     * Show bottom sheet to choose Video or Voice update
     */
    private fun showAddMediaBottomSheet() {
        val dialog = BottomSheetDialog(this)
        val view = LayoutInflater.from(this).inflate(R.layout.bottom_sheet_add_media, null)
        dialog.setContentView(view)

        view.findViewById<LinearLayout>(R.id.option_video).setOnClickListener {
            dialog.dismiss()
            // Video: open VideoConfigActivity and launch upload picker
            startActivity(Intent(this, VideoConfigActivity::class.java).putExtra(VideoConfigActivity.EXTRA_OPEN_UPLOAD, true))
        }

        view.findViewById<LinearLayout>(R.id.option_voice).setOnClickListener {
            dialog.dismiss()
            // Voice: open VoiceConfigActivity and launch upload picker
            startActivity(Intent(this, VoiceConfigActivity::class.java).putExtra(VoiceConfigActivity.EXTRA_OPEN_UPLOAD, true))
        }

        dialog.show()
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
                Toast.makeText(this, getString(R.string.all_permissions_granted), Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, getString(R.string.some_features_limited), Toast.LENGTH_LONG).show()
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
