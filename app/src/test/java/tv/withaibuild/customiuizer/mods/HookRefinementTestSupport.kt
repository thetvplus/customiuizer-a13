package tv.withaibuild.customiuizer.mods

import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModuleInterface.SystemServerStartingParam
import tv.withaibuild.customiuizer.MainModule
import tv.withaibuild.customiuizer.mods.catalog.FeatureDispatcher
import tv.withaibuild.customiuizer.mods.catalog.FeatureRuntime
import tv.withaibuild.customiuizer.utils.FakeXposedInterface
import tv.withaibuild.customiuizer.utils.PrefMap
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.Proxy

internal fun refinementRuntime(prefs: PrefMap<String, Any>): FeatureRuntime {
    MainModule.mPrefs = prefs
    val loader = FakeXposedInterface::class.java.classLoader!!
    val param = Proxy.newProxyInstance(loader, arrayOf(SystemServerStartingParam::class.java)) { _, method, _ ->
        when (method.name) {
            "getClassLoader" -> loader
            "getProcessName" -> "android"
            else -> null
        }
    } as SystemServerStartingParam
    return FeatureDispatcher.createRuntime("android", param, loader, prefs)
}

/** Explicitly dispatch nested synthetic calls; the JVM does not install real ART hooks. */
internal fun invokeRecordedRomHook(owner: Any, name: String, args: List<Any?>,
    proceed: ((List<Any?>) -> Any?)? = null): Any? {
    val record = requireNotNull(FakeXposedInterface.findHook(owner.javaClass.name, name))
    val chain = Proxy.newProxyInstance(FakeXposedInterface::class.java.classLoader,
        arrayOf(XposedInterface.Chain::class.java)) { _, method, forwarded ->
        when (method.name) {
            "getExecutable" -> record.executable
            "getThisObject" -> owner
            "getArgs" -> args
            "proceed" -> {
                val actual = (forwarded?.firstOrNull() as? Array<*>)?.toList() ?: args
                if (proceed != null) proceed(actual) else try {
                    (record.executable as Method).apply { isAccessible = true }.invoke(owner, *actual.toTypedArray())
                } catch (wrapped: InvocationTargetException) {
                    throw wrapped.targetException
                }
            }
            else -> null
        }
    } as XposedInterface.Chain
    return record.hook.intercept(chain)
}
