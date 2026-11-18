package com.android.systemui.statusbar.notification.row

class ExpandableNotificationRow {
    var mSbn: Any? = null
    var dozing = false

    fun isDozing(): Boolean = dozing
}
