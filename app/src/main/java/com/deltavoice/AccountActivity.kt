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
                Toast.makeText(this, getString(R.string.password_change_coming_soon), Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, getString(R.string.please_sign_in_first), Toast.LENGTH_SHORT).show()
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
        val userName = prefs.getString(KEY_USER_NAME, getString(R.string.guest_user)) ?: getString(R.string.guest_user)
        val userEmail = prefs.getString(KEY_USER_EMAIL, getString(R.string.not_signed_in)) ?: getString(R.string.not_signed_in)
        val isPremium = prefs.getBoolean(KEY_IS_PREMIUM, false)
        
        findViewById<TextView>(R.id.account_name).text = userName
        findViewById<TextView>(R.id.account_email).text = if (isLoggedIn) userEmail else getString(R.string.not_signed_in)
        findViewById<TextView>(R.id.plan_name).text = if (isPremium) getString(R.string.premium) else getString(R.string.free)
        
        // Show/hide login button based on login state
        findViewById<Button>(R.id.btn_login).visibility = if (isLoggedIn) View.GONE else View.VISIBLE
        findViewById<LinearLayout>(R.id.btn_logout).visibility = if (isLoggedIn) View.VISIBLE else View.GONE
        
        // Update login button text
        if (!isLoggedIn) {
            findViewById<Button>(R.id.btn_login).text = getString(R.string.sign_in_sign_up)
        }
    }
    
    private fun showLoginDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_login, null)
        val emailInput = dialogView.findViewById<EditText>(R.id.input_email)
        val passwordInput = dialogView.findViewById<EditText>(R.id.input_password)
        
        AlertDialog.Builder(this, R.style.Theme_DeltaVoice_Dialog)
            .setTitle(getString(R.string.sign_in))
            .setView(dialogView)
            .setPositiveButton(getString(R.string.sign_in)) { _, _ ->
                val email = emailInput.text.toString()
                val password = passwordInput.text.toString()
                
                if (email.isNotEmpty() && password.isNotEmpty()) {
                    // Simulate login (in real app, connect to backend)
                    prefs.edit()
                        .putBoolean(KEY_IS_LOGGED_IN, true)
                        .putString(KEY_USER_EMAIL, email)
                        .putString(KEY_USER_NAME, email.substringBefore("@").replaceFirstChar { it.uppercase() })
                        .apply()
                    
                    Toast.makeText(this, getString(R.string.signed_in_success), Toast.LENGTH_SHORT).show()
                    updateAccountUI()
                } else {
                    Toast.makeText(this, getString(R.string.enter_email_password), Toast.LENGTH_SHORT).show()
                }
            }
            .setNeutralButton(getString(R.string.sign_up)) { _, _ ->
                val email = emailInput.text.toString()
                val password = passwordInput.text.toString()
                
                if (email.isNotEmpty() && password.length >= 6) {
                    // Simulate sign up
                    prefs.edit()
                        .putBoolean(KEY_IS_LOGGED_IN, true)
                        .putString(KEY_USER_EMAIL, email)
                        .putString(KEY_USER_NAME, email.substringBefore("@").replaceFirstChar { it.uppercase() })
                        .apply()
                    
                    Toast.makeText(this, getString(R.string.account_created), Toast.LENGTH_SHORT).show()
                    updateAccountUI()
                } else {
                    Toast.makeText(this, getString(R.string.enter_valid_credentials), Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }
    
    private fun showEditProfileDialog() {
        val currentName = prefs.getString(KEY_USER_NAME, getString(R.string.guest_user)) ?: getString(R.string.guest_user)
        val editText = EditText(this).apply {
            setText(currentName)
            hint = getString(R.string.hint_enter_your_name)
            setPadding(48, 32, 48, 32)
            setTextColor(resources.getColor(R.color.text_primary, null))
            setHintTextColor(resources.getColor(R.color.text_secondary, null))
        }
        
        AlertDialog.Builder(this, R.style.Theme_DeltaVoice_Dialog)
            .setTitle(getString(R.string.edit_profile_title))
            .setView(editText)
            .setPositiveButton(getString(R.string.save)) { _, _ ->
                val newName = editText.text.toString()
                if (newName.isNotEmpty()) {
                    prefs.edit().putString(KEY_USER_NAME, newName).apply()
                    Toast.makeText(this, getString(R.string.profile_updated), Toast.LENGTH_SHORT).show()
                    updateAccountUI()
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }
    
    private fun showUpgradeDialog() {
        AlertDialog.Builder(this, R.style.Theme_DeltaVoice_Dialog)
            .setTitle(getString(R.string.upgrade_premium_title))
            .setMessage(getString(R.string.premium_features) + "\n\n" + getString(R.string.upgrade_price_hint))
            .setPositiveButton(getString(R.string.subscribe)) { _, _ ->
                // Simulate subscription (in real app, integrate payment)
                prefs.edit().putBoolean(KEY_IS_PREMIUM, true).apply()
                Toast.makeText(this, getString(R.string.welcome_to_premium), Toast.LENGTH_SHORT).show()
                updateAccountUI()
            }
            .setNegativeButton(getString(R.string.maybe_later), null)
            .show()
    }
    
    private fun showLogoutDialog() {
        AlertDialog.Builder(this, R.style.Theme_DeltaVoice_Dialog)
            .setTitle(getString(R.string.sign_out_title))
            .setMessage(getString(R.string.sign_out_confirm))
            .setPositiveButton(getString(R.string.sign_out)) { _, _ ->
                prefs.edit()
                    .putBoolean(KEY_IS_LOGGED_IN, false)
                    .putString(KEY_USER_NAME, getString(R.string.guest_user))
                    .putString(KEY_USER_EMAIL, "")
                    .apply()
                
                Toast.makeText(this, getString(R.string.signed_out_success), Toast.LENGTH_SHORT).show()
                updateAccountUI()
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }
    
    private fun showDeleteAccountDialog() {
        AlertDialog.Builder(this, R.style.Theme_DeltaVoice_Dialog)
            .setTitle(getString(R.string.delete_account_title))
            .setMessage(getString(R.string.delete_account_confirm))
            .setPositiveButton(getString(R.string.delete)) { _, _ ->
                // Clear all user data
                prefs.edit().clear().apply()
                
                Toast.makeText(this, getString(R.string.account_deleted), Toast.LENGTH_SHORT).show()
                updateAccountUI()
                finish()
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }
}
