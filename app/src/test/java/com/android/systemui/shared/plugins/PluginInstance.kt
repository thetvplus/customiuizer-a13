package com.android.systemui.shared.plugins

import android.content.pm.ApplicationInfo

class PluginInstance {
    class Factory {
        fun getClassLoader(appInfo: ApplicationInfo, parent: ClassLoader): ClassLoader? = null
        fun getClassLoader(appInfo: ApplicationInfo): ClassLoader? = null
    }
}
