package com.android.systemui.miui.volume

import android.view.accessibility.AccessibilityManager

class MiuiVolumeDialogImpl {
    @JvmField var mHovering = false
    @JvmField var mIsSafetyShowing = false
    @JvmField var mExpanded = false
    @JvmField var mAccessibilityMgr: AccessibilityManager? = AccessibilityManager()

    var nativeCalls = 0
        private set

    private fun computeTimeoutH(): Int {
        nativeCalls++
        val base = when {
            mHovering -> 16000
            mIsSafetyShowing || mExpanded -> 5000
            else -> 2000
        }
        val flags = if (!mHovering && mIsSafetyShowing) 6 else 4
        return mAccessibilityMgr?.getRecommendedTimeoutMillis(base, flags) ?: base
    }
}
