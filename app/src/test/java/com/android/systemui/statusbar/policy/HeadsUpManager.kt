package com.android.systemui.statusbar.policy

import android.os.Handler
import com.android.systemui.statusbar.AlertingNotificationManager.AlertEntry

class HeadsUpManager(
    private val mAccessibilityMgr: AccessibilityManagerWrapper,
    @JvmField val mHandler: Handler = Handler(),
) {
    val active = mutableMapOf<String, HeadsUpEntry>()

    fun onAlertEntryAdded(alert: AlertEntry) {
        alert.mEntry?.row?.apply {
            setHeadsUp(true)
            pinned = true
        }
    }

    fun getHeadsUpEntry(key: String): HeadsUpEntry? = active[key]

    inner class HeadsUpEntry(private val miuiTimeoutMs: Int) : AlertEntry(null) {
        fun getRecommendedHeadsUpTimeoutMs(baseMs: Int): Int =
            if (miuiTimeoutMs > 0) miuiTimeoutMs else mAccessibilityMgr.getRecommendedTimeoutMillis(baseMs, 7)
    }

    class AccessibilityManagerWrapper(private val recommendation: (Int, Int) -> Int) {
        fun getRecommendedTimeoutMillis(baseMs: Int, flags: Int): Int = recommendation(baseMs, flags)
    }
}
