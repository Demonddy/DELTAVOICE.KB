package com.deltavoice

import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageButton
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Positions the floating bubble top row: horizontal strip (scaled width) or arc band near screen edges.
 */
object OverlayTopRowPlacer {

    /** Lets touches pass through outside the window; helps multi-overlay touch routing. */
    private const val OVERLAY_FLAGS =
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL

    /** Fraction of screen half-width: bubble center within this band uses full-width horizontal strip (arc mode). */
    private const val CENTER_BAND_FRACTION = 0.18f

    private val BUTTON_IDS = intArrayOf(
        R.id.btn_more,
        R.id.btn_camera,
        R.id.btn_list,
        R.id.btn_text_t,
        R.id.btn_kb_plus,
        R.id.btn_voice,
        R.id.btn_app_grid
    )

    fun isBubbleHorizontallyCentered(bubbleCenterX: Float, screenWidthPx: Int): Boolean {
        val mid = screenWidthPx / 2f
        val band = screenWidthPx * CENTER_BAND_FRACTION
        return abs(bubbleCenterX - mid) <= band
    }

    /**
     * Arc band only when style is arc and bubble is not near horizontal center.
     */
    fun shouldShowArcBand(style: String, bubbleCenterX: Float, screenWidthPx: Int): Boolean {
        if (style != OverlayPrefs.STYLE_ARC) return false
        return !isBubbleHorizontallyCentered(bubbleCenterX, screenWidthPx)
    }

    fun bubbleCenterPx(
        bubbleParams: WindowManager.LayoutParams,
        bubbleSizePx: Int
    ): Pair<Float, Float> {
        val cx = bubbleParams.x + bubbleSizePx / 2f
        val cy = bubbleParams.y + bubbleSizePx / 2f
        return cx to cy
    }

    /**
     * Lays out arc icons in an **outward** sector toward the nearest screen edge (scatter),
     * with uniform angle spacing, [widthScale] on radius, and 48dp touch targets.
     * Glass underlay fills the window ([R.id.arc_band_glass_bg]).
     */
    fun layoutArcBand(
        root: FrameLayout,
        bubbleParams: WindowManager.LayoutParams,
        bubbleSizePx: Int,
        density: Float,
        widthScale: Float,
        screenWidthPx: Int,
        screenHeightPx: Int
    ): ArcWindowGeom {
        val iconPx = (48f * density).toInt().coerceAtLeast(1)
        val half = iconPx / 2f
        val padPx = (10f * density).toInt()
        val marginPx = (10f * density).toInt()

        val (cx, cy) = bubbleCenterPx(bubbleParams, bubbleSizePx)
        val bubbleOnRight = cx >= screenWidthPx / 2f

        // Outward toward physical edge: right sector vs left sector (standard math: 0° = +x right)
        val startDeg: Float
        val endDeg: Float
        val maxAbsDeg: Float
        if (bubbleOnRight) {
            startDeg = -58f
            endDeg = 58f
            maxAbsDeg = 58f
        } else {
            startDeg = 122f
            endDeg = 238f
            maxAbsDeg = 58f
        }

        val baseR = 100f * density * widthScale.coerceIn(0.65f, 1f)
        val rFloor = 56f * density

        val rMaxHorizontal = if (bubbleOnRight) {
            screenWidthPx - marginPx - cx - half
        } else {
            cx - marginPx - half
        }

        // Vertical: right-sector max |sin| = sin(58°); left sector 122°–238° includes 90°, so max |sin| = 1
        val rMaxVertical = if (bubbleOnRight) {
            val sinMaxSweep = sin(maxAbsDeg * PI / 180.0).toFloat()
            if (sinMaxSweep > 0.02f) {
                min(
                    (cy - marginPx) / sinMaxSweep,
                    (screenHeightPx - marginPx - cy) / sinMaxSweep
                )
            } else {
                Float.MAX_VALUE
            }
        } else {
            min(cy - marginPx, screenHeightPx - marginPx - cy)
        }

        val r = min(baseR, min(rMaxHorizontal, rMaxVertical)).coerceAtLeast(rFloor)

        val centers = Array(7) { i ->
            val t = i / 6f
            val deg = startDeg + (endDeg - startDeg) * t
            val rad = deg * PI / 180.0
            val ix = cx + r * cos(rad).toFloat()
            val iy = cy + r * sin(rad).toFloat()
            ix to iy
        }

        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = Float.MIN_VALUE
        var maxY = Float.MIN_VALUE
        for ((ix, iy) in centers) {
            minX = min(minX, ix - half - padPx)
            minY = min(minY, iy - half - padPx)
            maxX = max(maxX, ix + half + padPx)
            maxY = max(maxY, iy + half + padPx)
        }

        var winX = minX.toInt()
        var winY = minY.toInt()
        var winW = (maxX - minX).toInt().coerceAtLeast(iconPx + 2 * padPx)
        var winH = (maxY - minY).toInt().coerceAtLeast(iconPx + 2 * padPx)

        if (winX < 0) {
            winW += winX
            winX = 0
        }
        if (winY < 0) {
            winH += winY
            winY = 0
        }
        if (winX + winW > screenWidthPx) winW = (screenWidthPx - winX).coerceAtLeast(1)
        if (winY + winH > screenHeightPx) winH = (screenHeightPx - winY).coerceAtLeast(1)
        winW = max(iconPx, winW)
        winH = max(iconPx, winH)

        val winXf = winX.toFloat()
        val winYf = winY.toFloat()

        root.findViewById<View>(R.id.arc_band_glass_bg)?.apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            elevation = 0f
        }

        // After clamping winW/winH to the screen, icon centers can still sit past the window edge;
        // without clamping margins, hit-testing misses the buttons entirely.
        val maxLeft = (winW - iconPx).coerceAtLeast(0)
        val maxTop = (winH - iconPx).coerceAtLeast(0)

        for (i in 0 until 7) {
            val (ix, iy) = centers[i]
            val rawLeft = (ix - half - winXf).toInt()
            val rawTop = (iy - half - winYf).toInt()
            val left = rawLeft.coerceIn(0, maxLeft)
            val top = rawTop.coerceIn(0, maxTop)
            val btn = root.findViewById<ImageButton>(BUTTON_IDS[i])
            val lp = FrameLayout.LayoutParams(iconPx, iconPx).apply {
                leftMargin = left
                topMargin = top
            }
            btn?.layoutParams = lp
            btn?.elevation = 6f * density
        }

        root.layoutParams = FrameLayout.LayoutParams(winW, winH)

        return ArcWindowGeom(x = winX, y = winY, width = winW, height = winH)
    }

    data class ArcWindowGeom(val x: Int, val y: Int, val width: Int, val height: Int)

    fun createOverlayLayoutParams(
        width: Int,
        height: Int,
        x: Int,
        y: Int,
        gravity: Int
    ): WindowManager.LayoutParams {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
        return WindowManager.LayoutParams(
            width,
            height,
            type,
            OVERLAY_FLAGS,
            PixelFormat.TRANSLUCENT
        ).apply {
            this.gravity = gravity
            this.x = x
            this.y = y
        }
    }

    /** Horizontal strip: [widthPx] wide, vertically offset [yPos], centered horizontally. */
    fun horizontalTopRowParams(
        widthPx: Int,
        yPos: Int,
        screenWidthPx: Int
    ): WindowManager.LayoutParams {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
        val w = widthPx.coerceIn(1, screenWidthPx)
        return WindowManager.LayoutParams(
            w,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            OVERLAY_FLAGS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            x = 0
            y = yPos
        }
    }
}
