package com.jerry.dynamicisland

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.ServiceCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner

class OverlayService : LifecycleService(), SavedStateRegistryOwner {
    private val savedStateController = SavedStateRegistryController.create(this)

    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateController.savedStateRegistry

    override fun onCreate() {
        savedStateController.performAttach()
        savedStateController.performRestore(null)
        super.onCreate()
    }

    private var windowManager: WindowManager? = null
    private var islandView: ComposeView? = null
    private var expanded by mutableStateOf(false)

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
                NotificationChannel(
                    CHANNEL_ID,
                    "Dynamic Island",
                    NotificationManager.IMPORTANCE_LOW
                )
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
                this,
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, 0)
        }
    }

    private fun showIsland() {
        if (!Settings.canDrawOverlays(this) || islandView != null) return

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val view = ComposeView(this)
        view.setViewTreeLifecycleOwner(this)
        view.setViewTreeSavedStateRegistryOwner(this)

        view.setContent {
            MaterialTheme(colorScheme = androidx.compose.material3.darkColorScheme()) {
                Box(
                    modifier = Modifier.fillMaxWidth().background(Color.Transparent),
                    contentAlignment = Alignment.TopCenter
                ) {
                    Column(
                        modifier = Modifier
                            .padding(top = 8.dp)
                            .width(if (expanded) 320.dp else 176.dp)
                            .background(Color.Black, RoundedCornerShape(28.dp))
                            .animateContentSize()
                            .clickable { expanded = !expanded }
                            .padding(horizontal = 18.dp, vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("●", color = Color(0xFF45D483))
                            Text(
                                if (expanded) "Dynamic Island" else "Island",
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text("⋯", color = Color.LightGray)
                        }

                        if (expanded) {
                            Spacer(Modifier.height(14.dp))
                            Text("Your Android island is active", color = Color.White)
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "Tap the island to collapse it.",
                                color = Color(0xFFB8B8B8),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = (8 * resources.displayMetrics.density).toInt()
        }

        try {
            windowManager?.addView(view, params)
            islandView = view
        } catch (_: Exception) {
            view.disposeComposition()
        }
    }

    private fun removeIsland() {
        islandView?.let { view ->
            try {
                windowManager?.removeView(view)
            } catch (_: Exception) {
            }
            view.disposeComposition()
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
