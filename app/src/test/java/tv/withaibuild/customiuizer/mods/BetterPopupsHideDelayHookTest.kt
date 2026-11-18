package tv.withaibuild.customiuizer.mods

import com.android.systemui.statusbar.policy.HeadsUpManager
import com.android.systemui.statusbar.policy.HeadsUpManager.AccessibilityManagerWrapper
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import tv.withaibuild.customiuizer.MainModule
import tv.withaibuild.customiuizer.installers.SystemUiInstaller
import tv.withaibuild.customiuizer.mods.utils.XposedHelpers
import tv.withaibuild.customiuizer.utils.FakeXposedInterface
import tv.withaibuild.customiuizer.utils.PrefMap
import java.lang.reflect.Proxy

class BetterPopupsHideDelayHookTest {
    @Before fun setUp() {
        FakeXposedInterface.reset()
        XposedHelpers.moduleInst = FakeXposedInterface.create()
        MainModule.mPrefs = PrefMap()
    }

    @After fun tearDown() {
        FakeXposedInterface.reset()
        XposedHelpers.moduleInst = null
        MainModule.mPrefs = PrefMap()
    }

    @Test fun hooksTheNativeEntryAndReadsCurrentDelayWithAccessibilityFlags() {
        MainModule.mPrefs[delayKey] = 6
        assertTrue(SystemUiInstaller.hasAnySystemUiStartupFeature(MainModule.mPrefs))
        install()
        val target = FakeXposedInterface.recordedHooks.single().executable
        assertEquals(HeadsUpManager.HeadsUpEntry::class.java.getDeclaredMethod(method, Int::class.javaPrimitiveType), target)
        var wrapperCalls = 0
        val owner = HeadsUpManager(AccessibilityManagerWrapper { baseMs, flags ->
            assertEquals(7, flags)
            wrapperCalls++
            baseMs * 2
        }).HeadsUpEntry(1800)

        assertEquals(12000, timeout(owner))
        MainModule.mPrefs[delayKey] = 8
        assertEquals(16000, timeout(owner))
        MainModule.mPrefs["system_betterpopups_nohide"] = true
        assertEquals(1800, timeout(owner))
        MainModule.mPrefs["system_betterpopups_nohide"] = false
        MainModule.mPrefs[delayKey] = 0
        assertEquals(1800, timeout(owner))
        assertEquals(2, wrapperCalls)
    }

    @Test fun defaultInvalidAndNoHideSettingsInstallNoDelayHook() {
        for (value in listOf(0, -1, 61)) {
            MainModule.mPrefs[delayKey] = value
            install()
            assertFalse(SystemUiInstaller.hasAnySystemUiStartupFeature(MainModule.mPrefs))
        }
        MainModule.mPrefs[delayKey] = 5
        MainModule.mPrefs["system_betterpopups_nohide"] = true
        install()
        assertTrue(FakeXposedInterface.recordedHooks.isEmpty())
    }

    @Test fun ordinaryWrapperFailureReturnsToNativeAndFatalFailurePropagates() {
        MainModule.mPrefs[delayKey] = 6
        install()
        val ordinary = HeadsUpManager(AccessibilityManagerWrapper { _, _ -> error("accessibility unavailable") })
            .HeadsUpEntry(1800)
        assertEquals(1800, timeout(ordinary))
        val fatal = OutOfMemoryError("host failure")
        val failing = HeadsUpManager(AccessibilityManagerWrapper { _, _ -> throw fatal }).HeadsUpEntry(1800)
        assertSame(fatal, assertThrows(OutOfMemoryError::class.java) { timeout(failing) })
    }

    private fun install() {
        val loader = javaClass.classLoader!!
        val param = Proxy.newProxyInstance(loader, arrayOf(PackageReadyParam::class.java)) { _, method, _ ->
            if (method.name == "getClassLoader") loader else null
        } as PackageReadyParam
        SystemNotificationPopupsHooks.BetterPopupsHideDelayHook(param)
    }

    private fun timeout(entry: HeadsUpManager.HeadsUpEntry): Any? =
        invokeRecordedRomHook(entry, method, listOf(5000))

    private val delayKey = "system_betterpopups_delay"
    private val method = "getRecommendedHeadsUpTimeoutMs"
}
