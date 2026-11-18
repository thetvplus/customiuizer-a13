package tv.withaibuild.customiuizer.mods

import android.app.Notification
import android.app.PendingIntent
import android.media.AudioManager
import com.android.systemui.statusbar.notification.ExpandedNotification
import com.android.systemui.statusbar.notification.collection.NotificationEntry
import com.android.systemui.statusbar.notification.interruption.MiuiNotificationInterruptStateProviderImpl
import com.android.systemui.statusbar.phone.MiuiPhoneStatusBarPolicy
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

class HeadsUpMuteRefinementTest {
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

    @Test fun exactPeekTracksNativeSilentAndVibrateWithoutShortCircuitingTheHost() {
        enableAndInstall()
        val target = FakeXposedInterface.recordedHooks.single { it.executable.name == "shouldPeek" }.executable
        assertEquals(
            provider.javaClass.getDeclaredMethod("shouldPeek", NotificationEntry::class.java, ExpandedNotification::class.java),
            target
        )
        val ordinary = expandedNotification()
        assertEquals(true, peek(ordinary))
        updateRinger(AudioManager.RINGER_MODE_SILENT)
        assertEquals(false, peek(ordinary))
        updateRinger(AudioManager.RINGER_MODE_VIBRATE)
        assertEquals(false, peek(ordinary))
        updateRinger(AudioManager.RINGER_MODE_NORMAL)
        assertEquals(true, peek(ordinary))
        provider.nativeDecision = false
        assertEquals(false, peek(ordinary))
        assertEquals(5, provider.nativePeeks)
    }

    @Test fun fullscreenCandidateKeepsTheResultUsedByNativeFallback() {
        enableAndInstall()
        updateRinger(AudioManager.RINGER_MODE_SILENT)
        val fullscreen = expandedNotification(PendingIntent("com.example.alarm"))
        assertEquals(true, peek(fullscreen))
        provider.nativeDecision = false
        assertEquals(false, peek(fullscreen))
        provider.nativeDecision = true
        assertEquals(true, peek(null))
        MainModule.mPrefs[key] = false
        assertEquals(true, peek(expandedNotification()))
    }

    @Test fun disabledOrFailedPairInstallsNoHooks() {
        SystemUINotificationHooks.DisableHeadsUpWhenMuteHook(packageParam)
        assertTrue(FakeXposedInterface.recordedHooks.isEmpty())
        for (name in listOf("updateVolumeZen", "shouldPeek")) {
            FakeXposedInterface.failedHookName = name
            enableAndInstall()
            assertTrue(FakeXposedInterface.recordedHooks.isEmpty())
        }
    }

    @Test fun failedNativeRingerUpdateDoesNotPublishNewState() {
        enableAndInstall()
        updateRinger(AudioManager.RINGER_MODE_NORMAL)
        val failure = IllegalStateException("native ringer policy")
        assertSame(failure, assertThrows(IllegalStateException::class.java) {
            invokeRecordedRomHook(policy, "updateVolumeZen", emptyList()) {
                policy.mMuteVisible = true
                throw failure
            }
        })
        assertEquals(true, peek(expandedNotification()))
    }

    private fun enableAndInstall() {
        MainModule.mPrefs[key] = true
        SystemUINotificationHooks.DisableHeadsUpWhenMuteHook(packageParam)
    }

    private fun updateRinger(mode: Int) {
        policy.ringerMode = mode
        invokeRecordedRomHook(policy, "updateVolumeZen", emptyList())
    }

    private fun expandedNotification(fullScreen: PendingIntent? = null): ExpandedNotification = ExpandedNotification().apply {
        this.notification = Notification().also { it.fullScreenIntent = fullScreen }
    }

    private fun peek(expanded: ExpandedNotification?): Any? =
        invokeRecordedRomHook(provider, "shouldPeek", listOf(NotificationEntry(), expanded))

    private val key = "system_betterpopups_disablewhenmute"
    private val provider = MiuiNotificationInterruptStateProviderImpl()
    private val policy = MiuiPhoneStatusBarPolicy()
    private val loader = javaClass.classLoader!!
    private val packageParam = Proxy.newProxyInstance(loader, arrayOf(PackageReadyParam::class.java)) { _, method, _ ->
        if (method.name == "getClassLoader") loader else null
    } as PackageReadyParam
}
