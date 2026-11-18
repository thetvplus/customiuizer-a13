package com.android.systemui.statusbar.phone

import com.android.systemui.statusbar.AlertingNotificationManager
import com.android.systemui.statusbar.notification.collection.NotificationEntry

class HeadsUpManagerPhone {
    class HeadsUpEntryPhone(entry: NotificationEntry?) : AlertingNotificationManager.AlertEntry(entry) {
        @JvmField var expanded = false
        @JvmField var remoteInputActive = false
        @JvmField var mMenuShownPinned = false
        var dispatchUpdate: ((Boolean) -> Unit)? = null
        var stickyForTimer: (() -> Boolean)? = null
        var lastUpdatePostTime: Boolean? = null
            private set

        fun isSticky(): Boolean = (expanded && mEntry?.isRowPinned() == true) ||
            remoteInputActive || mMenuShownPinned || mEntry?.getSbn()?.notification?.fullScreenIntent != null

        fun setExpanded(value: Boolean) {
            if (expanded == value) return
            expanded = value
            if (value) removeAutoRemovalCallbacks() else updateEntry(false)
        }

        override fun updateEntry(updatePostTime: Boolean) {
            val dispatch = dispatchUpdate
            if (dispatch == null) updateNativeEntry(updatePostTime) else dispatch(updatePostTime)
        }

        fun updateNativeEntry(updatePostTime: Boolean) {
            if (mEntry == null) return
            lastUpdatePostTime = updatePostTime
            super.updateEntry(updatePostTime)
            if (stickyForTimer?.invoke() ?: isSticky()) removeAutoRemovalCallbacks()
        }
    }
}
