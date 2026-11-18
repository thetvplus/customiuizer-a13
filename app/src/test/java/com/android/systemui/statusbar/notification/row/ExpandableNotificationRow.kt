package com.android.systemui.statusbar.notification.row

import android.view.View
import com.android.systemui.statusbar.notification.collection.NotificationEntry
import com.android.systemui.statusbar.notification.collection.render.GroupMembershipManager

class ExpandableNotificationRow : View(null) {
    var mSbn: Any? = null
    var dozing = false

    fun isDozing(): Boolean = dozing

    @JvmField var mEntry: NotificationEntry? = null
    @JvmField var mExpandedWhenPinned = false
    @JvmField var mIsLowPriority = false
    @JvmField val mGroupMembershipManager = GroupMembershipManager { summary }
    var summary = false
    var groupExpanded = false
    var headsUp = false
        private set
    var pinned = false
    var expanded = false
    var onKeyguard = false
    var redacted = false
    var removed = false
    var attached = true
    var expansionClicks = 0
        private set
    var systemExpandedState = false
        private set
    var systemExpansionChanges = 0
        private set

    override fun isAttachedToWindow(): Boolean = attached
    fun setHeadsUp(value: Boolean) { headsUp = value }
    fun isHeadsUp(): Boolean = headsUp
    fun isPinned(): Boolean = pinned
    fun isExpanded(): Boolean = expanded
    fun isGroupExpanded(): Boolean = groupExpanded
    fun isOnKeyguard(): Boolean = onKeyguard
    fun isRemoved(): Boolean = removed
    fun shouldShowPublic(): Boolean = redacted
    fun getEntry(): NotificationEntry? = mEntry

    fun setSystemExpanded(value: Boolean) {
        if (systemExpandedState == value) return
        systemExpandedState = value
        systemExpansionChanges++
    }

    fun expandNotification() {
        expansionClicks++
        when {
            !redacted && (!mIsLowPriority || expanded) && summary -> groupExpanded = !groupExpanded
            pinned -> mExpandedWhenPinned = !mExpandedWhenPinned
            else -> expanded = !expanded
        }
    }
}
