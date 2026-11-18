package tv.withaibuild.customiuizer.mods

import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import com.android.server.power.PowerGroup
import com.android.server.power.PowerManagerService
import com.android.server.wm.ScreenRotationAnimationImpl
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

class DisplayWindowAbiRefinementTest {
    @Before fun setUp() {
        FeatureInstallRegistry.clearStatesForTesting()
        FakeXposedInterface.reset()
        XposedHelpers.moduleInst = FakeXposedInterface.create()
    }

    @After fun tearDown() {
        FeatureInstallRegistry.clearStatesForTesting()
        FakeXposedInterface.reset()
        XposedHelpers.moduleInst = null
        MainModule.mPrefs = PrefMap()
    }

    @Test fun rotationCatalogHooksAllFourRomFactoriesAndKeepsNativeBookkeeping() {
        val runtime = refinementRuntime(PrefMap<String, Any>().apply { put("system_rotateanim", "2") })
        assertTrue(FeatureDispatcher.installById("rotationAnimation", runtime))
        val expected = mapOf(
            "createRotation180Enter" to 0,
            "createRotation180Exit" to 0,
            "createRotationEnterAnimation" to 4,
            "createRotationExitAnimation" to 4
        )
        val hooks = FakeXposedInterface.recordedHooks.map { it.executable }
        assertEquals(4, hooks.size)
        assertEquals(expected, hooks.associate { it.name to it.parameterCount })

        val owner = ScreenRotationAnimationImpl()
        var nativeCalls = 0
        for ((method, argumentCount) in expected) {
            val animation = TimedAnimation()
            val result = invokeRecordedRomHook(owner, method, List(argumentCount) { it }) {
                nativeCalls++
                animation
            }
            assertSame(animation, result)
            assertEquals(0L, animation.appliedDuration)
        }
        assertEquals(4, nativeCalls)
    }

    @Test fun rotationFadeReplacesOnlySuccessfulNativeAnimations() {
        val runtime = refinementRuntime(PrefMap<String, Any>().apply { put("system_rotateanim", "3") })
        assertTrue(FeatureDispatcher.installById("rotationAnimation", runtime))
        val owner = ScreenRotationAnimationImpl()
        val nativeFailure = IllegalStateException("native factory failed")

        val result = invokeRecordedRomHook(owner, "createRotation180Enter", emptyList()) { TimedAnimation() }
        assertTrue(result is AlphaAnimation)
        assertNull(invokeRecordedRomHook(owner, "createRotation180Exit", emptyList()) { null })
        assertSame(nativeFailure, assertThrows(IllegalStateException::class.java) {
            invokeRecordedRomHook(owner, "createRotation180Exit", emptyList()) { throw nativeFailure }
        })
    }

    @Test fun chargeWakeSkipsVoidWithNullAndRetainsModeSpecificWakeReasons() {
        val prefs = PrefMap<String, Any>().apply { put("system_nolightuponcharges", "2") }
        val runtime = refinementRuntime(prefs)
        assertTrue(FeatureDispatcher.installById("noLightUpOnCharge", runtime))
        val service = PowerManagerService()
        var nativeCalls = 0
        fun wake(reason: String): Any? = invokeRecordedRomHook(
            service, "wakePowerGroupLocked", listOf(PowerGroup(), 10L, 0, reason, 1000, "android", 1000)
        ) {
            nativeCalls++
            null
        }

        assertNull(wake("android.server.power:PLUGGED:true"))
        assertNull(wake("com.android.systemui:WIRELESS_CHARGE"))
        assertEquals(0, nativeCalls)
        wake("unrelated user wake")
        prefs["system_nolightuponcharges"] = "3"
        wake("com.android.systemui:WIRELESS_CHARGE")
        assertNull(wake("android.server.power:PLUGGED:false"))
        prefs["system_nolightuponcharges"] = "corrupt"
        wake("android.server.power:PLUGGED:true")
        assertEquals(3, nativeCalls)
    }

    private class TimedAnimation : Animation() {
        var appliedDuration = -1L

        override fun setDuration(durationMillis: Long) {
            appliedDuration = durationMillis
        }
    }
}
