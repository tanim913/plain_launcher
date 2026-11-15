package app.plainlauncher.data

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class NotifListener : NotificationListenerService() {
    override fun onListenerConnected() = rebuild()
    override fun onListenerDisconnected() = NotifDots.update(emptyMap())
    override fun onNotificationPosted(sbn: StatusBarNotification?) = rebuild()
    override fun onNotificationRemoved(sbn: StatusBarNotification?) = rebuild()

    private fun rebuild() {
        val m = HashMap<String, Int>()
        runCatching {
            activeNotifications?.forEach {
                val n = it.notification
                if (it.isOngoing || (n.flags and Notification.FLAG_GROUP_SUMMARY) != 0) return@forEach
                m[it.packageName] = (m[it.packageName] ?: 0) + 1
            }
        }
        NotifDots.update(m)
    }
}
