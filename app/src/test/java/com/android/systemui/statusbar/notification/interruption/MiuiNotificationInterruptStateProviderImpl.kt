package com.android.systemui.statusbar.notification.interruption

import com.android.systemui.statusbar.notification.ExpandedNotification
import com.android.systemui.statusbar.notification.collection.NotificationEntry

class MiuiNotificationInterruptStateProviderImpl {
    var nativeDecision = true
    var nativePeeks = 0
        private set

    fun shouldPeek(entry: NotificationEntry): Boolean =
        shouldPeek(entry, entry.mSbn as? ExpandedNotification)

    fun shouldPeek(entry: NotificationEntry, notification: ExpandedNotification?): Boolean {
        nativePeeks++
        return nativeDecision
    }
}
