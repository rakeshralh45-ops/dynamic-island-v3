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
        getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE).edit()
            .putString(MainActivity.KEY_DEBUG_LISTENER, "CONNECTED")
            .apply()
        Log.i(TAG, "Notification listener connected")
    }

    override fun onListenerDisconnected() {
        getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE).edit()
            .putString(MainActivity.KEY_DEBUG_LISTENER, "DISCONNECTED")
            .apply()
        super.onListenerDisconnected()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName == packageName) return

        val extras = sbn.notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim().orEmpty()
        val body = (
            extras.getCharSequence(Notification.EXTRA_BIG_TEXT)
                ?: extras.getCharSequence(Notification.EXTRA_TEXT)
                ?: extras.getCharSequence(Notification.EXTRA_SUB_TEXT)
            )?.toString()?.trim().orEmpty()

        val prefs = getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE)
        val appName = try {
            val app = packageManager.getApplicationInfo(sbn.packageName, 0)
            packageManager.getApplicationLabel(app).toString()
        } catch (_: Exception) {
            sbn.packageName
        }
        prefs.edit()
            .putString(MainActivity.KEY_DEBUG_LAST_SEEN, "$appName | $title | $body")
            .putLong(MainActivity.KEY_DEBUG_LAST_SEEN_AT, System.currentTimeMillis())
            .apply()

        if (!prefs.getBoolean(MainActivity.KEY_ISLAND_RUNNING, false)) {
            prefs.edit().putString(MainActivity.KEY_DEBUG_LAST_FORWARD, "NOT SENT: Island is stopped").apply()
            return
        }

        if (title.isBlank() && body.isBlank()) {
            prefs.edit().putString(MainActivity.KEY_DEBUG_LAST_FORWARD, "NOT SENT: Notification has no readable title/text").apply()
            return
        }

        val update = Intent(this, OverlayService::class.java).apply {
            action = OverlayService.ACTION_NOTIFICATION
            putExtra(OverlayService.EXTRA_NOTIFICATION_APP, appName)
            putExtra(OverlayService.EXTRA_NOTIFICATION_TITLE, title)
            putExtra(OverlayService.EXTRA_NOTIFICATION_TEXT, body)
        }

        try {
            ContextCompat.startForegroundService(this, update)
            prefs.edit().putString(MainActivity.KEY_DEBUG_LAST_FORWARD, "SENT to OverlayService: $appName").apply()
            Log.i(TAG, "Forwarded notification from ${sbn.packageName}")
        } catch (error: Exception) {
            prefs.edit().putString(MainActivity.KEY_DEBUG_LAST_FORWARD, "ERROR: ${error.javaClass.simpleName}: ${error.message}").apply()
            Log.e(TAG, "Could not forward notification from ${sbn.packageName}", error)
        }
    }

    companion object {
        private const val TAG = "IslandNotification"
    }
}
