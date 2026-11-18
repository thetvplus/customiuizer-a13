package com.android.systemui.statusbar.notification

import android.service.notification.StatusBarNotification

class ExpandedNotification(
    packageName: String = "com.example.app",
    uid: Int = 1012345,
    val hybrid: Boolean = false
) : StatusBarNotification() {
    private var shownAfterUnlock = false

    init {
        mPackageName = packageName
        mPkgName = packageName
        mAppUid = uid
    }

    fun hasShownAfterUnlock(): Boolean = shownAfterUnlock

    fun setHasShownAfterUnlock(shown: Boolean) {
        shownAfterUnlock = shown
    }
}
