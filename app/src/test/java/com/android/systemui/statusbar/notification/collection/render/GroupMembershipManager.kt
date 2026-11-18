package com.android.systemui.statusbar.notification.collection.render

import com.android.systemui.statusbar.notification.collection.NotificationEntry

fun interface GroupMembershipManager {
    fun isGroupSummary(entry: NotificationEntry): Boolean
}
