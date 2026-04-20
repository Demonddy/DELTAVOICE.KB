package com.deltavoice

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.content.res.Resources
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat

/**
 * Foreground service that displays a floating bubble overlay.
 * Tap bubble to expand top row icons; each icon opens its feature overlay.
 */
class OverlayBubbleService : Service() {

    private var windowManager: WindowManager? = null
    private var bubbleView: View? = null
    private var topRowView: View? = null
    private var bubbleParams: WindowManager.LayoutParams? = null
    private var topRowParams: WindowManager.LayoutParams? = null
    private var featureController: OverlayFeatureController? = null
    private var appLocaleReceiver: BroadcastReceiver? = null

    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var hasMoved = false

    companion object {
        private const val CHANNEL_ID = "overlay_bubble_channel"
        private const val NOTIFICATION_ID = 1001
        const val ACTION_STOP = "com.deltavoice.OVERLAY_STOP"
    }

    private var localeResourcesCacheTag: String? = null
    private var localeWrappedResources: Resources? = null

    override fun getResources(): Resources {
        val locales = AppCompatDelegate.getApplicationLocales()
        val tag = if (locales.isEmpty) "" else locales[0]?.toLanguageTag() ?: ""
        if (tag == localeResourcesCacheTag && localeWrappedResources != null) {
            return localeWrappedResources!!
        }
        localeResourcesCacheTag = tag
        localeWrappedResources = AppLocaleHelper.wrap(applicationContext).resources
        return localeWrappedResources!!
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        localeResourcesCacheTag = null
        localeWrappedResources = null
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        featureController = OverlayFeatureController(this)
        appLocaleReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: android.content.Context?, intent: Intent?) {
                if (intent?.action != AppLocaleHelper.ACTION_APP_LOCALE_CHANGED) return
                localeResourcesCacheTag = null
                localeWrappedResources = null
            }
        }
        ContextCompat.registerReceiver(
            this,
            appLocaleReceiver,
            IntentFilter(AppLocaleHelper.ACTION_APP_LOCALE_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
            else -> {
                createNotificationChannel()
                val notification = buildNotification()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    ServiceCompat.startForeground(
                        this,
                        NOTIFICATION_ID,
                        notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                    )
                } else {
                    startForeground(NOTIFICATION_ID, notification)
                }
                showBubble()
            }
        }
        return START_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.overlay_notification_channel),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val stopIntent = Intent(this, OverlayBubbleService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 0, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.overlay_notification_title))
            .setContentText(getString(R.string.overlay_notification_text))
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, getString(R.string.overlay_close), stopPendingIntent)
            .build()
    }

    private fun showBubble() {
        if (bubbleView != null) return

        val inflater = LayoutInflater.from(this)
        bubbleView = inflater.inflate(R.layout.overlay_bubble, null)
        forceLtr(bubbleView)

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        bubbleParams = WindowManager.LayoutParams(
            (48 * resources.displayMetrics.density).toInt(),
            (48 * resources.displayMetrics.density).toInt(),
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = resources.displayMetrics.widthPixels - (64 * resources.displayMetrics.density).toInt()
            y = resources.displayMetrics.heightPixels / 2 - (24 * resources.displayMetrics.density).toInt()
        }

        bubbleView?.setOnTouchListener(bubbleTouchListener)
        bubbleView?.setOnClickListener {
            if (!hasMoved) toggleTopRow()
        }

        windowManager?.addView(bubbleView, bubbleParams)
    }

    private val bubbleTouchListener = View.OnTouchListener { _, event ->
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                hasMoved = false
                initialX = bubbleParams?.x ?: 0
                initialY = bubbleParams?.y ?: 0
                initialTouchX = event.rawX
                initialTouchY = event.rawY
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = (event.rawX - initialTouchX).toInt()
                val dy = (event.rawY - initialTouchY).toInt()
                if (kotlin.math.abs(dx) > 5 || kotlin.math.abs(dy) > 5) hasMoved = true
                bubbleParams?.x = initialX + dx
                bubbleParams?.y = initialY + dy
                windowManager?.updateViewLayout(bubbleView, bubbleParams)
            }
        }
        false
    }

    private fun toggleTopRow() {
        if (topRowView != null) {
            hideTopRow()
        } else {
            showTopRow()
        }
    }

    private fun forceLtr(root: View?) {
        if (root == null) return
        root.layoutDirection = View.LAYOUT_DIRECTION_LTR
        if (root is ViewGroup) {
            for (i in 0 until root.childCount) {
                root.getChildAt(i)?.layoutDirection = View.LAYOUT_DIRECTION_LTR
            }
        }
    }

    private fun bubbleSizePx(): Int =
        (48 * resources.displayMetrics.density).toInt()

    private fun statusBarInsetPx(): Int {
        val id = resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (id > 0) resources.getDimensionPixelSize(id)
        else (24 * resources.displayMetrics.density).toInt()
    }

    private fun showTopRow() {
        if (topRowView != null) return

        val inflater = LayoutInflater.from(this)
        val widthScale = OverlayPrefs.getTopRowWidthScale(this)
        val dm = resources.displayMetrics
        val bp = bubbleParams ?: return
        val bubbleSize = bubbleSizePx()
        val (bubbleCx, _) = OverlayTopRowPlacer.bubbleCenterPx(bp, bubbleSize)

        val useArc = OverlayTopRowPlacer.shouldShowArcBand(
            OverlayPrefs.getTopRowStyle(this),
            bubbleCx,
            dm.widthPixels
        )

        if (useArc) {
            topRowView = inflater.inflate(R.layout.overlay_top_row_arc, null)
            forceLtr(topRowView)
            val root = topRowView as FrameLayout
            val geom = OverlayTopRowPlacer.layoutArcBand(
                root,
                bp,
                bubbleSize,
                dm.density,
                widthScale,
                dm.widthPixels,
                dm.heightPixels
            )
            topRowParams = OverlayTopRowPlacer.createOverlayLayoutParams(
                width = geom.width,
                height = geom.height,
                x = geom.x,
                y = geom.y,
                gravity = Gravity.TOP or Gravity.START
            )
        } else {
            val stripWidth = (dm.widthPixels * widthScale).toInt().coerceAtLeast(1)
            topRowView = inflater.inflate(R.layout.overlay_top_row, null)
            forceLtr(topRowView)
            topRowView?.measure(
                View.MeasureSpec.makeMeasureSpec(stripWidth, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            )
            val rowHeight = topRowView?.measuredHeight ?: (72 * dm.density).toInt()
            val gap = (8 * dm.density).toInt()
            val bubbleY = bp.y
            val statusTop = statusBarInsetPx()
            val bubbleBottom = bubbleY + bubbleSize

            var yPos = bubbleY - rowHeight - gap
            if (yPos < statusTop) {
                yPos = bubbleBottom + gap
            }

            topRowParams = OverlayTopRowPlacer.horizontalTopRowParams(
                stripWidth,
                yPos,
                dm.widthPixels
            )
        }

        setupTopRowListeners()
        windowManager?.addView(topRowView, topRowParams)
    }

    private fun hideTopRow() {
        topRowView?.let {
            windowManager?.removeView(it)
            topRowView = null
        }
    }

    private fun setupTopRowListeners() {
        val view = topRowView ?: return
        val controller = featureController ?: return

        view.findViewById<ImageButton>(R.id.btn_more)?.setOnClickListener {
            hideTopRow()
            controller.showMoreOptions { showTopRow() }
        }
        view.findViewById<ImageButton>(R.id.btn_camera)?.setOnClickListener {
            hideTopRow()
            controller.showVideoRecording()
        }
        view.findViewById<ImageButton>(R.id.btn_list)?.setOnClickListener {
            hideTopRow()
            openAppHomepage()
        }
        view.findViewById<ImageButton>(R.id.btn_text_t)?.setOnClickListener {
            hideTopRow()
            controller.showAiChat()
        }
        view.findViewById<ImageButton>(R.id.btn_kb_plus)?.setOnClickListener {
            hideTopRow()
            controller.showAiWritingTools()
        }
        view.findViewById<ImageButton>(R.id.btn_voice)?.setOnClickListener {
            hideTopRow()
            controller.showVoiceRecording()
        }
        view.findViewById<ImageButton>(R.id.btn_app_grid)?.setOnClickListener {
            hideTopRow()
            controller.showClipboard()
        }
    }

    private fun openAppHomepage() {
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(intent)
    }

    override fun onDestroy() {
        appLocaleReceiver?.let {
            try {
                unregisterReceiver(it)
            } catch (_: Exception) {
            }
        }
        appLocaleReceiver = null
        featureController?.dismissAll()
        hideTopRow()
        bubbleView?.let {
            windowManager?.removeView(it)
        }
        bubbleView = null
        featureController = null
        MainKeyboardService.releaseStandaloneOverlayHostForBubble()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
