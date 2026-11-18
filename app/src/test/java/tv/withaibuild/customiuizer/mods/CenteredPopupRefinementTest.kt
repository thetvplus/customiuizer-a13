package tv.withaibuild.customiuizer.mods

import android.content.Context
import android.content.res.Configuration
import android.content.res.FakeAssetManagerFactory
import android.content.res.Resources
import android.util.DisplayMetrics
import com.android.systemui.statusbar.policy.HeadsUpManagerInjector
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

class CenteredPopupRefinementTest {
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

    @Test fun exactInsetHookReadsUpdatedDisplayDimensionsOnTheSameContext() {
        enableAndInstall()
        val executable = FakeXposedInterface.recordedHooks.single().executable
        assertEquals(HeadsUpManagerInjector.javaClass.getDeclaredMethod(method, Context::class.java), executable)

        assertEquals(888, inset(64))
        sample.display.heightPixels = 2400
        sample.padding = 23
        sample.popupHeight = 408
        assertEquals(1032, inset(95))
    }

    @Test fun landscapeAndMissingDimensionsUseNativeInset() {
        enableAndInstall()
        sample.config.orientation = Configuration.ORIENTATION_LANDSCAPE
        assertEquals(16, inset(16))
        assertEquals(0, sample.dimensionReads)

        sample.config.orientation = Configuration.ORIENTATION_PORTRAIT
        sample.hasHeight = false
        assertEquals(64, inset(64))
        assertEquals(0, sample.dimensionReads)
    }

    @Test fun centeredBoundsRespectNativeTopAndAvoidOversizedPopups() {
        enableAndInstall()
        sample.display.heightPixels = 600
        sample.popupHeight = 430
        assertEquals(160, inset(160))
        sample.popupHeight = 500
        assertEquals(160, inset(160))
    }

    @Test fun disabledFeatureDoesNotInstallAndNativeFailurePropagates() {
        install()
        assertTrue(FakeXposedInterface.recordedHooks.isEmpty())
        enableAndInstall()
        val failure = IllegalStateException("native inset calculation")
        assertSame(failure, assertThrows(IllegalStateException::class.java) {
            invokeRecordedRomHook(HeadsUpManagerInjector, method, listOf(context)) { throw failure }
        })
        assertEquals(0, sample.dimensionReads)
    }

    private fun enableAndInstall() {
        MainModule.mPrefs["system_betterpopups_center"] = true
        install()
    }

    private fun install() {
        val loader = javaClass.classLoader!!
        val param = Proxy.newProxyInstance(loader, arrayOf(PackageReadyParam::class.java)) { _, method, _ ->
            if (method.name == "getClassLoader") loader else null
        } as PackageReadyParam
        SystemNotificationMoreHooks.BetterPopupsCenteredHook(param)
    }

    private fun inset(nativeInset: Int): Any? =
        invokeRecordedRomHook(HeadsUpManagerInjector, method, listOf(context)) { nativeInset }

    private val method = "miuiHeadsUpInset"
    private val sample = PopupResources()
    private val context = object : FakeContext() {
        override fun getResources() = sample
    }

    private class PopupResources : Resources(FakeAssetManagerFactory.create(), DisplayMetrics(), Configuration()) {
        val display = DisplayMetrics().apply { heightPixels = 2000 }
        val config = Configuration().apply { orientation = Configuration.ORIENTATION_PORTRAIT }
        var padding = 16
        var popupHeight = 272
        var hasHeight = true
        var dimensionReads = 0
            private set

        override fun getDisplayMetrics() = display
        override fun getConfiguration() = config

        override fun getIdentifier(name: String?, defType: String?, defPackage: String?): Int = when (name) {
            "heads_up_status_bar_padding" -> 1
            "notification_max_heads_up_height" -> if (hasHeight) 2 else 0
            else -> 0
        }

        override fun getDimensionPixelSize(id: Int): Int {
            dimensionReads++
            return when (id) {
                1 -> padding
                2 -> popupHeight
                else -> throw Resources.NotFoundException("dimension $id")
            }
        }
    }
}
