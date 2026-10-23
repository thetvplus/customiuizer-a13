package tv.withaibuild.customiuizer.mods

import android.media.IVolumeController
import com.android.server.audio.AudioService
import com.android.server.audio.FocusRequester
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import tv.withaibuild.customiuizer.MainModule
import tv.withaibuild.customiuizer.mods.catalog.FeatureDispatcher
import tv.withaibuild.customiuizer.mods.catalog.FeatureInstallRegistry
import tv.withaibuild.customiuizer.mods.catalog.CatalogContracts
import tv.withaibuild.customiuizer.mods.utils.HookTargetResolver
import tv.withaibuild.customiuizer.mods.utils.XposedHelpers
import tv.withaibuild.customiuizer.utils.FakeXposedInterface
import tv.withaibuild.customiuizer.utils.PrefMap
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.InvocationTargetException

class AudioHookRefinementTest {
    private val loader = javaClass.classLoader!!
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

    private fun runtime(enabled: Boolean = true) = refinementRuntime(PrefMap<String, Any>().apply {
        put("system_noducking", enabled)
        put("system_firstpress", enabled)
    })

    private fun install(feature: String) = runtime().also { assertTrue(FeatureDispatcher.installById(feature, it)) }

    private fun requester() = FocusRequester().apply {
        frameworkInvoker = { loss, winner, force ->
            invokeRecordedRomHook(this, "frameworkHandleFocusLoss", listOf(loss, winner, force)) as Boolean
        }
    }

    private fun loss(owner: FocusRequester, event: Int = -3, winner: FocusRequester? = FocusRequester(20002), force: Boolean = false) {
        // Dispatch whichever parent/helper hook the installer really registered. This
        // makes the old broad parent hook reproduce its missing native state updates.
        if (FakeXposedInterface.findHook(owner.javaClass.name, "handleFocusLoss") != null) {
            invokeRecordedRomHook(owner, "handleFocusLoss", listOf(event, winner, force))
        } else owner.handleFocusLoss(event, winner, force)
    }

    private fun controller() = AudioService().VolumeController().apply {
        mController = object : IVolumeController { }
        nativeResult = true
    }

    private fun adjust(owner: AudioService.VolumeController, stream: Int = 3, flags: Int = 1, mute: Boolean = false) =
        invokeRecordedRomHook(owner, "suppressAdjustment", listOf(stream, flags, mute)) as Boolean

    @Test fun suppressedDuckingStillRecordsLossAndNotifiesFocusPolicy() {
        install("noDucking")
        val owner = requester()
        loss(owner)
        assertEquals(-3, owner.lossReceived)
        assertEquals(1, owner.parentCalls)
        assertEquals(listOf(false), owner.policyLossNotifications)
        assertFalse(owner.lossWasNotified)
        assertTrue(owner.dispatchedLosses.isEmpty())
        assertEquals(0, owner.duckCalls)
    }

    @Test fun firstVolumePressNeverReplacesAnOriginalExceptionWithSuccess() {
        install("firstVolumePress")
        val owner = controller()
        val failure = IllegalStateException("native AudioSystem failed")
        owner.nativeFailure = failure
        assertSame(failure, assertThrows(IllegalStateException::class.java) { adjust(owner) })
        assertEquals(1, owner.calls)
    }

    @Test fun noDuckingInstallsOnlyTheProvenHelperAndDeoptimizesOnlyItsCaller() {
        install("noDucking")
        val method = FakeXposedInterface.recordedHooks.single().executable as Method
        assertEquals("frameworkHandleFocusLoss", method.name)
        assertEquals(Boolean::class.javaPrimitiveType, method.returnType)
        assertEquals(listOf(Int::class.javaPrimitiveType, FocusRequester::class.java, Boolean::class.javaPrimitiveType), method.parameterTypes.toList())
        assertEquals("handleFocusLoss", FakeXposedInterface.deoptimizedExecutables.single().name)
    }

    @Test fun disabledAudioFeaturesDoNotResolveOrCreateBusinessHooks() {
        for (feature in listOf("noDucking", "firstVolumePress")) {
            val runtime = runtime(false)
            assertFalse(FeatureDispatcher.installById(feature, runtime))
            assertFalse(runtime.isResolverInitialized())
            assertTrue(FakeXposedInterface.recordedHooks.isEmpty())
            assertTrue(FakeXposedInterface.deoptimizedExecutables.isEmpty())
        }
    }

    @Test fun missingDuckingHelperPreventsAnyHookOrCallerDeoptimization() {
        val runtime = runtime()
        runtime.resolverForTest = object : HookTargetResolver(loader) {
            override fun resolveAllMethods(className: String, methodName: String, diagnosticId: String): List<Method>? =
                if (methodName == "frameworkHandleFocusLoss") emptyList() else super.resolveAllMethods(className, methodName, diagnosticId)
        }
        assertFalse(FeatureDispatcher.installById("noDucking", runtime))
        assertTrue(FakeXposedInterface.recordedHooks.isEmpty())
        assertTrue(FakeXposedInterface.deoptimizedExecutables.isEmpty())
    }

