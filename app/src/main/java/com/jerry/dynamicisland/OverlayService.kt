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
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.ServiceCompat
import androidx.lifecycle.LifecycleService

class OverlayService : LifecycleService() {
    private var windowManager: WindowManager? = null
    private var islandView: View? = null
    private var expanded = false

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        when (intent?.action) {
            ACTION_STOP -> {
                removeIsland()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return Service.START_NOT_STICKY
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
        val root = FrameLayout(this).apply {
            setBackgroundColor(Color.TRANSPARENT)
        }
        islandView = root
        renderIsland(root)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = dp(8)
        }

        try {
            windowManager?.addView(root, params)
        } catch (_: Exception) {
            islandView = null
        }
    }

    private fun renderIsland(root: FrameLayout) {
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(12), dp(18), dp(12))
            background = GradientDrawable().apply {
                setColor(Color.BLACK)
                cornerRadius = dp(28).toFloat()
            }
            layoutParams = FrameLayout.LayoutParams(
                if (expanded) dp(320) else dp(176),
                WindowManager.LayoutParams.WRAP_CONTENT,
                Gravity.TOP or Gravity.CENTER_HORIZONTAL
            )
            isClickable = true
            setOnClickListener {
                expanded = !expanded
                renderIsland(root)
            }
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(makeText("●", 16f, Color.rgb(69, 212, 131)), LinearLayout.LayoutParams(0, dp(24), 1f))
        header.addView(
            makeText(if (expanded) "Dynamic Island" else "Island", 14f, Color.WHITE, true),
            LinearLayout.LayoutParams(0, dp(24), 3f)
        )
        header.addView(makeText("⋯", 18f, Color.LTGRAY), LinearLayout.LayoutParams(0, dp(24), 1f))
        panel.addView(header)

        if (expanded) {
            val detail = makeText("Your Android island is active", 14f, Color.WHITE)
            val detailParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(32)
            ).apply { topMargin = dp(14) }
            panel.addView(detail, detailParams)
            panel.addView(
                makeText("Tap the island to collapse it.", 12f, Color.LTGRAY),
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(22))
            )
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
    }

    override fun onDestroy() {
        removeIsland()
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "com.jerry.dynamicisland.START"
        const val ACTION_STOP = "com.jerry.dynamicisland.STOP"
        private const val CHANNEL_ID = "dynamic_island_service"
        private const val NOTIFICATION_ID = 1001
    }
}
