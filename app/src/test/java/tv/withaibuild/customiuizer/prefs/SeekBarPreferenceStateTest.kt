package tv.withaibuild.customiuizer.prefs

import androidx.preference.Preference
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import sun.misc.Unsafe
import tv.withaibuild.customiuizer.utils.AppHelper
import tv.withaibuild.customiuizer.utils.FakeSharedPreferences

/** Exercises the production value path with no row bound, as when it is off screen. */
class SeekBarPreferenceStateTest {
    private val prefs = FakeSharedPreferences()
    private val previousPrefs = AppHelper.appPrefs

    @Before fun setUp() { AppHelper.appPrefs = prefs }
    @After fun tearDown() { AppHelper.appPrefs = previousPrefs }

    private fun preference(step: Int = 1): SeekBarPreference {
        // Android View inflation is outside this JVM test; initialize only the value fields.
        val field = Unsafe::class.java.getDeclaredField("theUnsafe").apply { isAccessible = true }
        val result = (field.get(null) as Unsafe).allocateInstance(SeekBarPreference::class.java) as SeekBarPreference
        Preference::class.java.getDeclaredField("mKey").apply { isAccessible = true }.set(result, "pref_key_slider")
        for ((name, value) in mapOf("mDefaultValue" to 10, "mMinValue" to 0,
            "mMaxValue" to 100, "mStepValue" to step, "mSteppedMinValue" to 0,
            "mSteppedMaxValue" to 100 / step)) {
            SeekBarPreference::class.java.getDeclaredField(name).apply { isAccessible = true }.setInt(result, value)
        }
        return result
    }

    @Test fun unboundValueReadsTheSavedPreference() {
        prefs.put("pref_key_slider", 37)
        assertEquals(37, preference().getValue())
    }

    @Test fun settingAnUnboundSliderSavesItsNewValueInsteadOfTheDefault() {
        val slider = preference()
        slider.setValue(23, true)
        assertEquals(23, slider.getValue())
        assertEquals(23, prefs.getInt("pref_key_slider", -1))
        slider.setValue(46, true)
        assertEquals(46, prefs.getInt("pref_key_slider", -1))
    }

    @Test fun programmaticValueRetainsStepAndRangeWithoutAView() {
        val slider = preference(5)
        slider.setValue(23, true)
        assertEquals(25, slider.getValue())
        assertEquals(25, prefs.getInt("pref_key_slider", -1))
        slider.setValue(500, true)
        assertEquals(100, prefs.getInt("pref_key_slider", -1))
        slider.setValue(-20, true)
        assertEquals(0, prefs.getInt("pref_key_slider", -1))
    }

    @Test fun dependentRangeChangesKeepTheValueAndDoNotCommitUntilRequested() {
        prefs.put("pref_key_slider", 40)
        val slider = preference()
        slider.setMinValue(50)
        assertEquals(50, slider.getValue())
        assertEquals(40, prefs.getInt("pref_key_slider", -1))
        slider.setValue(60)
        assertEquals(60, slider.getValue())
        assertEquals(40, prefs.getInt("pref_key_slider", -1))
        slider.setValue(60, true)
        assertEquals(60, prefs.getInt("pref_key_slider", -1))
    }
}
