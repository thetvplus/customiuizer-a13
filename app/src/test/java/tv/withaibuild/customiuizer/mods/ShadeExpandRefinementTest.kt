package tv.withaibuild.customiuizer.mods

import com.android.systemui.statusbar.notification.ExpandedNotification
import com.android.systemui.statusbar.notification.collection.NotificationEntry
import com.android.systemui.statusbar.notification.row.ExpandableNotificationRow
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

class ShadeExpandRefinementTest {
    private val row = ExpandableNotificationRow()
    private val entry = NotificationEntry().apply { mSbn = ExpandedNotification("com.example.chat") }

    @Before fun setUp() {
        FakeXposedInterface.reset()
        XposedHelpers.moduleInst = FakeXposedInterface.create()
        MainModule.mPrefs = PrefMap()
        MainModule.mPrefs["system_expandnotifs"] = "2"
        row.mEntry = entry
    }

    @After fun tearDown() {
        FakeXposedInterface.reset()
        XposedHelpers.moduleInst = null
        MainModule.mPrefs = PrefMap()
    }

    @Test fun selectedRowsReceiveOneEffectiveExpansionAcrossNativeRefreshes() {
        install()
        assertEquals(
            row.javaClass.getDeclaredMethod("setSystemExpanded", Boolean::class.javaPrimitiveType),
            FakeXposedInterface.recordedHooks.single().executable,
        )
        repeat(3) {
            var calls = 0
            invokeRecordedRomHook(row, "setSystemExpanded", listOf(false)) { args ->
                calls++
                row.setSystemExpanded(args.single() as Boolean)
                null
            }
            assertEquals(1, calls)
            assertTrue(row.systemExpandedState)
        }
        assertEquals(1, row.systemExpansionChanges)
    }

    @Test fun appliesBothListModesAndRespectsNativeTrueAndLockscreen() {
        install()
        MainModule.mPrefs["system_expandnotifs_apps"] = setOf("com.example.chat")
        refresh(false)
        assertFalse(row.systemExpandedState)
        refresh(true)
        assertTrue(row.systemExpandedState)

        MainModule.mPrefs["system_expandnotifs"] = "3"
        row.onKeyguard = true
        refresh(false)
        assertFalse(row.systemExpandedState)
        row.onKeyguard = false
        refresh(false)
        assertTrue(row.systemExpandedState)
        row.mEntry = null
        refresh(false)
        assertFalse(row.systemExpandedState)
    }

    @Test fun disabledModeAndOriginalFailureKeepTheirSemantics() {
        MainModule.mPrefs["system_expandnotifs"] = "bad"
        install()
        assertTrue(FakeXposedInterface.recordedHooks.isEmpty())
        MainModule.mPrefs["system_expandnotifs"] = "2"
        install()
        val failure = IllegalStateException("native setter")
        assertSame(failure, assertThrows(IllegalStateException::class.java) {
            invokeRecordedRomHook(row, "setSystemExpanded", listOf(false)) { throw failure }
        })
        MainModule.mPrefs["system_expandnotifs"] = "1"
        refresh(false)
        assertFalse(row.systemExpandedState)
    }

    private fun refresh(expanded: Boolean) = invokeRecordedRomHook(row, "setSystemExpanded", listOf(expanded))

    private fun install() {
        val loader = javaClass.classLoader!!
        val param = Proxy.newProxyInstance(loader, arrayOf(PackageReadyParam::class.java)) { _, method, _ ->
            if (method.name == "getClassLoader") loader else null
        } as PackageReadyParam
        SystemStatusBarClockAndMoreHooks.ExpandNotificationsHook(param)
    }
}
