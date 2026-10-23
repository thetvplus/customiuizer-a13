package com.android.systemui.statusbar.notification

object NotificationUtil {
    @JvmStatic
    fun isHybrid(notification: ExpandedNotification): Boolean = notification.hybrid
}
