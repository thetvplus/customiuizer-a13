package tv.withaibuild.customiuizer.mods

import android.content.pm.ApplicationInfo
import com.android.systemui.miui.volume.MiuiVolumeDialogImpl
import com.android.systemui.shared.plugins.PluginInstance
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

class SystemUIPluginHookTest {
    private val hostLoader = javaClass.classLoader!!
    private val dialogClass = MiuiVolumeDialogImpl::class.java
    private val factory = PluginInstance.Factory()
    private val collapsed = "system_volumedialogdelay_collapsed"
    private val expanded = "system_volumedialogdelay_expanded"

    @Before fun setUp() {
        XposedHelpers.moduleInst = FakeXposedInterface.create()
        FakeXposedInterface.reset()
        MainModule.mPrefs = PrefMap()
    }

    @After fun tearDown() {
        FakeXposedInterface.reset()
        XposedHelpers.moduleInst = null
        MainModule.mPrefs = PrefMap()
    }

    @Test fun onlyTheRomFactoryOverloadIsHooked() {
        installFactoryHook()
        val expected = factory.javaClass.getDeclaredMethod("getClassLoader", ApplicationInfo::class.java, ClassLoader::class.java)
        assertEquals(listOf(expected), FakeXposedInterface.recordedHooks.map { it.executable })
    }

    @Test fun nullOrFailedLoadDoesNotConsumeTheFirstSuccessfulPluginLoad() {
        MainModule.mPrefs[collapsed] = 3000
        installFactoryHook()
        val plugin = application("miui.systemui.plugin")
        val loader = object : ClassLoader(hostLoader) {}
        val failure = IllegalStateException("native load failed")
        assertSame(failure, assertThrows(IllegalStateException::class.java) {
            invokeRecordedRomHook(factory, "getClassLoader", listOf(plugin, hostLoader)) { throw failure }
        })
        assertNull(loadPlugin(plugin, null))
        assertSame(loader, loadPlugin(application("com.example.otherplugin"), loader))
        assertNull(FakeXposedInterface.findHook(dialogClass.name, "computeTimeoutH"))

        assertSame(loader, loadPlugin(plugin, loader))
        assertSame(loader, loadPlugin(plugin, loader))
        assertEquals(1, FakeXposedInterface.recordedHooks.count { it.executable.declaringClass === dialogClass })
    }

    @Test fun normalCollapsedAndExpandedTimeoutsPassThroughAccessibility() {
        MainModule.mPrefs[collapsed] = 3000
        MainModule.mPrefs[expanded] = 7000
        val dialog = installTimeoutHook()
        val accessibility = requireNotNull(dialog.mAccessibilityMgr).apply { minimum = 9000 }

        assertEquals(9000, timeout(dialog))
        assertEquals(3000, accessibility.lastBase)
        assertEquals(4, accessibility.lastFlags)

        dialog.mExpanded = true
        assertEquals(9000, timeout(dialog))
        assertEquals(7000, accessibility.lastBase)
        assertEquals(4, accessibility.lastFlags)
        assertEquals(2, accessibility.calls)
    }

    @Test fun hoveringAndUnconfiguredSafetyWarningUseNativeTiming() {
        MainModule.mPrefs[collapsed] = 3000
        val dialog = installTimeoutHook()
        val accessibility = requireNotNull(dialog.mAccessibilityMgr).apply { minimum = 20000 }
        dialog.mHovering = true
        dialog.mIsSafetyShowing = true

        assertEquals(20000, timeout(dialog))
        assertEquals(16000, accessibility.lastBase)
        assertEquals(4, accessibility.lastFlags)

        dialog.mHovering = false
        assertEquals(20000, timeout(dialog))
        assertEquals(5000, accessibility.lastBase)
        assertEquals(6, accessibility.lastFlags)
        assertEquals(2, dialog.nativeCalls)
    }

    @Test fun configuredSafetyUsesTheExpandedPreferenceAndTextFlags() {
        MainModule.mPrefs[expanded] = 6000
        val dialog = installTimeoutHook().apply { mIsSafetyShowing = true }
        val accessibility = requireNotNull(dialog.mAccessibilityMgr).apply { minimum = 8000 }

        assertEquals(8000, timeout(dialog))
        assertEquals(6000, accessibility.lastBase)
        assertEquals(6, accessibility.lastFlags)
        assertEquals(1, accessibility.calls)
    }

    @Test fun unsetOrUnavailableCustomTimeoutPreservesOneNativeCall() {
        val dialog = installTimeoutHook()

        assertEquals(2000, timeout(dialog))
        MainModule.mPrefs[collapsed] = 3000
        dialog.mAccessibilityMgr = null
        assertEquals(2000, timeout(dialog))
        assertEquals(2, dialog.nativeCalls)
    }

    private fun application(pkg: String) = ApplicationInfo().apply { packageName = pkg }

    private fun loadPlugin(info: ApplicationInfo, result: ClassLoader?) =
        invokeRecordedRomHook(factory, "getClassLoader", listOf(info, hostLoader)) { result }

    private fun installFactoryHook() {
        val param = Proxy.newProxyInstance(hostLoader, arrayOf(PackageReadyParam::class.java)) { _, method, _ ->
            if (method.name == "getClassLoader") hostLoader else null
        } as PackageReadyParam
        SystemUIControlCenterHooks.MIUIVolumeDialogHook(param)
    }

    private fun installTimeoutHook(): MiuiVolumeDialogImpl {
        SystemUIControlCenterHooks.VolumeDialogAutohideDelayHook(hostLoader)
        return MiuiVolumeDialogImpl()
    }

    private fun timeout(dialog: MiuiVolumeDialogImpl) = invokeRecordedRomHook(dialog, "computeTimeoutH", emptyList())
}
