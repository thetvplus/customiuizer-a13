package com.android.systemui.statusbar.phone

class NotificationIconAreaController {
    @JvmField var mNotificationIcons: NotificationIconContainer? = NotificationIconContainer()
    @JvmField val mAodIcons = NotificationIconContainer()
    @JvmField val mMoreIcon = Any()
    @JvmField var mForceHideMoreIcon = false

    fun onNotificationIconChanged(show: Boolean) {
        mNotificationIcons?.miuiShowNotificationIcons(show)
    }

    fun setIconsVisibility() {}
    fun onDarkChanged() {}
}
