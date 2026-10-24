package tv.withaibuild.customiuizer.prefs

import android.content.SharedPreferences
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

    @Test fun externalWritesAndRestoreReplaceCachedAndUnsavedValues() {
        prefs.put("pref_key_slider", 37)
        val slider = preference()
        assertEquals(37, slider.getValue())
        prefs.put("pref_key_slider", 64)
        assertEquals(64, slider.getValue())
        slider.setValue(50)
        assertEquals(50, slider.getValue())
        prefs.replaceAndNotifyAll(mapOf("pref_key_slider" to 72))
        assertEquals(72, slider.getValue())
        prefs.replaceAndNotifyAll(emptyMap())
        assertEquals(10, slider.getValue())
        assertEquals(10, preference().getValue())
    }

    @Test fun successfulSaveDoesNotMaskLaterWritesOrRecreatedPreferences() {
        val slider = preference()
        repeat(20) { slider.setValue(it, true) }
        assertEquals(19, slider.getValue())
        assertEquals(19, preference().getValue())
        prefs.put("pref_key_slider", 81)
        assertEquals(81, slider.getValue())
        assertEquals(81, preference().getValue())
    }

    @Test fun stepChangesPreserveThePreviousValueBeforeRebounding() {
        prefs.put("pref_key_slider", 40)
        val slider = preference(5)
        slider.setStepValue(10)
        assertEquals(40, slider.getValue())
        slider.setMaxValue(30)
        assertEquals(30, slider.getValue())
        slider.setStepValue(0)
        assertEquals(1, slider.getStepValue())
        assertEquals(30, slider.getValue())
        slider.setStepValue(-5)
        assertEquals(1, slider.getStepValue())
        assertEquals(40, prefs.getInt("pref_key_slider", -1))
    }

    @Test fun failedOrUnavailableWriterDoesNotRetainTheAttemptedValue() {
        prefs.put("pref_key_slider", 37)
        val slider = preference()
        AppHelper.appPrefs = object : SharedPreferences by prefs {
            override fun edit(): SharedPreferences.Editor = throw IllegalStateException("test write failure")
        }
        try {
            slider.setValue(65, true)
            org.junit.Assert.fail("write failure must propagate")
        } catch (_: IllegalStateException) { }
        assertEquals(37, slider.getValue())
        AppHelper.appPrefs = null
        slider.setValue(90, true)
        assertEquals(10, slider.getValue())
        AppHelper.appPrefs = prefs
        assertEquals(37, slider.getValue())
    }
}
