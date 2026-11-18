package tv.withaibuild.customiuizer.mods

import com.android.systemui.statusbar.notification.ExpandedNotification
import com.android.systemui.statusbar.notification.NotificationSettingsHelper
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import tv.withaibuild.customiuizer.MainModule
import tv.withaibuild.customiuizer.mods.utils.XposedHelpers
import tv.withaibuild.customiuizer.utils.FakeXposedInterface
import tv.withaibuild.customiuizer.utils.PrefMap
import java.lang.reflect.Proxy

class KeepLockscreenNotificationsRefinementTest {
    private val preference = "system_notifafterunlock"
    private val policyMethod = "alwaysShowKeyguardNotifications"

    @Before fun setUp() {
        FakeXposedInterface.reset()
        XposedHelpers.moduleInst = FakeXposedInterface.create()
        MainModule.mPrefs = PrefMap()
        MainModule.mPrefs[preference] = true
    }

    @After fun tearDown() {
        FakeXposedInterface.reset()
        XposedHelpers.moduleInst = null
        MainModule.mPrefs = PrefMap()
    }

    @Test fun changesOnlyTheNativeUnlockRetentionDecision() {
        install()
        assertEquals(
            NotificationSettingsHelper.javaClass.getDeclaredMethod(policyMethod),
            FakeXposedInterface.recordedHooks.single().executable,
        )
        var nativeCalls = 0
        assertEquals(true, invokeRecordedRomHook(NotificationSettingsHelper, policyMethod, emptyList()) {
            nativeCalls++
            false
        })
        assertEquals(1, nativeCalls)
        MainModule.mPrefs[preference] = false
        assertEquals(false, invokeRecordedRomHook(NotificationSettingsHelper, policyMethod, emptyList()))
    }

    @Test fun disabledFeatureInstallsNothingAndOriginalFailurePropagates() {
        MainModule.mPrefs[preference] = false
        install()
        assertTrue(FakeXposedInterface.recordedHooks.isEmpty())
        MainModule.mPrefs[preference] = true
        install()
        val failure = IllegalStateException("native retention policy")
        assertSame(failure, assertThrows(IllegalStateException::class.java) {
            invokeRecordedRomHook(NotificationSettingsHelper, policyMethod, emptyList()) { throw failure }
        })
    }

    @Test fun legacyGetterFallbackLeavesTheStoredMarkerOwnedBySystemUi() {
        val loader = object : ClassLoader(javaClass.classLoader) {
            override fun loadClass(name: String, resolve: Boolean): Class<*> {
                if (name == NotificationSettingsHelper.javaClass.name) throw ClassNotFoundException(name)
                return super.loadClass(name, resolve)
            }
        }
        install(loader)
        val notification = ExpandedNotification()
        assertEquals(
            notification.javaClass.getDeclaredMethod("hasShownAfterUnlock"),
            FakeXposedInterface.recordedHooks.single().executable,
        )
        notification.setHasShownAfterUnlock(true)
        assertEquals(false, invokeRecordedRomHook(notification, "hasShownAfterUnlock", emptyList()))
        MainModule.mPrefs[preference] = false
        assertEquals(true, invokeRecordedRomHook(notification, "hasShownAfterUnlock", emptyList()))
    }

    private fun install(loader: ClassLoader = javaClass.classLoader!!) {
        val param = Proxy.newProxyInstance(javaClass.classLoader, arrayOf(PackageReadyParam::class.java)) { _, method, _ ->
            if (method.name == "getClassLoader") loader else null
        } as PackageReadyParam
        SystemNotificationMoreHooks.ShowNotificationsAfterUnlockHook(param)
    }
}
