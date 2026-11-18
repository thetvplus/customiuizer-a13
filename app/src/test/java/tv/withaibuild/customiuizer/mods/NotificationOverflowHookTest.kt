package tv.withaibuild.customiuizer.mods

import com.android.systemui.statusbar.phone.NotificationIconAreaController
import com.android.systemui.statusbar.phone.NotificationIconContainer
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import tv.withaibuild.customiuizer.MainModule
import tv.withaibuild.customiuizer.mods.catalog.FeatureDispatcher
import tv.withaibuild.customiuizer.mods.catalog.FeatureInstallRegistry
import tv.withaibuild.customiuizer.mods.utils.XposedHelpers
import tv.withaibuild.customiuizer.utils.FakeXposedInterface
import tv.withaibuild.customiuizer.utils.PrefMap
import java.lang.reflect.Proxy

class NotificationOverflowHookTest {
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

    @Test fun initialObserverCallbackClearsOnlyStatusBarDots() {
        enableOverflowHook()
        val hooked = FakeXposedInterface.recordedHooks.single().executable
        assertEquals(NotificationIconAreaController::class.java.getDeclaredMethod(observer, Boolean::class.javaPrimitiveType), hooked)
        val controller = NotificationIconAreaController()
        val statusBar = requireNotNull(controller.mNotificationIcons)
        val shelf = NotificationIconContainer()

        invokeRecordedRomHook(controller, observer, listOf(true))
        assertEquals(0, statusBar.renderedDotLimit)
        assertEquals(3, statusBar.MAX_STATIC_ICONS)
        assertEquals(3, statusBar.MAX_ICONS_ON_LOCKSCREEN)
        assertEquals(2, statusBar.layoutUpdates)
        assertEquals(3, controller.mAodIcons.MAX_DOTS)
        assertEquals(3, shelf.MAX_DOTS)
        assertEquals(0, controller.mAodIcons.layoutUpdates)
        assertEquals(0, shelf.layoutUpdates)
    }

    @Test fun maximumIconsKeepsItsCountWhenNativeVisibilityIsToggled() {
        enableOverflowHook()
        MainModule.mPrefs["system_maxsbicons"] = "7"
        SystemNotificationMoreHooks.MaxNotificationIconsHook(packageParam)
        val controller = NotificationIconAreaController()
        val statusBar = requireNotNull(controller.mNotificationIcons)

        fun dispatch(show: Boolean) {
            invokeRecordedRomHook(controller, observer, listOf(show)) {
                // Dispatch the nested container hook in the JVM's recorded-hook model.
                invokeRecordedRomHook(statusBar, "miuiShowNotificationIcons", it)
                null
            }
        }

        dispatch(true)
        assertEquals(7, statusBar.MAX_STATIC_ICONS)
        assertEquals(7, statusBar.MAX_ICONS_ON_LOCKSCREEN)
        assertEquals(0, statusBar.renderedDotLimit)
        dispatch(false)
        assertEquals(0, statusBar.MAX_STATIC_ICONS)
        assertEquals(0, statusBar.MAX_ICONS_ON_LOCKSCREEN)
        assertEquals(3, statusBar.layoutUpdates)
        dispatch(true)
        assertEquals(7, statusBar.MAX_STATIC_ICONS)
        assertEquals(0, statusBar.renderedDotLimit)
        MainModule.mPrefs["system_maxsbicons"] = "0"
        dispatch(true)
        assertEquals(3, statusBar.MAX_STATIC_ICONS)
        assertEquals(3, statusBar.MAX_ICONS_ON_LOCKSCREEN)
        assertEquals(0, statusBar.renderedDotLimit)
    }

    @Test fun iconLimitSkipsInstallationAtDefaultAndUsesNativeResultForInvalidValues() {
        SystemNotificationMoreHooks.MaxNotificationIconsHook(packageParam)
        assertTrue(FakeXposedInterface.recordedHooks.isEmpty())

        MainModule.mPrefs["system_maxsbicons"] = "-1"
        SystemNotificationMoreHooks.MaxNotificationIconsHook(packageParam)
        val statusBar = NotificationIconContainer()
        val visibility = "miuiShowNotificationIcons"
        assertEquals(statusBar.javaClass.getDeclaredMethod(visibility, Boolean::class.javaPrimitiveType),
            FakeXposedInterface.recordedHooks.single().executable)
        invokeRecordedRomHook(statusBar, visibility, listOf(true))
        assertEquals(999, statusBar.MAX_STATIC_ICONS)
        assertEquals(1, statusBar.layoutUpdates)

        MainModule.mPrefs["system_maxsbicons"] = "-2"
        invokeRecordedRomHook(statusBar, visibility, listOf(true))
        assertEquals(3, statusBar.MAX_STATIC_ICONS)
        assertEquals(2, statusBar.layoutUpdates)
    }

    @Test fun disabledFeatureInstallsNothingAndNativeFailureKeepsTheOriginalState() {
        assertFalse(installOverflowHook())
        assertTrue(FakeXposedInterface.recordedHooks.isEmpty())
        enableOverflowHook()
        val controller = NotificationIconAreaController()
        val statusBar = requireNotNull(controller.mNotificationIcons)
        val failure = IllegalStateException("native notification visibility")
        assertSame(failure, assertThrows(IllegalStateException::class.java) {
            invokeRecordedRomHook(controller, observer, listOf(true)) { throw failure }
        })
        assertEquals(3, statusBar.MAX_DOTS)
        assertEquals(0, statusBar.layoutUpdates)
    }

    private fun enableOverflowHook() {
        MainModule.mPrefs["system_hidemoreicon"] = true
        assertTrue(installOverflowHook())
    }

    private fun installOverflowHook(): Boolean = FeatureDispatcher.installById(
        "noMoreIcon",
        FeatureDispatcher.createRuntime("com.android.systemui", packageParam, loader, MainModule.mPrefs)
    )

    private val observer = "onNotificationIconChanged"
    private val loader = javaClass.classLoader!!
    private val packageParam = Proxy.newProxyInstance(loader, arrayOf(PackageReadyParam::class.java)) { _, method, _ ->
        when (method.name) {
            "getPackageName", "getProcessName" -> "com.android.systemui"
            "getClassLoader" -> loader
            "isFirstPackage" -> true
            else -> null
        }
    } as PackageReadyParam
}
