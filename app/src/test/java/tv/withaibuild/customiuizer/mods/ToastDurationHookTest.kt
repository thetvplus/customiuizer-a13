package tv.withaibuild.customiuizer.mods

import android.view.WindowManager
import android.view.accessibility.AccessibilityManager
import com.android.server.notification.NotificationManagerService
import com.android.server.notification.toast.TextToastRecord
import com.android.server.notification.toast.ToastRecord
import com.android.server.wm.DisplayPolicy
import com.android.server.wm.WindowState
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import tv.withaibuild.customiuizer.MainModule
import tv.withaibuild.customiuizer.mods.catalog.CatalogContracts
import tv.withaibuild.customiuizer.mods.catalog.FeatureDispatcher
import tv.withaibuild.customiuizer.mods.catalog.FeatureInstallRegistry
import tv.withaibuild.customiuizer.mods.utils.HookTargetResolver
import tv.withaibuild.customiuizer.mods.utils.XposedHelpers
import tv.withaibuild.customiuizer.utils.FakeXposedInterface
import tv.withaibuild.customiuizer.utils.PrefMap
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.InvocationTargetException
import java.util.concurrent.atomic.AtomicReference

class ToastDurationHookTest {
    private val loader = javaClass.classLoader!!
    private fun runtime(option: Int = 6) = refinementRuntime(PrefMap<String, Any>().apply { put("system_toasttime", option) })

    @Before fun setUp() {
        FeatureInstallRegistry.clearStatesForTesting()
        FakeXposedInterface.reset()
        XposedHelpers.moduleInst = FakeXposedInterface.create()
        MainModule.mPrefs = PrefMap()
    }

    @After fun tearDown() {
        FakeXposedInterface.reset()
        FeatureInstallRegistry.clearStatesForTesting()
        XposedHelpers.moduleInst = null
        MainModule.mPrefs = PrefMap()
    }

    private fun install(option: Int = 6) = runtime(option).also {
        assertTrue(FeatureDispatcher.installById("toastTime", it))
    }

    private fun recommend(manager: AccessibilityManager, base: Int, flags: Int = 2): Int =
        invokeRecordedRomHook(manager, "getRecommendedTimeoutMillis", listOf(base, flags)) as Int

    private fun service(manager: AccessibilityManager = AccessibilityManager()) = NotificationManagerService().apply {
        mAccessibilityManager = manager
        timeoutCalculator = java.util.function.BiFunction { base, flags -> recommend(manager, base, flags) }
    }

    private fun policy(manager: AccessibilityManager = AccessibilityManager()) = DisplayPolicy().apply {
        mAccessibilityManager = manager
        timeoutCalculator = java.util.function.BiFunction { base, flags -> recommend(manager, base, flags) }
    }

    private fun schedule(service: NotificationManagerService, record: ToastRecord = ToastRecord(0), previousText: Boolean = false) =
        invokeRecordedRomHook(service, "scheduleDurationReachedLocked", listOf(record, previousText))

    private fun attributes(timeout: Long = 5000, type: Int = WindowManager.LayoutParams.TYPE_TOAST) =
        WindowManager.LayoutParams().apply { this.type = type; hideTimeoutMilliseconds = timeout }

    @Test fun installsAllThreeRequiredHooksAndNoQueueOrHandlerHook() {
        install()
        assertEquals(setOf("scheduleDurationReachedLocked", "adjustWindowParamsLw", "getRecommendedTimeoutMillis"),
            FakeXposedInterface.recordedHooks.map { it.executable.name }.toSet())
        assertEquals(3, FakeXposedInterface.recordedHooks.size)
        assertEquals(6, CatalogContracts.toastTime.requirements.size)
        assertEquals(setOf("scheduleDurationReachedLocked", "adjustWindowParamsLw"),
            FakeXposedInterface.deoptimizedExecutables.map { it.name }.toSet())
        assertEquals(2, FakeXposedInterface.deoptimizedExecutables.size)
        val scheduler = FakeXposedInterface.findHook(NotificationManagerService::class.java.name, "scheduleDurationReachedLocked")!!
        assertEquals(listOf(ToastRecord::class.java, Boolean::class.javaPrimitiveType), (scheduler.executable as Method).parameterTypes.toList())
    }

    @Test fun disabledFeatureDoesNotResolveOrInstall() {
        val runtime = runtime(0)
        assertFalse(FeatureDispatcher.installById("toastTime", runtime))
        assertFalse(runtime.isResolverInitialized())
        assertTrue(FakeXposedInterface.recordedHooks.isEmpty())
        assertTrue(FakeXposedInterface.deoptimizedExecutables.isEmpty())
    }

