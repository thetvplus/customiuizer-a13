package tv.withaibuild.customiuizer.mods

import android.animation.ObjectAnimator
import com.android.server.display.DisplayPowerController
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModuleInterface.SystemServerStartingParam
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import tv.withaibuild.customiuizer.MainModule
import tv.withaibuild.customiuizer.mods.catalog.FeatureDispatcher
import tv.withaibuild.customiuizer.mods.catalog.FeatureInstallRegistry
import tv.withaibuild.customiuizer.mods.catalog.FeatureRuntime
import tv.withaibuild.customiuizer.mods.catalog.CatalogContracts
import tv.withaibuild.customiuizer.mods.utils.HookTargetResolver
import tv.withaibuild.customiuizer.mods.utils.ModuleHelper
import tv.withaibuild.customiuizer.mods.utils.XposedHelpers
import tv.withaibuild.customiuizer.utils.FakeXposedInterface
import tv.withaibuild.customiuizer.utils.PrefMap
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Proxy

class ScreenAnimationHookTest {
    private val loader = javaClass.classLoader!!
    private val controllers = mutableListOf<DisplayPowerController>()

    @Before
    fun setUp() {
        FeatureInstallRegistry.clearStatesForTesting()
        FakeXposedInterface.reset()
        XposedHelpers.moduleInst = FakeXposedInterface.create()
        MainModule.mPrefs = PrefMap()
    }

    @After
    fun tearDown() {
        for (controller in controllers) ModuleHelper.removePreferenceObserver("system.screenAnimation", controller)
        FakeXposedInterface.reset()
        FeatureInstallRegistry.clearStatesForTesting()
        XposedHelpers.moduleInst = null
        MainModule.mPrefs = PrefMap()
    }

    private fun runtime(duration: Int = 350): FeatureRuntime {
        val prefs = PrefMap<String, Any>().apply { put("system_screenanim_duration", duration) }
        MainModule.mPrefs = prefs
        val param = Proxy.newProxyInstance(loader, arrayOf(SystemServerStartingParam::class.java)) { _, method, _ ->
            when (method.name) {
                "getClassLoader" -> loader
                "getProcessName" -> "android"
                else -> null
            }
        } as SystemServerStartingParam
        return FeatureDispatcher.createRuntime("android", param, loader, prefs)
    }

    private fun install(duration: Int = 350): FeatureRuntime {
        val runtime = runtime(duration)
        assertTrue("screenAnim must install on the ROM-proven A13 ABI", FeatureDispatcher.installById("screenAnim", runtime))
        return runtime
    }

    private fun controller(): DisplayPowerController = DisplayPowerController().apply {
        animatorFactory = { ObjectAnimator() }
        controllers.add(this)
    }

    private fun initialize(controller: DisplayPowerController, screenState: Int = 2) {
        val hook = requireNotNull(FakeXposedInterface.findHook(DisplayPowerController::class.java.name, "initialize"))
        val chain = Proxy.newProxyInstance(loader, arrayOf(XposedInterface.Chain::class.java)) { _, method, _ ->
            when (method.name) {
                "getExecutable" -> hook.executable
                "getThisObject" -> controller
                "getArgs" -> listOf(screenState)
                "proceed" -> try {
                    (hook.executable as Method).invoke(controller, screenState)
                } catch (wrapped: InvocationTargetException) {
                    throw wrapped.targetException
                }
                else -> null
            }
        } as XposedInterface.Chain
        hook.hook.intercept(chain)
    }

    private fun observerCount(): Int {
        val field = ModuleHelper::class.java.getDeclaredField("ownedPrefObservers").apply { isAccessible = true }
        return (field.get(null) as Collection<*>).count { registration ->
            val key = registration!!.javaClass.getDeclaredField("key").apply { isAccessible = true }
            key.get(registration) == "system.screenAnimation"
        }
    }

    @Test
    fun installsOnlyTheExactIntInitializationTarget() {
        install()
        val hooks = FakeXposedInterface.recordedHooks
        assertEquals(1, hooks.size)
        assertEquals(listOf(Int::class.javaPrimitiveType), (hooks.single().executable as Method).parameterTypes.toList())
        assertEquals(4, CatalogContracts.screenAnim.requirements.size)
    }

