package tv.withaibuild.customiuizer.mods

import android.app.PendingIntent
import android.view.accessibility.AccessibilityManager
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
import tv.withaibuild.customiuizer.MainModule
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper.AfterHookCallback
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper.BeforeHookCallback
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper.MethodHook
import tv.withaibuild.customiuizer.mods.utils.ModuleHelper
import tv.withaibuild.customiuizer.mods.utils.XposedHelpers
import tv.withaibuild.customiuizer.utils.PrefMap

object SystemNotificationPopupsHooks {

    @JvmStatic
    fun hasCustomHideDelay(prefs: PrefMap<String, Any>): Boolean =
        customHideDelayMs(prefs) != 0

    private fun customHideDelayMs(prefs: PrefMap<String, Any>): Int {
        val seconds = prefs.getInt("system_betterpopups_delay", 0)
        return if (seconds in 1..60 && !prefs.getBoolean("system_betterpopups_nohide")) seconds * 1000 else 0
    }

    @JvmStatic
    fun BetterPopupsHideDelayHook(lpparam: PackageReadyParam) {
        if (!hasCustomHideDelay(MainModule.mPrefs)) return
        val timeout = XposedHelpers.findMethodExactIfExists(
            "com.android.systemui.statusbar.policy.HeadsUpManager\$HeadsUpEntry",
            lpparam.classLoader, "getRecommendedHeadsUpTimeoutMs", Int::class.javaPrimitiveType
        ) ?: return
        val manager = XposedHelpers.findFieldIfExists(timeout.declaringClass, "this\$0") ?: return
        val accessibility = XposedHelpers.findFieldIfExists(manager.type, "mAccessibilityMgr") ?: return
        val recommend = XposedHelpers.findMethodExactIfExists(
            accessibility.type, "getRecommendedTimeoutMillis", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType
        ) ?: return
        if (timeout.returnType != Int::class.javaPrimitiveType || recommend.returnType != Int::class.javaPrimitiveType) return
        val contentFlags = AccessibilityManager.FLAG_CONTENT_ICONS or
            AccessibilityManager.FLAG_CONTENT_TEXT or AccessibilityManager.FLAG_CONTENT_CONTROLS

        // Reuse the host's accessibility conversion before it applies a per-notification OEM timeout.
        ModuleHelper.hookMethod(timeout, object : MethodHook() {
            override fun before(param: BeforeHookCallback) {
                val delayMs = customHideDelayMs(MainModule.mPrefs)
                if (delayMs == 0) return
                val owner = manager.get(param.thisObject) ?: return
                val wrapper = accessibility.get(owner) ?: return
                val recommendedMs = recommend.invoke(wrapper, delayMs, contentFlags) as Int
                param.returnAndSkip(recommendedMs)
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
        if (!MainModule.mPrefs.getBoolean("system_betterpopups_swipedown")) return
        val canSlide = XposedHelpers.findMethodExactIfExists(
            "com.android.systemui.statusbar.notification.policy.AppMiniWindowManager",
            lpparam.classLoader, "canNotificationSlide", String::class.java, PendingIntent::class.java
        ) ?: return
        if (canSlide.returnType == Boolean::class.javaPrimitiveType) {
            // The native heads-up touch helper takes over when the mini-window gesture declines.
            ModuleHelper.hookMethod(canSlide, HookerClassHelper.returnConstant(false))
        }
    }
}
