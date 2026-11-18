package tv.withaibuild.customiuizer.mods

import com.android.systemui.statusbar.notification.ExpandedNotification
import com.android.systemui.statusbar.notification.NotificationEntryManager
import com.android.systemui.statusbar.notification.collection.NotificationEntry
import com.android.systemui.statusbar.notification.policy.NotificationCountLimitPolicy
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

class MoreNotificationsRefinementTest {
    private val manager = NotificationEntryManager()
    private val policy = NotificationCountLimitPolicy(manager)
    private val targetPackage = "com.example.chat"
    private val preference = "system_morenotif"

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

    @Test fun retainsTwentyFourForThePostingPackageAndDelegatesOverflowOnce() {
        install()
        assertEquals(
            policy.javaClass.getDeclaredMethod("checkNotificationCountLimit", String::class.java),
            FakeXposedInterface.recordedHooks.single().executable,
        )
        val first = add()
        repeat(23) { add() }
        repeat(12) { add("com.example.other") }
        checkLimit()
        assertEquals(0, policy.checks)
        assertEquals(0, policy.eligibilityCalls)
        assertEquals(36, manager.entries.size)

        add()
        checkLimit()
        assertEquals(1, policy.checks)
        assertSame(first, policy.removed)
        assertEquals(36, manager.entries.size)
    }

    @Test fun reusesTheRomCandidateFilterWhenItIsPresent() {
        install()
        val important = add()
        policy.important.add(important)
        repeat(24) { add() }
        checkLimit()
        assertEquals(0, policy.checks)

        add()
        checkLimit()
        assertEquals(1, policy.checks)
        assertTrue(manager.entries.contains(important))
        assertEquals(25, manager.entries.size)
    }

    @Test fun disablingTheFeatureRestoresTheOriginalLimit() {
        MainModule.mPrefs[preference] = false
        install()
        assertTrue(FakeXposedInterface.recordedHooks.isEmpty())
        MainModule.mPrefs[preference] = true
        install()
        repeat(11) { add() }
        MainModule.mPrefs[preference] = false
        checkLimit()
        assertEquals(1, policy.checks)
        assertEquals(10, manager.entries.size)
    }

    private fun add(packageName: String = targetPackage): NotificationEntry =
        NotificationEntry().apply { mSbn = ExpandedNotification(packageName) }.also(manager.entries::add)

    private fun checkLimit() =
        invokeRecordedRomHook(policy, "checkNotificationCountLimit", listOf(targetPackage))

    private fun install() {
        val loader = javaClass.classLoader!!
        val param = Proxy.newProxyInstance(loader, arrayOf(PackageReadyParam::class.java)) { _, method, _ ->
            if (method.name == "getClassLoader") loader else null
        } as PackageReadyParam
        SystemNotificationMoreHooks.MoreNotificationsHook(param)
    }
}
