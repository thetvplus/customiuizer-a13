package com.android.systemui.statusbar.notification.collection

import android.app.NotificationManager
import com.android.systemui.statusbar.notification.ExpandedNotification
import com.android.systemui.statusbar.notification.row.ExpandableNotificationRow

class NotificationEntry {
    var mSbn: Any? = null
    var row: ExpandableNotificationRow? = null
    var rowPinned = false
    var key = "notification-key"
    var importance = NotificationManager.IMPORTANCE_DEFAULT

    fun getSbn(): ExpandedNotification? = mSbn as? ExpandedNotification

    fun isRowPinned(): Boolean = rowPinned
}