    @Test fun failedDeoptimizationRejectsInstallationWithoutPartialHooksOrRetry() {
        val runtime = runtime()
        FakeXposedInterface.deoptimizationResult = false
        assertFalse(FeatureDispatcher.installById("toastTime", runtime))
        assertFalse(FeatureDispatcher.installById("toastTime", runtime))
        assertTrue(FakeXposedInterface.recordedHooks.isEmpty())
        assertEquals(1, FakeXposedInterface.deoptimizedExecutables.size)
    }

    @Test fun partialFrameworkInstallationCannotBeRetriedAndDuplicateBusinessHooks() {
        val runtime = runtime()
        FakeXposedInterface.failedHookName = "adjustWindowParamsLw"
        assertFalse(FeatureDispatcher.installById("toastTime", runtime))
        assertEquals(2, FakeXposedInterface.recordedHooks.size)
        assertFalse(FeatureDispatcher.installById("toastTime", runtime))
        assertEquals(2, FakeXposedInterface.recordedHooks.size)
        assertEquals(2, FakeXposedInterface.deoptimizedExecutables.size)
    }

    @Test fun missingAnyMemberRejectsTheWholeBundleBeforeInstallation() {
        for (target in CatalogContracts.toastTime.allTargets) {
            FeatureInstallRegistry.clearStatesForTesting()
            val runtime = runtime()
            runtime.resolverForTest = object : HookTargetResolver(loader) {
                override fun resolveAllMethods(className: String, methodName: String, diagnosticId: String): List<Method>? =
                    if (className == target.className && methodName == target.memberName) emptyList() else super.resolveAllMethods(className, methodName, diagnosticId)
                override fun resolveMethod(className: String, methodName: String, vararg parameterTypes: Class<*>, diagnosticId: String): Method? =
                    if (className == target.className && methodName == target.memberName) null else super.resolveMethod(className, methodName, *parameterTypes, diagnosticId = diagnosticId)
                override fun resolveField(className: String, fieldName: String, diagnosticId: String): Field? =
                    if (className == target.className && fieldName == target.memberName) null else super.resolveField(className, fieldName, diagnosticId)
            }
            assertFalse(target.id, FeatureDispatcher.installById("toastTime", runtime))
            assertTrue(target.id, FakeXposedInterface.recordedHooks.isEmpty())
        }
    }

    @Test fun hiddenTargetsRejectChangedSignaturesAndExtraOverloads() {
        val check = SystemAudioAndVisualAndMoreHooks::class.java.getDeclaredMethod("requireSingleToastMethod",
            Class::class.java, String::class.java, Array<String>::class.java).apply { isAccessible = true }
        for (shape in listOf(ExtraOverload::class.java, WrongShape::class.java)) {
            val failure = assertThrows(InvocationTargetException::class.java) {
                check.invoke(SystemAudioAndVisualAndMoreHooks, shape, "scheduleDurationReachedLocked",
                    arrayOf(ToastRecord::class.java.name, "boolean"))
            }
            assertTrue(failure.targetException is IllegalArgumentException)
        }
    }

    @Test fun shortAndLongKeepNativeAnimationAllowancesAndArguments() {
        install(6)
        val service = service()
        val short = ToastRecord(0)
        schedule(service, short)
        assertEquals(4000L, service.scheduledDelay)
        assertSame(short, service.scheduledRecord)
        assertFalse(service.previousWasText)
        val long = TextToastRecord(1)
        schedule(service, long, true)
        assertEquals(6083L, service.scheduledDelay)
        assertSame(long, service.scheduledRecord)
        assertTrue(service.previousWasText)
        assertEquals(2, service.scheduleCalls)
        assertEquals(2, service.mAccessibilityManager.calls)
    }

    @Test fun accessibilityMinimumIsAppliedAfterAdjustmentForBothPaths() {
        install(1)
        val manager = AccessibilityManager().apply { minimum = 10000 }
        val service = service(manager)
        schedule(service, TextToastRecord(0), true)
        assertEquals(10583L, service.scheduledDelay)
        assertEquals(1000, manager.lastBase)
        val policy = policy(manager)
        val attributes = attributes()
        val window = WindowState()
        invokeRecordedRomHook(policy, "adjustWindowParamsLw", listOf(window, attributes))
        assertEquals(10000L, attributes.hideTimeoutMilliseconds)
        assertSame(window, policy.lastWindow)
        assertEquals(1, policy.adjustCalls)
        assertTrue(policy.remainingNativePolicyRan)
        assertEquals(0x10, attributes.flags)
    }

    @Test fun windowUsesLongToastBaseAndKeepsCustomSentinelAndOtherWindowsNative() {
        install(6)
        val policy = policy()
        for ((timeout, type, expected) in listOf(
            Triple(4000L, 2005, 5500L), Triple(7000L, 2005, 5500L),
            Triple(2000L, 2005, 2000L), Triple(-1L, 2005, 4100L), Triple(5000L, 2, 5000L))) {
            val attributes = attributes(timeout, type)
            invokeRecordedRomHook(policy, "adjustWindowParamsLw", listOf(WindowState(), attributes))
            assertEquals("timeout=$timeout type=$type", expected, attributes.hideTimeoutMilliseconds)
        }
        assertEquals(5, policy.adjustCalls)
        assertEquals(4, policy.mAccessibilityManager.calls)
    }

