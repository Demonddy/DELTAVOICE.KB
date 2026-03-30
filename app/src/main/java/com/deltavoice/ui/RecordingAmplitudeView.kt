package com.deltavoice.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.deltavoice.R
import kotlin.math.max
import kotlin.math.sin

/**
 * Messenger-style vertical bars for live recording feedback.
 * Real levels via [pushNormalized]; API 21–25 uses [tickSynthetic] instead.
 */
class RecordingAmplitudeView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val barCount = 28
    private val levels = FloatArray(barCount) { 0.08f }

    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = ContextCompat.getColor(context, R.color.primary)
    }

    private val gapPx: Float
        get() = 2f * resources.displayMetrics.density

    fun pushNormalized(n: Float) {
        val v = n.coerceIn(0f, 1f)
        for (i in 0 until barCount - 1) {
            levels[i] = levels[i + 1]
        }
        levels[barCount - 1] = max(0.08f, v)
        invalidate()
    }

    /** Animated placeholder when [android.media.MediaRecorder.getMaxAmplitude] is unavailable. */
    fun tickSynthetic(phase: Float) {
        for (i in levels.indices) {
            val t = (0.35f + 0.55f * sin((phase + i * 0.35).toDouble()).toFloat()).coerceIn(0.08f, 1f)
            levels[i] = t
        }
        invalidate()
    }

    fun reset() {
        for (i in levels.indices) levels[i] = 0.08f
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val gap = gapPx
        val totalGap = gap * (barCount - 1).coerceAtLeast(0)
        val barW = ((w - totalGap) / barCount).coerceAtLeast(2f)
        var x = 0f
        val bottom = h - paddingBottom
        val topMin = paddingTop

        for (i in 0 until barCount) {
            val frac = levels[i].coerceIn(0.08f, 1f)
            val barH = (bottom - topMin) * frac
            val top = bottom - barH
            canvas.drawRoundRect(x, top, x + barW, bottom, barW / 2f, barW / 2f, barPaint)
            x += barW + gap
        }
    }
}