    @Test
    fun missingRequiredFieldPreventsAllHookInstallation() {
        for (missing in listOf("mColorFadeEnabled", "mColorFadeFadesConfig", "mColorFadeOffAnimator")) {
            FeatureInstallRegistry.clearStatesForTesting()
            val runtime = runtime()
            runtime.resolverForTest = object : HookTargetResolver(loader) {
                override fun resolveField(className: String, fieldName: String, diagnosticId: String): Field? =
                    if (fieldName == missing) null else super.resolveField(className, fieldName, diagnosticId)
            }
            assertFalse("missing $missing must reject the whole target bundle", FeatureDispatcher.installById("screenAnim", runtime))
            assertTrue(FakeXposedInterface.recordedHooks.isEmpty())
            assertEquals(0, observerCount())
        }
    }

    @Test
    fun disabledFeatureCreatesNoHooksResolverOrObservers() {
        val runtime = runtime(0)
        val observers = observerCount()
        assertFalse(FeatureDispatcher.installById("screenAnim", runtime))
        assertFalse(runtime.isResolverInitialized())
        assertTrue(FakeXposedInterface.recordedHooks.isEmpty())
        assertEquals(observers, observerCount())
    }

    @Test
    fun enablesFadeBeforeNativeInitializationThenUpdatesOnlyOffDuration() {
        install(420)
        val controller = controller()
        initialize(controller, 4)
        assertEquals(1, controller.initializeCalls)
        assertEquals(4, controller.initialScreenState)
        assertTrue(controller.initializedWithFade)
        assertEquals(420L, controller.mColorFadeOffAnimator!!.duration)
        assertEquals(250L, controller.mColorFadeOnAnimator!!.duration)
        assertEquals(1, observerCount())
    }

    @Test
    fun zeroDurationRetainsTheLegacy250msFallback() {
        val runtime = install()
        runtime.prefs["system_screenanim_duration"] = 0
        val controller = controller()
        initialize(controller)
        assertEquals(250L, controller.mColorFadeOffAnimator!!.duration)
    }

    @Test
    fun preferenceReloadReadsTheCurrentAnimatorAndReplacesOwnerRegistration() {
        val runtime = install(420)
        val controller = controller()
        initialize(controller)
        val oldAnimator = controller.mColorFadeOffAnimator!!
        initialize(controller)
        val currentAnimator = controller.mColorFadeOffAnimator!!
        assertNotSame(oldAnimator, currentAnimator)
        assertEquals(1, observerCount())
        runtime.prefs["system_screenanim_duration"] = 640
        ModuleHelper.handlePreferenceChanged("system_screenanim_duration")
        assertEquals(640L, currentAnimator.duration)
        assertEquals(420L, oldAnimator.duration)
        assertEquals(250L, controller.mColorFadeOnAnimator!!.duration)
        runtime.prefs["system_screenanim_duration"] = 900
        ModuleHelper.handlePreferenceChanged("system_other")
        assertEquals(640L, currentAnimator.duration)
    }

    @Test
    fun preferenceChangesDoNotReinstallTheHook() {
        val runtime = install()
        runtime.prefs["system_screenanim_duration"] = 600
        assertTrue(FeatureDispatcher.installById("screenAnim", runtime))
        assertEquals(1, FakeXposedInterface.recordedHooks.size)
    }

    @Test
    fun nativeFailurePropagatesOnceAndRegistersNoObserver() {
        install()
        val failure = IllegalStateException("native initialization failed")
        val controller = controller().apply { initializeFailure = failure }
        val thrown = assertThrows(IllegalStateException::class.java) { initialize(controller) }
        assertSame(failure, thrown)
        assertEquals(1, controller.initializeCalls)
        assertEquals(0, observerCount())
    }

    @Test
    fun wrappedFatalNativeFailureIsNotSwallowed() {
        install()
        val failure = InternalError("native fatal")
        val controller = controller().apply { initializeFailure = RuntimeException(failure) }
        val thrown = assertThrows(InternalError::class.java) { initialize(controller) }
        assertSame(failure, thrown)
        assertEquals(1, controller.initializeCalls)
        assertEquals(0, observerCount())
    }

}
