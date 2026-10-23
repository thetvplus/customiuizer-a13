package tv.withaibuild.customiuizer.mods

import android.os.CombinedVibration
import android.os.IBinder
import android.os.VibrationAttributes
import com.android.server.vibrator.VibratorManagerService
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import tv.withaibuild.customiuizer.MainModule
import tv.withaibuild.customiuizer.mods.catalog.CatalogContracts
import tv.withaibuild.customiuizer.mods.catalog.FeatureDispatcher
import tv.withaibuild.customiuizer.mods.catalog.FeatureInstallRegistry
import tv.withaibuild.customiuizer.mods.utils.HookTargetResolver
import tv.withaibuild.customiuizer.mods.utils.ModuleHelper
import tv.withaibuild.customiuizer.mods.utils.XposedHelpers
import tv.withaibuild.customiuizer.utils.FakeXposedInterface
import tv.withaibuild.customiuizer.utils.PrefMap
import java.lang.reflect.Method
import java.lang.reflect.Proxy

class SelectiveVibrationHookTest {
    private val loader = javaClass.classLoader!!
    private val owners = mutableListOf<VibratorManagerService>()
    private fun runtime(mode: String = "2") = refinementRuntime(PrefMap<String, Any>().apply {
        put("system_vibration", mode)
        put("system_vibration_apps", setOf("selected.pkg"))
    })

    @Before fun setUp() {
        FeatureInstallRegistry.clearStatesForTesting()
        FakeXposedInterface.reset()
        XposedHelpers.moduleInst = FakeXposedInterface.create()
        MainModule.mPrefs = PrefMap()
    }

    @After fun tearDown() {
        for (owner in owners) {
            ModuleHelper.removePreferenceObserver("system.vibrationMode", owner)
            ModuleHelper.removePreferenceObserver("system.vibrationApps", owner)
        }
        FakeXposedInterface.reset()
        FeatureInstallRegistry.clearStatesForTesting()
        XposedHelpers.moduleInst = null
        MainModule.mPrefs = PrefMap()
    }

    private fun install(mode: String = "2") = runtime(mode).also {
        assertTrue(FeatureDispatcher.installById("selectiveVibration", it))
    }
    private fun service() = VibratorManagerService().also { owners.add(it) }
    private fun ready(owner: VibratorManagerService) = invokeRecordedRomHook(owner, "systemReady", emptyList())
    private fun vibrate(owner: VibratorManagerService, pkg: String?, token: IBinder? = null) =
        invokeRecordedRomHook(owner, "vibrate", listOf(100042, pkg, null, null, "reason", token))

    private fun observerCount(): Int {
        val registrations = ModuleHelper::class.java.getDeclaredField("ownedPrefObservers").apply { isAccessible = true }.get(null) as Collection<*>
        return registrations.count { registration ->
            val key = registration!!.javaClass.getDeclaredField("key").apply { isAccessible = true }.get(registration)
            key == "system.vibrationMode" || key == "system.vibrationApps"
        }
    }

    @Test fun exactA13SignatureExcludesTheA14DeviceIdOverload() {
        install()
        assertEquals(2, CatalogContracts.selectiveVibration.requirements.size)
        assertEquals(2, FakeXposedInterface.recordedHooks.size)
        val method = FakeXposedInterface.findHook(VibratorManagerService::class.java.name, "vibrate")!!.executable as Method
        assertEquals(listOf(Int::class.javaPrimitiveType, String::class.java, CombinedVibration::class.java,
            VibrationAttributes::class.java, String::class.java, IBinder::class.java), method.parameterTypes.toList())
    }

    @Test fun missingVibrationEntryPreventsHalfInstallation() {
        val runtime = runtime()
        runtime.resolverForTest = object : HookTargetResolver(loader) {
            override fun resolveMethod(className: String, methodName: String, vararg parameterTypes: Class<*>, diagnosticId: String): Method? =
                if (methodName == "vibrate") null else super.resolveMethod(className, methodName, *parameterTypes, diagnosticId = diagnosticId)
        }
        assertFalse(FeatureDispatcher.installById("selectiveVibration", runtime))
        assertTrue(FakeXposedInterface.recordedHooks.isEmpty())
        assertEquals(0, observerCount())
    }