    @Test fun unsupportedDuckingHelperOverloadsAndReturnTypesAreRejected() {
        val check = SystemNotificationMoreHooks::class.java.getDeclaredMethod("requireNoDuckingTarget", Class::class.java).apply { isAccessible = true }
        for (shape in listOf(OverloadedDucking::class.java, WrongDuckingReturn::class.java)) {
            val failure = assertThrows(InvocationTargetException::class.java) {
                check.invoke(SystemNotificationMoreHooks, shape)
            }
            assertTrue(failure.targetException is IllegalArgumentException)
        }
        assertTrue(FakeXposedInterface.recordedHooks.isEmpty())
    }

    @Test fun callerDeoptimizationFailureIsTerminalAndInstallsNothing() {
        val runtime = runtime()
        FakeXposedInterface.deoptimizationResult = false
        assertFalse(FeatureDispatcher.installById("noDucking", runtime))
        FakeXposedInterface.deoptimizationResult = true
        assertFalse(FeatureDispatcher.installById("noDucking", runtime))
        assertEquals(1, FakeXposedInterface.deoptimizedExecutables.size)
        assertTrue(FakeXposedInterface.recordedHooks.isEmpty())
    }

    @Test fun frameworkInstallFailuresAreTerminalForBothAudioFeatures() {
        for ((feature, target) in listOf("noDucking" to "frameworkHandleFocusLoss", "firstVolumePress" to "suppressAdjustment")) {
            FeatureInstallRegistry.clearStatesForTesting()
            FakeXposedInterface.reset()
            val runtime = runtime()
            FakeXposedInterface.failedHookName = target
            assertFalse(FeatureDispatcher.installById(feature, runtime))
            FakeXposedInterface.failedHookName = null
            assertFalse(FeatureDispatcher.installById(feature, runtime))
            assertTrue(FakeXposedInterface.recordedHooks.isEmpty())
        }
    }

    @Test fun repeatedDuckingLossKeepsNativeDeduplicationAndGainRecovery() {
        install("noDucking")
        val owner = requester()
        loss(owner)
        loss(owner)
        assertEquals(2, owner.parentCalls)
        assertEquals(listOf(false), owner.policyLossNotifications)
        owner.handleFocusGain(1)
        assertEquals(0, owner.lossReceived)
        assertEquals(1, owner.gainCalls)
        loss(owner)
        assertEquals(listOf(false, false), owner.policyLossNotifications)
    }

    @Test fun externalFocusPolicyAndMiuiIgnoreRuleStillRunBeforeTheHelper() {
        install("noDucking")
        val external = requester().apply { mustNotifyOwnerOnDuck = false }
        loss(external)
        assertEquals(-3, external.lossReceived)
        assertEquals(listOf(false), external.policyLossNotifications)
        assertEquals(0, external.frameworkCalls)
        val ignored = requester().apply { ignoreFocusRequest = true; lossReceived = -2 }
        loss(ignored)
        assertEquals(-2, ignored.lossReceived)
        assertTrue(ignored.policyLossNotifications.isEmpty())
        assertEquals(0, ignored.frameworkCalls)
    }

    @Test fun pauseOnDuckOldSdkSameUidAndForceDuckDoNotReintroduceAppDucking() {
        install("noDucking")
        for (kind in 0..3) {
            val owner = requester()
            when (kind) {
                0 -> owner.grantFlags = 2
                1 -> owner.sdk = 25
            }
            loss(owner, winner = FocusRequester(if (kind == 2) owner.uid else 20002), force = kind == 3)
            assertEquals(-3, owner.lossReceived)
            assertEquals(0, owner.duckCalls)
            assertTrue(owner.dispatchedLosses.isEmpty())
            assertEquals(listOf(false), owner.policyLossNotifications)
        }
    }

    @Test fun transientAndPermanentLossRetainNativeDispatchFadeAndArguments() {
        install("noDucking")
        val transient = requester()
        val winner = FocusRequester(20002)
        loss(transient, -2, winner, true)
        assertEquals(1, transient.frameworkCalls)
        assertEquals(listOf(-2, winner, true), transient.lastFrameworkArgs)
        assertEquals(listOf(-2), transient.dispatchedLosses)
        assertTrue(transient.lossWasNotified)
        val permanent = requester()
        loss(permanent, -1, winner)
        assertEquals(1, permanent.fadeCalls)
        assertEquals(1, permanent.frameworkCalls)
        assertEquals(listOf(false), permanent.policyLossNotifications)
        assertTrue(permanent.dispatchedLosses.isEmpty())
    }

