package com.android.systemui.statusbar.notification.policy

import android.content.ComponentName
import android.content.Intent

object MiniWindowPolicy {
    fun canSlidePackage(pkgName: String, intent: Intent?, topPkg: String?, topActivity: ComponentName?,
        hasSmallWindow: Boolean): Boolean =
        intent != null && pkgName != topPkg && pkgName != topActivity?.packageName && !hasSmallWindow
}
