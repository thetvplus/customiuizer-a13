package com.android.systemui.statusbar.phone

import com.android.systemui.statusbar.AlertingNotificationManager
import com.android.systemui.statusbar.notification.collection.NotificationEntry

class HeadsUpManagerPhone {
    class HeadsUpEntryPhone(entry: NotificationEntry?) : AlertingNotificationManager.AlertEntry(entry) {
        override fun updateEntry(updatePostTime: Boolean) {
            if (mEntry != null) super.updateEntry(updatePostTime)
        }
    }
}
