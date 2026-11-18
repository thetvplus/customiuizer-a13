package com.android.systemui.statusbar.notification

import com.android.systemui.statusbar.notification.collection.NotificationEntry

class NotificationEntryManager {
    val entries = mutableListOf<NotificationEntry>()

    fun getAllNotifs(): Collection<NotificationEntry> = entries
}
