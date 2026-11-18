package tv.withaibuild.customiuizer.mods

import android.app.Notification
import android.app.PendingIntent
import com.android.systemui.statusbar.notification.ExpandedNotification
import com.android.systemui.statusbar.notification.collection.NotificationEntry
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

class ExpandedPopupTimerRefinementTest {
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

    @Test fun expansionKeepsNativeStateAndRearmsOnlyOnFirstExpand() {
        enableAndInstall()
        val hooks = FakeXposedInterface.recordedHooks.map { it.executable }
        assertEquals(setOf("isSticky", "updateEntry", "setExpanded"), hooks.map { it.name }.toSet())
        assertTrue(hooks.all { it.declaringClass == HeadsUpEntryPhone::class.java })

        val entry = newEntry()
        setExpanded(entry, true)
        assertTrue(entry.expanded)
        assertTrue(entry.timerPending)
        assertEquals(false, entry.lastUpdatePostTime)
        assertEquals(true, sticky(entry))
        setExpanded(entry, true)
        assertEquals(1, entry.updates)

        setExpanded(entry, false)
        assertFalse(entry.expanded)
        assertTrue(entry.timerPending)
        assertEquals(2, entry.updates)
    }

    @Test fun remoteInputMenuAndNoHideControlTheSameNativeTimer() {
        enableAndInstall()
        val entry = newEntry()
        setExpanded(entry, true)

        entry.remoteInputActive = true
        entry.removeAutoRemovalCallbacks()
        entry.updateEntry(false)
        assertFalse(entry.timerPending)
        entry.remoteInputActive = false
        entry.updateEntry(false)
        assertTrue(entry.timerPending)

        entry.mMenuShownPinned = true
        entry.removeAutoRemovalCallbacks()
        entry.updateEntry(false)
        assertFalse(entry.timerPending)
        entry.mMenuShownPinned = false
        entry.updateEntry(false)
        assertTrue(entry.timerPending)

        MainModule.mPrefs[noHideKey] = true
        entry.updateEntry(false)
        assertFalse(entry.timerPending)
    }

    @Test fun fullscreenAndUnpinnedEntriesUseNativeExpansion() {
        enableAndInstall()
        for (entry in listOf(newEntry(pinned = false), newEntry(fullScreen = PendingIntent("com.example.alarm")))) {
            setExpanded(entry, true)
            assertTrue(entry.expanded)
            assertFalse(entry.timerPending)
            assertEquals(0, entry.updates)
        }
    }

    @Test fun timerScopeRemainsOwnedByOneEntryAndClearsAfterNativeFailure() {
        enableAndInstall()
        val entry = newEntry().apply { expanded = true }
        val other = newEntry().apply { expanded = true }
        val failure = IllegalStateException("native timer update")

        assertSame(failure, assertThrows(IllegalStateException::class.java) {
            invokeRecordedRomHook(entry, "updateEntry", listOf(false)) {
                assertEquals(false, sticky(entry))
                assertEquals(true, sticky(other))
                throw failure
            }
        })
        assertEquals(true, sticky(entry))
    }

    @Test fun disabledOrFailedInstallationLeavesNoPartialHooks() {
        install()
        assertTrue(FakeXposedInterface.recordedHooks.isEmpty())
        MainModule.mPrefs[noHideKey] = true
        enableAndInstall()
        assertTrue(FakeXposedInterface.recordedHooks.isEmpty())
        MainModule.mPrefs[noHideKey] = false
        for (target in listOf("isSticky", "updateEntry", "setExpanded")) {
            FakeXposedInterface.failedHookName = target
            install()
            assertTrue(FakeXposedInterface.recordedHooks.isEmpty())
        }
    }

    private fun newEntry(pinned: Boolean = true, fullScreen: PendingIntent? = null): HeadsUpEntryPhone {
        val expandedNotification = ExpandedNotification().apply {
            notification = Notification().also { it.fullScreenIntent = fullScreen }
        }
        val notificationEntry = NotificationEntry().apply {
            mSbn = expandedNotification
            rowPinned = pinned
        }
        val entry = HeadsUpEntryPhone(notificationEntry)
        // Simulate the nested calls that ART would dispatch through the hooks.
        entry.dispatchUpdate = { updatePostTime ->
            invokeRecordedRomHook(entry, "updateEntry", listOf(updatePostTime)) {
                entry.updateNativeEntry(it[0] as Boolean)
                null
            }
        }
        entry.stickyForTimer = { sticky(entry) }
        return entry
    }

    private fun setExpanded(entry: HeadsUpEntryPhone, value: Boolean) =
        invokeRecordedRomHook(entry, "setExpanded", listOf(value))

    private fun sticky(entry: HeadsUpEntryPhone): Boolean =
        invokeRecordedRomHook(entry, "isSticky", emptyList()) as Boolean

    private fun enableAndInstall() {
        MainModule.mPrefs["system_betterpopups_autoclose_expanded"] = true
        install()
    }

    private fun install() {
        val loader = javaClass.classLoader!!
        val param = Proxy.newProxyInstance(loader, arrayOf(PackageReadyParam::class.java)) { _, method, _ ->
            if (method.name == "getClassLoader") loader else null
        } as PackageReadyParam
        SystemNotificationMoreHooks.AutoDismissExpandedPopupsHook(param)
    }

    private val noHideKey = "system_betterpopups_nohide"
}
