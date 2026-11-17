package com.android.systemui.statusbar.phone

import com.android.internal.statusbar.RegisterStatusBarResult

class CentralSurfacesImpl {
    @JvmField var mPanelExpanded = false

    fun makeStatusBarView(result: RegisterStatusBarResult) {}
    fun createAndAddWindows() {}
    fun setPanelExpanded(expanded: Boolean) {}
    fun setQsExpanded(expanded: Boolean) {}
    fun updateIsKeyguard(keyguard: Boolean) {}
}
