package tv.withaibuild.customiuizer

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import org.junit.Assert.*
import org.junit.Test
import sun.misc.Unsafe
import tv.withaibuild.customiuizer.utils.Helpers

/** Real navigation guards and resume state; this does not simulate Fragment rendering. */
class SettingsNavigationGuardTest {
    private val unsafe = Unsafe::class.java.getDeclaredField("theUnsafe").run {
        isAccessible = true; get(null) as Unsafe
    }

    private fun fragment(added: Boolean, saved: Boolean, pending: Boolean): PreferenceFragmentBase {
        val fragment = unsafe.allocateInstance(PreferenceFragmentBase::class.java) as PreferenceFragmentBase
        fun field(owner: Class<*>, name: String, value: Any?) {
            owner.getDeclaredField(name).apply { isAccessible = true }.set(fragment, value)
        }
        val manager = unsafe.allocateInstance(Class.forName("androidx.fragment.app.FragmentManagerImpl")) as FragmentManager
        FragmentManager::class.java.getDeclaredField("mStateSaved").apply { isAccessible = true }.setBoolean(manager, saved)
        field(Fragment::class.java, "mFragmentManager", manager)
        field(Fragment::class.java, "mAdded", added)
        field(Fragment::class.java, "mHost", unsafe.allocateInstance(Class.forName("androidx.fragment.app.FragmentActivity\$HostCallbacks")))
        field(PreferenceFragmentBase::class.java, "navigationPending", pending)
        return fragment
    }

    @Test fun savedDetachedOrPendingNavigationNeverMutatesTheDestination() {
        for ((added, saved, pending) in listOf(Triple(true, true, false), Triple(false, false, false), Triple(true, false, true))) {
            val source = fragment(added, saved, pending)
            val destination = Fragment()
            source.openSubFragment(destination, null, Helpers.SettingsType.Preference,
                Helpers.ActionBarType.HomeUp, "test", 123)
            assertNull(destination.arguments)
        }
    }

    @Test fun returningToTheSourceUnlocksTheNextNavigationAttempt() {
        val source = fragment(true, false, true)
        source.onResume()
        val pending = PreferenceFragmentBase::class.java.getDeclaredField("navigationPending").apply { isAccessible = true }
        assertFalse(pending.getBoolean(source))
    }
}
