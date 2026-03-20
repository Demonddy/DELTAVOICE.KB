package com.deltavoice

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.LinearLayout
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat

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

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        featureController = OverlayFeatureController(this)
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
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Close", stopPendingIntent)
            .build()
    }

    private fun showBubble() {
        if (bubbleView != null) return

        val inflater = LayoutInflater.from(this)
        bubbleView = inflater.inflate(R.layout.overlay_bubble, null)

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
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
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

    private fun showTopRow() {
        if (topRowView != null) return

        val inflater = LayoutInflater.from(this)
        topRowView = inflater.inflate(R.layout.overlay_top_row, null)

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        topRowParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM
            y = (16 * resources.displayMetrics.density).toInt()
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
        featureController?.dismissAll()
        hideTopRow()
        bubbleView?.let {
            windowManager?.removeView(it)
        }
        bubbleView = null
        featureController = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
