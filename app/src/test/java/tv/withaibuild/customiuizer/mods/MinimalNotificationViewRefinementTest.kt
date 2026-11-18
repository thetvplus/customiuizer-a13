package tv.withaibuild.customiuizer.mods

import android.app.Notification
import android.app.NotificationManager
import com.android.systemui.statusbar.notification.ExpandedNotification
import com.android.systemui.statusbar.notification.collection.NotificationEntry
import com.android.systemui.statusbar.notification.collection.legacy.LowPriorityInflationHelper
import com.android.systemui.statusbar.notification.collection.legacy.NotificationGroupManagerLegacy
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

class MinimalNotificationViewRefinementTest {
    private val groups = NotificationGroupManagerLegacy()
    private val helper = LowPriorityInflationHelper(groups)
    private val preference = "system_minimalnotifview"
    private val decisionMethod = "shouldUseLowPriorityView"

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

    @Test fun usesCurrentMinimumImportanceAndKeepsGroupedNotificationsInNativeLayout() {
        enableAndInstall()
        assertEquals(
            helper.javaClass.getDeclaredMethod(decisionMethod, NotificationEntry::class.java),
            FakeXposedInterface.recordedHooks.single().executable,
        )
        val notification = Notification()
        val entry = NotificationEntry().apply {
            importance = NotificationManager.IMPORTANCE_MIN
            mSbn = ExpandedNotification().apply { this.notification = notification }
        }
        assertEquals(true, useLowPriority(entry))
        entry.importance = NotificationManager.IMPORTANCE_LOW
        assertEquals(false, useLowPriority(entry))
        entry.importance = NotificationManager.IMPORTANCE_MIN
        groups.groupedChildren.add(entry)
        assertEquals(false, useLowPriority(entry))
        groups.groupedChildren.clear()
        notification.flags = Notification.FLAG_GROUP_SUMMARY
        assertEquals(false, useLowPriority(entry))
    }

    @Test fun keepsTheNativeDecisionAndFailure() {
        install()
        assertTrue(FakeXposedInterface.recordedHooks.isEmpty())
        enableAndInstall()
        val entry = NotificationEntry()
        assertEquals(false, useLowPriority(entry))
        helper.nativeDecision = true
        assertEquals(true, useLowPriority(entry))
        val failure = IllegalStateException("native bind decision")
        assertSame(failure, assertThrows(IllegalStateException::class.java) {
            invokeRecordedRomHook(helper, decisionMethod, listOf(entry)) { throw failure }
        })
    }

    private fun useLowPriority(entry: NotificationEntry) =
        invokeRecordedRomHook(helper, decisionMethod, listOf(entry))

    private fun enableAndInstall() {
        MainModule.mPrefs[preference] = true
        install()
    }

    private fun install() {
        val loader = javaClass.classLoader!!
        val param = Proxy.newProxyInstance(loader, arrayOf(PackageReadyParam::class.java)) { _, method, _ ->
            if (method.name == "getClassLoader") loader else null
        } as PackageReadyParam
        SystemNotificationMoreHooks.MinimalNotificationViewHook(param)
    }
}
