package com.android.systemui.statusbar

import com.android.systemui.statusbar.notification.collection.NotificationEntry

class AlertingNotificationManager {
    open class AlertEntry(@JvmField val mEntry: NotificationEntry?) {
        var updates = 0
            private set
        var timerPending = false
            private set

        open fun updateEntry(updatePostTime: Boolean) {
            updates++
            timerPending = true
        }

        fun removeAutoRemovalCallbacks() {
            timerPending = false
        }

        fun removeAsSoonAsPossible() {
            timerPending = true
        }
    }
}
