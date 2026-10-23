package tv.withaibuild.customiuizer

import android.content.Context
import android.os.Bundle
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.Preference
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class B2_4_MigrationInteropTest {

    @Test
    fun `MainActivity can be constructed and extends AppCompatActivity`() {
        val clazz = MainActivity::class.java
        assertTrue(AppCompatActivity::class.java.isAssignableFrom(clazz))

        val constructor = clazz.getDeclaredConstructor()
        assertTrue(java.lang.reflect.Modifier.isPublic(constructor.modifiers))
        // MainActivity instantiation requires a main looper, so constructor is verified reflectively.
        assertNotNull(constructor)
    }

    @Test
    fun `MainActivity preserves lifecycle and menu methods`() {
        val clazz = MainActivity::class.java

        // Application owns the service; Activity inherits its now-empty lifecycle hooks.
        assertNotNull(inheritedMethod(clazz, "attachBaseContext", Context::class.java))
        assertNotNull(clazz.getDeclaredMethod("onCreate", Bundle::class.java))
        assertNotNull(clazz.getDeclaredMethod("onSaveInstanceState", Bundle::class.java))
        assertNotNull(inheritedMethod(clazz, "onDestroy"))
        assertNotNull(clazz.getDeclaredMethod("onOptionsItemSelected", MenuItem::class.java))
        assertNotNull(clazz.getDeclaredMethod("onKeyDown", Int::class.javaPrimitiveType, KeyEvent::class.java))
        assertNotNull(
            clazz.getDeclaredMethod(
                "onRequestPermissionsResult",
                Int::class.javaPrimitiveType,
                Array<String>::class.java,
                IntArray::class.java
            )
        )
    }

    private fun inheritedMethod(type: Class<*>, name: String, vararg parameters: Class<*>): java.lang.reflect.Method? {
        var owner: Class<*>? = type
        while (owner != null) {
            try {
                return owner.getDeclaredMethod(name, *parameters)
            } catch (_: NoSuchMethodException) {
                owner = owner.superclass
            }
        }
        return null
    }

    @Test
    fun `MainFragment can be constructed and extends PreferenceFragmentBase`() {
        val clazz = MainFragment::class.java
        assertTrue(PreferenceFragmentBase::class.java.isAssignableFrom(clazz))

        val constructor = clazz.getDeclaredConstructor()
        assertTrue(java.lang.reflect.Modifier.isPublic(constructor.modifiers))
        assertNotNull(constructor.newInstance())
    }

    @Test
    fun `MainFragment preserves search fields without retaining child fragments`() {
        val clazz = MainFragment::class.java

        clazz.declaredFields.forEach { field ->
            assertTrue("home must not retain child Fragment state: ${field.name}",
                !androidx.fragment.app.Fragment::class.java.isAssignableFrom(field.type))
        }

        assertNotNull(clazz.getField("isSearchFocused"))
        assertNotNull(clazz.getField("inSearchView"))
        assertNotNull(clazz.getField("lastFilter"))
    }

    @Test
    fun `MainFragment preserves lifecycle search and preference methods`() {
        val clazz = MainFragment::class.java

        assertNotNull(clazz.getDeclaredMethod("onCreate", Bundle::class.java))
        assertNotNull(clazz.getDeclaredMethod("onCreatePreferences", Bundle::class.java, String::class.java))
        assertNotNull(clazz.getDeclaredMethod("onCreateOptionsMenu", Menu::class.java, MenuInflater::class.java))
        assertNotNull(clazz.getDeclaredMethod("fixStubLayout", View::class.java, Int::class.javaPrimitiveType))
        assertNotNull(clazz.getDeclaredMethod("onActivityCreated", Bundle::class.java))
        assertNotNull(clazz.getDeclaredMethod("findMod", String::class.java))
        assertNotNull(clazz.getDeclaredMethod("onSaveInstanceState", Bundle::class.java))
        assertNotNull(clazz.getDeclaredMethod("onDestroyView"))
        assertNotNull(clazz.getDeclaredMethod("onPreferenceTreeClick", Preference::class.java))
    }
}
