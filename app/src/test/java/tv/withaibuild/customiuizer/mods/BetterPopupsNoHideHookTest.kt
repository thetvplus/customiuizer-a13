package tv.withaibuild.customiuizer.mods

import com.android.systemui.statusbar.notification.collection.NotificationEntry
import com.android.systemui.statusbar.notification.row.ExpandableNotificationRow
import com.android.systemui.statusbar.phone.HeadsUpManagerPhone.HeadsUpEntryPhone
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

class BetterPopupsNoHideHookTest {
    private val preference = "system_betterpopups_nohide"

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

    private fun install(loader: ClassLoader = javaClass.classLoader!!) {
        val param = Proxy.newProxyInstance(loader, arrayOf(PackageReadyParam::class.java)) { _, method, _ ->
            if (method.name == "getClassLoader") loader else null
        } as PackageReadyParam
        SystemNotificationPopupsHooks.BetterPopupsNoHideHook(param)
    }

    private fun update(entry: HeadsUpEntryPhone) =
        invokeRecordedRomHook(entry, "updateEntry", listOf(true))

    @Test fun cancelsTheAwakeEntryTimerAfterNativeUpdateAndLeavesExplicitRemovalAvailable() {
        MainModule.mPrefs[preference] = true
        install()
        val method = FakeXposedInterface.recordedHooks.single().executable
        assertEquals(HeadsUpEntryPhone::class.java.getDeclaredMethod("updateEntry", Boolean::class.javaPrimitiveType), method)
        val entry = HeadsUpEntryPhone(NotificationEntry().apply { row = ExpandableNotificationRow() })

        update(entry)
        assertEquals(1, entry.updates)
        assertFalse(entry.timerPending)
        entry.removeAsSoonAsPossible()
        assertTrue(entry.timerPending)
    }

    @Test fun leavesNativeTimersForDozeMissingRowAndDisabledPreference() {
        MainModule.mPrefs[preference] = true
        install()
        val row = ExpandableNotificationRow().apply { dozing = true }
        val dozing = HeadsUpEntryPhone(NotificationEntry().apply { this.row = row })
        val unbound = HeadsUpEntryPhone(NotificationEntry())
        update(dozing)
        update(unbound)
        assertTrue(dozing.timerPending)
        assertTrue(unbound.timerPending)

        MainModule.mPrefs[preference] = false
        row.dozing = false
        update(dozing)
        assertTrue(dozing.timerPending)
        assertEquals(1, FakeXposedInterface.recordedHooks.size)
    }

    @Test fun preservesNativeUpdateFailureAndPendingRemoval() {
        MainModule.mPrefs[preference] = true
        install()
        val entry = HeadsUpEntryPhone(NotificationEntry().apply { row = ExpandableNotificationRow() })
        entry.removeAsSoonAsPossible()
        val failure = IllegalStateException("native update failed")
        assertSame(failure, assertThrows(IllegalStateException::class.java) {
            invokeRecordedRomHook(entry, "updateEntry", listOf(true)) { throw failure }
        })
        assertTrue(entry.timerPending)
    }
}
