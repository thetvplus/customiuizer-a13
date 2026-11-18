package tv.withaibuild.customiuizer.mods

import android.app.PendingIntent
import android.provider.Settings
import com.android.systemui.statusbar.notification.NotificationSettingsManager
import com.android.systemui.statusbar.notification.policy.AppMiniWindowManager
import com.android.systemui.statusbar.notification.policy.MiniWindowPolicy
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
import io.github.libxposed.api.XposedModuleInterface.SystemServerStartingParam
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import tv.withaibuild.customiuizer.MainModule
import tv.withaibuild.customiuizer.installers.SystemServerInstaller
import tv.withaibuild.customiuizer.installers.SystemUiInstaller
import tv.withaibuild.customiuizer.mods.catalog.FeatureDispatcher
import tv.withaibuild.customiuizer.mods.catalog.FeatureInstallRegistry
import tv.withaibuild.customiuizer.mods.utils.XposedHelpers
import tv.withaibuild.customiuizer.utils.FakeXposedInterface
import tv.withaibuild.customiuizer.utils.PrefMap
import java.lang.reflect.Proxy

class PopupSwipeRefinementTest {
    @Before fun setUp() {
        FeatureInstallRegistry.clearStatesForTesting()
        FakeXposedInterface.reset()
        XposedHelpers.moduleInst = FakeXposedInterface.create()
        MainModule.mPrefs = PrefMap()
    }

    @After fun tearDown() {
        FeatureInstallRegistry.clearStatesForTesting()
        FakeXposedInterface.reset()
        XposedHelpers.moduleInst = null
        MainModule.mPrefs = PrefMap()
    }

    @Test fun onlyThePackageListDecisionChangesAndItFollowsUpdatedSelections() {
        MainModule.mPrefs[allowFloat] = true
        MainModule.mPrefs["${allowFloat}_apps"] = setOf("com.example.selected", "com.example.both")
        MainModule.mPrefs["${allowFloat}_apps_black"] = setOf("com.example.stock", "com.example.both")
        SystemFreeformAndMultiWindowHooks.BetterPopupsAllowFloatHook(packageParam)
        val hook = FakeXposedInterface.recordedHooks.single().executable
        assertEquals(NotificationSettingsManager::class.java.getDeclaredMethod("canSlide", String::class.java), hook)
        assertNull(FakeXposedInterface.findHook(MiniWindowPolicy::class.java.name, "canSlidePackage"))
        assertEquals(true, canSlide("com.example.selected"))
        assertEquals(true, canSlide("com.example.both"))
        assertEquals(false, canSlide("com.example.stock"))
        assertEquals(false, canSlide("com.example.other"))

        val failure = IllegalStateException("native package decision")
        assertSame(failure, assertThrows(IllegalStateException::class.java) {
            invokeRecordedRomHook(NotificationSettingsManager, "canSlide", listOf("com.example.selected")) { throw failure }
        })
        MainModule.mPrefs["${allowFloat}_apps"] = emptySet<String>()
        assertEquals(false, canSlide("com.example.selected"))
        MainModule.mPrefs[allowFloat] = false
        assertEquals(true, canSlide("com.example.stock"))
    }

    @Test fun drawerSwitchTakesPrecedenceOverTheAppListAtTheGestureEntry() {
        SystemFreeformAndMultiWindowHooks.BetterPopupsAllowFloatHook(packageParam)
        SystemNotificationPopupsHooks.BetterPopupsSwipeDownHook(packageParam)
        assertTrue(FakeXposedInterface.recordedHooks.isEmpty())

        MainModule.mPrefs[allowFloat] = true
        MainModule.mPrefs["${allowFloat}_apps"] = setOf("com.example.selected")
        SystemFreeformAndMultiWindowHooks.BetterPopupsAllowFloatHook(packageParam)
        MainModule.mPrefs["system_betterpopups_swipedown"] = true
        SystemNotificationPopupsHooks.BetterPopupsSwipeDownHook(packageParam)
        val manager = AppMiniWindowManager.getInstance()
        val pending = PendingIntent("com.example.selected")
        assertTrue(manager.canNotificationSlide("com.example.selected", pending))
        assertEquals(false, invokeRecordedRomHook(manager, "canNotificationSlide", listOf("com.example.selected", pending)) {
            fail("The mini-window path is skipped when the drawer switch is enabled")
        })
    }

    @Test fun swipeAppListAloneKeepsSystemUiAndSystemServerLaunchPathsNative() {
        MainModule.mPrefs[allowFloat] = true
        Settings.System.setOverrideLong(0L)
        try {
            SystemUiInstaller.install(packageParam) {}
        } finally {
            Settings.System.setOverrideLong(Long.MAX_VALUE)
        }
        assertNotNull(FakeXposedInterface.findHook(NotificationSettingsManager::class.java.name, "canSlide"))
        assertNull(FakeXposedInterface.findHook(AppMiniWindowManager::class.java.name, "launchMiniWindowActivity"))

        val server = refinementRuntime(MainModule.mPrefs)
        assertFalse(FeatureDispatcher.installById("openAppInFreeForm", server))
        SystemServerInstaller.install(server.lpparam as SystemServerStartingParam)
        assertNull(FakeXposedInterface.findHook("com.android.server.wm.ActivityStarter", "executeRequest"))
    }

    private fun canSlide(pkgName: String): Any? =
        invokeRecordedRomHook(NotificationSettingsManager, "canSlide", listOf(pkgName))

    private val allowFloat = "system_betterpopups_allowfloat"
    private val loader = javaClass.classLoader!!
    private val packageParam = Proxy.newProxyInstance(loader, arrayOf(PackageReadyParam::class.java)) { _, method, _ ->
        when (method.name) {
            "getClassLoader" -> loader
            "getPackageName", "getProcessName" -> "com.android.systemui"
            "isFirstPackage" -> true
            else -> null
        }
    } as PackageReadyParam
}
