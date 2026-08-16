package com.deltavoice.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.SweepGradient
import android.util.AttributeSet
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import com.deltavoice.R
import kotlin.math.max

/**
 * Panel container with a spinning gradient border. Uses normal FrameLayout padding/measure
 * so scrollable children (emoji grid) stay bounded and can scroll.
 */
class SpinningGradientBorderLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val density = resources.displayMetrics.density

    var borderWidthPx: Float = 2f * density
        set(value) {
            field = value
            borderPaint.strokeWidth = value
            applyContentInsets()
            requestLayout()
            invalidate()
        }

    var cornerRadiusPx: Float = 28f * density
        set(value) {
            field = value
            invalidate()
        }

    var contentPaddingPx: Float = 6f * density
        set(value) {
            field = value
            applyContentInsets()
            requestLayout()
            invalidate()
        }

    private var innerColor: Int = 0xDD121212.toInt()
    private var gradientColors: IntArray = seamlessColors(
        0xFFA78BFA.toInt(),
        0xFFC084FC.toInt(),
        0xFF8B5CF6.toInt(),
        0xFFC084FC.toInt()
    )

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }

    private val outerRect = RectF()
    private val innerRect = RectF()
    private val gradientMatrix = Matrix()
    private var sweepGradient: SweepGradient? = null
    private var gradientPositions: FloatArray? = null
    private var rotationDegrees = 0f
    private var borderAnimator: ValueAnimator? = null

    private val innerInsetPx: Int
        get() = (borderWidthPx + contentPaddingPx).toInt()

    private val innerCornerRadiusPx: Float
        get() = max(0f, cornerRadiusPx - borderWidthPx - contentPaddingPx)

    init {
        setWillNotDraw(false)
        clipChildren = true
        clipToPadding = true

        context.theme.obtainStyledAttributes(attrs, R.styleable.SpinningGradientBorderLayout, defStyleAttr, 0).apply {
            try {
                borderWidthPx = getDimension(R.styleable.SpinningGradientBorderLayout_borderWidth, borderWidthPx)
                cornerRadiusPx = getDimension(R.styleable.SpinningGradientBorderLayout_cornerRadius, cornerRadiusPx)
                contentPaddingPx = getDimension(R.styleable.SpinningGradientBorderLayout_contentPadding, contentPaddingPx)
            } finally {
                recycle()
            }
        }
        borderPaint.strokeWidth = borderWidthPx
        applyContentInsets()
    }

    fun setInnerColor(color: Int) {
        innerColor = color
        invalidate()
    }

    fun setGradientColors(vararg colors: Int) {
        gradientColors = if (colors.isNotEmpty()) seamlessColors(*colors) else gradientColors
        sweepGradient = null
        gradientPositions = null
        invalidate()
    }

    fun startBorderAnimation() {
        if (borderAnimator?.isRunning == true) return
        borderAnimator = ValueAnimator.ofFloat(0f, 360f).apply {
            duration = 3500L
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener { animation ->
                rotationDegrees = animation.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    fun stopBorderAnimation() {
        borderAnimator?.cancel()
        borderAnimator = null
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        sweepGradient = null
        gradientPositions = null
    }

    override fun onDraw(canvas: Canvas) {
        if (width <= 0 || height <= 0) return

        val halfBorder = borderWidthPx / 2f
        outerRect.set(halfBorder, halfBorder, width - halfBorder, height - halfBorder)

        val innerLeft = borderWidthPx + contentPaddingPx
        val innerTop = borderWidthPx + contentPaddingPx
        val innerRight = width - borderWidthPx - contentPaddingPx
        val innerBottom = height - borderWidthPx - contentPaddingPx
        innerRect.set(innerLeft, innerTop, innerRight, innerBottom)

        fillPaint.color = innerColor
        canvas.drawRoundRect(innerRect, innerCornerRadiusPx, innerCornerRadiusPx, fillPaint)

        val gradient = ensureSweepGradient()
        gradientMatrix.setRotate(rotationDegrees, width / 2f, height / 2f)
        gradient.setLocalMatrix(gradientMatrix)
        borderPaint.shader = gradient
        canvas.drawRoundRect(outerRect, cornerRadiusPx, cornerRadiusPx, borderPaint)

        super.onDraw(canvas)
    }

    override fun onDetachedFromWindow() {
        stopBorderAnimation()
        super.onDetachedFromWindow()
    }

    private fun applyContentInsets() {
        val inset = innerInsetPx
        setPadding(inset, inset, inset, inset)
    }

    private fun ensureSweepGradient(): SweepGradient {
        val existing = sweepGradient
        if (existing != null) return existing
        val positions = gradientPositions ?: buildGradientPositions(gradientColors.size).also {
            gradientPositions = it
        }
        val created = SweepGradient(width / 2f, height / 2f, gradientColors, positions)
        sweepGradient = created
        return created
    }

    companion object {
        private fun seamlessColors(vararg colors: Int): IntArray {
            if (colors.isEmpty()) return intArrayOf()
            if (colors.size == 1) return intArrayOf(colors[0], colors[0])
            return intArrayOf(*colors, colors[0])
        }

        private fun buildGradientPositions(count: Int): FloatArray {
            if (count <= 1) return floatArrayOf(0f, 1f)
            return FloatArray(count) { index -> index.toFloat() / (count - 1).toFloat() }
        }
    }
}
