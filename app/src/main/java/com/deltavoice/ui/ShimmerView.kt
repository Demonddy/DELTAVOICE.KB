package com.deltavoice.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator

/**
 * Professional shimmer effect view for loading states.
 * Use startShimmer() when loading and stopShimmer() when done.
 */
class ShimmerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var baseColor: Int = Color.parseColor("#1A1F2E")
        set(value) {
            field = value
            basePaint.color = value
            invalidate()
        }

    var cornerRadius: Float = 0f
        set(value) {
            field = value
            invalidate()
        }

    private val basePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = baseColor
        style = Paint.Style.FILL
    }

    private val shimmerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = createShader()
    }

    private var shimmerOffset = 0f
    private var animator: ValueAnimator? = null

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        basePaint.color = baseColor
        shimmerPaint.shader = createShader()
    }

    private fun createShader(): LinearGradient {
        val w = width.toFloat().coerceAtLeast(1f)
        val shimmerWidth = w * 0.5f
        val startX = -shimmerWidth + shimmerOffset * w * 2f
        return LinearGradient(
            startX, 0f, startX + shimmerWidth, 0f,
            intArrayOf(
                Color.TRANSPARENT,
                Color.argb(60, 255, 255, 255),
                Color.argb(100, 255, 255, 255),
                Color.argb(60, 255, 255, 255),
                Color.TRANSPARENT
            ),
            floatArrayOf(0f, 0.3f, 0.5f, 0.7f, 1f),
            Shader.TileMode.CLAMP
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val rect = RectF(0f, 0f, width.toFloat(), height.toFloat())
        if (cornerRadius > 0) {
            canvas.drawRoundRect(rect, cornerRadius, cornerRadius, basePaint)
            canvas.drawRoundRect(rect, cornerRadius, cornerRadius, shimmerPaint)
        } else {
            canvas.drawRect(rect, basePaint)
            canvas.drawRect(rect, shimmerPaint)
        }
    }

    /**
     * Start the shimmer animation. Call when entering loading state.
     */
    fun startShimmer() {
        visibility = VISIBLE
        if (animator?.isRunning == true) return
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1200
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener { animation ->
                shimmerOffset = animation.animatedValue as Float
                shimmerPaint.shader = createShader()
                invalidate()
            }
            start()
        }
    }

    /**
     * Stop the shimmer animation. Call when loading completes.
     */
    fun stopShimmer() {
        animator?.cancel()
        animator = null
        visibility = GONE
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stopShimmer()
    }
}
