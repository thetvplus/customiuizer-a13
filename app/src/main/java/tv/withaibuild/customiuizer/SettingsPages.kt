package tv.withaibuild.customiuizer

import tv.withaibuild.customiuizer.subs.*
import tv.withaibuild.customiuizer.subs.System

/** Existing detail destinations, shared by search and the coverage gate. Keys stay unchanged. */
internal object SettingsPages {
    data class Page(val xml: Int, val entry: String, val create: () -> SubFragment,
        val standalone: Boolean = false, val dynamic: Boolean = false)

    val details = listOf(
        Page(R.xml.prefs_system_autobrightness, "pref_key_system_autobrightness_cat", ::System_AutoBrightness),
        Page(R.xml.prefs_system_visualizer, "pref_key_system_visualizer_cat", ::System_Visualizer),
        Page(R.xml.prefs_system_detailednetspeed, "pref_key_system_detailednetspeed_cat", ::System, true),
        Page(R.xml.prefs_system_statusbar_batterytempandcurrent, "pref_key_system_statusbar_batterytempandcurrent_cat", ::System, true, true),
        Page(R.xml.prefs_system_statusbar_showdevicetemperature, "prefs_system_statusbar_showdevicetemperature_cat", ::System, true, true),
        Page(R.xml.prefs_system_statusbar_batterystyle, "pref_key_system_statusbar_batterystyle_cat", ::System, true),
        Page(R.xml.prefs_system_statusbar_mobilesignal, "pref_key_system_statusbar_mobile_signal_cat", ::System, true, true),
        Page(R.xml.prefs_system_hideicons, "pref_key_system_statusbaricons_cat", ::System, true, true),
        Page(R.xml.prefs_system_statusbar_righticons, "pref_key_system_statusbaricons_atright_cat", ::System, true, true),
        Page(R.xml.prefs_system_statusbar_clock, "pref_key_system_statusbar_clocktweak_cat", ::System, true, true),
        Page(R.xml.prefs_system_batteryindicator, "pref_key_system_batteryindicator_cat", ::System_BatteryIndicator),
        Page(R.xml.prefs_system_statusbarcontrols, "pref_key_system_statusbarcontrols_cat", ::System_StatusbarControls),
        Page(R.xml.prefs_system_controlcenter_clock, "pref_key_system_cc_clocktweak_cat", ::System, true, true),
        Page(R.xml.prefs_system_noscreenlock, "pref_key_system_noscreenlock_cat", ::System_NoScreenLock),
        Page(R.xml.prefs_system_lockscreenshortcuts, "pref_key_system_lockscreenshortcuts_cat", ::System_LockScreenShortcuts),
        Page(R.xml.prefs_system_albumartonlock, "pref_key_system_albumartonlock_cat", ::SubFragment),
        Page(R.xml.prefs_system_charginginfo, "pref_key_system_charginginfo_cat", ::SubFragment),
        Page(R.xml.prefs_system_alarmonlock, "pref_key_system_lsalarm_cat", ::SubFragment),
        Page(R.xml.prefs_system_secureqs, "pref_key_system_secureqs_cat", ::SubFragment),
        Page(R.xml.prefs_system_screenshot, "pref_key_system_screenshot_cat", ::System_ScreenshotConfig),
        Page(R.xml.prefs_system_airplanemode, "pref_key_system_airplanemodeconfig", ::System_AirplaneModeConfig),
        Page(R.xml.prefs_various_calluibright, "pref_key_various_calluibright_cat", ::Various_CallUIBright),
        Page(R.xml.prefs_various_hiddenfeatures, "pref_key_various_hiddenfeatures_cat", ::Various_HiddenFeatures),
    )
}
