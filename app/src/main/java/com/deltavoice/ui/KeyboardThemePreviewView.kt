package com.deltavoice.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import com.deltavoice.theme.KeyboardThemePalette
import kotlin.math.min

/**
 * Simplified mini-keyboard strip for theme preview (matches [KeyboardThemePalette] colors).
 */
class KeyboardThemePreviewView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var palette: KeyboardThemePalette? = null
        set(value) {
            field = value
            invalidate()
        }

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val panelPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val keyPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val keyBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
    }

    private val keyRect = RectF()
    private val row1 = listOf("Q", "W", "E", "R", "T", "Y")
    private val row2 = listOf("A", "S", "D", "F", "G")
    private val row3 = listOf("⇧", "Z", "X", "C", "⌫")

    override fun onDraw(canvas: Canvas) {
        val pal = palette ?: return
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        bgPaint.color = pal.background
        canvas.drawRect(0f, 0f, w, h, bgPaint)

        val pad = min(w, h) * 0.04f
        val panelTop = pad
        val panelH = h * 0.28f
        panelPaint.color = applyAlpha(pal.surface, 0xE0)
        keyBorderPaint.color = applyAlpha(pal.keyText, 0x28)
        val r = pad * 0.9f
        canvas.drawRoundRect(pad, panelTop, w - pad, panelTop + panelH, r, r, panelPaint)
        canvas.drawRoundRect(pad, panelTop, w - pad, panelTop + panelH, r, r, keyBorderPaint)

        val keyAreaTop = panelTop + panelH + pad * 0.8f
        val keyAreaH = h - keyAreaTop - pad
        val rows = 3
        val keyH = keyAreaH / rows * 0.82f
        val gapY = keyAreaH / rows * 0.18f

        labelPaint.color = pal.keyText
        labelPaint.textSize = keyH * 0.38f

        var y = keyAreaTop
        drawKeyRow(canvas, pal, pad, w, y, keyH, row1)
        y += keyH + gapY
        drawKeyRow(canvas, pal, pad, w, y, keyH, row2)
        y += keyH + gapY
        drawKeyRow(canvas, pal, pad, w, y, keyH, row3)
    }

    private fun drawKeyRow(
        canvas: Canvas,
        pal: KeyboardThemePalette,
        pad: Float,
        w: Float,
        y: Float,
        keyH: Float,
        labels: List<String>
    ) {
        val n = labels.size
        val innerPad = pad * 0.5f
        val totalW = w - innerPad * 2
        val gapX = totalW * 0.015f
        val keyW = (totalW - gapX * (n - 1)) / n
        var x = innerPad
        for (label in labels) {
            keyRect.set(x, y, x + keyW, y + keyH)
            keyPaint.color = applyAlpha(pal.keyText, 0x18)
            val kr = keyH * 0.18f
            canvas.drawRoundRect(keyRect, kr, kr, keyPaint)
            keyBorderPaint.color = applyAlpha(pal.keyText, 0x22)
            canvas.drawRoundRect(keyRect, kr, kr, keyBorderPaint)
            val cx = x + keyW / 2f
            val cy = y + keyH / 2f - (labelPaint.descent() + labelPaint.ascent()) / 2f
            val useAccent = label == "⇧" || label == "⌫"
            labelPaint.color = if (useAccent) pal.accent else pal.keyText
            canvas.drawText(label, cx, cy, labelPaint)
            x += keyW + gapX
        }
    }

    private fun applyAlpha(color: Int, alpha: Int): Int {
        return (color and 0x00FFFFFF) or ((alpha.coerceIn(0, 255)) shl 24)
    }
}
