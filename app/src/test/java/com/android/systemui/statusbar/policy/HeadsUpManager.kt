package com.android.systemui.statusbar.policy

class HeadsUpManager(private val mAccessibilityMgr: AccessibilityManagerWrapper) {
    inner class HeadsUpEntry(private val miuiTimeoutMs: Int) {
        fun getRecommendedHeadsUpTimeoutMs(baseMs: Int): Int =
            if (miuiTimeoutMs > 0) miuiTimeoutMs else mAccessibilityMgr.getRecommendedTimeoutMillis(baseMs, 7)
    }

    class AccessibilityManagerWrapper(private val recommendation: (Int, Int) -> Int) {
        fun getRecommendedTimeoutMillis(baseMs: Int, flags: Int): Int = recommendation(baseMs, flags)
    }
}
