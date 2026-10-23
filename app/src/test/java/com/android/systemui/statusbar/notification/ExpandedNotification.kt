package com.android.systemui.statusbar.notification

import tv.withaibuild.customiuizer.mods.FakeStatusBarNotification

class ExpandedNotification(
    packageName: String = "com.example.app",
    uid: Int = 1012345,
    val hybrid: Boolean = false
) : FakeStatusBarNotification(packageName, uid)
