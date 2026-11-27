package com.jerry.dynamicisland

import android.app.Notification
import android.content.Intent
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class IslandNotificationListener : NotificationListenerService() {
    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName == packageName) return

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
            startService(update)
        } catch (_: Exception) {
            // The user can still see the notification in the system shade if the overlay service is stopped.
        }
    }
}
