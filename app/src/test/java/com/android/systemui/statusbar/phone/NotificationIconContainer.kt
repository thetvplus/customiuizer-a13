package com.android.systemui.statusbar.phone

class NotificationIconContainer {
    @JvmField var MAX_DOTS = 3
    @JvmField var MAX_STATIC_ICONS = 3
    @JvmField var MAX_ICONS_ON_LOCKSCREEN = 3
    var layoutUpdates = 0
        private set
    var renderedDotLimit = MAX_DOTS
        private set

    fun miuiShowNotificationIcons(show: Boolean) {
        val limit = if (show) 3 else 0
        MAX_DOTS = limit
        MAX_STATIC_ICONS = limit
        MAX_ICONS_ON_LOCKSCREEN = limit
        updateState()
    }

    fun updateState() {
        layoutUpdates++
        renderedDotLimit = MAX_DOTS
    }
}
