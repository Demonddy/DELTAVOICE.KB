package com.deltavoice

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.deltavoice.util.NetworkUtils
import com.google.android.material.snackbar.Snackbar
import java.net.URLEncoder

/**
 * Search screen - displays results in-app via WebView.
 */
class SearchActivity : AppCompatActivity() {

    private lateinit var webView: WebView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_search)

        webView = findViewById(R.id.search_webview)
        webView.settings.javaScriptEnabled = true
        webView.webViewClient = WebViewClient() // Keep navigation in-app

        val searchInput = findViewById<EditText>(R.id.search_input)
        searchInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                performSearch(searchInput.text.toString())
                true
            } else false
        }

        searchInput.post {
            if (!NetworkUtils.isConnected(this)) {
                showInternetRequiredSnackbar()
            }
        }

        setupBottomNav()
    }

    private fun setupBottomNav() {
        findViewById<FrameLayout>(R.id.nav_home).setOnClickListener {
            finish()
        }
        findViewById<FrameLayout>(R.id.nav_camera).setOnClickListener {
            startActivity(Intent(this, VideoRecordingActivity::class.java))
        }
        findViewById<FrameLayout>(R.id.nav_add).setOnClickListener {
            showAddMediaBottomSheet()
        }
        findViewById<FrameLayout>(R.id.nav_mic).setOnClickListener {
            startActivity(Intent(this, VoiceConfigActivity::class.java))
        }
        findViewById<FrameLayout>(R.id.nav_search).setOnClickListener {
            // Already on search
        }

        BottomNavHelper.setActiveItem(this, R.id.nav_search)
    }

    private fun showAddMediaBottomSheet() {
        val dialog = com.google.android.material.bottomsheet.BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.bottom_sheet_add_media, null)
        dialog.setContentView(view)

        view.findViewById<android.widget.LinearLayout>(R.id.option_video).setOnClickListener {
            dialog.dismiss()
            startActivity(Intent(this, VideoConfigActivity::class.java))
        }
        view.findViewById<android.widget.LinearLayout>(R.id.option_voice).setOnClickListener {
            dialog.dismiss()
            startActivity(Intent(this, VoiceConfigActivity::class.java))
        }
        dialog.show()
    }

    private fun performSearch(query: String) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            Toast.makeText(this, getString(R.string.type_something_search), Toast.LENGTH_SHORT).show()
            return
        }
        if (!NetworkUtils.isConnected(this)) {
            showInternetRequiredSnackbar()
            return
        }
        val encoded = URLEncoder.encode(trimmed, "UTF-8")
        val searchUrl = "https://www.google.com/search?q=$encoded"
        webView.visibility = View.VISIBLE
        webView.loadUrl(searchUrl)
    }

    private fun showInternetRequiredSnackbar() {
        val snackbar = Snackbar.make(
            findViewById(android.R.id.content),
            getString(R.string.internet_required_search),
            Snackbar.LENGTH_LONG
        ).apply {
            setAction(R.string.internet_required_action_settings) {
                try {
                    startActivity(Intent(android.provider.Settings.ACTION_WIRELESS_SETTINGS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                } catch (_: Exception) {
                    startActivity(Intent(android.provider.Settings.ACTION_SETTINGS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            }
            setActionTextColor(Color.parseColor("#A78BFA"))
        }
        snackbar.view.setBackgroundColor(Color.parseColor("#1F2937"))
        snackbar.show()
    }
}
