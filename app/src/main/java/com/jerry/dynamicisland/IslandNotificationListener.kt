package com.jerry.dynamicisland

import android.app.Notification
import android.content.Intent
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.content.ContextCompat

class IslandNotificationListener : NotificationListenerService() {
    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.i(TAG, "Notification listener connected")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName == packageName) return

        val prefs = getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE)
        if (!prefs.getBoolean(MainActivity.KEY_ISLAND_RUNNING, false)) return

        val extras = sbn.notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim().orEmpty()
        val body = (
            extras.getCharSequence(Notification.EXTRA_BIG_TEXT)
                ?: extras.getCharSequence(Notification.EXTRA_TEXT)
                ?: extras.getCharSequence(Notification.EXTRA_SUB_TEXT)
            )?.toString()?.trim().orEmpty()

        if (title.isBlank() && body.isBlank()) return

        val appName = try {
            val app = packageManager.getApplicationInfo(sbn.packageName, 0)
            packageManager.getApplicationLabel(app).toString()
        } catch (_: Exception) {
            sbn.packageName
        }

        val update = Intent(this, OverlayService::class.java).apply {
            action = OverlayService.ACTION_NOTIFICATION
            putExtra(OverlayService.EXTRA_NOTIFICATION_APP, appName)
            putExtra(OverlayService.EXTRA_NOTIFICATION_TITLE, title)
            putExtra(OverlayService.EXTRA_NOTIFICATION_TEXT, body)
        }

        try {
            // A foreground-service start is reliable when Android has put the app in the background.
            ContextCompat.startForegroundService(this, update)
            Log.i(TAG, "Forwarded notification from ${sbn.packageName}")
        } catch (error: Exception) {
            Log.e(TAG, "Could not forward notification from ${sbn.packageName}", error)
        }
    }

    companion object {
        private const val TAG = "IslandNotification"
    }
}
