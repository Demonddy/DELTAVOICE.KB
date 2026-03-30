package com.deltavoice

import android.Manifest
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.widget.CompoundButton
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.RadioGroup
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.bottomsheet.BottomSheetDialog

/**
 * Main launcher activity - Professional homepage for deltavoice keyboard
 */
class MainActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var floatingBubbleSwitch: SwitchCompat
    private var floatingBubbleSwitchListener: CompoundButton.OnCheckedChangeListener? = null

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
        private const val OVERLAY_PERMISSION_REQUEST = 200
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
        setupFloatingBubble()
        setupOverlayTopRowSettings()
        updateStats()
    }
    
    override fun onResume() {
        super.onResume()
        updateStats()
        refreshFloatingBubbleSwitch()
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
     * Floating bubble: [SwitchCompat] reflects overlay service; row tap toggles for a larger hit area.
     */
    private fun setupFloatingBubble() {
        floatingBubbleSwitch = findViewById(R.id.switch_floating_bubble)
        val card = findViewById<LinearLayout>(R.id.card_floating_bubble)

        floatingBubbleSwitchListener = CompoundButton.OnCheckedChangeListener { _, isChecked ->
            if (!canDrawOverlaysCompat()) {
                if (isChecked) {
                    requestOverlayPermission()
                    floatingBubbleSwitch.post {
                        floatingBubbleSwitch.setOnCheckedChangeListener(null)
                        floatingBubbleSwitch.isChecked = false
                        floatingBubbleSwitch.setOnCheckedChangeListener(floatingBubbleSwitchListener)
                    }
                }
                return@OnCheckedChangeListener
            }
            if (isChecked) {
                if (!isOverlayServiceRunning()) startOverlayService()
            } else {
                if (isOverlayServiceRunning()) stopOverlayService()
            }
            updateFloatingBubbleStatusText()
        }
        floatingBubbleSwitch.setOnCheckedChangeListener(floatingBubbleSwitchListener)

        // Row tap opens overlay settings if permission missing; otherwise only the switch toggles
        // (avoids double-toggle from parent + switch).
        card.setOnClickListener {
            if (!canDrawOverlaysCompat()) {
                requestOverlayPermission()
            }
        }

        refreshFloatingBubbleSwitch()
    }

    private fun refreshFloatingBubbleSwitch() {
        floatingBubbleSwitch.setOnCheckedChangeListener(null)
        floatingBubbleSwitch.isChecked = isOverlayServiceRunning()
        floatingBubbleSwitch.setOnCheckedChangeListener(floatingBubbleSwitchListener)
        updateFloatingBubbleStatusText()
    }

    /**
     * Top row layout (horizontal vs arc) and width scale — read by [OverlayBubbleService].
     */
    private fun setupOverlayTopRowSettings() {
        val radio = findViewById<RadioGroup>(R.id.radio_overlay_top_row_style)
        val seek = findViewById<SeekBar>(R.id.seek_overlay_top_row_width)
        val widthLabel = findViewById<TextView>(R.id.overlay_top_row_width_label)

        fun refreshWidthLabel(scale: Float) {
            val pct = (scale * 100f).toInt().coerceIn(65, 100)
            widthLabel.text = getString(R.string.overlay_top_row_width_label, pct)
        }

        radio.setOnCheckedChangeListener(null)
        when (OverlayPrefs.getTopRowStyle(this)) {
            OverlayPrefs.STYLE_ARC -> radio.check(R.id.radio_top_row_arc)
            else -> radio.check(R.id.radio_top_row_horizontal)
        }
        val scale = OverlayPrefs.getTopRowWidthScale(this)
        seek.progress = ((scale - 0.65f) / 0.35f * 100f).toInt().coerceIn(0, 100)
        refreshWidthLabel(scale)

        radio.setOnCheckedChangeListener { _, checkedId ->
            val style = if (checkedId == R.id.radio_top_row_arc) {
                OverlayPrefs.STYLE_ARC
            } else {
                OverlayPrefs.STYLE_HORIZONTAL
            }
            OverlayPrefs.setTopRowStyle(this, style)
        }
        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val s = 0.65f + (progress / 100f) * 0.35f
                refreshWidthLabel(s)
                if (fromUser) {
                    OverlayPrefs.setTopRowWidthScale(this@MainActivity, s)
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}

            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    private fun updateFloatingBubbleStatusText() {
        findViewById<TextView>(R.id.floating_bubble_status).text = if (isOverlayServiceRunning()) {
            getString(R.string.floating_bubble_enabled)
        } else {
            getString(R.string.floating_bubble_tap_to_enable)
        }
    }

    private fun isOverlayServiceRunning(): Boolean {
        return try {
            val manager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            @Suppress("DEPRECATION")
            manager.getRunningServices(Int.MAX_VALUE).any {
                it.service.className == OverlayBubbleService::class.java.name
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun canDrawOverlaysCompat(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(this)
        } else {
            true
        }
    }

    private fun requestOverlayPermission() {
        Toast.makeText(this, getString(R.string.overlay_permission_required), Toast.LENGTH_LONG).show()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivityForResult(intent, OVERLAY_PERMISSION_REQUEST)
        } else {
            startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
            )
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == OVERLAY_PERMISSION_REQUEST) {
            if (canDrawOverlaysCompat()) {
                startOverlayService()
                Toast.makeText(this, "Floating bubble enabled", Toast.LENGTH_SHORT).show()
            }
            refreshFloatingBubbleSwitch()
        }
    }

    private fun startOverlayService() {
        val intent = Intent(this, OverlayBubbleService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun stopOverlayService() {
        val intent = Intent(this, OverlayBubbleService::class.java).apply {
            action = OverlayBubbleService.ACTION_STOP
        }
        startService(intent)
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
