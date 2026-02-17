package com.deltavoice

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

/**
 * Activity for user account management
 */
class AccountActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences
    
    companion object {
        private const val PREFS_NAME = "deltavoice_prefs"
        private const val KEY_USER_NAME = "user_name"
        private const val KEY_USER_EMAIL = "user_email"
        private const val KEY_IS_LOGGED_IN = "is_logged_in"
        private const val KEY_IS_PREMIUM = "is_premium"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_account)
        
        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        
        setupUI()
        updateAccountUI()
    }
    
    private fun setupUI() {
        // Back button
        findViewById<ImageButton>(R.id.btn_back).setOnClickListener {
            finish()
        }
        
        // Login button
        findViewById<Button>(R.id.btn_login).setOnClickListener {
            showLoginDialog()
        }
        
        // Upgrade button
        findViewById<Button>(R.id.btn_upgrade).setOnClickListener {
            showUpgradeDialog()
        }
        
        // Edit profile
        findViewById<LinearLayout>(R.id.btn_edit_profile).setOnClickListener {
            showEditProfileDialog()
        }
        
        // Change password
        findViewById<LinearLayout>(R.id.btn_change_password).setOnClickListener {
            val isLoggedIn = prefs.getBoolean(KEY_IS_LOGGED_IN, false)
            if (isLoggedIn) {
                Toast.makeText(this, "Password change coming soon", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Please sign in first", Toast.LENGTH_SHORT).show()
            }
        }
        
        // Logout
        findViewById<LinearLayout>(R.id.btn_logout).setOnClickListener {
            showLogoutDialog()
        }
        
        // Delete account
        findViewById<LinearLayout>(R.id.btn_delete_account).setOnClickListener {
            showDeleteAccountDialog()
        }
    }
    
    private fun updateAccountUI() {
        val isLoggedIn = prefs.getBoolean(KEY_IS_LOGGED_IN, false)
        val userName = prefs.getString(KEY_USER_NAME, "Guest User") ?: "Guest User"
        val userEmail = prefs.getString(KEY_USER_EMAIL, "Not signed in") ?: "Not signed in"
        val isPremium = prefs.getBoolean(KEY_IS_PREMIUM, false)
        
        findViewById<TextView>(R.id.account_name).text = userName
        findViewById<TextView>(R.id.account_email).text = if (isLoggedIn) userEmail else "Not signed in"
        findViewById<TextView>(R.id.plan_name).text = if (isPremium) "Premium" else "Free"
        
        // Show/hide login button based on login state
        findViewById<Button>(R.id.btn_login).visibility = if (isLoggedIn) View.GONE else View.VISIBLE
        findViewById<LinearLayout>(R.id.btn_logout).visibility = if (isLoggedIn) View.VISIBLE else View.GONE
        
        // Update login button text
        if (!isLoggedIn) {
            findViewById<Button>(R.id.btn_login).text = "Sign In / Sign Up"
        }
    }
    
    private fun showLoginDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_login, null)
        val emailInput = dialogView.findViewById<EditText>(R.id.input_email)
        val passwordInput = dialogView.findViewById<EditText>(R.id.input_password)
        
        AlertDialog.Builder(this, R.style.Theme_DeltaVoice_Dialog)
            .setTitle("Sign in")
            .setView(dialogView)
            .setPositiveButton("Sign in") { _, _ ->
                val email = emailInput.text.toString()
                val password = passwordInput.text.toString()
                
                if (email.isNotEmpty() && password.isNotEmpty()) {
                    // Simulate login (in real app, connect to backend)
                    prefs.edit()
                        .putBoolean(KEY_IS_LOGGED_IN, true)
                        .putString(KEY_USER_EMAIL, email)
                        .putString(KEY_USER_NAME, email.substringBefore("@").replaceFirstChar { it.uppercase() })
                        .apply()
                    
                    Toast.makeText(this, "Signed in successfully!", Toast.LENGTH_SHORT).show()
                    updateAccountUI()
                } else {
                    Toast.makeText(this, "Please enter email and password", Toast.LENGTH_SHORT).show()
                }
            }
            .setNeutralButton("Sign up") { _, _ ->
                val email = emailInput.text.toString()
                val password = passwordInput.text.toString()
                
                if (email.isNotEmpty() && password.length >= 6) {
                    // Simulate sign up
                    prefs.edit()
                        .putBoolean(KEY_IS_LOGGED_IN, true)
                        .putString(KEY_USER_EMAIL, email)
                        .putString(KEY_USER_NAME, email.substringBefore("@").replaceFirstChar { it.uppercase() })
                        .apply()
                    
                    Toast.makeText(this, "Account created successfully!", Toast.LENGTH_SHORT).show()
                    updateAccountUI()
                } else {
                    Toast.makeText(this, "Please enter valid email and password (min 6 chars)", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun showEditProfileDialog() {
        val currentName = prefs.getString(KEY_USER_NAME, "Guest User") ?: "Guest User"
        val editText = EditText(this).apply {
            setText(currentName)
            hint = "Enter your name"
            setPadding(48, 32, 48, 32)
            setTextColor(resources.getColor(R.color.text_primary, null))
            setHintTextColor(resources.getColor(R.color.text_secondary, null))
        }
        
        AlertDialog.Builder(this, R.style.Theme_DeltaVoice_Dialog)
            .setTitle("Edit profile")
            .setView(editText)
            .setPositiveButton("Save") { _, _ ->
                val newName = editText.text.toString()
                if (newName.isNotEmpty()) {
                    prefs.edit().putString(KEY_USER_NAME, newName).apply()
                    Toast.makeText(this, "Profile updated", Toast.LENGTH_SHORT).show()
                    updateAccountUI()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun showUpgradeDialog() {
        AlertDialog.Builder(this, R.style.Theme_DeltaVoice_Dialog)
            .setTitle("Upgrade to Premium")
            .setMessage("Premium features include:\n\n" +
                "• Unlimited voice translations\n" +
                "• Unlimited video translations\n" +
                "• Unlimited AI chat messages\n" +
                "• Premium themes\n" +
                "• Priority support\n\n" +
                "Price: \$4.99/month")
            .setPositiveButton("Subscribe") { _, _ ->
                // Simulate subscription (in real app, integrate payment)
                prefs.edit().putBoolean(KEY_IS_PREMIUM, true).apply()
                Toast.makeText(this, "Welcome to Premium!", Toast.LENGTH_SHORT).show()
                updateAccountUI()
            }
            .setNegativeButton("Maybe later", null)
            .show()
    }
    
    private fun showLogoutDialog() {
        AlertDialog.Builder(this, R.style.Theme_DeltaVoice_Dialog)
            .setTitle("Sign out")
            .setMessage("Are you sure you want to sign out?")
            .setPositiveButton("Sign out") { _, _ ->
                prefs.edit()
                    .putBoolean(KEY_IS_LOGGED_IN, false)
                    .putString(KEY_USER_NAME, "Guest User")
                    .putString(KEY_USER_EMAIL, "")
                    .apply()
                
                Toast.makeText(this, "Signed out successfully", Toast.LENGTH_SHORT).show()
                updateAccountUI()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun showDeleteAccountDialog() {
        AlertDialog.Builder(this, R.style.Theme_DeltaVoice_Dialog)
            .setTitle("Delete account")
            .setMessage("Are you sure you want to delete your account? This action cannot be undone and all your data will be lost.")
            .setPositiveButton("Delete") { _, _ ->
                // Clear all user data
                prefs.edit().clear().apply()
                
                Toast.makeText(this, "Account deleted", Toast.LENGTH_SHORT).show()
                updateAccountUI()
                finish()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
