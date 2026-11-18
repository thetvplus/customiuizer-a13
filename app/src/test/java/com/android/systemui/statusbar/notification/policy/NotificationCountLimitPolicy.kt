package com.android.systemui.statusbar.notification.policy

import com.android.systemui.statusbar.notification.NotificationEntryManager
import com.android.systemui.statusbar.notification.collection.NotificationEntry

class NotificationCountLimitPolicy(private val mEntryManager: NotificationEntryManager) {
    var checks = 0
        private set
    var eligibilityCalls = 0
        private set
    val important = mutableSetOf<NotificationEntry>()
    var removed: NotificationEntry? = null
        private set

    fun checkNotificationCountLimit(packageName: String) {
        checks++
        val candidates = mEntryManager.entries.filter {
            it.getSbn()?.getPackageName() == packageName && `lambda$checkNotificationCountLimit$1`(it)
        }
        if (candidates.size > 10) {
            val first = candidates.first()
            removed = first
            mEntryManager.entries.remove(first)
        }
    }

    private fun `lambda$checkNotificationCountLimit$1`(entry: NotificationEntry): Boolean {
        eligibilityCalls++
        return entry !in important
    }
}
