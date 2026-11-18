package tv.withaibuild.customiuizer.mods

import android.os.Handler
import com.android.systemui.statusbar.AlertingNotificationManager.AlertEntry
import com.android.systemui.statusbar.notification.ExpandedNotification
import com.android.systemui.statusbar.notification.collection.NotificationEntry
import com.android.systemui.statusbar.notification.row.ExpandableNotificationRow
import com.android.systemui.statusbar.policy.HeadsUpManager
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

class HeadsUpAutoExpandRefinementTest {
    private val queued = mutableListOf<Runnable>()
    private val handler = object : Handler() {
        override fun postDelayed(r: Runnable, delayMillis: Long): Boolean {
            assertEquals(60L, delayMillis)
            queued.add(r)
            return true
        }
    }
    private val manager = HeadsUpManager(HeadsUpManager.AccessibilityManagerWrapper { base, _ -> base }, handler)
    private val row = ExpandableNotificationRow()
    private val entry = NotificationEntry().apply {
        row = this@HeadsUpAutoExpandRefinementTest.row
        mSbn = ExpandedNotification("com.example.chat")
    }
    private val alert = manager.HeadsUpEntry(0).apply { mEntry = entry }

    @Before fun setUp() {
        FakeXposedInterface.reset()
        XposedHelpers.moduleInst = FakeXposedInterface.create()
        MainModule.mPrefs = PrefMap()
        MainModule.mPrefs["system_expandheadups"] = "2"
        row.mEntry = entry
        manager.active[entry.key] = alert
    }

    @After fun tearDown() {
        FakeXposedInterface.reset()
        XposedHelpers.moduleInst = null
        MainModule.mPrefs = PrefMap()
    }

    @Test fun queuesOneNativeClickAfterAdmissionRatherThanRowRefresh() {
        install()
        val hooked = FakeXposedInterface.recordedHooks.single().executable
        assertEquals(HeadsUpManager::class.java.getDeclaredMethod("onAlertEntryAdded", AlertEntry::class.java), hooked)
        admit()
        repeat(3) { row.setHeadsUp(true) }
        assertTrue(row.pinned)
        assertEquals(0, row.expansionClicks)
        queued.single().run()
        assertTrue(row.mExpandedWhenPinned)
        assertEquals(1, row.expansionClicks)
    }

    @Test fun pooledAlertReuseAndReboundRowInvalidateTheOldClick() {
        install()
        admit()
        val oldClick = queued.removeAt(0)
        alert.mRemoveAlertRunnable = Runnable {} // Same alert wrapper and key, new admission.
        admit()
        oldClick.run()
        assertEquals(0, row.expansionClicks)
        row.mEntry = NotificationEntry()
        queued.single().run()
        assertEquals(0, row.expansionClicks)
    }

    @Test fun preservesManualExpansionForPinnedRowsAndGroups() {
        install()
        admit()
        row.mExpandedWhenPinned = true
        queued.removeAt(0).run()
        assertTrue(row.mExpandedWhenPinned)
        row.summary = true
        row.groupExpanded = true
        admit()
        queued.removeAt(0).run()
        assertEquals(0, row.expansionClicks)
        row.groupExpanded = false
        admit()
        queued.single().run()
        assertTrue(row.groupExpanded)
    }

    @Test fun checksLiveEligibilityAndKeepsOriginalFailures() {
        install()
        val failure = IllegalStateException("native admission")
        assertSame(failure, assertThrows(IllegalStateException::class.java) {
            invokeRecordedRomHook(manager, "onAlertEntryAdded", listOf(alert)) { throw failure }
        })
        assertTrue(queued.isEmpty())

        admit()
        row.onKeyguard = true
        queued.removeAt(0).run()
        row.onKeyguard = false
        admit()
        MainModule.mPrefs["system_expandheadups"] = "3"
        queued.removeAt(0).run()
        assertEquals(0, row.expansionClicks)
        admit()
        assertTrue(queued.isEmpty())
        MainModule.mPrefs["system_expandheadups_apps"] = setOf("com.example.chat")
        admit()
        queued.single().run()
        assertEquals(1, row.expansionClicks)
    }

    @Test fun disabledSettingDoesNotInstall() {
        MainModule.mPrefs["system_expandheadups"] = "invalid"
        install()
        assertTrue(FakeXposedInterface.recordedHooks.isEmpty())
    }

    private fun admit() = invokeRecordedRomHook(manager, "onAlertEntryAdded", listOf(alert))

    private fun install() {
        val loader = javaClass.classLoader!!
        val param = Proxy.newProxyInstance(loader, arrayOf(PackageReadyParam::class.java)) { _, method, _ ->
            if (method.name == "getClassLoader") loader else null
        } as PackageReadyParam
        SystemStatusBarClockAndMoreHooks.ExpandHeadsUpHook(param)
    }
}
