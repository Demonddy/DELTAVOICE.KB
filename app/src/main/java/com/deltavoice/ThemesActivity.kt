package com.deltavoice

import android.graphics.Color
import android.os.Bundle
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.deltavoice.debug.DebugSession44
import com.deltavoice.theme.KeyboardThemeStore
import com.deltavoice.ui.KeyboardThemePreviewView

/**
 * Keyboard skin selection: staged preview and explicit Apply; custom RGB accent.
 */
class ThemesActivity : AppCompatActivity() {

    private lateinit var previewView: KeyboardThemePreviewView
    private lateinit var stagedLabel: TextView

    private var stagedThemeId: String = KeyboardThemeStore.THEME_DARK_PURPLE
    private var stagedCustomColor: Int = Color.parseColor("#A78BFA")
    /** `-1` = use theme accent for icons. */
    private var stagedIconColor: Int = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_themes)

        val prefs = KeyboardThemeStore.prefs(this)
        stagedThemeId = prefs.getString(KeyboardThemeStore.KEY_SELECTED_THEME, KeyboardThemeStore.THEME_DARK_PURPLE)
            ?: KeyboardThemeStore.THEME_DARK_PURPLE
        stagedCustomColor = prefs.getInt(KeyboardThemeStore.KEY_CUSTOM_THEME_COLOR, stagedCustomColor)
        stagedIconColor = prefs.getInt(KeyboardThemeStore.KEY_ICON_COLOR, -1)

        previewView = findViewById(R.id.theme_keyboard_preview)
        stagedLabel = findViewById(R.id.theme_staged_label)

        findViewById<ImageButton>(R.id.btn_back).setOnClickListener { finish() }

        findViewById<Button>(R.id.btn_apply_theme).setOnClickListener { applyStagedTheme() }

        val presets = mapOf(
            R.id.theme_dark_purple to KeyboardThemeStore.THEME_DARK_PURPLE,
            R.id.theme_midnight_blue to KeyboardThemeStore.THEME_MIDNIGHT_BLUE,
            R.id.theme_forest_green to KeyboardThemeStore.THEME_FOREST_GREEN,
            R.id.theme_sunset_orange to KeyboardThemeStore.THEME_SUNSET_ORANGE,
            R.id.theme_rose_pink to KeyboardThemeStore.THEME_ROSE_PINK,
            R.id.theme_pure_dark to KeyboardThemeStore.THEME_PURE_DARK
        )
        presets.forEach { (viewId, themeId) ->
            findViewById<LinearLayout>(viewId)?.setOnClickListener {
                stagedThemeId = themeId
                refreshPreview()
            }
        }

        findViewById<LinearLayout>(R.id.theme_galaxy)?.setOnClickListener {
            val isPremium = prefs.getBoolean("is_premium", false)
            if (isPremium) {
                stagedThemeId = KeyboardThemeStore.THEME_GALAXY
                refreshPreview()
            } else {
                Toast.makeText(this, R.string.upgrade_premium_unlock_theme, Toast.LENGTH_SHORT).show()
            }
        }

        findViewById<LinearLayout>(R.id.theme_neon)?.setOnClickListener {
            val isPremium = prefs.getBoolean("is_premium", false)
            if (isPremium) {
                stagedThemeId = KeyboardThemeStore.THEME_NEON
                refreshPreview()
            } else {
                Toast.makeText(this, R.string.upgrade_premium_unlock_theme, Toast.LENGTH_SHORT).show()
            }
        }

        findViewById<LinearLayout>(R.id.theme_custom)?.setOnClickListener { showCustomColorDialog() }

        findViewById<Button>(R.id.btn_theme_icon_color)?.setOnClickListener { showIconColorDialog() }

        refreshPreview()
    }

    private fun refreshPreview() {
        val pal = KeyboardThemeStore.paletteForThemeId(stagedThemeId, stagedCustomColor, stagedIconColor)
        previewView.palette = pal
        stagedLabel.text = getString(R.string.theme_staged_hint) + "\n" + displayNameFor(stagedThemeId)
    }

    private fun displayNameFor(themeId: String): String {
        if (themeId == KeyboardThemeStore.THEME_CUSTOM) {
            return getString(R.string.theme_custom)
        }
        return themeId.replace("_", " ").replaceFirstChar { it.uppercase() }
    }

    private fun applyStagedTheme() {
        KeyboardThemeStore.prefs(this).edit()
            .putString(KeyboardThemeStore.KEY_SELECTED_THEME, stagedThemeId)
            .putInt(KeyboardThemeStore.KEY_CUSTOM_THEME_COLOR, stagedCustomColor)
            .putInt(KeyboardThemeStore.KEY_ICON_COLOR, stagedIconColor)
            .apply()
        // #region agent log
        DebugSession44.log(
            this, "H1", "ThemesActivity.applyStagedTheme",
            "prefs_written_after_apply",
            mapOf(
                "stagedThemeId" to stagedThemeId,
                "stagedCustomColor" to stagedCustomColor.toString(),
                "stagedIconColor" to stagedIconColor.toString()
            )
        )
        // #endregion
        Toast.makeText(this, getString(R.string.theme_changed, displayNameFor(stagedThemeId)), Toast.LENGTH_SHORT).show()
    }

    private fun showCustomColorDialog() {
        val snapshotTheme = stagedThemeId
        val snapshotColor = stagedCustomColor

        val pad = (16 * resources.displayMetrics.density).toInt()
        val dialogView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }

        fun label(text: String) = TextView(this).apply {
            this.text = text
            setTextColor(ContextCompat.getColor(this@ThemesActivity, R.color.text_primary))
        }

        val rBar = SeekBar(this).apply { max = 255 }
        val gBar = SeekBar(this).apply { max = 255 }
        val bBar = SeekBar(this).apply { max = 255 }

        fun readColor(): Int = Color.rgb(rBar.progress, gBar.progress, bBar.progress)

        fun applyFromBars() {
            stagedThemeId = KeyboardThemeStore.THEME_CUSTOM
            stagedCustomColor = readColor()
            refreshPreview()
        }

        rBar.progress = Color.red(stagedCustomColor)
        gBar.progress = Color.green(stagedCustomColor)
        bBar.progress = Color.blue(stagedCustomColor)

        val listener = object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) applyFromBars()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        }
        rBar.setOnSeekBarChangeListener(listener)
        gBar.setOnSeekBarChangeListener(listener)
        bBar.setOnSeekBarChangeListener(listener)

        dialogView.addView(label("R"))
        dialogView.addView(rBar)
        dialogView.addView(label("G"))
        dialogView.addView(gBar)
        dialogView.addView(label("B"))
        dialogView.addView(bBar)

        AlertDialog.Builder(this)
            .setTitle(R.string.theme_color_picker_title)
            .setView(dialogView)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                stagedThemeId = KeyboardThemeStore.THEME_CUSTOM
                stagedCustomColor = readColor()
                refreshPreview()
            }
            .setNegativeButton(android.R.string.cancel) { _, _ ->
                stagedThemeId = snapshotTheme
                stagedCustomColor = snapshotColor
                refreshPreview()
            }
            .show()
    }

    private fun showIconColorDialog() {
        val snapshot = stagedIconColor
        val basePal = KeyboardThemeStore.paletteForThemeId(stagedThemeId, stagedCustomColor, -1)
        val initialRgb = if (stagedIconColor == -1) {
            Triple(Color.red(basePal.iconTint), Color.green(basePal.iconTint), Color.blue(basePal.iconTint))
        } else {
            Triple(Color.red(stagedIconColor), Color.green(stagedIconColor), Color.blue(stagedIconColor))
        }

        val pad = (16 * resources.displayMetrics.density).toInt()
        val dialogView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }

        fun label(text: String) = TextView(this).apply {
            this.text = text
            setTextColor(ContextCompat.getColor(this@ThemesActivity, R.color.text_primary))
        }

        val rBar = SeekBar(this).apply { max = 255 }
        val gBar = SeekBar(this).apply { max = 255 }
        val bBar = SeekBar(this).apply { max = 255 }

        fun readColor(): Int = Color.rgb(rBar.progress, gBar.progress, bBar.progress)

        fun applyFromBars() {
            stagedIconColor = readColor()
            refreshPreview()
        }

        val (ir, ig, ib) = initialRgb
        rBar.progress = ir
        gBar.progress = ig
        bBar.progress = ib

        val listener = object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) applyFromBars()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        }
        rBar.setOnSeekBarChangeListener(listener)
        gBar.setOnSeekBarChangeListener(listener)
        bBar.setOnSeekBarChangeListener(listener)

        dialogView.addView(label("R"))
        dialogView.addView(rBar)
        dialogView.addView(label("G"))
        dialogView.addView(gBar)
        dialogView.addView(label("B"))
        dialogView.addView(bBar)

        AlertDialog.Builder(this)
            .setTitle(R.string.theme_icon_color_picker_title)
            .setView(dialogView)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                stagedIconColor = readColor()
                refreshPreview()
            }
            .setNeutralButton(R.string.theme_icon_color_reset) { _, _ ->
                stagedIconColor = -1
                refreshPreview()
            }
            .setNegativeButton(android.R.string.cancel) { _, _ ->
                stagedIconColor = snapshot
                refreshPreview()
            }
            .show()
    }
}
