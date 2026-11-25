package com.maya.assistant.service

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

data class MayaNotificationItem(
    val packageName: String,
    val appName: String,
    val title: String,
    val text: String
)

class MayaNotificationListener : NotificationListenerService() {

    override fun onListenerConnected() {
        super.onListenerConnected()
        isConnected = true
        refreshSnapshot()
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        isConnected = false
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        refreshSnapshot()
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        refreshSnapshot()
    }

    private fun refreshSnapshot() {
        val list = runCatching { activeNotifications }.getOrNull() ?: return
        activeNotificationsSnapshot = list.mapNotNull { sbn ->
            val extras = sbn.notification.extras
            val title = extras.getCharSequence("android.title")?.toString()?.trim().orEmpty()
            val text = extras.getCharSequence("android.text")?.toString()?.trim().orEmpty()
            if (title.isEmpty() && text.isEmpty()) return@mapNotNull null
            MayaNotificationItem(
                packageName = sbn.packageName,
                appName = sbn.packageName.substringAfterLast('.'),
                title = title,
                text = text
            )
        }
    }

    companion object {
        @Volatile
        var isConnected: Boolean = false
            private set

        @Volatile
        var activeNotificationsSnapshot: List<MayaNotificationItem> = emptyList()
            private set
    }
}