    @Test fun nullWinnerPreservesTheParentFallbackAndNativeHelperFailure() {
        install("noDucking")
        val owner = requester()
        loss(owner, winner = null)
        assertEquals(listOf(-3), owner.dispatchedLosses)
        assertTrue(owner.lossWasNotified)
        assertThrows(NullPointerException::class.java) {
            invokeRecordedRomHook(owner, "frameworkHandleFocusLoss", listOf(-3, null, false))
        }
        assertEquals(1, owner.frameworkCalls)
    }

    @Test fun disablingDuckingFeatureRestoresTheNativeDecisionWithoutReinstalling() {
        val runtime = install("noDucking")
        val owner = requester()
        loss(owner)
        runtime.prefs["system_noducking"] = false
        assertTrue(FeatureDispatcher.installById("noDucking", runtime))
        owner.handleFocusGain(1)
        loss(owner)
        assertEquals(1, owner.duckCalls)
        assertEquals(1, FakeXposedInterface.recordedHooks.size)
        assertEquals(1, FakeXposedInterface.deoptimizedExecutables.size)
    }

    @Test fun unhandledNativeFocusFailuresPropagateExactlyOnceIncludingFatalCauses() {
        install("noDucking")
        val failure = IllegalStateException("native focus failure")
        val owner = requester().apply { frameworkFailure = failure }
        assertSame(failure, assertThrows(IllegalStateException::class.java) { loss(owner, -2) })
        assertEquals(1, owner.frameworkCalls)
        assertEquals(1, owner.parentCalls)
        val fatal = ThreadDeath()
        val fatalOwner = requester().apply { frameworkFailure = RuntimeException(fatal) }
        assertSame(fatal, assertThrows(ThreadDeath::class.java) { loss(fatalOwner, -1) })
        assertEquals(1, fatalOwner.frameworkCalls)
        assertEquals(1, fatalOwner.parentCalls)
    }

    @Test fun firstVolumePressContractRequiresItsControllerField() {
        val runtime = runtime()
        assertEquals(2, CatalogContracts.firstVolumePress.requirements.size)
        runtime.resolverForTest = object : HookTargetResolver(loader) {
            override fun resolveField(className: String, fieldName: String, diagnosticId: String): Field? =
                if (fieldName == "mController") null else super.resolveField(className, fieldName, diagnosticId)
        }
        assertFalse(FeatureDispatcher.installById("firstVolumePress", runtime))
        assertTrue(FakeXposedInterface.recordedHooks.isEmpty())
    }

    @Test fun firstVolumePressChangesOnlyTheSuccessfulResultAfterNativeBookkeeping() {
        install("firstVolumePress")
        val owner = controller()
        assertFalse(adjust(owner, flags = 123))
        assertTrue(owner.nativeStateUpdated)
        assertEquals(1, owner.calls)
        assertEquals(listOf(3, 123, false), owner.lastArgs)
        assertEquals(1, FakeXposedInterface.recordedHooks.size)
        assertTrue(FakeXposedInterface.deoptimizedExecutables.isEmpty())
    }

    @Test fun otherStreamsMuteAdjustAndMissingControllerRetainTheNativeResult() {
        install("firstVolumePress")
        val owner = controller()
        assertTrue(adjust(owner, stream = 2))
        assertTrue(adjust(owner, mute = true))
        owner.mController = null
        assertTrue(adjust(owner))
        owner.nativeResult = false
        assertFalse(adjust(owner))
        assertEquals(4, owner.calls)
    }

    @Test fun firstVolumePressCanBeDisabledWithoutChangingInstalledState() {
        val runtime = install("firstVolumePress")
        val owner = controller()
        assertFalse(adjust(owner))
        runtime.prefs["system_firstpress"] = false
        assertTrue(FeatureDispatcher.installById("firstVolumePress", runtime))
        assertTrue(adjust(owner))
        assertEquals(2, owner.calls)
        assertEquals(1, FakeXposedInterface.recordedHooks.size)
    }

    @Test fun fatalVolumeFailuresAreNeverConvertedToAnAdjustmentResult() {
        install("firstVolumePress")
        for (fatal in listOf(OutOfMemoryError("oom"), ThreadDeath(), InternalError("fatal"))) {
            val owner = controller().apply { nativeFailure = RuntimeException(RuntimeException(fatal)) }
            assertSame(fatal, assertThrows(fatal.javaClass) { adjust(owner) })
            assertEquals(1, owner.calls)
        }
    }

    private class OverloadedDucking {
        fun frameworkHandleFocusLoss(loss: Int, winner: OverloadedDucking, force: Boolean): Boolean = false
        fun frameworkHandleFocusLoss(loss: Int): Boolean = false
    }
    private class WrongDuckingReturn {
        fun frameworkHandleFocusLoss(loss: Int, winner: WrongDuckingReturn, force: Boolean): Int = 0
    }
}
