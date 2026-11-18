package com.android.systemui.statusbar.notification.collection

import com.android.systemui.statusbar.notification.ExpandedNotification
import com.android.systemui.statusbar.notification.row.ExpandableNotificationRow

class NotificationEntry {
    var mSbn: Any? = null
    var row: ExpandableNotificationRow? = null
    var rowPinned = false

    fun getSbn(): ExpandedNotification? = mSbn as? ExpandedNotification

    fun isRowPinned(): Boolean = rowPinned
}
