package tv.withaibuild.customiuizer.mods

import android.content.Intent
import android.os.UserHandle
import com.android.systemui.Dependency
import com.android.systemui.statusbar.notification.ExpandedNotification
import com.android.systemui.statusbar.notification.modal.ModalController
import com.android.systemui.statusbar.notification.row.MiuiNotificationMenuRow
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import tv.withaibuild.customiuizer.mods.utils.XposedHelpers
import tv.withaibuild.customiuizer.utils.FakeXposedInterface
import java.lang.reflect.Proxy

class NotificationChannelSettingsHookTest {
    private val loader = javaClass.classLoader!!
    private var originalCalls = 0

    @Before
    fun setUp() {
        FakeXposedInterface.reset()
        Dependency.clear()
        XposedHelpers.moduleInst = FakeXposedInterface.create()
        val param = Proxy.newProxyInstance(loader, arrayOf(PackageReadyParam::class.java)) { _, method, _ ->
            when (method.name) {
                "getClassLoader" -> loader
                "getPackageName", "getProcessName" -> "com.android.systemui"
                else -> null
            }
        } as PackageReadyParam
        SystemNotificationMoreHooks.NotificationChannelSettingsHook(param)
    }

    @After
    fun tearDown() {
        FakeXposedInterface.reset()
        Dependency.clear()
        XposedHelpers.moduleInst = null
    }

    private fun click(entry: Entry, context: SettingsContext = SettingsContext()): SettingsContext {
        val row = MiuiNotificationMenuRow().apply { mParent = Parent(entry) }
        val recorded = requireNotNull(FakeXposedInterface.findHook(
            MiuiNotificationMenuRow::class.java.name, "onClickInfoItem"
        ))
        val chain = Proxy.newProxyInstance(loader, arrayOf(XposedInterface.Chain::class.java)) { _, method, _ ->
            when (method.name) {
                "getExecutable" -> recorded.executable
                "getThisObject" -> row
                "getArgs" -> listOf(context, null)
                "proceed" -> { originalCalls++; null }
                else -> null
            }
        } as XposedInterface.Chain
        recorded.hook.intercept(chain)
        return context
    }

    @Test
    fun publicChannelRouteReplacesTheOriginalExactlyOnce() {
        val modal = ModalController()
        Dependency.setMock(ModalController::class.java, modal)
        val context = click(Entry())
        val intent = context.startedActivities.single()
        assertEquals("android.settings.CHANNEL_NOTIFICATION_SETTINGS", intent.action)
        assertEquals("com.android.settings", intent.getPackage())
        assertNull("Settings owns the destination component", intent.getComponent())
        assertEquals("com.example.app", intent.getStringExtra("android.provider.extra.APP_PACKAGE"))
        assertEquals("messages", intent.getStringExtra("android.provider.extra.CHANNEL_ID"))
        assertFalse(intent.hasExtra(":android:show_fragment"))
        assertEquals(0, originalCalls)
        assertTrue(modal.animExitCalled)
        assertEquals("notification UID belongs to profile 10", 10, context.startedUsers.single()!!.identifier)
        assertEquals(1, FakeXposedInterface.recordedHooks.count {
            it.executable.declaringClass == MiuiNotificationMenuRow::class.java && it.executable.name == "onClickInfoItem"
        })
    }

    @Test
    fun hybridAndDefaultChannelsKeepSystemBehavior() {
        for (entry in listOf(
            Entry(channel = Channel("miscellaneous")),
            Entry(channel = Channel("")),
            Entry(notification = ExpandedNotification(hybrid = true))
        )) {
            assertTrue(click(entry).startedActivities.isEmpty())
        }
        assertEquals(3, originalCalls)
    }

    @Test
    fun invalidNotificationIdentityKeepsSystemBehavior() {
        assertTrue(click(Entry(notification = ExpandedNotification(uid = -1))).startedActivities.isEmpty())
        assertTrue(click(Entry(notification = ExpandedNotification(packageName = ""))).startedActivities.isEmpty())
        assertEquals(2, originalCalls)
    }

    @Test
    fun launchFailureFallsBackToTheOriginal() {
        val context = SettingsContext().apply { launchFailure = IllegalStateException("unavailable") }
        click(Entry(), context)
        assertTrue(context.startedActivities.isEmpty())
        assertEquals(1, originalCalls)
    }

    @Test
    fun collapseFailureAfterLaunchDoesNotOpenASecondPage() {
        Dependency.throwOnGet = IllegalStateException("controller unavailable")
        assertEquals(1, click(Entry()).startedActivities.size)
        assertEquals(0, originalCalls)
    }

    @Test
    fun wrappedFatalDuringLaunchPropagatesWithoutFallback() {
        val fatal = OutOfMemoryError("launch")
        val context = SettingsContext().apply { launchFailure = RuntimeException(fatal) }
        try {
            click(Entry(), context)
            fail("expected fatal")
        } catch (error: OutOfMemoryError) {
            assertSame(fatal, error)
        }
        assertEquals(0, originalCalls)
    }

    class Channel(private val id: String) { fun getId(): String = id }
    class Entry(
        private val channel: Channel = Channel("messages"),
        private val notification: ExpandedNotification = ExpandedNotification()
    ) {
        fun getChannel(): Channel = channel
        fun getSbn(): ExpandedNotification = notification
    }
    class Parent(private val entry: Entry) : BaseExpandableNotificationRow() { fun getEntry(): Entry = entry }
    class SettingsContext : FakeContext() {
        var launchFailure: Throwable? = null
        val startedUsers = mutableListOf<UserHandle?>()
        @Suppress("UNUSED_PARAMETER")
        fun startActivityAsUser(intent: Intent, user: UserHandle?) {
            launchFailure?.let { throw it }
            startedActivities.add(intent)
            startedUsers.add(user)
        }
    }
}
