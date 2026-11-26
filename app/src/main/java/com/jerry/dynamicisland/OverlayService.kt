package com.jerry.dynamicisland

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.ServiceCompat
import androidx.lifecycle.LifecycleService
import kotlin.math.abs

class OverlayService : LifecycleService() {
    private var windowManager: WindowManager? = null
    private var islandView: FrameLayout? = null
    private var overlayParams: WindowManager.LayoutParams? = null
    private var expanded = false
    private var downRawX = 0f
    private var downRawY = 0f
    private var initialX = 0
    private var initialY = 0
    private var dragging = false

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        when (intent?.action) {
            ACTION_STOP -> {
                removeIsland()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return Service.START_NOT_STICKY
            }
            ACTION_REFRESH -> {
                if (islandView != null) {
                    removeIsland()
                    showIsland()
                }
            }
            ACTION_START, null -> {
                startAsForeground()
                showIsland()
            }
        }
        return Service.START_STICKY
    }

    private fun startAsForeground() {
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Dynamic Island", NotificationManager.IMPORTANCE_LOW)
            )
        }

        val notification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("Dynamic Island is active")
                .setContentText("Use the app to stop the floating island.")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setOngoing(true)
                .build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
                .setContentTitle("Dynamic Island is active")
                .setContentText("Use the app to stop the floating island.")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setOngoing(true)
                .build()
        }

        if (Build.VERSION.SDK_INT >= 34) {
            ServiceCompat.startForeground(
                this, NOTIFICATION_ID, notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, 0)
        }
    }

    private fun showIsland() {
        if (!Settings.canDrawOverlays(this) || islandView != null) return
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val root = FrameLayout(this).apply { setBackgroundColor(Color.TRANSPARENT) }
        islandView = root
        renderIsland(root)

        val prefs = getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE)
        val blur = prefs.getFloat(MainActivity.KEY_BLUR, 28f).toInt()
        val islandWidth = prefs.getFloat(MainActivity.KEY_WIDTH, 220f).toInt()
            .coerceIn(160, 340)
        val params = WindowManager.LayoutParams(
            dp(islandWidth),
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = dp(prefs.getFloat(MainActivity.KEY_TOP, 8f).toInt())
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && blur > 0) {
                flags = flags or WindowManager.LayoutParams.FLAG_BLUR_BEHIND
                setBlurBehindRadius(dp(blur))
            }
        }
        overlayParams = params

        try {
            windowManager?.addView(root, params)
        } catch (_: Exception) {
            islandView = null
            overlayParams = null
        }
    }

    private fun renderIsland(root: FrameLayout) {
        val prefs = getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE)
        val width = prefs.getFloat(MainActivity.KEY_WIDTH, 220f).toInt()
        val opacity = prefs.getFloat(MainActivity.KEY_OPACITY, 82f).toInt()
            .coerceIn(0, 100)
        val alpha = (opacity * 255 / 100).coerceIn(0, 255)

        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(12), dp(18), dp(12))
            background = GradientDrawable().apply {
                setColor(Color.argb(alpha, 20, 21, 27))
                cornerRadius = dp(32).toFloat()
                setStroke(dp(1), Color.argb(90, 255, 255, 255))
            }
            layoutParams = FrameLayout.LayoutParams(
                dp(width.coerceIn(160, 340)),
                WindowManager.LayoutParams.WRAP_CONTENT,
                Gravity.TOP or Gravity.CENTER_HORIZONTAL
            )
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(
            makeText("●", 16f, Color.rgb(69, 212, 131)),
            LinearLayout.LayoutParams(0, dp(26), 1f)
        )
        header.addView(
            makeText(if (expanded) "Dynamic Island" else "Island", 14f, Color.WHITE, true),
            LinearLayout.LayoutParams(0, dp(26), 3f)
        )
        header.addView(
            makeText("⋯", 18f, Color.LTGRAY),
            LinearLayout.LayoutParams(0, dp(26), 1f)
        )
        panel.addView(header)

        if (expanded) {
            val detailParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(32)
            ).apply { topMargin = dp(14) }
            panel.addView(
                makeText("Your Android island is active", 14f, Color.WHITE),
                detailParams
            )
            panel.addView(
                makeText("Drag to move • Tap to collapse", 12f, Color.LTGRAY),
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(22))
            )
        }

        panel.setOnTouchListener { _, event ->
            val params = overlayParams
            val view = islandView
            if (params == null || view == null) return@setOnTouchListener false

            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downRawX = event.rawX
                    downRawY = event.rawY
                    initialX = params.x
                    initialY = params.y
                    dragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - downRawX).toInt()
                    val dy = (event.rawY - downRawY).toInt()
                    val slop = ViewConfiguration.get(this).scaledTouchSlop
                    if (abs(dx) > slop || abs(dy) > slop) dragging = true
                    if (dragging) {
                        params.x = initialX + dx
                        params.y = initialY + dy
                        try {
                            windowManager?.updateViewLayout(view, params)
                        } catch (_: Exception) {
                        }
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!dragging) {
                        expanded = !expanded
                        renderIsland(view)
                    }
                    true
                }
                MotionEvent.ACTION_CANCEL -> true
                else -> true
            }
        }

        root.removeAllViews()
        root.addView(panel)
    }

    private fun makeText(
        value: String,
        size: Float,
        color: Int,
        bold: Boolean = false
    ) = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(color)
        gravity = Gravity.CENTER
        if (bold) setTypeface(typeface, Typeface.BOLD)
        maxLines = 1
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    private fun removeIsland() {
        islandView?.let { view ->
            try {
                windowManager?.removeView(view)
            } catch (_: Exception) {
            }
        }
        islandView = null
        overlayParams = null
    }

    override fun onDestroy() {
        removeIsland()
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "com.jerry.dynamicisland.START"
        const val ACTION_STOP = "com.jerry.dynamicisland.STOP"
        const val ACTION_REFRESH = "com.jerry.dynamicisland.REFRESH"
        private const val CHANNEL_ID = "dynamic_island_service"
        private const val NOTIFICATION_ID = 1001
    }
}
