package tv.withaibuild.customiuizer.mods

import android.app.MiuiNotification
import android.app.PendingIntent
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
import tv.withaibuild.customiuizer.MainModule
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper.AfterHookCallback
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper.MethodHook
import tv.withaibuild.customiuizer.mods.utils.ModuleHelper
import tv.withaibuild.customiuizer.mods.utils.XposedHelpers

object SystemNotificationPopupsHooks {

    @JvmStatic
    fun BetterPopupsHideDelayHook(lpparam: PackageReadyParam) {
        ModuleHelper.findAndHookMethodSilently(MiuiNotification::class.java, "getFloatTime", HookerClassHelper.returnConstant(0))
        ModuleHelper.hookAllConstructors("com.android.systemui.statusbar.policy.HeadsUpManager", lpparam.classLoader, object : MethodHook() {
            override fun after(param: AfterHookCallback) {
                var delay = MainModule.mPrefs.getInt("system_betterpopups_delay", 0) * 1000
                if (delay == 0) delay = 5000
                XposedHelpers.setIntField(param.thisObject, "mMinimumDisplayTime", delay)
                XposedHelpers.setIntField(param.thisObject, "mHeadsUpNotificationDecay", delay)
                ModuleHelper.observeOwnedPreferenceChange("systemui.headsUpDelay", param.thisObject) { owner, key ->
                    if (key.contains("system_betterpopups_delay")) {
                        var updatedDelay = MainModule.mPrefs.getInt("system_betterpopups_delay", 0) * 1000
                        if (updatedDelay == 0) updatedDelay = 5000
                        XposedHelpers.setIntField(owner, "mMinimumDisplayTime", updatedDelay)
                        XposedHelpers.setIntField(owner, "mHeadsUpNotificationDecay", updatedDelay)
                    }
                }
            }
        })
    }

    @JvmStatic
    fun BetterPopupsNoHideHook(lpparam: PackageReadyParam) {
        val loader = lpparam.classLoader
        val alertEntry = XposedHelpers.findClassIfExists(
            "com.android.systemui.statusbar.AlertingNotificationManager\$AlertEntry", loader
        ) ?: return
        val updateEntry = XposedHelpers.findMethodExactIfExists(
            "com.android.systemui.statusbar.phone.HeadsUpManagerPhone\$HeadsUpEntryPhone",
            loader, "updateEntry", Boolean::class.javaPrimitiveType
        ) ?: return
        if (!alertEntry.isAssignableFrom(updateEntry.declaringClass) || updateEntry.returnType != Void.TYPE) return
        val entryField = XposedHelpers.findFieldIfExists(alertEntry, "mEntry") ?: return
        val rowField = XposedHelpers.findFieldIfExists(entryField.type, "row") ?: return
        val isDozing = XposedHelpers.findMethodExactIfExists(rowField.type, "isDozing") ?: return
        val cancelTimeout = XposedHelpers.findMethodExactIfExists(alertEntry, "removeAutoRemovalCallbacks") ?: return
        if (isDozing.returnType != Boolean::class.javaPrimitiveType || cancelTimeout.returnType != Void.TYPE) return

        ModuleHelper.hookMethod(updateEntry, object : MethodHook() {
            override fun after(param: AfterHookCallback) {
                if (param.throwable != null || param.isSkipped || !MainModule.mPrefs.getBoolean("system_betterpopups_nohide")) return
                val owner = param.thisObject ?: return
                val entry = entryField.get(owner) ?: return
                val row = rowField.get(entry) ?: return
                if (isDozing.invoke(row) != false) return
                // Cancel the timer from this update. Explicit removal can still schedule its own callback.
                cancelTimeout.invoke(owner)
            }
        })
    }

    @JvmStatic
    fun BetterPopupsSwipeDownHook(lpparam: PackageReadyParam) {
        ModuleHelper.findAndHookMethod("com.android.systemui.statusbar.notification.policy.AppMiniWindowManager", lpparam.classLoader, "canNotificationSlide", String::class.java, PendingIntent::class.java, HookerClassHelper.returnConstant(false))
    }
}
