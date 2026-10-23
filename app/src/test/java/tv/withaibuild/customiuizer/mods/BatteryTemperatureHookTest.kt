package tv.withaibuild.customiuizer.mods

import android.os.Message
import com.miui.powercenter.BatteryFragment
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import tv.withaibuild.customiuizer.mods.utils.XposedHelpers
import tv.withaibuild.customiuizer.utils.FakeXposedInterface
import java.lang.reflect.Proxy

class BatteryTemperatureHookTest {
    @Before fun setup() {
        FakeXposedInterface.reset()
        XposedHelpers.moduleInst = FakeXposedInterface.create()
    }
    @After fun cleanup() {
        FakeXposedInterface.reset()
        XposedHelpers.moduleInst = null
    }
    private fun install() {
        val loader = javaClass.classLoader!!
        val param = Proxy.newProxyInstance(loader, arrayOf(PackageReadyParam::class.java)) { _, method, _ ->
            when (method.name) {
                "getClassLoader" -> loader
                "getPackageName", "getProcessName" -> "com.miui.securitycenter"
                else -> null
            }
        } as PackageReadyParam
        Various.ShowTempInBatteryHook(param)
    }
    @Test fun findsHandlerWithWeakOwnerAndDoesNotRetainAFragment() {
        install()
        val hook = FakeXposedInterface.recordedHooks.single()
        assertEquals(BatteryFragment.BatteryHandler::class.java, hook.executable.declaringClass)
        val handler = BatteryFragment.BatteryHandler()
        invokeRecordedRomHook(handler, "handleMessage", listOf(Message().apply { what = 1 }))
        assertEquals(1, handler.calls)
        assertNull(handler.owner.get())
    }
    @Test fun nativeFailurePropagatesAndDoesNotRunUiUpdate() {
        install()
        val failure = IllegalStateException("native handler")
        val handler = BatteryFragment.BatteryHandler().apply { this.failure = failure }
        assertSame(failure, assertThrows(IllegalStateException::class.java) {
            invokeRecordedRomHook(handler, "handleMessage", listOf(Message().apply { what = 1 }))
        })
        assertEquals(1, handler.calls)
    }
}
