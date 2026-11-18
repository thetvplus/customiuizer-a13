package com.android.systemui.statusbar.notification.collection.legacy

import com.android.systemui.statusbar.notification.collection.NotificationEntry

class NotificationGroupManagerLegacy {
    val groupedChildren = mutableSetOf<NotificationEntry>()

    fun isChildInGroup(entry: NotificationEntry): Boolean = entry in groupedChildren
}

class LowPriorityInflationHelper(private val mGroupManager: NotificationGroupManagerLegacy) {
    var nativeDecision = false

    @Suppress("UNUSED_PARAMETER")
    fun shouldUseLowPriorityView(entry: NotificationEntry): Boolean = nativeDecision
}