    @Test fun scopedAdjustmentLeavesOtherManagersFlagsAndTimeoutsUntouched() {
        install()
        val service = service()
        val other = AccessibilityManager()
        service.duringSchedule = Runnable {
            assertEquals(2000, recommend(other, 2000))
            assertEquals(2000, recommend(service.mAccessibilityManager, 2000, 4))
            assertEquals(1234, recommend(service.mAccessibilityManager, 1234))
        }
        schedule(service)
        assertEquals(4000L, service.scheduledDelay)
        assertEquals(2000, recommend(service.mAccessibilityManager, 2000))
    }

    @Test fun nestedSchedulingRestoresOuterAdjustmentAndRemovesItAfterReturn() {
        val runtime = install(6)
        val manager = AccessibilityManager()
        val outer = service(manager)
        val inner = service(manager)
        outer.duringSchedule = Runnable {
            runtime.prefs["system_toasttime"] = 1
            schedule(inner)
            assertEquals(1000L, inner.scheduledDelay)
        }
        schedule(outer)
        assertEquals(4000L, outer.scheduledDelay)
        assertEquals(2000, recommend(manager, 2000))
    }

    @Test fun nativeExceptionsPropagateOnceAndAlwaysClearScope() {
        install()
        val service = service()
        for (failure in listOf(IllegalStateException("native"), RuntimeException(ThreadDeath()), RuntimeException(InternalError("fatal")))) {
            service.scheduleFailure = failure
            assertSame(failure, assertThrows(RuntimeException::class.java) { schedule(service) })
            assertEquals(2000, recommend(service.mAccessibilityManager, 2000))
        }
        assertEquals(3, service.scheduleCalls)
    }

    @Test fun directFatalFailuresAlsoClearTheScopedManager() {
        install()
        val service = service()
        for (failure in listOf(ThreadDeath(), InternalError("fatal"), OutOfMemoryError("oom"))) {
            val thrown = assertThrows(failure.javaClass) {
                invokeRecordedRomHook(service, "scheduleDurationReachedLocked", listOf(ToastRecord(0), false)) { throw failure }
            }
            assertSame(failure, thrown)
            assertEquals(2000, recommend(service.mAccessibilityManager, 2000))
        }
    }

    @Test fun anotherThreadCannotInheritTheActiveToastAdjustment() {
        install()
        val service = service()
        val result = AtomicReference<Int>()
        val failure = AtomicReference<Throwable>()
        service.duringSchedule = Runnable {
            val worker = Thread {
                try { result.set(recommend(service.mAccessibilityManager, 2000)) }
                catch (t: Throwable) { failure.set(t) }
            }
            worker.start()
            worker.join(5000)
            assertFalse(worker.isAlive)
            assertNull(failure.get())
            assertEquals(2000, result.get().toInt())
        }
        schedule(service)
        assertEquals(4000L, service.scheduledDelay)
    }

    @Test fun disabledNestedCallClearsTheOuterScopeForItsOwnNativeWork() {
        val runtime = install(6)
        val manager = AccessibilityManager()
        val outer = service(manager)
        val inner = service(manager)
        outer.duringSchedule = Runnable {
            runtime.prefs["system_toasttime"] = 0
            schedule(inner)
            assertEquals(2000L, inner.scheduledDelay)
        }
        schedule(outer)
        assertEquals(4000L, outer.scheduledDelay)
        assertEquals(2000, recommend(manager, 2000))
    }

    @Test fun preferenceDisableRestoresNativeWithoutReinstalling() {
        val runtime = install()
        val service = service()
        schedule(service)
        runtime.prefs["system_toasttime"] = 0
        assertTrue(FeatureDispatcher.installById("toastTime", runtime))
        schedule(service)
        assertEquals(2000L, service.scheduledDelay)
        assertEquals(3, FakeXposedInterface.recordedHooks.size)
    }

    @Test fun malformedLargeOptionIsBoundedToTheUiMaximum() {
        install(Int.MAX_VALUE)
        val service = service()
        schedule(service, ToastRecord(1))
        assertEquals(18500L, service.scheduledDelay)
    }

    private class ExtraOverload {
        fun scheduleDurationReachedLocked(record: ToastRecord, text: Boolean) { }
        fun scheduleDurationReachedLocked(record: ToastRecord) { }
    }
    private class WrongShape {
        fun scheduleDurationReachedLocked(record: ToastRecord, text: Int): Int = 0
    }
}
