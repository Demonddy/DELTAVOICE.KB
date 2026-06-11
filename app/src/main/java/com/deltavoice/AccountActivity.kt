package com.deltavoice

import android.content.Context
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
import com.deltavoice.auth.AuthManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * User account management backed by Supabase Auth (JWT sessions).
 */
class AccountActivity : AppCompatActivity() {

    private val uiScope = CoroutineScope(Dispatchers.Main)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_account)

        setupUI()
        uiScope.launch {
            AuthManager.refreshSessionIfNeeded()
            updateAccountUI()
        }
    }

    private fun setupUI() {
        findViewById<ImageButton>(R.id.btn_back).setOnClickListener { finish() }

        findViewById<Button>(R.id.btn_login).setOnClickListener { showLoginDialog() }

        findViewById<Button>(R.id.btn_upgrade).setOnClickListener { showUpgradeDialog() }

        findViewById<LinearLayout>(R.id.btn_edit_profile).setOnClickListener {
            showEditProfileDialog()
        }

        findViewById<LinearLayout>(R.id.btn_change_password).setOnClickListener {
            if (AuthManager.isLoggedIn()) {
                Toast.makeText(this, getString(R.string.password_change_coming_soon), Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, getString(R.string.please_sign_in_first), Toast.LENGTH_SHORT).show()
            }
        }

        findViewById<LinearLayout>(R.id.btn_logout).setOnClickListener { showLogoutDialog() }

        findViewById<LinearLayout>(R.id.btn_delete_account).setOnClickListener {
            showDeleteAccountDialog()
        }
    }

    private fun updateAccountUI() {
        val isLoggedIn = AuthManager.isLoggedIn()
        val userName = AuthManager.userName()
        val userEmail = AuthManager.userEmail().orEmpty()
        val isPremium = AuthManager.isPremium()

        findViewById<TextView>(R.id.account_name).text = userName
        findViewById<TextView>(R.id.account_email).text =
            if (isLoggedIn && userEmail.isNotBlank()) userEmail else getString(R.string.not_signed_in)
        findViewById<TextView>(R.id.plan_name).text =
            if (isPremium) getString(R.string.premium) else getString(R.string.free)

        findViewById<Button>(R.id.btn_login).visibility = if (isLoggedIn) View.GONE else View.VISIBLE
        findViewById<LinearLayout>(R.id.btn_logout).visibility = if (isLoggedIn) View.VISIBLE else View.GONE

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
                val email = emailInput.text.toString().trim()
                val password = passwordInput.text.toString()
                if (email.isEmpty() || password.isEmpty()) {
                    Toast.makeText(this, getString(R.string.enter_email_password), Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                uiScope.launch {
                    val result = withContext(Dispatchers.IO) { AuthManager.signIn(email, password) }
                    if (result.isSuccess) {
                        Toast.makeText(this@AccountActivity, getString(R.string.signed_in_success), Toast.LENGTH_SHORT).show()
                        updateAccountUI()
                    } else {
                        Toast.makeText(
                            this@AccountActivity,
                            result.exceptionOrNull()?.message ?: getString(R.string.enter_valid_credentials),
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
            .setNeutralButton(getString(R.string.sign_up)) { _, _ ->
                val email = emailInput.text.toString().trim()
                val password = passwordInput.text.toString()
                if (email.isEmpty() || password.length < 6) {
                    Toast.makeText(this, getString(R.string.enter_valid_credentials), Toast.LENGTH_SHORT).show()
                    return@setNeutralButton
                }
                uiScope.launch {
                    val result = withContext(Dispatchers.IO) { AuthManager.signUp(email, password) }
                    if (result.isSuccess) {
                        Toast.makeText(this@AccountActivity, getString(R.string.account_created), Toast.LENGTH_SHORT).show()
                        updateAccountUI()
                    } else {
                        Toast.makeText(
                            this@AccountActivity,
                            result.exceptionOrNull()?.message ?: getString(R.string.enter_valid_credentials),
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun showEditProfileDialog() {
        val currentName = AuthManager.userName()
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
                    getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                        .edit()
                        .putString(KEY_USER_NAME, newName)
                        .apply()
                    Toast.makeText(this, getString(R.string.profile_updated), Toast.LENGTH_SHORT).show()
                    updateAccountUI()
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun showUpgradeDialog() {
        if (!AuthManager.isLoggedIn()) {
            Toast.makeText(this, getString(R.string.please_sign_in_first), Toast.LENGTH_SHORT).show()
            return
        }
        AlertDialog.Builder(this, R.style.Theme_DeltaVoice_Dialog)
            .setTitle(getString(R.string.upgrade_premium_title))
            .setMessage(getString(R.string.premium_features) + "\n\n" + getString(R.string.upgrade_price_hint))
            .setPositiveButton(getString(R.string.subscribe)) { _, _ ->
                uiScope.launch {
                    val result = withContext(Dispatchers.IO) { AuthManager.syncSubscriptionStatus() }
                    if (result.isSuccess && result.getOrDefault(false)) {
                        Toast.makeText(this@AccountActivity, getString(R.string.welcome_to_premium), Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(
                            this@AccountActivity,
                            getString(R.string.upgrade_premium_unlock_theme),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    updateAccountUI()
                }
            }
            .setNegativeButton(getString(R.string.maybe_later), null)
            .show()
    }

    private fun showLogoutDialog() {
        AlertDialog.Builder(this, R.style.Theme_DeltaVoice_Dialog)
            .setTitle(getString(R.string.sign_out_title))
            .setMessage(getString(R.string.sign_out_confirm))
            .setPositiveButton(getString(R.string.sign_out)) { _, _ ->
                uiScope.launch {
                    withContext(Dispatchers.IO) { AuthManager.signOut() }
                    Toast.makeText(this@AccountActivity, getString(R.string.signed_out_success), Toast.LENGTH_SHORT).show()
                    updateAccountUI()
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun showDeleteAccountDialog() {
        AlertDialog.Builder(this, R.style.Theme_DeltaVoice_Dialog)
            .setTitle(getString(R.string.delete_account_title))
            .setMessage(getString(R.string.delete_account_confirm))
            .setPositiveButton(getString(R.string.delete)) { _, _ ->
                uiScope.launch {
                    withContext(Dispatchers.IO) { AuthManager.signOut() }
                    getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().clear().apply()
                    Toast.makeText(this@AccountActivity, getString(R.string.account_deleted), Toast.LENGTH_SHORT).show()
                    updateAccountUI()
                    finish()
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    companion object {
        private const val PREFS_NAME = "deltavoice_prefs"
        private const val KEY_USER_NAME = "user_name"
    }
}
