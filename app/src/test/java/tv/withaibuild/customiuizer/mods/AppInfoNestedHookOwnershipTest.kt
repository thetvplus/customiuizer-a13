package tv.withaibuild.customiuizer.mods

import com.miui.appmanager.AMAppInfomationActivity
import com.miui.appmanager.fragment.AMAppInformationFragment
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import tv.withaibuild.customiuizer.MainModule
import tv.withaibuild.customiuizer.mods.utils.ModuleHelper
import tv.withaibuild.customiuizer.mods.utils.XposedHelpers
import tv.withaibuild.customiuizer.utils.FakeXposedInterface
import tv.withaibuild.customiuizer.utils.PrefMap
import java.lang.reflect.Proxy

/**
 * B2B-D3 behavioral coverage: one onPreferenceTreeClick interceptor that
 * uses the current fragment's Activity / PackageInfo, not the first Activity.
 */
class AppInfoNestedHookOwnershipTest {

    private val parentClassLoader: ClassLoader
        get() = javaClass.classLoader!!

    @Before
    fun setUp() {
        FakeXposedInterface.reset()
        XposedHelpers.moduleInst = FakeXposedInterface.create()
        MainModule.mPrefs = PrefMap()
        ModuleHelper.mModuleContext = null
    }

    @After
    fun tearDown() {
        ModuleHelper.mModuleContext = null
        FakeXposedInterface.reset()
        XposedHelpers.moduleInst = null
        MainModule.mPrefs = PrefMap()
    }

    @Test
    fun secondActivityClickUsesCurrentPackageInfo_andHookInstallsOnce() {
        Various.AppInfoHook(lpparam())

        val onCreate = requireNotNull(
            FakeXposedInterface.findHook(AMAppInformationFragment::class.java.name, "onCreatePreferences")
        )
        assertEquals(1, clickHookCount())

        val activityA = AMAppInfomationActivity()
        val fragA = AMAppInformationFragment()
        fragA.host = activityA
        fragA.mPackageInfo = AMAppInformationFragment.packageInfo(
            "pkg.a",
            "/data/app/a.apk",
            "/data/data/pkg.a"
        )
        FakeXposedInterface.executeAfter(onCreate, fragA)

        assertEquals(1, clickHookCount())
        assertTrue(fragA.addedKeys.containsAll(listOf("apk_filename", "open_in_store", "launch_app")))

        val activityB = AMAppInfomationActivity()
        val fragB = AMAppInformationFragment()
        fragB.host = activityB
        fragB.mPackageInfo = AMAppInformationFragment.packageInfo(
            "pkg.b",
            "/data/app/b.apk",
            "/data/data/pkg.b"
        )
        FakeXposedInterface.executeAfter(onCreate, fragB)

        assertEquals("repeat onCreate must not add another click interceptor", 1, clickHookCount())
        assertTrue(fragB.addedKeys.contains("apk_filename"))

        val click = FakeXposedInterface.recordedHooks.find {
            it.executable.declaringClass == AMAppInformationFragment::class.java &&
                it.executable.name == "onPreferenceTreeClick"
        }
        assertNotNull(click)

        FakeXposedInterface.executeBefore(click!!, fragB, FakePreference("open_in_store", "store"))

        assertEquals("Activity A must not receive B's click", 0, activityA.started.size)
        assertEquals(1, activityB.started.size)
        val data = activityB.started[0].getData().toString()
        assertTrue("click must use B's package name, not A's", data.contains("pkg.b"))
        assertFalse(data.contains("pkg.a"))
    }

    @Test
    fun fragmentWithoutOwnerCannotPublishRowsOrHandleClicks() {
        Various.AppInfoHook(lpparam())
        val create = requireNotNull(
            FakeXposedInterface.findHook(AMAppInformationFragment::class.java.name, "onCreatePreferences")
        )
        val click = requireNotNull(
            FakeXposedInterface.findHook(AMAppInformationFragment::class.java.name, "onPreferenceTreeClick")
        )
        val detached = AMAppInformationFragment().apply {
            mPackageInfo = AMAppInformationFragment.packageInfo("pkg.a", "/a.apk", "/a")
        }
        FakeXposedInterface.executeAfter(create, detached)
        assertTrue(detached.addedKeys.isEmpty())
        assertEquals(null, FakeXposedInterface.executeBefore(click, detached, FakePreference("open_in_store", "store")))
    }

    private fun clickHookCount(): Int {
        return FakeXposedInterface.recordedHooks.count { it.executable.name == "onPreferenceTreeClick" }
    }

    private fun lpparam(): PackageReadyParam {
        return Proxy.newProxyInstance(
            parentClassLoader,
            arrayOf(PackageReadyParam::class.java)
        ) { _, method, _ ->
            when (method.name) {
                "getPackageName" -> "com.miui.securitycenter"
                "getProcessName" -> "com.miui.securitycenter"
                "getClassLoader" -> parentClassLoader
                else -> null
            }
        } as PackageReadyParam
    }

    class FakePreference(private val key: String, private val title: String) {
        fun getKey(): String = key
        fun getTitle(): CharSequence = title
    }

}
