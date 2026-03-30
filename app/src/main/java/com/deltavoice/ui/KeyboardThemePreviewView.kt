package com.deltavoice.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Color
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import androidx.core.graphics.ColorUtils
import com.deltavoice.theme.KeyboardThemePalette
import kotlin.math.min

/**
 * Theme preview that mirrors the IME: gradient root, rounded top toolbar with circular accent
 * buttons, rounded keyboard panel, and five rows (numbers, QWERTY, ASDF, ZXCV, bottom row).
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

    private val rootGradientPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val panelPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val panelStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val toolbarFillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val toolbarStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val accentCirclePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val iconDotPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val keyFillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val keyStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }

    private val keyRect = RectF()
    private val tmpRect = RectF()

    private val toolbarGlyphHints = listOf("⋮", "▶", "⌂", "AI", "KB", "♪", "▭")

    override fun onDraw(canvas: Canvas) {
        val pal = palette ?: return
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val pad = min(w, h) * 0.03f
        val c1 = ColorUtils.blendARGB(pal.background, pal.accent, 0.14f)
        rootGradientPaint.shader = LinearGradient(
            0f, 0f, w, h,
            intArrayOf(c1, pal.background),
            floatArrayOf(0f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, w, h, rootGradientPaint)
        rootGradientPaint.shader = null

        val innerLeft = pad
        val innerRight = w - pad
        val innerW = innerRight - innerLeft

        val toolbarH = h * 0.22f
        val gapSmall = h * 0.012f
        val toolbarTop = pad
        val toolbarR = toolbarH * 0.45f

        toolbarFillPaint.color = ColorUtils.setAlphaComponent(pal.surface, 0xCC)
        toolbarStrokePaint.color = ColorUtils.setAlphaComponent(pal.keyText, 0x28)
        toolbarStrokePaint.strokeWidth = maxOf(1f, resources.displayMetrics.density)
        tmpRect.set(innerLeft, toolbarTop, innerRight, toolbarTop + toolbarH)
        canvas.drawRoundRect(tmpRect, toolbarR, toolbarR, toolbarFillPaint)
        canvas.drawRoundRect(tmpRect, toolbarR, toolbarR, toolbarStrokePaint)

        val nTools = 7
        val toolGap = innerW * 0.02f
        val circleD = min((innerW - toolGap * (nTools + 1)) / nTools, toolbarH * 0.72f)
        var cx = innerLeft + toolGap + circleD / 2f
        val cy = toolbarTop + toolbarH / 2f
        accentCirclePaint.color = pal.iconTint
        iconDotPaint.color = ColorUtils.setAlphaComponent(0xFFFFFFFF.toInt(), 0xEE)
        labelPaint.color = iconDotPaint.color
        labelPaint.textSize = circleD * 0.28f
        for (i in 0 until nTools) {
            canvas.drawCircle(cx, cy, circleD / 2f, accentCirclePaint)
            val hint = toolbarGlyphHints[i]
            if (hint.length <= 2) {
                val ty = cy - (labelPaint.descent() + labelPaint.ascent()) / 2f
                canvas.drawText(hint, cx, ty, labelPaint)
            } else {
                canvas.drawCircle(cx, cy, circleD * 0.12f, iconDotPaint)
            }
            cx += circleD + toolGap
        }

        val panelTop = toolbarTop + toolbarH + gapSmall
        val panelBottom = h - pad
        val panelH = panelBottom - panelTop
        val panelCorner = panelH * 0.08f
        panelPaint.color = ColorUtils.setAlphaComponent(pal.surface, 0xE6)
        panelStrokePaint.color = ColorUtils.setAlphaComponent(pal.keyText, 0x22)
        panelStrokePaint.strokeWidth = maxOf(1f, resources.displayMetrics.density)
        tmpRect.set(innerLeft, panelTop, innerRight, panelBottom)
        canvas.drawRoundRect(tmpRect, panelCorner, panelCorner, panelPaint)
        canvas.drawRoundRect(tmpRect, panelCorner, panelCorner, panelStrokePaint)

        val keyPadX = innerW * 0.02f
        val keyAreaLeft = innerLeft + keyPadX
        val keyAreaRight = innerRight - keyPadX
        val keyAreaW = keyAreaRight - keyAreaLeft

        val rowGap = panelH * 0.02f
        val rowCount = 5
        val rowH = (panelH - rowGap * (rowCount + 1)) / rowCount

        var y = panelTop + rowGap

        val gapX = keyAreaW * 0.012f
        val numbers = (1..9).map { it.toString() } + "0"
        drawEqualRow(canvas, pal, keyAreaLeft, keyAreaRight, y, rowH, gapX, numbers)
        y += rowH + rowGap

        val qwerty = listOf("q", "w", "e", "r", "t", "y", "u", "i", "o", "p")
        drawEqualRow(canvas, pal, keyAreaLeft, keyAreaRight, y, rowH, gapX, qwerty)
        y += rowH + rowGap

        val asdf = listOf("a", "s", "d", "f", "g", "h", "j", "k", "l")
        val inset = keyAreaW * 0.055f
        drawEqualRow(canvas, pal, keyAreaLeft + inset, keyAreaRight - inset, y, rowH, gapX, asdf)
        y += rowH + rowGap

        val zxcvLabels = listOf("⇧", "z", "x", "c", "v", "b", "n", "m", "⌫")
        val zxcvWeights = listOf(1.5f) + List(7) { 1f } + listOf(1.5f)
        drawWeightedRow(
            canvas, pal, keyAreaLeft, keyAreaRight, y, rowH, gapX, zxcvLabels, zxcvWeights
        ) { label -> label == "⇧" || label == "⌫" }
        y += rowH + rowGap

        // Bottom row preview: !#1, globe, emoji, enter only (space and period omitted in preview).
        val bottomLabels = listOf("!#1", "🌐", "😊", "↵")
        val bottomWeights = listOf(1.2f, 1f, 1f, 1.2f)
        drawWeightedRow(
            canvas, pal, keyAreaLeft, keyAreaRight, y, rowH, gapX, bottomLabels, bottomWeights
        ) { label -> label == "!#1" || label == "🌐" || label == "😊" || label == "↵" }
    }

    private fun drawEqualRow(
        canvas: Canvas,
        pal: KeyboardThemePalette,
        left: Float,
        right: Float,
        y: Float,
        rowH: Float,
        gapX: Float,
        labels: List<String>
    ) {
        val weights = List(labels.size) { 1f }
        drawWeightedRow(canvas, pal, left, right, y, rowH, gapX, labels, weights) { false }
    }

    private fun drawWeightedRow(
        canvas: Canvas,
        pal: KeyboardThemePalette,
        left: Float,
        right: Float,
        y: Float,
        rowH: Float,
        gapX: Float,
        labels: List<String>,
        weights: List<Float>,
        useIconTint: (String) -> Boolean = { false }
    ) {
        require(labels.size == weights.size)
        val n = labels.size
        val totalGaps = gapX * (n - 1).coerceAtLeast(0)
        val totalW = right - left - totalGaps
        val sumW = weights.sum()
        var x = left
        val kr = rowH * 0.16f
        keyStrokePaint.strokeWidth = maxOf(0.8f, resources.displayMetrics.density * 0.5f)
        for (i in labels.indices) {
            val keyW = totalW * (weights[i] / sumW)
            keyRect.set(x, y, x + keyW, y + rowH)
            keyFillPaint.color = Color.argb(0x22, 255, 255, 255)
            keyStrokePaint.color = ColorUtils.setAlphaComponent(pal.keyText, 0x24)
            canvas.drawRoundRect(keyRect, kr, kr, keyFillPaint)
            canvas.drawRoundRect(keyRect, kr, kr, keyStrokePaint)

            val label = labels[i]
            val cx = x + keyW / 2f
            labelPaint.textSize = when {
                label.length > 2 -> rowH * 0.22f
                else -> rowH * 0.38f
            }
            val display = when (label) {
                "🌐" -> "G"
                else -> label
            }
            labelPaint.color = if (useIconTint(label)) pal.iconTint else pal.keyText
            val cy = y + rowH / 2f - (labelPaint.descent() + labelPaint.ascent()) / 2f
            canvas.drawText(display, cx, cy, labelPaint)
            x += keyW + gapX
        }
    }
}