    @Test fun partialFrameworkFailureIsTerminalAndCannotDuplicateInitializationHooks() {
        val runtime = runtime()
        FakeXposedInterface.failedHookName = "vibrate"
        assertFalse(FeatureDispatcher.installById("selectiveVibration", runtime))
        assertEquals(1, FakeXposedInterface.recordedHooks.size)
        assertFalse(FeatureDispatcher.installById("selectiveVibration", runtime))
        assertEquals(1, FakeXposedInterface.recordedHooks.size)
        assertEquals(0, observerCount())
    }

    @Test fun disabledFeatureDoesNotResolveHookOrObserve() {
        val runtime = runtime("1")
        assertFalse(FeatureDispatcher.installById("selectiveVibration", runtime))
        assertFalse(runtime.isResolverInitialized())
        assertTrue(FakeXposedInterface.recordedHooks.isEmpty())
        assertEquals(0, observerCount())
    }

    @Test fun allowListReadsPackageAtA13IndexAndPreservesAllNativeArguments() {
        install()
        val owner = service()
        ready(owner)
        val token = Proxy.newProxyInstance(loader, arrayOf(IBinder::class.java)) { proxy, method, args ->
            when (method.name) {
                "equals" -> proxy === args?.firstOrNull()
                "hashCode" -> System.identityHashCode(proxy)
                "toString" -> "test binder token"
                else -> null
            }
        } as IBinder
        vibrate(owner, "selected.pkg", token)
        assertEquals(listOf(100042, "selected.pkg", null, null, "reason", token), owner.lastArgs)
        vibrate(owner, "other.pkg")
        assertEquals(1, owner.vibrateCalls)
        assertEquals(1, owner.readyCalls)
    }

    @Test fun blockListOnlySuppressesSelectedPackage() {
        install("3")
        val owner = service()
        ready(owner)
        vibrate(owner, "selected.pkg")
        assertEquals(0, owner.vibrateCalls)
        vibrate(owner, "other.pkg")
        assertEquals(1, owner.vibrateCalls)
    }

    @Test fun uninitializedAndNullPackagesPreserveNativeBehavior() {
        install()
        val owner = service()
        vibrate(owner, "other.pkg")
        assertEquals(1, owner.vibrateCalls)
        ready(owner)
        vibrate(owner, null)
        assertEquals(2, owner.vibrateCalls)
    }

    @Test fun preferenceChangesUseCurrentOwnerStateWithoutAddingHooksOrObservers() {
        val runtime = install()
        val owner = service()
        ready(owner)
        ready(owner)
        assertEquals(2, observerCount())
        runtime.prefs["system_vibration_apps"] = setOf("other.pkg")
        ModuleHelper.handlePreferenceChanged("system_vibration_apps")
        vibrate(owner, "selected.pkg")
        assertEquals(0, owner.vibrateCalls)
        vibrate(owner, "other.pkg")
        assertEquals(1, owner.vibrateCalls)
        runtime.prefs["system_vibration"] = "1"
        ModuleHelper.handlePreferenceChanged("system_vibration")
        assertTrue(FeatureDispatcher.installById("selectiveVibration", runtime))
        vibrate(owner, "selected.pkg")
        assertEquals(2, owner.vibrateCalls)
        assertEquals(2, FakeXposedInterface.recordedHooks.size)
        assertEquals(2, observerCount())
    }

    @Test fun nativeReadyFailurePropagatesOnceWithoutRegisteringObservers() {
        install()
        val failure = IllegalStateException("ready failed")
        val owner = service().apply { readyFailure = failure }
        assertSame(failure, assertThrows(IllegalStateException::class.java) { ready(owner) })
        assertEquals(1, owner.readyCalls)
        assertEquals(0, observerCount())
        assertNull(XposedHelpers.getAdditionalInstanceField(owner, "mVibrationMode"))
    }

    @Test fun allowedNativeVibrationFailureAndFatalCauseAreNotSwallowed() {
        install()
        val owner = service()
        ready(owner)
        val failure = IllegalArgumentException("native vibration")
        owner.vibrationFailure = failure
        assertSame(failure, assertThrows(IllegalArgumentException::class.java) { vibrate(owner, "selected.pkg") })
        val fatal = InternalError("native fatal")
        owner.vibrationFailure = RuntimeException(fatal)
        assertSame(fatal, assertThrows(InternalError::class.java) { vibrate(owner, "selected.pkg") })
        assertEquals(2, owner.vibrateCalls)
    }
}
