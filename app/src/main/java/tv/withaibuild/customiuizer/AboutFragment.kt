package tv.withaibuild.customiuizer

import android.content.ActivityNotFoundException
import android.content.res.Configuration
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.preference.Preference
import tv.withaibuild.customiuizer.prefs.ListPreferenceEx
import tv.withaibuild.customiuizer.utils.AppHelper
import tv.withaibuild.customiuizer.utils.AppLocaleController
import tv.withaibuild.customiuizer.utils.Helpers
import tv.withaibuild.customiuizer.utils.SettingsDiagnostics
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AboutFragment : SubFragment() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        headLayoutId = R.layout.fragment_about_head
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        super.onCreatePreferences(savedInstanceState, rootKey)
        val locale = findPreference<ListPreferenceEx>("pref_key_miuizer_locale")
        AppLocaleController.setupLocalePreference(locale, AppHelper.appPrefs)
        bindLink("about_repository", REPOSITORY_URL)
        bindLink("about_releases", "$REPOSITORY_URL/releases/latest")
        bindLink("about_support", "https://paypal.me/Jinjitv")
    }

    private fun bindLink(key: String, url: String) {
        findPreference<Preference>(key)?.setOnPreferenceClickListener {
            val host = context ?: return@setOnPreferenceClickListener false
            try {
                Helpers.openURL(host, url)
            } catch (_: ActivityNotFoundException) {
                Toast.makeText(host, R.string.about_link_unavailable, Toast.LENGTH_SHORT).show()
            }
            true
        }
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        view?.let { v ->
            updateIconVisibility(v, resources.configuration)
            try {
                val version = v.findViewById<TextView>(R.id.about_version)
                var versionName = BuildConfig.VERSION_NAME
                if (BuildConfig.BUILD_TYPE == "develop") {
                    val formatter = SimpleDateFormat("yy.MM.dd", Locale.getDefault())
                    versionName = formatter.format(Date(BuildConfig.BUILD_TIME)) + "-test"
                }
                version.text = getString(R.string.about_version, versionName)
            } catch (e: Throwable) {
                if (e is OutOfMemoryError || e is ThreadDeath || e is VirtualMachineError) throw e
                SettingsDiagnostics.failure("AboutFragment.bindVersionText", e)
            }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        view?.let { updateIconVisibility(it, newConfig) }
    }

    private fun updateIconVisibility(root: View, configuration: Configuration) {
        root.findViewById<View>(R.id.miuizer_icon)?.visibility =
            if (configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) View.GONE else View.VISIBLE
    }

    private companion object {
        const val REPOSITORY_URL = "https://github.com/thetvplus/customiuizer-a13"
    }
}
