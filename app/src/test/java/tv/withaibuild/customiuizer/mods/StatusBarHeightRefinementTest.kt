package tv.withaibuild.customiuizer.mods

import android.content.res.Configuration
import android.content.res.FakeAssetManagerFactory
import android.content.res.Resources
import android.util.DisplayMetrics
import android.view.Surface
import com.android.server.wm.DisplayPolicy
import io.github.libxposed.api.XposedModuleInterface.SystemServerStartingParam
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import tv.withaibuild.customiuizer.MainModule
import tv.withaibuild.customiuizer.installers.SystemServerInstaller
import tv.withaibuild.customiuizer.mods.catalog.FeatureInstallRegistry
import tv.withaibuild.customiuizer.mods.utils.XposedHelpers
import tv.withaibuild.customiuizer.utils.FakeXposedInterface
import tv.withaibuild.customiuizer.utils.PrefMap

class StatusBarHeightRefinementTest {
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

    @Test fun systemServerInstallsTheConfigurationHookAtTheExactA13Signature() {
        val prefs = PrefMap<String, Any>().apply { put("system_statusbarheight", 35) }
        val runtime = refinementRuntime(prefs)
        SystemServerInstaller.install(runtime.lpparam as SystemServerStartingParam)

        val target = requireNotNull(FakeXposedInterface.findHook(DisplayPolicy::class.java.name, method)).executable
        assertEquals(DisplayPolicy::class.java.getDeclaredMethod(method, Int::class.javaPrimitiveType), target)

        var density = 2.75f
        val policy = DisplayPolicy().apply {
            mUiContext = object : FakeContext() {
                override fun getResources(): Resources {
                    val metrics = DisplayMetrics().also { it.density = density }
                    return object : Resources(FakeAssetManagerFactory.create(), metrics, Configuration()) {
                        override fun getDisplayMetrics() = metrics
                    }
                }
            }
        }
        var nativeCalls = 0
        assertEquals(96, invokeRecordedRomHook(policy, method, listOf(Surface.ROTATION_90)) {
            nativeCalls++
            70
        })
        assertEquals(1, nativeCalls)
        assertEquals(Surface.ROTATION_90, policy.mDisplayContent.lastRotation)

        density = 2f
        prefs["system_statusbarheight"] = 50
        assertEquals(70, invokeRecordedRomHook(policy, method, listOf(Surface.ROTATION_270)) { 95 })
        assertEquals(Surface.ROTATION_270, policy.mDisplayContent.lastRotation)

        val failed = IllegalStateException("native display configuration")
        val thrown = assertThrows(IllegalStateException::class.java) {
            invokeRecordedRomHook(policy, method, listOf(Surface.ROTATION_180)) { throw failed }
        }
        assertSame(failed, thrown)
        assertEquals(Surface.ROTATION_270, policy.mDisplayContent.lastRotation)

        assertEquals(75, invokeRecordedRomHook(policy, method, listOf(-1)))
        assertEquals(Surface.ROTATION_270, policy.mDisplayContent.lastRotation)
        policy.mUiContext = null
        assertEquals(75, invokeRecordedRomHook(policy, method, listOf(Surface.ROTATION_0)))
    }

    @Test fun defaultAndOutOfRangeHeightsInstallNoDisplayPolicyHook() {
        for (height in listOf(null, 19, 81)) {
            val prefs = PrefMap<String, Any>()
            if (height != null) prefs["system_statusbarheight"] = height
            val runtime = refinementRuntime(prefs)
            SystemStatusBarAndClockHooks.StatusBarHeightSystemHook(runtime.lpparam as SystemServerStartingParam)
            assertNull(FakeXposedInterface.findHook(DisplayPolicy::class.java.name, method))
        }
    }

    @Test fun configuredHeightUsesSystemUiPixelRoundingWithNativeCutoutFloors() {
        assertEquals(96, SystemStatusBarAndClockHooks.statusBarHeightPx(35, 2.75f, 0, 0))
        assertEquals(104, SystemStatusBarAndClockHooks.statusBarHeightPx(35, 2.75f, 5, 8))
        assertEquals(120, SystemStatusBarAndClockHooks.statusBarHeightPx(35, 2.75f, 120, 8))
    }

    private val method = "getStatusBarHeightForRotation"
}
